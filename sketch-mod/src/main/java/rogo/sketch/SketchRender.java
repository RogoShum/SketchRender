package rogo.sketch;

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
import net.minecraft.world.level.block.entity.ChestBlockEntity;
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
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLLoader;
import org.joml.FrustumIntersection;
import org.joml.Vector4f;
import org.joml.primitives.AABBf;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import rogo.sketch.compat.sodium.MeshResource;
import rogo.sketch.core.util.TimerUtil;
import rogo.sketch.event.ForgeEventBusImplementation;
import rogo.sketch.core.event.GraphicsPipelineInitEvent;
import rogo.sketch.core.event.RenderFlowRegisterEvent;
import rogo.sketch.core.event.UniformHookRegisterEvent;
import rogo.sketch.core.event.bridge.EventBusBridge;
import rogo.sketch.feature.culling.CullingRenderEvent;
import rogo.sketch.feature.culling.CullingStages;
import rogo.sketch.feature.culling.CullingStateManager;
import rogo.sketch.core.api.graphics.AABBGraphics;
import rogo.sketch.gui.ConfigScreen;
import rogo.sketch.core.driver.GraphicsDriver;
import rogo.sketch.vanilla.PipelineUtil;
import rogo.sketch.vanilla.driver.MinecraftAPI;
import rogo.sketch.core.pipeline.flow.RenderFlowRegistry;
import rogo.sketch.core.shader.uniform.UniformHookRegistry;
import rogo.sketch.core.state.DefaultRenderStates;
import rogo.sketch.core.util.CommandCallTimer;
import rogo.sketch.core.util.GLFeatureChecker;
import rogo.sketch.util.OcclusionCullerThread;
import rogo.sketch.core.util.RenderCallTimer;
import rogo.sketch.vanilla.McPipelineRegister;
import rogo.sketch.vanilla.MinecraftRenderStages;
import rogo.sketch.vanilla.ShaderManager;
import rogo.sketch.vanilla.event.VanillaPipelineEventHandler;
import rogo.sketch.vanilla.resource.RenderResourceManager;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

import static java.lang.Thread.MAX_PRIORITY;
import static rogo.sketch.feature.culling.CullingStateManager.*;

@Mod(SketchRender.MOD_ID)
public class SketchRender {
    public static final String MOD_ID = "sketch_render";
    public static final Logger LOGGER = LogUtils.getLogger();
    private static final ShaderManager shaderManager = new ShaderManager();
    private static final RenderResourceManager resourceManager = new RenderResourceManager();
    private static boolean initializedStaticGraphics = false;

    @SuppressWarnings("removal")
    public SketchRender() {
        GraphicsDriver.setCurrentAPI(new MinecraftAPI());
        EventBusBridge.setImplementation(new ForgeEventBusImplementation());
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            MinecraftRenderStages.addStage(CullingStages.HIZ_STAGE);
            FMLJavaModLoadingContext.get().getModEventBus().addGenericListener(GraphicsPipelineInitEvent.class,
                    VanillaPipelineEventHandler::onPipelineInit);
            FMLJavaModLoadingContext.get().getModEventBus().addGenericListener(UniformHookRegisterEvent.class,
                    VanillaPipelineEventHandler::onUniformInit);
            FMLJavaModLoadingContext.get().getModEventBus().addGenericListener(RenderFlowRegisterEvent.class,
                    VanillaPipelineEventHandler::onBaseRenderFlowRegisterInit);
            MinecraftForge.EVENT_BUS.register(new VanillaPipelineEventHandler());
            MinecraftForge.EVENT_BUS.register(this);
            MinecraftForge.EVENT_BUS.register(new CullingRenderEvent());
            MinecraftForge.EVENT_BUS.register(new CullingStateManager());
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
        VanillaPipelineEventHandler.registerPersistentResource();
    }

