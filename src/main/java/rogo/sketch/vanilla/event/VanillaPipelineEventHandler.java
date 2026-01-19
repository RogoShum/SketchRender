package rogo.sketch.vanilla.event;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.*;
import rogo.sketch.Config;
import rogo.sketch.SketchRender;
import rogo.sketch.api.graphics.Graphics;
import rogo.sketch.compat.sodium.MeshResource;
import rogo.sketch.compat.sodium.SodiumImplOptions;
import rogo.sketch.event.GraphicsPipelineInitEvent;
import rogo.sketch.event.RegisterStaticGraphicsEvent;
import rogo.sketch.event.RenderFlowRegisterEvent;
import rogo.sketch.event.UniformHookRegisterEvent;
import rogo.sketch.event.bridge.ProxyEvent;
import rogo.sketch.event.bridge.ProxyModEvent;
import rogo.sketch.feature.culling.CullingStages;
import rogo.sketch.feature.culling.CullingStateManager;
import rogo.sketch.feature.culling.graphics.*;
import rogo.sketch.mixin.AccessorFrustum;
import rogo.sketch.render.data.PrimitiveType;
import rogo.sketch.render.data.Usage;
import rogo.sketch.render.data.format.VertexLayoutSpec;
import rogo.sketch.render.instance.CubeTestGraphics;
import rogo.sketch.render.pipeline.*;
import rogo.sketch.render.pipeline.flow.impl.ComputeFlowStrategy;
import rogo.sketch.render.pipeline.flow.impl.RasterizationFlowStrategy;
import rogo.sketch.render.resource.GraphicsResourceManager;
import rogo.sketch.render.resource.ResourceTypes;
import rogo.sketch.render.shader.uniform.ValueGetter;
import rogo.sketch.render.vertex.DefaultDataFormats;
import rogo.sketch.util.KeyId;
import rogo.sketch.vanilla.McGraphicsPipeline;
import rogo.sketch.vanilla.McRenderContext;
import rogo.sketch.vanilla.MinecraftRenderStages;
import rogo.sketch.vanilla.resource.TempTexture;
import rogo.sketch.vanilla.resource.loader.VanillaTextureLoader;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public class VanillaPipelineEventHandler {
    public static final TempTexture mainColor = new TempTexture(() -> Minecraft.getInstance().getMainRenderTarget(),
            false);
    public static final TempTexture mainDepth = new TempTexture(() -> Minecraft.getInstance().getMainRenderTarget(),
            true);

    public static void registerPersistentResource() {
        GraphicsResourceManager.getInstance().registerMutable(ResourceTypes.TEXTURE,
                KeyId.of(SketchRender.MOD_ID, "hiz_texture"),
                () -> Optional.of(CullingStateManager.DEPTH_BUFFER_TARGET.texture()));

        GraphicsResourceManager.getInstance().registerMutable(ResourceTypes.TEXTURE,
                KeyId.of("minecraft", "main_color"),
                () -> Optional.of(mainColor.getTexture()));
        GraphicsResourceManager.getInstance().registerMutable(ResourceTypes.TEXTURE,
                KeyId.of("minecraft", "main_depth"),
                () -> Optional.of(mainDepth.getTexture()));

        GraphicsResourceManager.getInstance().registerMutable(ResourceTypes.SHADER_STORAGE_BUFFER,
                KeyId.of(SketchRender.MOD_ID, "entity_data"),
                () -> {
                    if (CullingStateManager.ENTITY_CULLING_MASK != null) {
                        return Optional.of(CullingStateManager.ENTITY_CULLING_MASK.getEntityDataSSBO());
                    } else {
                        return Optional.empty();
                    }
                });
        GraphicsResourceManager.getInstance().registerMutable(ResourceTypes.SHADER_STORAGE_BUFFER,
                KeyId.of(SketchRender.MOD_ID, "entity_culling_result"),
                () -> {
                    if (CullingStateManager.ENTITY_CULLING_MASK != null) {
                        return Optional.of(CullingStateManager.ENTITY_CULLING_MASK.getEntityCullingResult());
                    } else {
                        return Optional.empty();
                    }
                });

        GraphicsResourceManager.getInstance().registerMutable(ResourceTypes.SHADER_STORAGE_BUFFER,
                KeyId.of(SketchRender.MOD_ID, "chunk_section_mesh"),
                () -> {
                    return Optional.of(MeshResource.MESH_MANAGER.meshDataBuffer());
                });

        GraphicsResourceManager.getInstance().registerMutable(ResourceTypes.SHADER_STORAGE_BUFFER,
                KeyId.of(SketchRender.MOD_ID, "chunk_draw_command"),
                () -> {
                    if (MeshResource.COMMAND_BUFFER != null) {
                        return Optional.of(MeshResource.COMMAND_BUFFER);
                    } else {
                        return Optional.empty();
                    }
                });

        GraphicsResourceManager.getInstance().registerMutable(ResourceTypes.SHADER_STORAGE_BUFFER,
                KeyId.of(SketchRender.MOD_ID, "mesh_counter"),
                () -> {
                    if (MeshResource.BATCH_COUNTER != null) {
                        return Optional.of(MeshResource.BATCH_COUNTER);
                    } else {
                        return Optional.empty();
                    }
                });
        GraphicsResourceManager.getInstance().registerMutable(ResourceTypes.SHADER_STORAGE_BUFFER,
                KeyId.of(SketchRender.MOD_ID, "region_pos"),
                () -> {
                    if (MeshResource.REGION_INDEX_BUFFER != null) {
                        return Optional.of(MeshResource.REGION_INDEX_BUFFER);
                    } else {
                        return Optional.empty();
                    }
                });

        GraphicsResourceManager.getInstance().registerMutable(ResourceTypes.SHADER_STORAGE_BUFFER,
                KeyId.of(SketchRender.MOD_ID, "max_element_count"),
                () -> {
                    if (MeshResource.MAX_ELEMENT_BUFFER != null) {
                        return Optional.of(MeshResource.MAX_ELEMENT_BUFFER);
                    } else {
                        return Optional.empty();
                    }
                });

        GraphicsResourceManager.getInstance().registerMutable(ResourceTypes.SHADER_STORAGE_BUFFER,
                KeyId.of(SketchRender.MOD_ID, "persistent_max_element_count"),
                () -> {
                    if (MeshResource.PERSISTENT_MAX_ELEMENT_BUFFER != null) {
                        return Optional.of(MeshResource.PERSISTENT_MAX_ELEMENT_BUFFER);
                    } else {
                        return Optional.empty();
                    }
                });
    }

    public static void onUniformInit(ProxyModEvent<UniformHookRegisterEvent> event) {
        UniformHookRegisterEvent uniformEvent = event.getWrapped();
        uniformEvent.register(KeyId.of("viewMatrix"), ValueGetter.create((instance) -> {
            RenderContext context = (RenderContext) instance;
            return context.viewMatrix();
        }, Matrix4f.class, RenderContext.class));

        uniformEvent.register(KeyId.of("modelMatrix"), ValueGetter.create((instance) -> {
            RenderContext context = (RenderContext) instance;
            return context.modelMatrix();
        }, Matrix4f.class, RenderContext.class));

        uniformEvent.register(KeyId.of("projectionMatrix"), ValueGetter.create((instance) -> {
            RenderContext context = (RenderContext) instance;
            return context.projectionMatrix();
        }, Matrix4f.class, RenderContext.class));

        uniformEvent.register(KeyId.of("partialTicks"), ValueGetter.create((instance) -> {
            RenderContext context = (RenderContext) instance;
            return context.partialTicks();
        }, Float.class, RenderContext.class));

        uniformEvent.register(KeyId.of("renderTick"), ValueGetter.create((instance) -> {
            RenderContext context = (RenderContext) instance;
            return context.renderTick();
        }, Integer.class, RenderContext.class));

        uniformEvent.register(KeyId.of("windowWidth"), ValueGetter.create((instance) -> {
            RenderContext context = (RenderContext) instance;
            return context.windowWidth();
        }, Integer.class, RenderContext.class));

        uniformEvent.register(KeyId.of("windowHeight"), ValueGetter.create((instance) -> {
            RenderContext context = (RenderContext) instance;
            return context.windowHeight();
        }, Integer.class, RenderContext.class));

        uniformEvent.register(KeyId.of("windowSize"), ValueGetter.create((instance) -> {
            RenderContext context = (RenderContext) instance;
            return new Vector2f(context.windowWidth(), context.windowHeight());
        }, Vector2f.class, RenderContext.class));

        uniformEvent.register(KeyId.of("sketch_frustumPos"), ValueGetter.create((instance) -> {
            McRenderContext context = (McRenderContext) instance;
            if (context.cullingFrustum() != null) {
                return new Vector3f(
                        (float) ((AccessorFrustum) context.cullingFrustum()).camX(),
                        (float) ((AccessorFrustum) context.cullingFrustum()).camY(),
                        (float) ((AccessorFrustum) context.cullingFrustum()).camZ());
            }
            return null;
        }, Vector3f.class, McRenderContext.class));

        uniformEvent.register(KeyId.of("sketch_cullingFrustum"), ValueGetter.create((instance) -> {
            McRenderContext context = (McRenderContext) instance;
            if (context.cullingFrustum() != null) {
                return SketchRender
                        .getFrustumPlanes(((AccessorFrustum) context.cullingFrustum()).frustumIntersection());
            }
            return null;
        }, Vector4f[].class, McRenderContext.class));

        if (SketchRender.hasSodium()) {
            uniformEvent.register(KeyId.of("sketch_cullFacing"), ValueGetter.create(() -> {
                return SodiumImplOptions.useBlockFaceCulling() ? 1 : 0;
            }, Integer.class));

            uniformEvent.register(KeyId.of("sketch_translucentSort"), ValueGetter.create(() -> {
                return SodiumImplOptions.canApplyTranslucencySorting() ? 1 : 0;
            }, Integer.class));
        }

        uniformEvent.register(KeyId.of("sketch_entityCount"), ValueGetter.create(() -> {
            return CullingStateManager.ENTITY_CULLING_MASK == null ? 0
                    : CullingStateManager.ENTITY_CULLING_MASK.getEntityMap().size();
        }, Integer.class));

        uniformEvent.register(KeyId.of("sketch_cullingTerrain"), ValueGetter.create(() -> {
            return !Config.getCullChunk() || CullingStateManager.SHADER_LOADER.renderingShadowPass() ? 0 : 1;
        }, Integer.class));

        uniformEvent.register(KeyId.of("sketch_checkCulling"), ValueGetter.create(() -> {
            return CullingStateManager.CHECKING_CULL ? 1 : 0;
        }, Integer.class));

        uniformEvent.register(KeyId.of("sketch_levelMinPos"), ValueGetter.create((instance) -> {
            McRenderContext context = (McRenderContext) instance;
            return context.get(CullingStateManager.LEVEL_MIN_POS_ID);
        }, Integer.class, McRenderContext.class));

        uniformEvent.register(KeyId.of("sketch_levelPosRange"), ValueGetter.create((instance) -> {
            McRenderContext context = (McRenderContext) instance;
            return context.get(CullingStateManager.LEVEL_POS_RANGE_ID);
        }, Integer.class, McRenderContext.class));

        uniformEvent.register(KeyId.of("sketch_levelSectionRange"), ValueGetter.create((instance) -> {
            McRenderContext context = (McRenderContext) instance;
            return context.get(CullingStateManager.LEVEL_SECTION_RANGE_ID);
        }, Integer.class, McRenderContext.class));

        uniformEvent.register(KeyId.of("sketch_cameraOffset"), ValueGetter.create((instance) -> {
            McRenderContext context = (McRenderContext) instance;
            Vec3 pos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            BlockPos blockPos = new BlockPos((int) pos.x >> 4,
                    context.<Integer>get(CullingStateManager.LEVEL_MIN_POS_ID) >> 4, (int) pos.z >> 4);
            return new Vector3i(blockPos.getX(), blockPos.getY(), blockPos.getZ());
        }, Vector3i.class, McRenderContext.class));

        uniformEvent.register(KeyId.of("sketch_cameraPos"), ValueGetter.create(() -> {
            Vec3 pos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            return new Vector3i((int) pos.x, (int) pos.y, (int) pos.z);
        }, Vector3i.class));

        uniformEvent.register(KeyId.of("sketch_renderDistance"), ValueGetter.create(() -> {
            return MeshResource.getRenderDistance();
        }, Integer.class));

        uniformEvent.register(KeyId.of("sketch_spacePartitionSize"), ValueGetter.create(() -> {
            return MeshResource.getSpacePartitionSize();
        }, Integer.class));

        uniformEvent.register(KeyId.of("sketch_cullingViewMat"), ValueGetter.create((instance) -> {
            RenderContext context = (RenderContext) instance;
            return context.viewMatrix();
        }, Matrix4f.class, RenderContext.class));

        uniformEvent.register(KeyId.of("sketch_cullingProjMat"), ValueGetter.create((instance) -> {
            RenderContext context = (RenderContext) instance;
            return context.projectionMatrix();
        }, Matrix4f.class, RenderContext.class));

        uniformEvent.register(KeyId.of("sketch_cullingCameraPos"), ValueGetter.create(() -> {
            return Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().toVector3f();
        }, Vector3f.class));

        uniformEvent.register(KeyId.of("sketch_cullingCameraDir"), ValueGetter.create(() -> {
            return Minecraft.getInstance().gameRenderer.getMainCamera().getLookVector();
        }, Vector3f.class));

        uniformEvent.register(KeyId.of("sketch_depthSize"), ValueGetter.create(() -> {
            Vector3i[] array = new Vector3i[CullingStateManager.DEPTH_SIZE];
            System.arraycopy(CullingStateManager.DEPTH_BUFFER_INFORMATION, 0, array, 0, CullingStateManager.DEPTH_SIZE);

            return array;
        }, Vector3i[].class));

        uniformEvent.register(KeyId.of("sketch_linerDepth"), ValueGetter.create((instance) -> {
            ComputeHIZGraphics hizGraphics = (ComputeHIZGraphics) instance;
            return hizGraphics.first() ? 1 : 0;
        }, Integer.class, ComputeHIZGraphics.class));

        uniformEvent.register(KeyId.of("sketch_cullingFineness"), ValueGetter.create(() -> {
            return (float) Config.getDepthUpdateDelay();
        }, Float.class));

        uniformEvent.register(KeyId.of("sketch_screenSize"), ValueGetter.create((instance) -> {
            ComputeHIZGraphics hizGraphics = (ComputeHIZGraphics) instance;

            if (!hizGraphics.first()) {
                Vector3i screenSize = CullingStateManager.DEPTH_BUFFER_INFORMATION[3];
                return new Vector2i(screenSize.x, screenSize.y);
            } else {
                RenderTarget screen = Minecraft.getInstance().getMainRenderTarget();
                return new Vector2i(screen.width, screen.height);
            }
        }, Vector2i.class, ComputeHIZGraphics.class));

        uniformEvent.register(KeyId.of("sketch_testPos"), ValueGetter.create(() -> {
            if (SketchRender.testPos != null) {
                return new Vector4f(SketchRender.testPos.getX(), SketchRender.testPos.getY(),
                        SketchRender.testPos.getZ(), 1);
            }
            return new Vector4f(0, 0, 0, 0);
        }, Vector4f.class));

        // Separate uniforms for entities
        uniformEvent.register(KeyId.of("sketch_testEntityPos"), ValueGetter.create(() -> {
            if (SketchRender.testEntity != null) {
                return new Vector4f((float) SketchRender.testEntity.position().x,
                        (float) SketchRender.testEntity.position().y, (float) SketchRender.testEntity.position().z, 1);
            } else {
                return new Vector4f(0, 0, 0, 0);
            }
        }, Vector4f.class));

        uniformEvent.register(KeyId.of("sketch_testEntityAABB"), ValueGetter.create(() -> {
            if (SketchRender.testEntity != null) {
                AABB aabb = SketchRender.getObjectAABB(SketchRender.testEntity);
                return new Vector3f((float) aabb.getXsize(), (float) aabb.getYsize(), (float) aabb.getZsize());
            }
            return new Vector3f(0, 0, 0);
        }, Vector3f.class));

        // Separate uniforms for block entities
        uniformEvent.register(KeyId.of("sketch_testBlockEntityPos"), ValueGetter.create(() -> {
            if (SketchRender.testBlockEntity != null) {
                return new Vector4f((float) SketchRender.testBlockEntity.getBlockPos().getX() + 0.5f,
                        (float) SketchRender.testBlockEntity.getBlockPos().getY(),
                        (float) SketchRender.testBlockEntity.getBlockPos().getZ() + 0.5f, 1);
            } else {
                return new Vector4f(0, 0, 0, 0);
            }
        }, Vector4f.class));

        uniformEvent.register(KeyId.of("sketch_testBlockEntityAABB"), ValueGetter.create(() -> {
            if (SketchRender.testBlockEntity != null) {
                AABB aabb = SketchRender.getObjectAABB(SketchRender.testBlockEntity);
                return new Vector3f((float) aabb.getXsize(), (float) aabb.getYsize(), (float) aabb.getZsize());
            }
            return new Vector3f(0, 0, 0);
        }, Vector3f.class));
    }

    public static void onPipelineInit(ProxyModEvent<GraphicsPipelineInitEvent> event) {
        GraphicsPipelineInitEvent pipeLineInitEvent = event.getWrapped();
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
                GraphicsResourceManager.getInstance().registerLoader(ResourceTypes.TEXTURE, new VanillaTextureLoader());
                if (!mcPipeline.getPendingStages().isEmpty()) {
                    SketchRender.LOGGER.warn("Warning: {} stages are still pending",
                            mcPipeline.getPendingStages().size());
                }
            }
        }
    }

    @SubscribeEvent
    public void onStaticGraphicsRegister(ProxyEvent<RegisterStaticGraphicsEvent> event) {
        RegisterStaticGraphicsEvent registerEvent = event.getWrapped();
        registerReloadableComputeShader(registerEvent, SketchRender.MOD_ID + ":hierarchy_depth_buffer_first",
                () -> new ComputeHIZGraphics(KeyId.of(SketchRender.MOD_ID, "hierarchy_depth_buffer_first"), true));

        registerReloadableComputeShader(registerEvent, SketchRender.MOD_ID + ":hierarchy_depth_buffer_second",
                () -> new ComputeHIZGraphics(KeyId.of(SketchRender.MOD_ID, "hierarchy_depth_buffer_second"),
                        false));

        registerReloadableComputeShader(registerEvent, SketchRender.MOD_ID + ":cull_entity_batch",
                () -> new ComputeEntityCullingGraphics(KeyId.of(SketchRender.MOD_ID, "cull_entity_batch")));

        RenderSystem.recordRenderCall(() -> {
            registerNewPipelineCullingGraphics(registerEvent);
        });
    }

    public static void onBaseRenderFlowRegisterInit(ProxyModEvent<RenderFlowRegisterEvent> event) {
        RenderFlowRegisterEvent registerEvent = event.getWrapped();
        registerEvent.register(new ComputeFlowStrategy());
        registerEvent.register(new RasterizationFlowStrategy());
    }

    /**
     * Register new pipeline culling test graphics instances for chunk, entity, and
     * block entity
     */
    private static void registerNewPipelineCullingGraphics(RegisterStaticGraphicsEvent registerEvent) {
        // Register chunk culling graphics
        ChunkCullingTestGraphics chunkGraphics = new ChunkCullingTestGraphics(
                KeyId.of(SketchRender.MOD_ID, "culling_test_chunk_new"));
        // Since we don't have mesh yet, register as legacy for compatibility
        registerNewPipelineGraphicsAsLegacy(registerEvent, chunkGraphics, "culling_test_chunk");

        // Register entity culling graphics
        EntityCullingTestGraphics entityGraphics = new EntityCullingTestGraphics(
                KeyId.of(SketchRender.MOD_ID, "culling_test_entity_new"));
        registerNewPipelineGraphicsAsLegacy(registerEvent, entityGraphics, "culling_test_entity");

        // Register block entity culling graphics
        BlockEntityCullingTestGraphics blockEntityGraphics = new BlockEntityCullingTestGraphics(
                KeyId.of(SketchRender.MOD_ID, "culling_test_block_entity_new"));
        registerNewPipelineGraphicsAsLegacy(registerEvent, blockEntityGraphics, "culling_test_block_entity");

        registerTestCube(registerEvent, true, new Vector3f(0, 0, 0), new Vector3f(0.5f, 0.5f, 0.5f), new Vector3f(0, 0, 0) , MinecraftRenderStages.ENTITIES.getIdentifier());
        registerTestCube(registerEvent, true, new Vector3f(0.5f, 0, 0), new Vector3f(0.25f, 0.25f, 0.25f), new Vector3f(0, 0, 0), MinecraftRenderStages.ENTITIES.getIdentifier());
        registerTestCube(registerEvent, true, new Vector3f(-0.5f, 0, 0), new Vector3f(0.25f, 0.25f, 0.25f), new Vector3f(0, 0, 0), MinecraftRenderStages.ENTITIES.getIdentifier());
        registerTestCube(registerEvent, true, new Vector3f(0, 0.5f, 0), new Vector3f(0.25f, 0.25f, 0.25f), new Vector3f(0, 0, 0), MinecraftRenderStages.BLOCK_ENTITIES.getIdentifier());
        registerTestCube(registerEvent, false, new Vector3f(0.5f, 0, 0), new Vector3f(0.5f, 0.5f, 0.5f), new Vector3f(0, 0, 0), MinecraftRenderStages.DESTROY_PROGRESS.getIdentifier());
        registerTestCube(registerEvent, false, new Vector3f(-0.5f, 0, 0), new Vector3f(0.5f, 0.5f, 0.5f), new Vector3f(0, 0, 0), MinecraftRenderStages.BLOCK_OUTLINE.getIdentifier());
    }

    private static void registerNewPipelineGraphicsAsLegacy(RegisterStaticGraphicsEvent registerEvent,
                                                            Graphics instance,
                                                            String settingName) {
        KeyId settingId = KeyId.of(SketchRender.MOD_ID, settingName);
        Optional<PartialRenderSetting> renderSetting = GraphicsResourceManager.getInstance()
                .getResource(ResourceTypes.PARTIAL_RENDER_SETTING, settingId);

        if (renderSetting.isPresent()) {
            PartialRenderSetting partialRenderSetting = renderSetting.get();
            VertexLayoutSpec layout = VertexLayoutSpec.builder()
                    .addDynamic(0, DefaultDataFormats.POSITION)
                    .build();

            RenderParameter renderParameter = RasterizationParameter.create(
                    layout,
                    PrimitiveType.QUADS,
                    Usage.DYNAMIC_DRAW,
                    false);
            RenderSetting setting = RenderSetting.fromPartial(partialRenderSetting, renderParameter);
            registerEvent.register(MinecraftRenderStages.POST_PROGRESS.getIdentifier(), instance, setting);
        }
    }

    private static void registerTestCube(RegisterStaticGraphicsEvent registerEvent,
                                                            boolean attachHead,
                                                            Vector3f offset,
                                                            Vector3f scale,
                                                            Vector3f rotation,
                                                            KeyId stage) {
        Graphics cubeTestGraphics = new CubeTestGraphics(KeyId.of(SketchRender.MOD_ID, "cube_test_" + UUID.randomUUID()), attachHead, offset, scale, rotation);
        Optional<PartialRenderSetting> renderSetting = GraphicsResourceManager.getInstance()
                .getResource(ResourceTypes.PARTIAL_RENDER_SETTING, KeyId.of(SketchRender.MOD_ID, "cube_test"));

        if (renderSetting.isPresent()) {
            PartialRenderSetting partialRenderSetting = renderSetting.get();
            VertexLayoutSpec layout = VertexLayoutSpec.builder()
                    .addStatic(0, DefaultDataFormats.POSITION_UV_NORMAL)
                    .addDynamicInstanced(1, DefaultDataFormats.POSITION)
                    .addDynamicInstanced(2, DefaultDataFormats.TRANSFORM)
                    .build();

            RenderParameter renderParameter = RasterizationParameter.create(
                    layout,
                    PrimitiveType.QUADS,
                    Usage.DYNAMIC_DRAW,
                    false);
            RenderSetting setting = RenderSetting.fromPartial(partialRenderSetting, renderParameter);
            registerEvent.register(stage, cubeTestGraphics, setting);
        }
    }

    private static void registerReloadableComputeShader(RegisterStaticGraphicsEvent registerEvent,
                                                        String settingIdString, Supplier<Graphics> instanceSupplier) {
        KeyId settingId = KeyId.of(settingIdString);
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