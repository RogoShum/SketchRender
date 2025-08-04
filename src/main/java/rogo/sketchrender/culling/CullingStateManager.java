package rogo.sketchrender.culling;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector2i;
import org.lwjgl.opengl.GL43;
import rogo.sketchrender.SketchRender;
import rogo.sketchrender.api.Config;
import rogo.sketchrender.api.DefaultShaderLoader;
import rogo.sketchrender.compat.sodium.MeshUniform;
import rogo.sketchrender.compat.sodium.SodiumSectionAsyncUtil;
import rogo.sketchrender.mixin.AccessorLevelRender;
import rogo.sketchrender.mixin.AccessorMinecraft;
import rogo.sketchrender.shader.ComputeShader;
import rogo.sketchrender.shader.ShaderManager;
import rogo.sketchrender.shader.ShaderModifier;
import rogo.sketchrender.util.DepthContext;
import rogo.sketchrender.util.OcclusionCullerThread;
import rogo.sketchrender.util.ShaderLoader;

import java.util.HashMap;
import java.util.function.Consumer;

import static org.lwjgl.opengl.GL11.GL_TEXTURE;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL42C.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT;

public class CullingStateManager {
    public static EntityCullingMask ENTITY_CULLING_MASK = null;
    public static Matrix4f VIEW_MATRIX = new Matrix4f();
    public static Matrix4f PROJECTION_MATRIX = new Matrix4f();

    static {
        PROJECTION_MATRIX.identity();
    }

    public static final int DEPTH_SIZE = 8;
    public static int DEPTH_INDEX;
    public static int MAIN_DEPTH_TEXTURE = 0;
    public static RenderTarget[] DEPTH_BUFFER_TARGET = new RenderTarget[DEPTH_SIZE];
    public static Frustum FRUSTUM;
    public static boolean updatingDepth;
    public static boolean applyFrustum;
    public static int DEBUG = 0;
    public static ShaderLoader SHADER_LOADER = null;

    private static boolean[] nextTick = new boolean[20];
    public static int fps = 0;
    private static int tick = 0;
    public static int clientTickCount = 0;
    public static int entityCulling = 0;
    public static int entityCount = 0;
    public static int blockCulling = 0;
    public static int blockCount = 0;
    public static long entityCullingTime = 0;
    public static long blockCullingTime = 0;
    public static long chunkCullingTime = 0;
    private static long preEntityCullingTime = 0;
    private static long preBlockCullingTime = 0;
    private static long preChunkCullingTime = 0;
    public static long preApplyFrustumTime = 0;
    public static long applyFrustumTime = 0;
    public static long entityCullingInitTime = 0;
    public static long preEntityCullingInitTime = 0;
    public static int cullingInitCount = 0;
    public static int preCullingInitCount = 0;
    public static boolean checkCulling = false;
    public static boolean checkTexture = false;
    private static boolean usingShader = false;
    public static int fullChunkUpdateCooldown = 0;
    public static int LEVEL_SECTION_RANGE;
    public static int LEVEL_POS_RANGE;
    public static int LEVEL_MIN_SECTION_ABS;
    public static int LEVEL_MIN_POS;
    public static Camera CAMERA;
    private static final HashMap<Integer, Integer> SHADER_DEPTH_BUFFER_ID = new HashMap<>();
    public static volatile boolean useOcclusionCulling = true;
    private static int continueUpdateCount;

    static {
        RenderSystem.recordRenderCall(() -> {
            for (int i = 0; i < DEPTH_BUFFER_TARGET.length; ++i) {
                DEPTH_BUFFER_TARGET[i] = new HizTarget(Minecraft.getInstance().getWindow().getWidth(), Minecraft.getInstance().getWindow().getHeight(), true);
                DEPTH_BUFFER_TARGET[i].setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
                DEPTH_BUFFER_TARGET[i].clear(Minecraft.ON_OSX);
            }
        });
    }

    public static void init() {
        if (SketchRender.hasIris()) {
            try {
                SHADER_LOADER = Class.forName("rogo.sketchrender.util.IrisLoaderImpl").asSubclass(ShaderLoader.class).newInstance();
            } catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        } else {
            SHADER_LOADER = new DefaultShaderLoader();
        }

        ShaderModifier.loadAll(Minecraft.getInstance().getResourceManager());
    }

