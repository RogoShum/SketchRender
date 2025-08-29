package rogo.sketch.feature.culling;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Matrix4f;
import rogo.sketch.Config;
import rogo.sketch.SketchRender;
import rogo.sketch.compat.sodium.MeshResource;
import rogo.sketch.compat.sodium.SodiumSectionAsyncUtil;
import rogo.sketch.event.GraphicsPipelineStageEvent;
import rogo.sketch.event.bridge.ProxyEvent;
import rogo.sketch.mixin.AccessorLevelRender;
import rogo.sketch.mixin.AccessorMinecraft;
import rogo.sketch.util.DepthContext;
import rogo.sketch.util.Identifier;
import rogo.sketch.util.OcclusionCullerThread;
import rogo.sketch.util.ShaderPackLoader;
import rogo.sketch.vanilla.McRenderContext;
import rogo.sketch.vanilla.MinecraftRenderStages;
import rogo.sketch.vanilla.VanillaShaderPackLoader;

import java.util.HashMap;
import java.util.function.Consumer;

import static org.lwjgl.opengl.GL11.GL_TEXTURE;
import static org.lwjgl.opengl.GL30.*;

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
    public static HizTarget[] DEPTH_BUFFER_TARGET = new HizTarget[DEPTH_SIZE];
    public static int DEBUG = 0;
    public static ShaderPackLoader SHADER_LOADER = null;
    private static boolean[] NEXT_TICK = new boolean[20];
    public static int FPS = 0;
    private static int CURRENT_TICK = 0;
    public static int CLIENT_TICK_COUNT = 0;
    public static int ENTITY_CULLING = 0;
    public static int ENTITY_COUNT = 0;
    public static int BLOCK_CULLING = 0;
    public static int BLOCK_COUNT = 0;
    public static boolean CHECKING_CULL = false;
    public static boolean CHECKING_TEXTURE = false;
    private static boolean USING_SHADER_PACK = false;
    public static Identifier LEVEL_SECTION_RANGE_ID = Identifier.of("level_section_range");
    public static Identifier LEVEL_POS_RANGE_ID = Identifier.of("level_pos_range");
    public static Identifier LEVEL_MIN_SECTION_ABS_ID = Identifier.of("level_min_section_abs");
    public static Identifier LEVEL_MIN_POS_ID = Identifier.of("level_min_section_abs");
    public static Camera CAMERA;
    private static final HashMap<Integer, Integer> SHADER_DEPTH_BUFFER_ID = new HashMap<>();
    public static volatile boolean USE_OCCLUSION_CULLING = true;
    private static int CONTINUE_UPDATE_COUNT;

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
                SHADER_LOADER = Class.forName("rogo.sketch.util.IrisLoaderImpl").asSubclass(ShaderPackLoader.class).newInstance();
            } catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        } else {
            SHADER_LOADER = new VanillaShaderPackLoader();
        }
    }

    public static void onWorldReload(Level world) {
        if (world != Minecraft.getInstance().level) {
            cleanup();
        }
    }

    public static void cleanup() {
        CURRENT_TICK = 0;
        CLIENT_TICK_COUNT = 0;

        if (ENTITY_CULLING_MASK != null) {
            ENTITY_CULLING_MASK.cleanup();
            ENTITY_CULLING_MASK = null;
        }
        SHADER_DEPTH_BUFFER_ID.clear();
        if (SketchRender.hasSodium()) {
            SodiumSectionAsyncUtil.pauseAsync();
        }

        Window window = Minecraft.getInstance().getWindow();
        int width = window.getWidth();
        int height = window.getHeight();

        runOnDepthFrame((depthContext) -> {
            int scaleWidth = Math.max(1, width >> (depthContext.index() + 1));
            int scaleHeight = Math.max(1, height >> (depthContext.index() + 1));

            if (scaleWidth % 2 == 1) {
                scaleWidth += 1;
            }

            if (scaleHeight % 2 == 1) {
                scaleHeight += 1;
            }
            if (depthContext.frame().width != scaleWidth || depthContext.frame().height != scaleHeight) {
                depthContext.frame().resize(scaleWidth, scaleHeight, Minecraft.ON_OSX);
            }
        });

        bindMainFrameTarget();
    }

    public static boolean shouldSkipBlockEntity(BlockEntity blockEntity, BlockPos pos) {
        BLOCK_COUNT++;
        if (ENTITY_CULLING_MASK == null || renderingShadowPass() || !Config.getCullBlockEntity()) return false;
        //for valkyrien skies
        if (CAMERA.getPosition().distanceToSqr(pos.getX(), pos.getY(), pos.getZ()) >
                Minecraft.getInstance().options.getEffectiveRenderDistance() * 16 * Minecraft.getInstance().options.getEffectiveRenderDistance() * 16 * 2) {
            return false;
        }

        String type = BlockEntityType.getKey(blockEntity.getType()).toString();
        if (Config.getBlockEntitiesSkip().contains(type))
            return false;

        if (ENTITY_CULLING_MASK.isObjectVisible(blockEntity)) {
            return false;
        }

        BLOCK_CULLING++;
        return true;
    }

    public static boolean shouldSkipEntity(Entity entity) {
        ENTITY_COUNT++;
        if (ENTITY_CULLING_MASK == null || renderingShadowPass() || !Config.getCullEntity()) return false;
        if (entity instanceof Player || entity.isCurrentlyGlowing()) return false;
        if (entity.distanceToSqr(CAMERA.getPosition()) < 4) return false;
        if (Config.getEntitiesSkip().contains(entity.getType().getDescriptionId()))
            return false;

        if (ENTITY_CULLING_MASK.isObjectVisible(entity)) {
            return false;
        }

        ENTITY_CULLING++;
        return true;
    }

    @SubscribeEvent
    public void renderStage(ProxyEvent<GraphicsPipelineStageEvent<McRenderContext>> proxyEvent) {
        GraphicsPipelineStageEvent<McRenderContext> event = proxyEvent.getWrapped();
        McRenderContext context = event.getContext();

        if (event.getPhase() == GraphicsPipelineStageEvent.Phase.PRE) {
            if (event.getStage() == MinecraftRenderStages.SKY.getIdentifier()) {
                CAMERA = Minecraft.getInstance().gameRenderer.getMainCamera();
                int thisTick = CLIENT_TICK_COUNT % 20;
                NEXT_TICK = new boolean[20];

                if (CURRENT_TICK != thisTick) {
                    CURRENT_TICK = thisTick;
                    NEXT_TICK[thisTick] = true;
                }

                ENTITY_CULLING = 0;
                ENTITY_COUNT = 0;
                BLOCK_CULLING = 0;
                BLOCK_COUNT = 0;

                if (anyNextTick()) {
                    if (CONTINUE_UPDATE_COUNT > 0) {
                        CONTINUE_UPDATE_COUNT--;
                    }
                }

                if (isNextLoop()) {
                    if (CullingStateManager.ENTITY_CULLING_MASK != null) {
                        CullingStateManager.ENTITY_CULLING_MASK.swapBuffer(CLIENT_TICK_COUNT);
                    }

                    MeshResource.LAST_QUEUE_UPDATE_COUNT = MeshResource.QUEUE_UPDATE_COUNT;
                    MeshResource.QUEUE_UPDATE_COUNT = 0;
                }
            } else if (event.getStage() == MinecraftRenderStages.RENDER_START.getIdentifier()) {
                if (((AccessorLevelRender) Minecraft.getInstance().levelRenderer).getNeedsFullRenderChunkUpdate() && Minecraft.getInstance().level != null) {
                    context.set(LEVEL_SECTION_RANGE_ID, Minecraft.getInstance().level.getMaxSection() - Minecraft.getInstance().level.getMinSection());
                    context.set(LEVEL_POS_RANGE_ID, Minecraft.getInstance().level.getMaxBuildHeight() - Minecraft.getInstance().level.getMinBuildHeight());
                    context.set(LEVEL_MIN_SECTION_ABS_ID, Math.abs(Minecraft.getInstance().level.getMinSection()));
                    context.set(LEVEL_MIN_POS_ID, Minecraft.getInstance().level.getMinBuildHeight());
                }
            } else if (event.getStage() == MinecraftRenderStages.RENDER_END.getIdentifier()) {
                updateMapData();
                OcclusionCullerThread.notifyUpdate();
            } else if (event.getStage() == MinecraftRenderStages.DESTROY_PROGRESS.getIdentifier()) {
                updateDepthMap();
            } else if (event.getStage() == MinecraftRenderStages.PREPARE_FRUSTUM.getIdentifier()) {
                checkShader();
            }
        }
    }

    public static void checkShader() {
        if (SHADER_LOADER != null) {
            boolean clear = false;
            if (SHADER_LOADER.enabledShader() && !USING_SHADER_PACK) {
                clear = true;
                USING_SHADER_PACK = true;
            }

            if (!SHADER_LOADER.enabledShader() && USING_SHADER_PACK) {
                clear = true;
                USING_SHADER_PACK = false;
            }

            if (clear) {
                cleanup();
            }
        }
    }

    public static void updateDepthMap() {
        CullingStateManager.PROJECTION_MATRIX = new Matrix4f(RenderSystem.getProjectionMatrix());
        if (anyCulling() && !CHECKING_CULL && continueUpdateDepth()) {
            Window window = Minecraft.getInstance().getWindow();
            int width = window.getWidth();
            int height = window.getHeight();

            runOnDepthFrame((depthContext) -> {
                int scaleWidth = Math.max(1, width >> (depthContext.index() + 1));
                int scaleHeight = Math.max(1, height >> (depthContext.index() + 1));

                if (scaleWidth % 2 == 1) {
                    scaleWidth += 1;
                }

                if (scaleHeight % 2 == 1) {
                    scaleHeight += 1;
                }
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

    public static void updateMapData() {
        FPS = ((AccessorMinecraft) Minecraft.getInstance()).getFps();
        if (anyCulling()) {
            MeshResource.updateDistance(Minecraft.getInstance().options.getEffectiveRenderDistance());

            if (Config.doEntityCulling()) {
                if (ENTITY_CULLING_MASK == null) {
                    ENTITY_CULLING_MASK = new EntityCullingMask(64);
                }
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
        if (SHADER_LOADER.renderingShadowPass()) {
            SHADER_LOADER.bindDefaultFrameBuffer();
        } else {
            Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
        }
    }

    public static void runOnDepthFrame(Consumer<DepthContext> consumer) {
        for (DEPTH_INDEX = 0; DEPTH_INDEX < DEPTH_BUFFER_TARGET.length; ++DEPTH_INDEX) {
            int lastTexture = DEPTH_INDEX == 0 ? MAIN_DEPTH_TEXTURE : DEPTH_BUFFER_TARGET[DEPTH_INDEX - 1].getColorTextureId();
            consumer.accept(new DepthContext(DEPTH_BUFFER_TARGET[DEPTH_INDEX], DEPTH_INDEX, lastTexture));
        }
    }

    public static boolean renderingShadowPass() {
        return SHADER_LOADER.renderingShadowPass();
    }

    public static boolean enabledShader() {
        return SHADER_LOADER.enabledShader();
    }

    public static boolean anyNextTick() {
        for (int i = 0; i < 20; ++i) {
            if (NEXT_TICK[i])
                return true;
        }
        return false;
    }

    public static boolean isNextLoop() {
        return NEXT_TICK[0];
    }

    public static boolean anyCulling() {
        return Config.getCullChunk() || Config.doEntityCulling();
    }

    public static void updating() {
        CONTINUE_UPDATE_COUNT = 10;
    }

    public static boolean continueUpdateDepth() {
        return CONTINUE_UPDATE_COUNT > 0;
    }
}