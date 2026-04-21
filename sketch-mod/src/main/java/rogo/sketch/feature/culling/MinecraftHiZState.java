package rogo.sketch.feature.culling;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ViewportEvent;
import org.joml.Matrix4f;
import org.joml.Vector3i;
import org.jetbrains.annotations.Nullable;
import rogo.sketch.backend.opengl.OpenGLTextureHandleResource;
import rogo.sketch.core.api.Resizable;
import rogo.sketch.core.backend.BufferedResourceDescriptor;
import rogo.sketch.core.backend.BufferedResourceSet;
import rogo.sketch.core.backend.BufferingMode;
import rogo.sketch.core.backend.ResourceEpoch;
import rogo.sketch.core.driver.GraphicsDriver;
import rogo.sketch.core.resource.descriptor.ImageFormat;
import rogo.sketch.core.resource.descriptor.ImageUsage;
import rogo.sketch.core.resource.descriptor.ResolvedImageResource;
import rogo.sketch.core.resource.descriptor.SamplerFilter;
import rogo.sketch.core.resource.descriptor.SamplerWrap;
import rogo.sketch.core.resource.vision.Texture;
import rogo.sketch.core.util.KeyId;
import rogo.sketch.mixin.AccessorLevelRender;
import rogo.sketch.vanilla.McRenderContext;

import java.util.EnumSet;

public final class MinecraftHiZState {
    public static final int DEPTH_SIZE = 8;
    public static final rogo.sketch.core.util.KeyId LEVEL_SECTION_RANGE_ID = rogo.sketch.core.util.KeyId.of("level_section_range");
    public static final rogo.sketch.core.util.KeyId LEVEL_POS_RANGE_ID = rogo.sketch.core.util.KeyId.of("level_pos_range");
    public static final rogo.sketch.core.util.KeyId LEVEL_MIN_SECTION_ABS_ID = rogo.sketch.core.util.KeyId.of("level_min_section_abs");
    public static final rogo.sketch.core.util.KeyId LEVEL_MIN_POS_ID = rogo.sketch.core.util.KeyId.of("level_min_section_abs");
    private static final KeyId HIZ_DEPTH_BUFFER_FAMILY_ID = KeyId.of("sketch_render", "hiz_depth_buffer_family");
    private static final KeyId HIZ_SOURCE_SNAPSHOT_FAMILY_ID = KeyId.of("sketch_render", "hiz_source_depth_snapshot_family");

    private static final MinecraftHiZState INSTANCE = new MinecraftHiZState();

    private final Vector3i[] depthBufferInformation = new Vector3i[DEPTH_SIZE];
    private volatile Matrix4f viewMatrix = new Matrix4f();
    private volatile Matrix4f projectionMatrix = new Matrix4f().identity();
    private final BufferedResourceSet<Texture> depthBufferTargets;
    private final BufferedResourceSet<Texture> sourceDepthSnapshotTargets;
    private volatile Camera camera;
    private volatile int continueUpdateCount;
    private volatile long submittedEpoch;
    private volatile long completedEpoch;

    private MinecraftHiZState() {
        for (int i = 0; i < DEPTH_SIZE; i++) {
            depthBufferInformation[i] = new Vector3i();
        }
        depthBufferTargets = BufferedResourceSet.create(
                new BufferedResourceDescriptor(HIZ_DEPTH_BUFFER_FAMILY_ID, BufferingMode.ASYNC_PRODUCE_SYNC_CONSUME, 2, "minecraft-hiz-depth"),
                ignored -> null,
                MinecraftHiZState::disposeQuietly);
        sourceDepthSnapshotTargets = BufferedResourceSet.create(
                new BufferedResourceDescriptor(HIZ_SOURCE_SNAPSHOT_FAMILY_ID, BufferingMode.SINGLE, 1, "minecraft-hiz-source-depth"),
                ignored -> null,
                MinecraftHiZState::disposeQuietly);
    }

    public static MinecraftHiZState getInstance() {
        return INSTANCE;
    }

    public void initialize() {
        RenderSystem.recordRenderCall(this::ensureDepthTargetsForWindow);
    }

    public void onSkyStage(boolean anyNextTick, boolean nextLoop) {
        camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        if (anyNextTick && continueUpdateCount > 0) {
            continueUpdateCount--;
        }
        if (nextLoop) {
            rogo.sketch.compat.sodium.MeshResource.resourceSet().rollQueueUpdateCounters();
        }
    }

    public void populateLevelContext(McRenderContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        if (((AccessorLevelRender) minecraft.levelRenderer).getNeedsFullRenderChunkUpdate()) {
            context.set(LEVEL_SECTION_RANGE_ID, minecraft.level.getMaxSection() - minecraft.level.getMinSection());
            context.set(LEVEL_POS_RANGE_ID, minecraft.level.getMaxBuildHeight() - minecraft.level.getMinBuildHeight());
            context.set(LEVEL_MIN_SECTION_ABS_ID, Math.abs(minecraft.level.getMinSection()));
            context.set(LEVEL_MIN_POS_ID, minecraft.level.getMinBuildHeight());
        }
    }