    public static void onWorldUnload(Level world) {
        if (world != Minecraft.getInstance().level) {
            cleanup();
        }
    }

    public static void cleanup() {
        tick = 0;
        clientTickCount = 0;

        if (ENTITY_CULLING_MASK != null) {
            ENTITY_CULLING_MASK.cleanup();
            ENTITY_CULLING_MASK = null;
        }
        SHADER_DEPTH_BUFFER_ID.clear();
        SketchRender.pauseAsync();
        if (SketchRender.hasSodium()) {
            SodiumSectionAsyncUtil.pauseAsync();
        }
    }

    public static boolean shouldSkipBlockEntity(BlockEntity blockEntity, AABB aabb, BlockPos pos) {
        blockCount++;
        //for valkyrien skies
        if (CAMERA.getPosition().distanceToSqr(pos.getX(), pos.getY(), pos.getZ()) >
                Minecraft.getInstance().options.getEffectiveRenderDistance() * 16 * Minecraft.getInstance().options.getEffectiveRenderDistance() * 16 * 2) {
            return false;
        }

        if (ENTITY_CULLING_MASK == null || !Config.getCullBlockEntity()) return false;
        String type = BlockEntityType.getKey(blockEntity.getType()).toString();
        if (Config.getBlockEntitiesSkip().contains(type))
            return false;

        if (!ENTITY_CULLING_MASK.isObjectVisible(blockEntity)) {
            blockCulling++;
            return true;
        }

        return false;
    }

    public static boolean shouldSkipEntity(Entity entity) {
        entityCount++;
        if (entity instanceof Player || entity.isCurrentlyGlowing()) return false;
        if (entity.distanceToSqr(CAMERA.getPosition()) < 4) return false;
        if (Config.getEntitiesSkip().contains(entity.getType().getDescriptionId()))
            return false;
        if (ENTITY_CULLING_MASK == null || !Config.getCullEntity()) return false;

        if (!ENTITY_CULLING_MASK.isObjectVisible(entity)) {
            entityCulling++;
            return true;
        }

        return false;
    }

    public static void onProfilerPopPush(String s) {
        switch (s) {
            case "beforeRunTick" -> {
                if (((AccessorLevelRender) Minecraft.getInstance().levelRenderer).getNeedsFullRenderChunkUpdate() && Minecraft.getInstance().level != null) {
                    if (SketchRender.hasMod("embeddium")) {
                        SketchRender.pauseAsync();
                    }
                    LEVEL_SECTION_RANGE = Minecraft.getInstance().level.getMaxSection() - Minecraft.getInstance().level.getMinSection();
                    LEVEL_MIN_SECTION_ABS = Math.abs(Minecraft.getInstance().level.getMinSection());
                    LEVEL_MIN_POS = Minecraft.getInstance().level.getMinBuildHeight();
                    LEVEL_POS_RANGE = Minecraft.getInstance().level.getMaxBuildHeight() - Minecraft.getInstance().level.getMinBuildHeight();
                }
            }
            case "afterRunTick" -> {
                updateMapData();
                OcclusionCullerThread.shouldUpdate();
            }
            case "captureFrustum" -> {
                AccessorLevelRender levelFrustum = (AccessorLevelRender) Minecraft.getInstance().levelRenderer;
                Frustum frustum;
                if (levelFrustum.getCapturedFrustum() != null) {
                    frustum = levelFrustum.getCapturedFrustum();
                } else {
                    frustum = levelFrustum.getCullingFrustum();
                }
                CullingStateManager.FRUSTUM = new Frustum(frustum).offsetToFullyIncludeCameraCube(8);
                checkShader();
                CullingRenderEvent.updateChunkCullingMap();
            }
            case "destroyProgress" -> {
                updatingDepth = true;
                updateDepthMap();
                CullingRenderEvent.updateEntityCullingMap();
                updatingDepth = false;
            }
        }
    }

