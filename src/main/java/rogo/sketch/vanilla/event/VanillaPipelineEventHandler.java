package rogo.sketch.vanilla.event;

import com.mojang.blaze3d.pipeline.RenderTarget;
import me.jellysquid.mods.sodium.client.SodiumClientMod;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.*;
import rogo.sketch.Config;
import rogo.sketch.SketchRender;
import rogo.sketch.api.GraphicsInstance;
import rogo.sketch.compat.sodium.MeshResource;
import rogo.sketch.event.GraphicsPipelineInitEvent;
import rogo.sketch.event.RegisterStaticGraphicsEvent;
import rogo.sketch.event.UniformHookRegisterEvent;
import rogo.sketch.event.bridge.ProxyEvent;
import rogo.sketch.event.bridge.ProxyModEvent;
import rogo.sketch.feature.culling.CullingStages;
import rogo.sketch.feature.culling.CullingStateManager;
import rogo.sketch.feature.culling.graphics.ComputeEntityCullingGraphics;
import rogo.sketch.feature.culling.graphics.ComputeHIZGraphics;
import rogo.sketch.feature.culling.graphics.CullingTestGraphics;
import rogo.sketch.mixin.AccessorFrustum;
import rogo.sketch.render.PartialRenderSetting;
import rogo.sketch.render.RenderContext;
import rogo.sketch.render.RenderParameter;
import rogo.sketch.render.RenderSetting;
import rogo.sketch.render.data.PrimitiveType;
import rogo.sketch.render.data.Usage;
import rogo.sketch.render.resource.GraphicsResourceManager;
import rogo.sketch.render.resource.ResourceTypes;
import rogo.sketch.render.shader.uniform.ValueGetter;
import rogo.sketch.render.vertex.DefaultDataFormats;
import rogo.sketch.util.Identifier;
import rogo.sketch.vanilla.McGraphicsPipeline;
import rogo.sketch.vanilla.McRenderContext;
import rogo.sketch.vanilla.MinecraftRenderStages;
import rogo.sketch.vanilla.resource.TempTexture;

import java.util.Optional;
import java.util.function.Supplier;

public class VanillaPipelineEventHandler {
    public static final TempTexture mainColor = new TempTexture(() -> Minecraft.getInstance().getMainRenderTarget(), false);
    public static final TempTexture mainDepth = new TempTexture(() -> Minecraft.getInstance().getMainRenderTarget(), true);