    public void registerKeyBinding(RegisterKeyMappingsEvent event) {
        event.register(CONFIG_KEY);
        event.register(DEBUG_KEY);
        event.register(TEST_CULL_KEY);
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
                HitResult hitResult = ProjectileUtil.getHitResultOnViewVector(Minecraft.getInstance().player,
                        Entity::isAlive, 999);
                if (hitResult instanceof EntityHitResult entityHitResult) {
                    if (entityHitResult.getEntity() != testEntity) {
                        testEntity = entityHitResult.getEntity();
                    } else {
                        testEntity = null;
                    }
                } else if (hitResult instanceof BlockHitResult) {
                    BlockPos pos = ((BlockHitResult) hitResult).getBlockPos();
                    if (Minecraft.getInstance().level.getBlockEntity(pos) != null) {
                        BlockEntity blockEntity = Minecraft.getInstance().level.getBlockEntity(pos);
                        if (blockEntity != testBlockEntity) {
                            testBlockEntity = blockEntity;
                        } else {
                            testBlockEntity = null;
                        }
                    } else {
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
    }

    public static BlockPos testPos = null;
    public static Entity testEntity = null;
    public static BlockEntity testBlockEntity = null;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            if (Minecraft.getInstance().player != null && Minecraft.getInstance().level != null) {
                CLIENT_TICK_COUNT++;
                if (OcclusionCullerThread.INSTANCE == null || !OcclusionCullerThread.INSTANCE.isAlive()) {
                    OcclusionCullerThread occlusionCullerThread = new OcclusionCullerThread();
                    OcclusionCullerThread.INSTANCE = occlusionCullerThread;
                    occlusionCullerThread.setName("Chunk Depth Occlusion Cull thread");
                    occlusionCullerThread.setPriority(MAX_PRIORITY);
                    occlusionCullerThread.start();
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
        return FMLLoader.getLoadingModList().getMods().stream()
                .anyMatch(modInfo -> modInfo.getModId().equals("sodium") || modInfo.getModId().equals("embeddium"));
    }

    public static boolean hasIris() {
        return FMLLoader.getLoadingModList().getMods().stream()
                .anyMatch(modInfo -> modInfo.getModId().equals("iris") || modInfo.getModId().equals("oculus"));
    }

    public static AABB getObjectAABB(Object o) {
        if (o instanceof ChestBlockEntity chestBlockEntity && chestBlockEntity.getOpenNess(0) <= 0) {
            return new AABB(chestBlockEntity.getBlockPos(), chestBlockEntity.getBlockPos().offset(1, 1, 1));
        } else if (o instanceof BlockEntity) {
            return ((BlockEntity) o).getRenderBoundingBox();
        } else if (o instanceof Entity) {
            return ((Entity) o).getBoundingBox();
        } else if (o instanceof AABBGraphics) {
            AABBf aabbf = ((AABBGraphics) o).getAABB();
            return new AABB(aabbf.minX, aabbf.minY, aabbf.minZ, aabbf.maxX, aabbf.maxY, aabbf.maxZ);
        }

        return null;
    }

    @SubscribeEvent
    public void onRenderGameOverlayEvent(RenderGuiOverlayEvent event) {
        if (event.getOverlay() == VanillaGuiOverlay.HELMET.type() && !FMLEnvironment.production) {
            int fps = Minecraft.getInstance().getFps();
            Map<String, Object> debugText = new LinkedHashMap<>();
            debugText.put("帧数", fps);
            long capacityInBytes = MeshResource.CHUNK_COMMAND.getCapacity();
            double capacityInMB = capacityInBytes / (1024.0 * 1024.0);
            debugText.put("IndirectCommandBuffer", String.format("%.2f MB",
                    capacityInMB));

            CommandCallTimer commandTimer = TimerUtil.COMMAND_TIMER;
            debugText.putAll(commandTimer.getResults());

            RenderCallTimer renderTimer = TimerUtil.RENDER_TIMER;
            debugText.putAll(renderTimer.getResults());

            StringBuilder debug = new StringBuilder();
            for (Map.Entry<String, Object> entry : debugText.entrySet()) {
                String text = entry.getKey();
                Object value = entry.getValue();
                debug.append(text).append(": ").append(value).append("\n");
            }

            String[] strings = debug.toString().split("\n");

            for (int i = 0; i < strings.length; ++i) {
                event.getGuiGraphics().drawString(Minecraft.getInstance().font, strings[i], 0,
                        Minecraft.getInstance().font.lineHeight * i, 16777215 + (255 << 24));
            }
        }
    }

    @SubscribeEvent
    public void onEnterLevel(LevelEvent.Load event) {
        if (!initializedStaticGraphics) {
            McPipelineRegister.initGraphics();
            initializedStaticGraphics = true;
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
            TimerUtil.COMMAND_TIMER.calculateAverageTimes(CullingStateManager.FPS);
            TimerUtil.RENDER_TIMER.calculateAverageTimes(CullingStateManager.FPS);
        }
    }

    @SubscribeEvent
    public void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.side == LogicalSide.CLIENT) {
            if (event.phase == TickEvent.Phase.START) {
                PipelineUtil.pipeline().asyncGraphicsTicker().onPreTick();
                PipelineUtil.pipeline().tickLogic();
            } else {
                PipelineUtil.pipeline().tickGraphics();
                PipelineUtil.pipeline().asyncGraphicsTicker().onPostTick();
            }
        }
    }

    public void initClient(FMLClientSetupEvent event) {
        McPipelineRegister.initPipeline();
        UniformHookRegistry.getInstance().init();
        RenderFlowRegistry.getInstance().init();
        DefaultRenderStates.init();
    }
}