    public static void onProfilerPush(String s) {
        if (s.equals("onKeyboardInput")) {
            SketchRender.onKeyPress();
        } else if (s.equals("center")) {
            CAMERA = Minecraft.getInstance().gameRenderer.getMainCamera();
            int thisTick = clientTickCount % 20;
            nextTick = new boolean[20];

            if (tick != thisTick) {
                tick = thisTick;
                nextTick[thisTick] = true;
            }

            entityCulling = 0;
            entityCount = 0;
            blockCulling = 0;
            blockCount = 0;

            if (anyNextTick()) {
                if (CullingStateManager.ENTITY_CULLING_MASK != null) {
                    CullingStateManager.ENTITY_CULLING_MASK.swapBuffer(clientTickCount);
                }

                if (fullChunkUpdateCooldown > 0) {
                    fullChunkUpdateCooldown--;
                }

                if (continueUpdateCount > 0) {
                    continueUpdateCount--;
                }
            }

            if (isNextLoop()) {
                applyFrustumTime = preApplyFrustumTime;
                preApplyFrustumTime = 0;

                entityCullingTime = preEntityCullingTime;
                preEntityCullingTime = 0;

                blockCullingTime = preBlockCullingTime;
                preBlockCullingTime = 0;

                cullingInitCount = preCullingInitCount;
                preCullingInitCount = 0;

                entityCullingInitTime = preEntityCullingInitTime;
                preEntityCullingInitTime = 0;

                MeshUniform.lastQueueUpdateCount = MeshUniform.queueUpdateCount;
                MeshUniform.queueUpdateCount = 0;

                if (preChunkCullingTime != 0) {
                    chunkCullingTime = preChunkCullingTime;
                    preChunkCullingTime = 0;
                }
            }
        }
    }

    public static void checkShader() {
        if (SHADER_LOADER != null) {
            boolean clear = false;
            if (SHADER_LOADER.enabledShader() && !usingShader) {
                clear = true;
                usingShader = true;
            }

            if (!SHADER_LOADER.enabledShader() && usingShader) {
                clear = true;
                usingShader = false;
            }

            if (clear) {
                cleanup();
            }
        }
    }

    public static void updateDepthMap() {
        CullingStateManager.PROJECTION_MATRIX = new Matrix4f(RenderSystem.getProjectionMatrix());
        if (anyCulling() && !checkCulling && continueUpdateDepth()) {
            Window window = Minecraft.getInstance().getWindow();
            int width = window.getWidth();
            int height = window.getHeight();

            runOnDepthFrame((depthContext) -> {
                int scaleWidth = Math.max(1, width >> (depthContext.index() + 1));
                int scaleHeight = Math.max(1, height >> (depthContext.index() + 1));
                if (depthContext.frame().width != scaleWidth || depthContext.frame().height != scaleHeight) {
                    depthContext.frame().resize(scaleWidth, scaleHeight, Minecraft.ON_OSX);
                }
            });

            int depthTexture = Minecraft.getInstance().getMainRenderTarget().getDepthTextureId();
            if (SHADER_LOADER.enabledShader()) {
                if (!SHADER_DEPTH_BUFFER_ID.containsKey(SHADER_LOADER.getFrameBufferID())) {
                    RenderSystem.assertOnRenderThreadOrInit();
                    GlStateManager._glBindFramebuffer(GL_FRAMEBUFFER, SHADER_LOADER.getFrameBufferID());

                    int attachmentType = GL_DEPTH_ATTACHMENT;
                    int[] attachmentObjectType = new int[1];
                    glGetFramebufferAttachmentParameteriv(GL_FRAMEBUFFER, attachmentType, GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE, attachmentObjectType);

                    if (attachmentObjectType[0] == GL_TEXTURE) {
                        int[] depthTextureID = new int[1];
                        glGetFramebufferAttachmentParameteriv(GL_FRAMEBUFFER, attachmentType, GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME, depthTextureID);
                        depthTexture = depthTextureID[0];
                        SHADER_DEPTH_BUFFER_ID.put(SHADER_LOADER.getFrameBufferID(), depthTexture);
                    }
                } else {
                    depthTexture = SHADER_DEPTH_BUFFER_ID.get(SHADER_LOADER.getFrameBufferID());
                }
            }

            MAIN_DEPTH_TEXTURE = depthTexture;

            computeHizTexture();

            bindMainFrameTarget();

            net.minecraftforge.client.event.ViewportEvent.ComputeCameraAngles cameraSetup = net.minecraftforge.client.ForgeHooksClient.onCameraSetup(Minecraft.getInstance().gameRenderer
                    , CAMERA, Minecraft.getInstance().getFrameTime());
            PoseStack viewMatrix = new PoseStack();
            Vec3 cameraPos = CAMERA.getPosition();
            viewMatrix.mulPose(Axis.ZP.rotationDegrees(cameraSetup.getRoll()));
            viewMatrix.mulPose(Axis.XP.rotationDegrees(CAMERA.getXRot()));
            viewMatrix.mulPose(Axis.YP.rotationDegrees(CAMERA.getYRot() + 180.0F));
            viewMatrix.translate((float) -cameraPos.x, (float) -cameraPos.y, (float) -cameraPos.z);
            VIEW_MATRIX = new Matrix4f(viewMatrix.last().pose());
        }
    }