    public void updateDepthMap(boolean anyCulling, boolean checkingCull, MinecraftShaderCapabilityService shaderService) {
        projectionMatrix = new Matrix4f(RenderSystem.getProjectionMatrix());
        if (!anyCulling || checkingCull || !continueUpdateDepth()) {
            return;
        }
        Window window = Minecraft.getInstance().getWindow();
        int width = window.getWidth();
        int height = window.getHeight();
        int totalHeight = 0;
        for (int index = 0; index < DEPTH_SIZE; ++index) {
            int scaleWidth = Math.max(1, width >> (index + 1));
            int scaleHeight = Math.max(1, height >> (index + 1));
            if ((scaleWidth & 1) == 1) {
                scaleWidth += 1;
            }
            if ((scaleHeight & 1) == 1) {
                scaleHeight += 1;
            }
            depthBufferInformation[index].set(scaleWidth, scaleHeight, totalHeight);
            totalHeight += scaleHeight;
        }
        ensureDepthTargets(depthBufferInformation[0].x, totalHeight);
        ensureSourceDepthSnapshot(width, height);

        Camera activeCamera = camera != null ? camera : Minecraft.getInstance().gameRenderer.getMainCamera();
        if (activeCamera == null) {
            return;
        }
        ViewportEvent.ComputeCameraAngles cameraSetup = net.minecraftforge.client.ForgeHooksClient.onCameraSetup(
                Minecraft.getInstance().gameRenderer,
                activeCamera,
                Minecraft.getInstance().getFrameTime());
        PoseStack viewMatrixStack = new PoseStack();
        Vec3 cameraPos = activeCamera.getPosition();
        viewMatrixStack.mulPose(Axis.ZP.rotationDegrees(cameraSetup.getRoll()));
        viewMatrixStack.mulPose(Axis.XP.rotationDegrees(activeCamera.getXRot()));
        viewMatrixStack.mulPose(Axis.YP.rotationDegrees(activeCamera.getYRot() + 180.0F));
        viewMatrixStack.translate((float) -cameraPos.x, (float) -cameraPos.y, (float) -cameraPos.z);
        viewMatrix = new Matrix4f(viewMatrixStack.last().pose());
    }

    public void useShader(ShaderInstance instance) {
        RenderSystem.setShader(() -> instance);
    }

    public void updating() {
        continueUpdateCount = 10;
    }

    public boolean continueUpdateDepth() {
        return continueUpdateCount > 0;
    }

    public Camera camera() {
        return camera;
    }

    public Matrix4f viewMatrix() {
        return new Matrix4f(viewMatrix);
    }

    public Matrix4f projectionMatrix() {
        return new Matrix4f(projectionMatrix);
    }

    public void setProjectionMatrix(Matrix4f projectionMatrix) {
        this.projectionMatrix = projectionMatrix != null ? new Matrix4f(projectionMatrix) : new Matrix4f().identity();
    }

    public @Nullable Texture depthBufferTarget() {
        return depthBufferTargets.readResource();
    }

    public @Nullable Texture writeDepthBufferTarget() {
        return depthBufferTargets.writeResource();
    }

    public @Nullable Texture sourceDepthSnapshotTexture() {
        return sourceDepthSnapshotTargets.readResource();
    }

    public int depthBufferTargetWidth() {
        Texture texture = depthBufferTarget();
        return texture != null ? texture.getCurrentWidth() : 0;
    }

    public int depthBufferTargetHeight() {
        Texture texture = depthBufferTarget();
        return texture != null ? texture.getCurrentHeight() : 0;
    }

    public int depthBufferTargetHandle() {
        Texture texture = depthBufferTarget();
        if (texture instanceof OpenGLTextureHandleResource handleResource) {
            return handleResource.textureHandle();
        }
        return 0;
    }

    public Vector3i[] depthBufferInformation() {
        Vector3i[] copy = new Vector3i[DEPTH_SIZE];
        for (int i = 0; i < DEPTH_SIZE; i++) {
            copy[i] = new Vector3i(depthBufferInformation[i]);
        }
        return copy;
    }

    public void onAsyncHiZSubmitted(long epoch) {
        submittedEpoch = Math.max(submittedEpoch, epoch);
    }

    public void onAsyncHiZCompleted(long epoch) {
        if (epoch < submittedEpoch) {
            return;
        }
        depthBufferTargets.promote(new ResourceEpoch(epoch));
        completedEpoch = epoch;
    }

    public long completedEpoch() {
        return completedEpoch;
    }

    public void clearTransientState() {
        submittedEpoch = 0L;
        completedEpoch = 0L;
        continueUpdateCount = 0;
        camera = null;
        viewMatrix = new Matrix4f();
        projectionMatrix = new Matrix4f().identity();
        depthBufferTargets.resetState();
        sourceDepthSnapshotTargets.resetState();
    }