    public static void registerPersistentResource() {
        GraphicsResourceManager.getInstance().registerMutable(ResourceTypes.TEXTURE, Identifier.of(SketchRender.MOD_ID, "hiz_texture_0"),
                () -> Optional.of(CullingStateManager.DEPTH_BUFFER_TARGET[0].texture()));
        GraphicsResourceManager.getInstance().registerMutable(ResourceTypes.TEXTURE, Identifier.of(SketchRender.MOD_ID, "hiz_texture_1"),
                () -> Optional.of(CullingStateManager.DEPTH_BUFFER_TARGET[1].texture()));
        GraphicsResourceManager.getInstance().registerMutable(ResourceTypes.TEXTURE, Identifier.of(SketchRender.MOD_ID, "hiz_texture_2"),
                () -> Optional.of(CullingStateManager.DEPTH_BUFFER_TARGET[2].texture()));
        GraphicsResourceManager.getInstance().registerMutable(ResourceTypes.TEXTURE, Identifier.of(SketchRender.MOD_ID, "hiz_texture_3"),
                () -> Optional.of(CullingStateManager.DEPTH_BUFFER_TARGET[3].texture()));
        GraphicsResourceManager.getInstance().registerMutable(ResourceTypes.TEXTURE, Identifier.of(SketchRender.MOD_ID, "hiz_texture_4"),
                () -> Optional.of(CullingStateManager.DEPTH_BUFFER_TARGET[4].texture()));
        GraphicsResourceManager.getInstance().registerMutable(ResourceTypes.TEXTURE, Identifier.of(SketchRender.MOD_ID, "hiz_texture_5"),
                () -> Optional.of(CullingStateManager.DEPTH_BUFFER_TARGET[5].texture()));
        GraphicsResourceManager.getInstance().registerMutable(ResourceTypes.TEXTURE, Identifier.of(SketchRender.MOD_ID, "hiz_texture_6"),
                () -> Optional.of(CullingStateManager.DEPTH_BUFFER_TARGET[6].texture()));
        GraphicsResourceManager.getInstance().registerMutable(ResourceTypes.TEXTURE, Identifier.of(SketchRender.MOD_ID, "hiz_texture_7"),
                () -> Optional.of(CullingStateManager.DEPTH_BUFFER_TARGET[7].texture()));

        GraphicsResourceManager.getInstance().registerMutable(ResourceTypes.TEXTURE, Identifier.of("minecraft", "main_color"),
                () -> Optional.of(mainColor.getTexture()));
        GraphicsResourceManager.getInstance().registerMutable(ResourceTypes.TEXTURE, Identifier.of("minecraft", "main_depth"),
                () -> Optional.of(mainDepth.getTexture()));

        GraphicsResourceManager.getInstance().registerMutable(ResourceTypes.SHADER_STORAGE_BUFFER, Identifier.of(SketchRender.MOD_ID, "entity_data"),
                () -> {
                    if (CullingStateManager.ENTITY_CULLING_MASK != null) {
                        return Optional.of(CullingStateManager.ENTITY_CULLING_MASK.getEntityDataSSBO());
                    } else {
                        return Optional.empty();
                    }
                });
        GraphicsResourceManager.getInstance().registerMutable(ResourceTypes.SHADER_STORAGE_BUFFER, Identifier.of(SketchRender.MOD_ID, "entity_culling_result"),
                () -> {
                    if (CullingStateManager.ENTITY_CULLING_MASK != null) {
                        return Optional.of(CullingStateManager.ENTITY_CULLING_MASK.getEntityCullingResult());
                    } else {
                        return Optional.empty();
                    }
                });

        GraphicsResourceManager.getInstance().registerMutable(ResourceTypes.SHADER_STORAGE_BUFFER, Identifier.of(SketchRender.MOD_ID, "chunk_section_mesh"),
                () -> {
                    return Optional.of(MeshResource.MESH_MANAGER.meshDataBuffer());
                });

        GraphicsResourceManager.getInstance().registerMutable(ResourceTypes.SHADER_STORAGE_BUFFER, Identifier.of(SketchRender.MOD_ID, "chunk_draw_command"),
                () -> {
                    if (MeshResource.batchCommand != null) {
                        return Optional.of(MeshResource.batchCommand);
                    } else {
                        return Optional.empty();
                    }
                });

        GraphicsResourceManager.getInstance().registerMutable(ResourceTypes.SHADER_STORAGE_BUFFER, Identifier.of(SketchRender.MOD_ID, "mesh_counter"),
                () -> {
                    if (MeshResource.batchCounter != null) {
                        return Optional.of(MeshResource.batchCounter);
                    } else {
                        return Optional.empty();
                    }
                });
        GraphicsResourceManager.getInstance().registerMutable(ResourceTypes.SHADER_STORAGE_BUFFER, Identifier.of(SketchRender.MOD_ID, "region_pos"),
                () -> {
                    if (MeshResource.batchRegionIndex != null) {
                        return Optional.of(MeshResource.batchRegionIndex);
                    } else {
                        return Optional.empty();
                    }
                });

        GraphicsResourceManager.getInstance().registerMutable(ResourceTypes.SHADER_STORAGE_BUFFER, Identifier.of(SketchRender.MOD_ID, "max_element_count"),
                () -> {
                    if (MeshResource.batchMaxElement != null) {
                        return Optional.of(MeshResource.batchMaxElement);
                    } else {
                        return Optional.empty();
                    }
                });

        GraphicsResourceManager.getInstance().registerMutable(ResourceTypes.SHADER_STORAGE_BUFFER, Identifier.of(SketchRender.MOD_ID, "persistent_max_element_count"),
                () -> {
                    if (MeshResource.maxElementPersistent != null) {
                        return Optional.of(MeshResource.maxElementPersistent);
                    } else {
                        return Optional.empty();
                    }
                });
    }

