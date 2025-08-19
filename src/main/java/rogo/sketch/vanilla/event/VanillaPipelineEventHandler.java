package rogo.sketch.vanilla.event;

import com.mojang.blaze3d.pipeline.RenderTarget;
import me.jellysquid.mods.sodium.client.SodiumClientMod;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.*;
import rogo.sketch.Config;
import rogo.sketch.SketchRender;
import rogo.sketch.compat.sodium.MeshResource;
import rogo.sketch.event.GraphicsPipelineInitEvent;
import rogo.sketch.event.RegisterStaticGraphicsEvent;
import rogo.sketch.event.UniformHookRegisterEvent;
import rogo.sketch.event.bridge.ProxyEvent;
import rogo.sketch.event.bridge.ProxyModEvent;
import rogo.sketch.feature.culling.CullingStages;
import rogo.sketch.feature.culling.CullingStateManager;
import rogo.sketch.mixin.AccessorFrustum;
import rogo.sketch.render.PartialRenderSetting;
import rogo.sketch.render.RenderContext;
import rogo.sketch.render.RenderSetting;
import rogo.sketch.render.resource.GraphicsResourceManager;
import rogo.sketch.render.resource.ResourceTypes;
import rogo.sketch.render.shader.uniform.ValueGetter;
import rogo.sketch.util.Identifier;
import rogo.sketch.vanilla.McGraphicsPipeline;
import rogo.sketch.vanilla.McRenderContext;
import rogo.sketch.vanilla.MinecraftRenderStages;
import rogo.sketch.vanilla.graph.ComputeEntityCullingGraphics;
import rogo.sketch.vanilla.graph.ComputeHIZGraphics;
import rogo.sketch.vanilla.resource.TempTexture;