    private void ensureDepthTargetsForWindow() {
        Minecraft minecraft = Minecraft.getInstance();
        Window window = minecraft.getWindow();
        if (window == null) {
            return;
        }
        int width = Math.max(1, window.getWidth());
        int height = Math.max(1, window.getHeight());
        int totalHeight = computePackedHiZHeight(width, height);
        ensureDepthTargets(computeFirstLevelWidth(width), totalHeight);
        ensureSourceDepthSnapshot(width, height);
    }

    private void ensureDepthTargets(int width, int height) {
        if (!GraphicsDriver.isBootstrapped()) {
            return;
        }
        int safeWidth = Math.max(1, width);
        int safeHeight = Math.max(1, height);
        Texture readTexture = depthBufferTargets.readResource();
        Texture writeTexture = depthBufferTargets.writeResource();
        if (needsRecreate(readTexture, safeWidth, safeHeight) || needsRecreate(writeTexture, safeWidth, safeHeight)) {
            depthBufferTargets.recreate(slot -> createDepthTarget(slot, safeWidth, safeHeight));
            return;
        }
        resizeIfNeeded(readTexture, safeWidth, safeHeight);
        resizeIfNeeded(writeTexture, safeWidth, safeHeight);
    }

    private Texture createDepthTarget(int index, int width, int height) {
        return GraphicsDriver.resourceAllocator().createTexture(
                KeyId.of("sketch_render", "hiz_depth_texture_" + index),
                new ResolvedImageResource(
                        KeyId.of("sketch_render", "hiz_depth_texture_" + index),
                        width,
                        height,
                        1,
                        ImageFormat.R16_FLOAT,
                        EnumSet.of(ImageUsage.SAMPLED, ImageUsage.STORAGE, ImageUsage.TRANSFER_SRC, ImageUsage.TRANSFER_DST),
                        SamplerFilter.NEAREST,
                        SamplerFilter.NEAREST,
                        null,
                        SamplerWrap.CLAMP_TO_EDGE,
                        SamplerWrap.CLAMP_TO_EDGE,
                        null),
                null,
                null);
    }

    private void ensureSourceDepthSnapshot(int width, int height) {
        if (!GraphicsDriver.isBootstrapped()) {
            return;
        }
        int safeWidth = Math.max(1, width);
        int safeHeight = Math.max(1, height);
        Texture current = sourceDepthSnapshotTargets.readResource();
        if (needsRecreate(current, safeWidth, safeHeight)) {
            sourceDepthSnapshotTargets.recreate(ignored -> createSourceDepthSnapshotTarget(safeWidth, safeHeight));
            return;
        }
        resizeIfNeeded(current, safeWidth, safeHeight);
    }

    private Texture createSourceDepthSnapshotTarget(int width, int height) {
        return GraphicsDriver.resourceAllocator().createTexture(
                KeyId.of("sketch_render", "hiz_source_depth_snapshot_texture"),
                new ResolvedImageResource(
                        KeyId.of("sketch_render", "hiz_source_depth_snapshot_texture"),
                        width,
                        height,
                        1,
                        ImageFormat.R32_FLOAT,
                        EnumSet.of(ImageUsage.SAMPLED, ImageUsage.STORAGE, ImageUsage.TRANSFER_SRC, ImageUsage.TRANSFER_DST),
                        SamplerFilter.NEAREST,
                        SamplerFilter.NEAREST,
                        null,
                        SamplerWrap.CLAMP_TO_EDGE,
                        SamplerWrap.CLAMP_TO_EDGE,
                        null),
                null,
                null);
    }

    private static int computeFirstLevelWidth(int width) {
        int value = Math.max(1, width >> 1);
        return (value & 1) == 1 ? value + 1 : value;
    }

    private static int computePackedHiZHeight(int width, int height) {
        int totalHeight = 0;
        for (int index = 0; index < DEPTH_SIZE; ++index) {
            int scaleWidth = Math.max(1, width >> (index + 1));
            int scaleHeight = Math.max(1, height >> (index + 1));
            if ((scaleWidth & 1) == 1) {
                scaleWidth += 1;
            }
            if ((scaleHeight & 1) == 1) {
                scaleHeight += 1;
            }
            totalHeight += scaleHeight;
        }
        return Math.max(1, totalHeight);
    }

    private static boolean needsRecreate(@Nullable Texture texture, int width, int height) {
        if (texture == null || texture.isDisposed()) {
            return true;
        }
        return !(texture instanceof Resizable)
                && (texture.getCurrentWidth() != width || texture.getCurrentHeight() != height);
    }

    private static void resizeIfNeeded(@Nullable Texture texture, int width, int height) {
        if (texture instanceof Resizable resizable) {
            resizable.resize(width, height);
        }
    }

    private static void disposeQuietly(@Nullable Texture texture) {
        if (texture != null && !texture.isDisposed()) {
            texture.dispose();
        }
    }
}