    public static void onUniformInit(ProxyModEvent event) {
        if (event.getWrapped() instanceof UniformHookRegisterEvent uniformEvent) {
            uniformEvent.register(Identifier.of("viewMatrix"), ValueGetter.create((instance) -> {
                RenderContext context = (RenderContext) instance;
                return context.viewMatrix();
            }, Matrix4f.class, RenderContext.class));

            uniformEvent.register(Identifier.of("modelMatrix"), ValueGetter.create((instance) -> {
                RenderContext context = (RenderContext) instance;
                return context.modelMatrix();
            }, Matrix4f.class, RenderContext.class));

            uniformEvent.register(Identifier.of("projectionMatrix"), ValueGetter.create((instance) -> {
                RenderContext context = (RenderContext) instance;
                return context.projectionMatrix();
            }, Matrix4f.class, RenderContext.class));

            uniformEvent.register(Identifier.of("partialTicks"), ValueGetter.create((instance) -> {
                RenderContext context = (RenderContext) instance;
                return context.partialTicks();
            }, Float.class, RenderContext.class));

            uniformEvent.register(Identifier.of("renderTick"), ValueGetter.create((instance) -> {
                RenderContext context = (RenderContext) instance;
                return context.renderTick();
            }, Integer.class, RenderContext.class));

            uniformEvent.register(Identifier.of("windowWidth"), ValueGetter.create((instance) -> {
                RenderContext context = (RenderContext) instance;
                return context.windowWidth();
            }, Integer.class, RenderContext.class));

            uniformEvent.register(Identifier.of("windowHeight"), ValueGetter.create((instance) -> {
                RenderContext context = (RenderContext) instance;
                return context.windowHeight();
            }, Integer.class, RenderContext.class));

            uniformEvent.register(Identifier.of("windowSize"), ValueGetter.create((instance) -> {
                RenderContext context = (RenderContext) instance;
                return new Vector2f(context.windowWidth(), context.windowHeight());
            }, Vector2f.class, RenderContext.class));

            uniformEvent.register(Identifier.of("sketch_frustumPos"), ValueGetter.create((instance) -> {
                McRenderContext context = (McRenderContext) instance;
                if (context.cullingFrustum() != null) {
                    return new Vector3f(
                            (float) ((AccessorFrustum) context.cullingFrustum()).camX(),
                            (float) ((AccessorFrustum) context.cullingFrustum()).camY(),
                            (float) ((AccessorFrustum) context.cullingFrustum()).camZ());
                }
                return null;
            }, Vector3f.class, McRenderContext.class));

            uniformEvent.register(Identifier.of("sketch_cullingFrustum"), ValueGetter.create((instance) -> {
                McRenderContext context = (McRenderContext) instance;
                if (context.cullingFrustum() != null) {
                    return SketchRender.getFrustumPlanes(((AccessorFrustum) context.cullingFrustum()).frustumIntersection());
                }
                return null;
            }, Vector4f[].class, McRenderContext.class));

            uniformEvent.register(Identifier.of("sketch_cullFacing"), ValueGetter.create(() -> {
                return SodiumClientMod.options().performance.useBlockFaceCulling ? 1 : 0;
            }, Integer.class));

            uniformEvent.register(Identifier.of("sketch_translucentSort"), ValueGetter.create(() -> {
                return SodiumClientMod.canApplyTranslucencySorting() ? 1 : 0;
            }, Integer.class));

            uniformEvent.register(Identifier.of("sketch_entityCount"), ValueGetter.create(() -> {
                return CullingStateManager.ENTITY_CULLING_MASK == null ? 0 : CullingStateManager.ENTITY_CULLING_MASK.getEntityMap().size();
            }, Integer.class));

            uniformEvent.register(Identifier.of("sketch_cullingTerrain"), ValueGetter.create(() -> {
                return !Config.getCullChunk() || CullingStateManager.SHADER_LOADER.renderingShadowPass() ? 0 : 1;
            }, Integer.class));

            uniformEvent.register(Identifier.of("sketch_checkCulling"), ValueGetter.create(() -> {
                return CullingStateManager.checkCulling ? 1 : 0;
            }, Integer.class));

            uniformEvent.register(Identifier.of("sketch_levelMinPos"), ValueGetter.create((instance) -> {
                McRenderContext context = (McRenderContext) instance;
                return context.get(CullingStateManager.LEVEL_MIN_POS_ID);
            }, Integer.class, McRenderContext.class));

            uniformEvent.register(Identifier.of("sketch_levelPosRange"), ValueGetter.create((instance) -> {
                McRenderContext context = (McRenderContext) instance;
                return context.get(CullingStateManager.LEVEL_POS_RANGE_ID);
            }, Integer.class, McRenderContext.class));

            uniformEvent.register(Identifier.of("sketch_levelSectionRange"), ValueGetter.create((instance) -> {
                McRenderContext context = (McRenderContext) instance;
                return context.get(CullingStateManager.LEVEL_SECTION_RANGE_ID);
            }, Integer.class, McRenderContext.class));

            uniformEvent.register(Identifier.of("sketch_cameraOffset"), ValueGetter.create((instance) -> {
                McRenderContext context = (McRenderContext) instance;
                Vec3 pos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
                BlockPos blockPos = new BlockPos((int) pos.x >> 4, context.<Integer>get(CullingStateManager.LEVEL_MIN_POS_ID) >> 4, (int) pos.z >> 4);
                return new Vector3i(blockPos.getX(), blockPos.getY(), blockPos.getZ());
            }, Vector3i.class, McRenderContext.class));

            uniformEvent.register(Identifier.of("sketch_cameraPos"), ValueGetter.create(() -> {
                Vec3 pos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
                return new Vector3i((int) pos.x, (int) pos.y, (int) pos.z);
            }, Vector3i.class));

            uniformEvent.register(Identifier.of("sketch_renderDistance"), ValueGetter.create(() -> {
                return MeshResource.getRenderDistance();
            }, Integer.class));

            uniformEvent.register(Identifier.of("sketch_spacePartitionSize"), ValueGetter.create(() -> {
                return MeshResource.getSpacePartitionSize();
            }, Integer.class));

            uniformEvent.register(Identifier.of("sketch_cullingViewMat"), ValueGetter.create((instance) -> {
                RenderContext context = (RenderContext) instance;
                return context.viewMatrix();
            }, Matrix4f.class, RenderContext.class));

            uniformEvent.register(Identifier.of("sketch_cullingProjMat"), ValueGetter.create((instance) -> {
                RenderContext context = (RenderContext) instance;
                return context.projectionMatrix();
            }, Matrix4f.class, RenderContext.class));

            uniformEvent.register(Identifier.of("sketch_cullingCameraPos"), ValueGetter.create(() -> {
                return Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().toVector3f();
            }, Vector3f.class));

            uniformEvent.register(Identifier.of("sketch_cullingCameraDir"), ValueGetter.create(() -> {
                return Minecraft.getInstance().gameRenderer.getMainCamera().getLookVector();
            }, Vector3f.class));

            uniformEvent.register(Identifier.of("sketch_depthSize"), ValueGetter.create(() -> {
                Vector2f[] array = new Vector2f[CullingStateManager.DEPTH_SIZE];
                for (int i = 0; i < CullingStateManager.DEPTH_SIZE; ++i) {
                    array[i] = new Vector2f((float) CullingStateManager.DEPTH_BUFFER_TARGET[i].width, (float) CullingStateManager.DEPTH_BUFFER_TARGET[i].height);
                }

                return array;
            }, Vector2f[].class));

            uniformEvent.register(Identifier.of("sketch_linerDepth"), ValueGetter.create((instance) -> {
                ComputeHIZGraphics hizGraphics = (ComputeHIZGraphics) instance;
                return hizGraphics.first() ? 1 : 0;
            }, Integer.class, ComputeHIZGraphics.class));

            uniformEvent.register(Identifier.of("sketch_screenSize"), ValueGetter.create((instance) -> {
                ComputeHIZGraphics hizGraphics = (ComputeHIZGraphics) instance;
                RenderTarget screen = Minecraft.getInstance().getMainRenderTarget();
                if (!hizGraphics.first()) {
                    screen = CullingStateManager.DEPTH_BUFFER_TARGET[3];
                }
                return new Vector2i(screen.width, screen.height);
            }, Vector2i.class, ComputeHIZGraphics.class));

            uniformEvent.register(Identifier.of("sketch_testPos"), ValueGetter.create(() -> {
                if (SketchRender.testPos != null) {
                    return new Vector4f(SketchRender.testPos.getX(), SketchRender.testPos.getY(), SketchRender.testPos.getZ(), 1);
                }
                return new Vector4f(0, 0, 0, 0);
            }, Vector4f.class));

            uniformEvent.register(Identifier.of("sketch_testEntityPos"), ValueGetter.create(() -> {
                if (SketchRender.testBlockEntity != null) {
                    return new Vector4f((float) SketchRender.testBlockEntity.getBlockPos().getX() + 0.5f, (float) SketchRender.testBlockEntity.getBlockPos().getY(), (float) SketchRender.testBlockEntity.getBlockPos().getZ() + 0.5f, 1);
                } else if (SketchRender.testEntity != null) {
                    return new Vector4f((float) SketchRender.testEntity.position().x, (float) SketchRender.testEntity.position().y, (float) SketchRender.testEntity.position().z, 1);
                } else {
                    return new Vector4f(0, 0, 0, 0);
                }
            }, Vector4f.class));

            uniformEvent.register(Identifier.of("sketch_testEntityAABB"), ValueGetter.create(() -> {
                if (SketchRender.testBlockEntity != null) {
                    return new Vector3f((float) SketchRender.testBlockEntity.getRenderBoundingBox().getXsize()
                            , (float) SketchRender.testBlockEntity.getRenderBoundingBox().getYsize()
                            , (float) SketchRender.testBlockEntity.getRenderBoundingBox().getZsize());
                } else if (SketchRender.testEntity != null) {
                    return new Vector3f((float) SketchRender.testEntity.getBoundingBoxForCulling().getXsize()
                            , (float) SketchRender.testEntity.getBoundingBoxForCulling().getYsize()
                            , (float) SketchRender.testEntity.getBoundingBoxForCulling().getZsize());
                }

                return new Vector3f(0, 0, 0);
            }, Vector3f.class));
        }
    }

