package rogo.sketch;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLLoader;
import org.joml.FrustumIntersection;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import rogo.sketch.event.bridge.EventBusBridge;
import rogo.sketch.event.bridge.ForgeEventBusImplementation;
import rogo.sketch.feature.culling.CullingRenderEvent;
import rogo.sketch.feature.culling.CullingStateManager;
import rogo.sketch.gui.ConfigScreen;
import rogo.sketch.render.resource.buffer.IndirectCommandBuffer;
import rogo.sketch.render.shader.ShaderManager;
import rogo.sketch.render.shader.uniform.UniformHookRegistry;
import rogo.sketch.util.CommandCallTimer;
import rogo.sketch.util.GLFeatureChecker;
import rogo.sketch.util.OcclusionCullerThread;
import rogo.sketch.util.RenderCallTimer;
import rogo.sketch.vanilla.McPipelineRegister;
import rogo.sketch.vanilla.event.VanillaPipelineEventHandler;
import rogo.sketch.vanilla.instance.AABBObject;
import rogo.sketch.vanilla.resource.RenderResourceManager;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

import static java.lang.Thread.MAX_PRIORITY;
import static rogo.sketch.feature.culling.CullingStateManager.*;

@Mod(SketchRender.MOD_ID)
public class SketchRender {
    public static final String MOD_ID = "sketchrender";
    public static final Logger LOGGER = LogUtils.getLogger();
    private static final ShaderManager shaderManager = new ShaderManager();
    private static final RenderResourceManager resourceManager = new RenderResourceManager();