import java.util.Optional;

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
                    return Optional.of(MeshResource.meshManager.meshDataBuffer());
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
                if (instance instanceof RenderContext context) {
                    return context.viewMatrix();
                }

                return null;
            }, Matrix4f.class));

            uniformEvent.register(Identifier.of("modelMatrix"), ValueGetter.create((instance) -> {
                if (instance instanceof RenderContext context) {
                    return context.modelMatrix();
                }

                return null;
            }, Matrix4f.class));

            uniformEvent.register(Identifier.of("projectionMatrix"), ValueGetter.create((instance) -> {
                if (instance instanceof RenderContext context) {
                    return context.projectionMatrix();
                }

                return null;
            }, Matrix4f.class));

            uniformEvent.register(Identifier.of("partialTicks"), ValueGetter.create((instance) -> {
                if (instance instanceof RenderContext context) {
                    return context.partialTicks();
                }

                return null;
            }, Float.class));

            uniformEvent.register(Identifier.of("renderTick"), ValueGetter.create((instance) -> {
                if (instance instanceof RenderContext context) {
                    return context.renderTick();
                }

                return null;
            }, Integer.class));

            uniformEvent.register(Identifier.of("windowWidth"), ValueGetter.create((instance) -> {
                if (instance instanceof RenderContext context) {
                    return context.windowWidth();
                }

                return null;
            }, Integer.class));

            uniformEvent.register(Identifier.of("windowHeight"), ValueGetter.create((instance) -> {
                if (instance instanceof RenderContext context) {
                    return context.windowHeight();
                }

                return null;
            }, Integer.class));

            uniformEvent.register(Identifier.of("sketch_frustumPos"), ValueGetter.create((instance) -> {
                if (instance instanceof McRenderContext context && context.cullingFrustum() != null) {
                    return new Vector3f(
                            (float) ((AccessorFrustum) context.cullingFrustum()).camX(),
                            (float) ((AccessorFrustum) context.cullingFrustum()).camY(),
                            (float) ((AccessorFrustum) context.cullingFrustum()).camZ());
                }

                return null;
            }, Vector3f.class));

            uniformEvent.register(Identifier.of("sketch_cullingFrustum"), ValueGetter.create((instance) -> {
                if (instance instanceof McRenderContext context && context.cullingFrustum() != null) {
                    return SketchRender.getFrustumPlanes(((AccessorFrustum) context.cullingFrustum()).frustumIntersection());
                }

                return null;
            }, Vector4f[].class));

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
                if (instance instanceof McRenderContext context) {
                    return context.get(CullingStateManager.LEVEL_MIN_POS_ID);
                }

                return null;
            }, Integer.class));

            uniformEvent.register(Identifier.of("sketch_levelPosRange"), ValueGetter.create((instance) -> {
                if (instance instanceof McRenderContext context) {
                    return context.get(CullingStateManager.LEVEL_POS_RANGE_ID);
                }

                return null;
            }, Integer.class));

            uniformEvent.register(Identifier.of("sketch_levelSectionRange"), ValueGetter.create((instance) -> {
                if (instance instanceof McRenderContext context) {
                    return context.get(CullingStateManager.LEVEL_SECTION_RANGE_ID);
                }

                return null;
            }, Integer.class));

            uniformEvent.register(Identifier.of("sketch_cameraOffset"), ValueGetter.create((instance) -> {
                if (instance instanceof McRenderContext context) {
                    Vec3 pos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
                    BlockPos blockPos = new BlockPos((int) pos.x >> 4, context.<Integer>get(CullingStateManager.LEVEL_MIN_POS_ID) >> 4, (int) pos.z >> 4);
                    return new Vector3i(blockPos.getX(), blockPos.getY(), blockPos.getZ());
                }

                return null;
            }, Vector3i.class));

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
                if (instance instanceof RenderContext context) {
                    return context.viewMatrix();
                }

                return null;
            }, Matrix4f.class));

            uniformEvent.register(Identifier.of("sketch_cullingProjMat"), ValueGetter.create((instance) -> {
                if (instance instanceof RenderContext context) {
                    return context.projectionMatrix();
                }

                return null;
            }, Matrix4f.class));

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
                if (instance instanceof ComputeHIZGraphics hizGraphics) {
                    return hizGraphics.first() ? 1 : 0;
                }

                return null;
            }, Integer.class));

            uniformEvent.register(Identifier.of("sketch_screenSize"), ValueGetter.create((instance) -> {
                if (instance instanceof ComputeHIZGraphics hizGraphics) {
                    RenderTarget screen = Minecraft.getInstance().getMainRenderTarget();
                    if (!hizGraphics.first()) {
                        screen = CullingStateManager.DEPTH_BUFFER_TARGET[3];
                    }
                    return new Vector2i(screen.width, screen.height);
                }

                return null;
            }, Vector2i.class));
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
            Optional<PartialRenderSetting> renderSetting = GraphicsResourceManager.getInstance().getResource(ResourceTypes.PARTIAL_RENDER_SETTING, Identifier.of("sketchrender:hierarchy_depth_buffer_first"));
            if (renderSetting.isPresent()) {
                PartialRenderSetting partialRenderSetting = renderSetting.get();
                RenderSetting setting = RenderSetting.computeShader(partialRenderSetting);
                registerEvent.register(CullingStages.HIZ, new ComputeHIZGraphics(Identifier.of(SketchRender.MOD_ID, "hierarchy_depth_buffer_first"), true), setting);
            }

            renderSetting = GraphicsResourceManager.getInstance().getResource(ResourceTypes.PARTIAL_RENDER_SETTING, Identifier.of(SketchRender.MOD_ID, "hierarchy_depth_buffer_second"));
            if (renderSetting.isPresent()) {
                PartialRenderSetting partialRenderSetting = renderSetting.get();
                RenderSetting setting = RenderSetting.computeShader(partialRenderSetting);
                registerEvent.register(CullingStages.HIZ, new ComputeHIZGraphics(Identifier.of(SketchRender.MOD_ID, "hierarchy_depth_buffer_second"), false), setting);
            }

            renderSetting = GraphicsResourceManager.getInstance().getResource(ResourceTypes.PARTIAL_RENDER_SETTING, Identifier.of(SketchRender.MOD_ID, "cull_entity_batch"));
            if (renderSetting.isPresent()) {
                PartialRenderSetting partialRenderSetting = renderSetting.get();
                RenderSetting setting = RenderSetting.computeShader(partialRenderSetting);
                registerEvent.register(CullingStages.HIZ, new ComputeEntityCullingGraphics(Identifier.of(SketchRender.MOD_ID, "cull_entity_batch")), setting);
            }
        }
    }
}