    public static void onPipelineInit(ProxyModEvent event) {
        if (event.getWrapped() instanceof GraphicsPipelineInitEvent pipeLineInitEvent) {
            if (!(pipeLineInitEvent.getPipeline() instanceof McGraphicsPipeline mcPipeline)) {
                return;
            }

            switch (pipeLineInitEvent.getPhase()) {
                case EARLY -> {
                    // Register vanilla stages first
                    MinecraftRenderStages.registerVanillaStages(mcPipeline);
                }
                case NORMAL -> {
                    // Register extra stages that might be added by mods
                    MinecraftRenderStages.registerExtraStages(mcPipeline);
                }
                case LATE -> {
                    if (!mcPipeline.getPendingStages().isEmpty()) {
                        SketchRender.LOGGER.warn("Warning: {} stages are still pending", mcPipeline.getPendingStages().size());
                    }
                }
            }
        }
    }

    public static void onStaticGraphicsRegister(ProxyEvent<?> event) {
        if (event.getWrapped() instanceof RegisterStaticGraphicsEvent registerEvent) {
            registerReloadableComputeShader(registerEvent, "sketchrender:hierarchy_depth_buffer_first",
                    () -> new ComputeHIZGraphics(Identifier.of(SketchRender.MOD_ID, "hierarchy_depth_buffer_first"), true));

            registerReloadableComputeShader(registerEvent, SketchRender.MOD_ID + ":hierarchy_depth_buffer_second",
                    () -> new ComputeHIZGraphics(Identifier.of(SketchRender.MOD_ID, "hierarchy_depth_buffer_second"), false));

            registerReloadableComputeShader(registerEvent, SketchRender.MOD_ID + ":cull_entity_batch",
                    () -> new ComputeEntityCullingGraphics(Identifier.of(SketchRender.MOD_ID, "cull_entity_batch")));

            Identifier settingId = Identifier.of("sketchrender:culling_test");
            Optional<PartialRenderSetting> renderSetting = GraphicsResourceManager.getInstance()
                    .getResource(ResourceTypes.PARTIAL_RENDER_SETTING, settingId);

            if (renderSetting.isPresent()) {
                PartialRenderSetting partialRenderSetting = renderSetting.get();
                RenderParameter renderParameter = new RenderParameter(DefaultDataFormats.POSITION, PrimitiveType.QUADS, Usage.DYNAMIC_DRAW, false);
                RenderSetting setting = RenderSetting.fromPartial(partialRenderSetting, renderParameter);
                CullingTestGraphics cullingTestGraphics = new CullingTestGraphics(Identifier.of(SketchRender.MOD_ID, "culling_test"));
                registerEvent.register(MinecraftRenderStages.LEVEL_END.getIdentifier(), cullingTestGraphics, setting);
            }
        }
    }

    private static void registerReloadableComputeShader(RegisterStaticGraphicsEvent registerEvent, String settingIdString, Supplier<GraphicsInstance> instanceSupplier) {
        Identifier settingId = Identifier.of(settingIdString);
        Optional<PartialRenderSetting> renderSetting = GraphicsResourceManager.getInstance()
                .getResource(ResourceTypes.PARTIAL_RENDER_SETTING, settingId);

        if (renderSetting.isPresent()) {
            PartialRenderSetting partialRenderSetting = renderSetting.get();
            RenderSetting setting = RenderSetting.computeShader(partialRenderSetting);
            registerEvent.register(CullingStages.HIZ, instanceSupplier.get(), setting);
        } else {
            System.err.println("Failed to find PartialRenderSetting: " + settingId);
        }
    }
}