    @SuppressWarnings("removal")
    public SketchRender() {
        EventBusBridge.setImplementation(new ForgeEventBusImplementation());
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            FMLJavaModLoadingContext.get().getModEventBus().addListener(VanillaPipelineEventHandler::onPipelineInit);
            FMLJavaModLoadingContext.get().getModEventBus().addListener(VanillaPipelineEventHandler::onUniformInit);
            MinecraftForge.EVENT_BUS.register(this);
            MinecraftForge.EVENT_BUS.register(new CullingRenderEvent());
            ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, Config.CLIENT_CONFIG);
            FMLJavaModLoadingContext.get().getModEventBus().addListener(this::registerReloadListener);
            FMLJavaModLoadingContext.get().getModEventBus().addListener(this::registerKeyBinding);
            RenderSystem.recordRenderCall(GLFeatureChecker::initialize);
            FMLJavaModLoadingContext.get().getModEventBus().addListener(this::initClient);
            init();
        });
    }

    public static final KeyMapping CONFIG_KEY = new KeyMapping(MOD_ID + ".key.config",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            "key.category." + MOD_ID);

    public static final KeyMapping DEBUG_KEY = new KeyMapping(MOD_ID + ".key.debug",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_X,
            "key.category." + MOD_ID);

    public static final KeyMapping TEST_CULL_KEY = new KeyMapping(MOD_ID + ".key.cull",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            "key.category." + MOD_ID);

    public void registerReloadListener(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(SketchRender.shaderManager);
        event.registerReloadListener(SketchRender.resourceManager);
        VanillaPipelineEventHandler.registerStaticResource();
    }

    public void registerKeyBinding(RegisterKeyMappingsEvent event) {
        event.register(CONFIG_KEY);
        event.register(DEBUG_KEY);
        event.register(TEST_CULL_KEY);
    }

    public static RenderTarget CULL_TEST_TARGET;

    static {
        CULL_TEST_TARGET = new TextureTarget(Minecraft.getInstance().getWindow().getWidth(), Minecraft.getInstance().getWindow().getHeight(), false, Minecraft.ON_OSX);
        CULL_TEST_TARGET.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
    }

    @SubscribeEvent
    public void onKeyboardInput(InputEvent.Key event) {
        if (Minecraft.getInstance().player != null) {
            if (CONFIG_KEY.isDown()) {
                Minecraft.getInstance().setScreen(new ConfigScreen(Component.translatable(MOD_ID + ".config")));
            }
            if (DEBUG_KEY.isDown()) {
                DEBUG++;
                if (DEBUG >= 3)
                    DEBUG = 0;
            }
            if (TEST_CULL_KEY.isDown()) {
                HitResult hitResult = ProjectileUtil.getHitResultOnViewVector(Minecraft.getInstance().player, Entity::isAlive, 999);
                if (hitResult instanceof EntityHitResult entityHitResult) {
                    if (entityHitResult.getEntity() != testEntity) {
                        testEntity = entityHitResult.getEntity();
                    } else {
                        testEntity = null;
                    }
                } else if (hitResult instanceof BlockHitResult) {
                    BlockPos pos = ((BlockHitResult) hitResult).getBlockPos();
                    BlockPos testPos_ = new BlockPos(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4);
                    if (testPos_.equals(testPos)) {
                        testPos = null;
                    } else {
                        testPos = testPos_;
                    }
                }
            }
        }
    }

    public static BlockPos testPos = null;
    public static Entity testEntity = null;

    public static void onKeyPress() {
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            if (Minecraft.getInstance().player != null && Minecraft.getInstance().level != null) {
                clientTickCount++;
                if (Minecraft.getInstance().player.tickCount > 60 && clientTickCount > 60) {
                    LEVEL_SECTION_RANGE = Minecraft.getInstance().level.getMaxSection() - Minecraft.getInstance().level.getMinSection();
                    LEVEL_MIN_SECTION_ABS = Math.abs(Minecraft.getInstance().level.getMinSection());
                    LEVEL_MIN_POS = Minecraft.getInstance().level.getMinBuildHeight();
                    LEVEL_POS_RANGE = Minecraft.getInstance().level.getMaxBuildHeight() - Minecraft.getInstance().level.getMinBuildHeight();

                    if (OcclusionCullerThread.INSTANCE == null || !OcclusionCullerThread.INSTANCE.isAlive()) {
                        OcclusionCullerThread occlusionCullerThread = new OcclusionCullerThread();
                        OcclusionCullerThread.INSTANCE = occlusionCullerThread;
                        occlusionCullerThread.setName("Chunk Depth Occlusion Cull thread");
                        occlusionCullerThread.setPriority(MAX_PRIORITY);
                        occlusionCullerThread.start();
                    }
                }
                Config.setLoaded();
            } else {
                cleanup();
            }
        }
    }

    public static Vector4f[] getFrustumPlanes(FrustumIntersection frustum) {
        try {
            Field f = FrustumIntersection.class.getDeclaredField("planes");
            f.setAccessible(true);
            return (Vector4f[]) f.get(frustum);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.fillInStackTrace();
        }

        return new Vector4f[6];
    }

    public static boolean hasMod(String s) {
        return FMLLoader.getLoadingModList().getMods().stream().anyMatch(modInfo -> modInfo.getModId().equals(s));
    }

    public static boolean hasSodium() {
        return FMLLoader.getLoadingModList().getMods().stream().anyMatch(modInfo -> modInfo.getModId().equals("sodium") || modInfo.getModId().equals("embeddium"));
    }

    public static boolean hasIris() {
        return FMLLoader.getLoadingModList().getMods().stream().anyMatch(modInfo -> modInfo.getModId().equals("iris") || modInfo.getModId().equals("oculus"));
    }

    public static AABB getObjectAABB(Object o) {
        if (o instanceof BlockEntity) {
            return ((BlockEntity) o).getRenderBoundingBox();
        } else if (o instanceof Entity) {
            return ((Entity) o).getBoundingBox();
        } else if (o instanceof AABBObject) {
            return ((AABBObject) o).getAABB();
        }

        return null;
    }

    public static void pauseAsync() {
        fullChunkUpdateCooldown = 0;
    }

    @SubscribeEvent
    public void onRenderGameOverlayEvent(RenderGuiOverlayEvent event) {
        if (event.getOverlay() == VanillaGuiOverlay.HELMET.type()) {
            int fps = Minecraft.getInstance().getFps();
            Map<String, Object> debugText = new LinkedHashMap<>();
            debugText.put("帧数", fps);
            long capacityInBytes = IndirectCommandBuffer.INSTANCE.getCapacity();
            double capacityInMB = capacityInBytes / (1024.0 * 1024.0);
            debugText.put("IndirectCommandBuffer", String.format("%.2f MB", capacityInMB));

            CommandCallTimer commandTimer = SketchRender.COMMAND_TIMER;
            debugText.putAll(commandTimer.getResults());

            RenderCallTimer renderTimer = SketchRender.RENDER_TIMER;
            debugText.putAll(renderTimer.getResults());

            StringBuilder debug = new StringBuilder();
            for (Map.Entry<String, Object> entry : debugText.entrySet()) {
                String text = entry.getKey();
                Object value = entry.getValue();
                debug.append(text).append(": ").append(value).append("\n");
            }

            String[] strings = debug.toString().split("\n");

            for (int i = 0; i < strings.length; ++i) {
                event.getGuiGraphics().drawString(Minecraft.getInstance().font, strings[i], 0, Minecraft.getInstance().font.lineHeight * i
                        , 16777215 + (255 << 24));
            }
        }
    }

    public static ShaderManager getShaderManager() {
        return SketchRender.shaderManager;
    }

    private long lastSwitchTime = 0;

    @SubscribeEvent
    public void onRenderStart(TickEvent.RenderTickEvent event) {
        long currentTime = System.nanoTime();
        long SWITCH_INTERVAL = 1000000000L;
        if (currentTime - lastSwitchTime >= SWITCH_INTERVAL) {
            lastSwitchTime = currentTime;
            COMMAND_TIMER.calculateAverageTimes(CullingStateManager.fps);
            RENDER_TIMER.calculateAverageTimes(CullingStateManager.fps);
        }
    }

    public void initClient(FMLClientSetupEvent event) {
        McPipelineRegister.initPipeline();
        UniformHookRegistry.getInstance().init();
    }

    public static CommandCallTimer COMMAND_TIMER = new CommandCallTimer();
    public static RenderCallTimer RENDER_TIMER = new RenderCallTimer();
}