    public static void computeHizTexture() {
        ComputeShader shader = Config.shouldComputeShader() ? ShaderManager.COPY_HIERARCHY_DEPTH_CS : ShaderManager.COPY_DEPTH_CS;
        shader.bind();
        shader.getUniforms().setUniform("sketch_render_distance", Minecraft.getInstance().options.getEffectiveRenderDistance());

        runOnDepthFrame((depthContext) -> {
            GL43.glBindImageTexture(depthContext.index(), depthContext.frame().getColorTextureId(), 0, false, 0, GL_READ_WRITE, GL_R16F);
            RenderSystem.activeTexture(GL43.GL_TEXTURE0 + depthContext.index());
            RenderSystem.bindTexture(depthContext.lastTexture());
        });

        RenderTarget screen = Minecraft.getInstance().getMainRenderTarget();
        shader.getUniforms().setUniform("sketch_sampler_texture_0", 0);
        shader.getUniforms().setUniform("sketch_sampler_texture_1", 1);
        shader.getUniforms().setUniform("sketch_sampler_texture_2", 2);
        shader.getUniforms().setUniform("sketch_sampler_texture_3", 3);
        shader.getUniforms().setUniform("sketch_sampler_texture_4", 4);
        shader.getUniforms().setUniform("sketch_sampler_texture_5", 5);
        shader.getUniforms().setUniform("sketch_screen_size", new Vector2i(screen.width, screen.height));
        shader.getUniforms().setUniform("sketch_liner_depth", 1);
        if (Config.shouldComputeShader()) {
            int tileSizeX = 16;
            int tileSizeY = 16;
            int groupsX = (screen.width / 2 + tileSizeX - 1) / tileSizeX;
            int groupsY = (screen.height / 2 + tileSizeY - 1) / tileSizeY;
            runOnDepthFrame((depthContext) -> {
                GL43.glBindImageTexture(depthContext.index(), depthContext.frame().getColorTextureId(), 0, false, 0, GL_READ_WRITE, GL_R16F);
                if (depthContext.index() == 0) {
                    RenderSystem.activeTexture(GL43.GL_TEXTURE0);
                    RenderSystem.bindTexture(depthContext.lastTexture());
                }
            }, 0, 4);

            shader.execute(groupsX, groupsY, 1);
            shader.memoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);

            groupsX = ((screen.width >> 4) / 2 + tileSizeX - 1) / tileSizeX;
            groupsY = ((screen.height >> 4) / 2 + tileSizeY - 1) / tileSizeY;
            runOnDepthFrame((depthContext) -> {
                GL43.glBindImageTexture(depthContext.index() - 4, depthContext.frame().getColorTextureId(), 0, false, 0, GL_READ_WRITE, GL_R16F);
                if (depthContext.index() == 4) {
                    RenderSystem.activeTexture(GL43.GL_TEXTURE0);
                    RenderSystem.bindTexture(depthContext.lastTexture());
                }
            }, 4, 8);
            shader.getUniforms().setUniform("sketch_liner_depth", 0);
            shader.getUniforms().setUniform("sketch_screen_size", new Vector2i(screen.width >> 4, screen.height >> 4));
            shader.execute(groupsX, groupsY, 1);
            shader.memoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);
        } else {
            int tileSizeX = 16;
            int tileSizeY = 16;
            int groupsX = (screen.width / 2 + tileSizeX - 1) / tileSizeX;
            int groupsY = (screen.height / 2 + tileSizeY - 1) / tileSizeY;
            shader.execute(groupsX, groupsY, 1);
            shader.memoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);
        }

        RenderSystem.activeTexture(GL43.GL_TEXTURE0);
        GL43.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 0, 0);
    }

    public static void computeEntityCulling() {
        ComputeShader shader = ShaderManager.CULL_ENTITY_BATCH_CS;
        shader.bindUniforms();
        CullingStateManager.runOnDepthFrame((depthContext) -> {
            RenderSystem.activeTexture(GL_TEXTURE0 + depthContext.index());
            RenderSystem.bindTexture(depthContext.frame().getColorTextureId());
        });
        RenderSystem.activeTexture(GL_TEXTURE0);
        CullingStateManager.ENTITY_CULLING_MASK.bindSSBO();
        shader.execute((CullingStateManager.ENTITY_CULLING_MASK.getEntityTable().size() / 64 + 1), 1, 1);
    }

    public static void updateMapData() {
        fps = ((AccessorMinecraft) Minecraft.getInstance()).getFps();
        if (anyCulling()) {
            MeshUniform.updateDistance(Minecraft.getInstance().options.getEffectiveRenderDistance());

            if (Config.doEntityCulling()) {
                if (ENTITY_CULLING_MASK == null) {
                    ENTITY_CULLING_MASK = new EntityCullingMask(64);
                }

                long time = System.nanoTime();
                preEntityCullingInitTime += System.nanoTime() - time;
            }
        } else {
            if (ENTITY_CULLING_MASK != null) {
                ENTITY_CULLING_MASK.cleanup();
                ENTITY_CULLING_MASK = null;
            }
        }
    }

    public static void useShader(ShaderInstance instance) {
        RenderSystem.setShader(() -> instance);
    }

    public static void bindMainFrameTarget() {
        if (SHADER_LOADER.renderingShaderPass()) {
            SHADER_LOADER.bindDefaultFrameBuffer();
        } else {
            Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
        }
    }

    public static void runOnDepthFrame(Consumer<DepthContext> consumer) {
        runOnDepthFrame(consumer, 0);
    }

    public static void runOnDepthFrame(Consumer<DepthContext> consumer, int startAtIndex) {
        for (DEPTH_INDEX = startAtIndex; DEPTH_INDEX < DEPTH_BUFFER_TARGET.length; ++DEPTH_INDEX) {
            int lastTexture = DEPTH_INDEX == 0 ? MAIN_DEPTH_TEXTURE : DEPTH_BUFFER_TARGET[DEPTH_INDEX - 1].getColorTextureId();
            consumer.accept(new DepthContext(DEPTH_BUFFER_TARGET[DEPTH_INDEX], DEPTH_INDEX, lastTexture));
        }
    }

    public static void runOnDepthFrame(Consumer<DepthContext> consumer, int startAtIndex, int endAtIndex) {
        for (DEPTH_INDEX = startAtIndex; DEPTH_INDEX < endAtIndex; ++DEPTH_INDEX) {
            int lastTexture = DEPTH_INDEX == 0 ? MAIN_DEPTH_TEXTURE : DEPTH_BUFFER_TARGET[DEPTH_INDEX - 1].getColorTextureId();
            consumer.accept(new DepthContext(DEPTH_BUFFER_TARGET[DEPTH_INDEX], DEPTH_INDEX, lastTexture));
        }
    }

    public static void callDepthTexture() {
        CullingStateManager.runOnDepthFrame((depthContext) -> {
            RenderSystem.setShaderTexture(depthContext.index(), depthContext.frame().getColorTextureId());
        });
    }

    public static boolean renderingIris() {
        return renderingShader();
    }

    public static boolean renderingShader() {
        return SHADER_LOADER.renderingShaderPass();
    }

    public static boolean enabledShader() {
        return SHADER_LOADER.enabledShader();
    }

    public static boolean anyNextTick() {
        for (int i = 0; i < 20; ++i) {
            if (nextTick[i])
                return true;
        }
        return false;
    }

    public static boolean isNextLoop() {
        return nextTick[0];
    }

    public static boolean anyCulling() {
        return Config.getCullChunk() || Config.doEntityCulling();
    }

    public static boolean needPauseRebuild() {
        return fullChunkUpdateCooldown > 0;
    }

    public static void updating() {
        continueUpdateCount = 10;
    }

    public static boolean continueUpdateDepth() {
        return continueUpdateCount > 0;
    }
}