package rogo.sketch.vanilla.event;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.joml.*;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import rogo.sketch.Config;
import rogo.sketch.SketchRender;
import rogo.sketch.compat.sodium.MeshResource;
import rogo.sketch.compat.sodium.SodiumImplOptions;
import rogo.sketch.core.api.graphics.Graphics;
import rogo.sketch.core.api.model.BakedTypeMesh;
import rogo.sketch.core.api.model.DynamicTypeMesh;
import rogo.sketch.core.data.PrimitiveType;
import rogo.sketch.core.data.Usage;
import rogo.sketch.core.data.format.VertexLayoutSpec;
import rogo.sketch.core.event.*;
import rogo.sketch.core.instance.TransformComputeGraphics;
import rogo.sketch.core.pipeline.PartialRenderSetting;
import rogo.sketch.core.pipeline.PipelineType;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.flow.impl.ComputeFlowStrategy;
import rogo.sketch.core.pipeline.flow.impl.FunctionFlowStrategy;
import rogo.sketch.core.pipeline.flow.impl.RasterizationFlowStrategy;
import rogo.sketch.core.pipeline.parmeter.RasterizationParameter;
import rogo.sketch.core.pipeline.parmeter.RenderParameter;
import rogo.sketch.core.resource.GraphicsResourceManager;
import rogo.sketch.core.resource.ResourceReference;
import rogo.sketch.core.resource.ResourceTypes;
import rogo.sketch.core.resource.loader.DrawCallGraphicsLoader;
import rogo.sketch.core.resource.loader.FunctionGraphicsLoader;
import rogo.sketch.core.shader.uniform.ValueGetter;
import rogo.sketch.core.util.KeyId;
import rogo.sketch.core.vertex.DefaultDataFormats;
import rogo.sketch.event.ProxyEvent;
import rogo.sketch.event.ProxyModEvent;
import rogo.sketch.feature.culling.CullingStages;
import rogo.sketch.feature.culling.CullingStateManager;
import rogo.sketch.feature.culling.graphics.*;
import rogo.sketch.mixin.AccessorFrustum;
import rogo.sketch.vanilla.McGraphicsPipeline;
import rogo.sketch.vanilla.McRenderContext;
import rogo.sketch.vanilla.MinecraftRenderStages;
import rogo.sketch.vanilla.PipelineUtil;
import rogo.sketch.vanilla.graphics.CubeTestGraphics;
import rogo.sketch.vanilla.graphics.PlayerGraphics;
import rogo.sketch.vanilla.resource.BuildInRTTexture;
import rogo.sketch.vanilla.resource.BuildInRenderTarget;
import rogo.sketch.vanilla.resource.loader.VanillaTextureLoader;

import java.util.Random;
import java.util.*;
import java.util.function.Supplier;

public class VanillaPipelineEventHandler {
    public static final BuildInRTTexture mainColor = new BuildInRTTexture(
            () -> Minecraft.getInstance().getMainRenderTarget(), GL11.GL_RGBA, false, GL11.GL_NEAREST, GL11.GL_NEAREST, GL12.GL_CLAMP_TO_EDGE, GL12.GL_CLAMP_TO_EDGE);
    public static final BuildInRTTexture mainDepth = new BuildInRTTexture(
            () -> Minecraft.getInstance().getMainRenderTarget(), GL11.GL_RGBA, true, GL11.GL_NEAREST, GL11.GL_NEAREST, GL12.GL_CLAMP_TO_EDGE, GL12.GL_CLAMP_TO_EDGE);
    public static final BuildInRenderTarget mainTarget = new BuildInRenderTarget(
            () -> Minecraft.getInstance().getMainRenderTarget().frameBufferId, KeyId.of("minecraft:main_target"));

    public static void registerPersistentResource() {
        GraphicsResourceManager.getInstance().registerBuiltIn(ResourceTypes.TEXTURE,
                KeyId.of(SketchRender.MOD_ID, "hiz_texture"),
                () -> CullingStateManager.DEPTH_BUFFER_TARGET.texture());

        GraphicsResourceManager.getInstance().registerBuiltIn(ResourceTypes.TEXTURE,
                KeyId.of("minecraft", "main_color"),
                () -> mainColor);
        GraphicsResourceManager.getInstance().registerBuiltIn(ResourceTypes.TEXTURE,
                KeyId.of("minecraft", "main_depth"),
                () -> mainDepth);
        GraphicsResourceManager.getInstance().registerBuiltIn(ResourceTypes.RENDER_TARGET,
                KeyId.of("minecraft:main_target"), () -> {
                    return mainTarget;
                });

        GraphicsResourceManager.getInstance().registerBuiltIn(ResourceTypes.SHADER_STORAGE_BUFFER,
                KeyId.of(SketchRender.MOD_ID, "entity_data"),
                () -> {
                    if (CullingStateManager.ENTITY_CULLING_MASK != null && !CullingStateManager.ENTITY_CULLING_MASK.getEntityDataSSBO().isDisposed()) {
                        return CullingStateManager.ENTITY_CULLING_MASK.getEntityDataSSBO();
                    } else {
                        return null;
                    }
                });

        GraphicsResourceManager.getInstance().registerBuiltIn(ResourceTypes.SHADER_STORAGE_BUFFER,
                KeyId.of(SketchRender.MOD_ID, "entity_culling_result"),
                () -> {
                    if (CullingStateManager.ENTITY_CULLING_MASK != null && !CullingStateManager.ENTITY_CULLING_MASK.getEntityCullingResult().isDisposed()) {
                        return CullingStateManager.ENTITY_CULLING_MASK.getEntityCullingResult();
                    } else {
                        return null;
                    }
                });

        GraphicsResourceManager.getInstance().registerBuiltIn(ResourceTypes.SHADER_STORAGE_BUFFER,
                KeyId.of(SketchRender.MOD_ID, "chunk_section_mesh"),
                () -> {
                    return MeshResource.MESH_MANAGER.meshDataBuffer();
                });

        GraphicsResourceManager.getInstance().registerBuiltIn(ResourceTypes.SHADER_STORAGE_BUFFER,
                KeyId.of(SketchRender.MOD_ID, "chunk_draw_command"),
                () -> {
                    if (MeshResource.COMMAND_BUFFER != null) {
                        return MeshResource.COMMAND_BUFFER;
                    } else {
                        return null;
                    }
                });

        GraphicsResourceManager.getInstance().registerBuiltIn(ResourceTypes.SHADER_STORAGE_BUFFER,
                KeyId.of(SketchRender.MOD_ID, "mesh_counter"),
                () -> {
                    if (MeshResource.BATCH_COUNTER != null) {
                        return MeshResource.BATCH_COUNTER;
                    } else {
                        return null;
                    }
                });
        GraphicsResourceManager.getInstance().registerBuiltIn(ResourceTypes.SHADER_STORAGE_BUFFER,
                KeyId.of(SketchRender.MOD_ID, "region_pos"),
                () -> {
                    if (MeshResource.REGION_INDEX_BUFFER != null) {
                        return MeshResource.REGION_INDEX_BUFFER;
                    } else {
                        return null;
                    }
                });

        GraphicsResourceManager.getInstance().registerBuiltIn(ResourceTypes.SHADER_STORAGE_BUFFER,
                KeyId.of(SketchRender.MOD_ID, "max_element_count"),
                () -> {
                    if (MeshResource.MAX_ELEMENT_BUFFER != null) {
                        return MeshResource.MAX_ELEMENT_BUFFER;
                    } else {
                        return null;
                    }
                });

        GraphicsResourceManager.getInstance().registerBuiltIn(ResourceTypes.SHADER_STORAGE_BUFFER,
                KeyId.of(SketchRender.MOD_ID, "persistent_max_element_count"),
                () -> {
                    if (MeshResource.PERSISTENT_MAX_ELEMENT_BUFFER != null) {
                        return MeshResource.PERSISTENT_MAX_ELEMENT_BUFFER;
                    } else {
                        return null;
                    }
                });

        // Transform System SSBOs
        GraphicsResourceManager.getInstance().registerBuiltIn(ResourceTypes.SHADER_STORAGE_BUFFER,
                KeyId.of(SketchRender.MOD_ID, "transform_input_async"),
                () -> {
                    if (PipelineUtil.pipeline().transformStateManager() != null && PipelineUtil.pipeline().transformStateManager().matrixManager != null) {
                        return PipelineUtil.pipeline().transformStateManager().matrixManager.getAsyncPipeline().inputSSBO();
                    } else {
                        return null;
                    }
                });

        GraphicsResourceManager.getInstance().registerBuiltIn(ResourceTypes.SHADER_STORAGE_BUFFER,
                KeyId.of(SketchRender.MOD_ID, "transform_index_async"),
                () -> {
                    if (PipelineUtil.pipeline().transformStateManager() != null && PipelineUtil.pipeline().transformStateManager().matrixManager != null) {
                        return PipelineUtil.pipeline().transformStateManager().matrixManager.getAsyncPipeline().indexSSBO();
                    } else {
                        return null;
                    }
                });

        GraphicsResourceManager.getInstance().registerBuiltIn(ResourceTypes.SHADER_STORAGE_BUFFER,
                KeyId.of(SketchRender.MOD_ID, "transform_input_sync"),
                () -> {
                    if (PipelineUtil.pipeline().transformStateManager() != null && PipelineUtil.pipeline().transformStateManager().matrixManager != null) {
                        return PipelineUtil.pipeline().transformStateManager().matrixManager.getSyncPipeline().inputSSBO();
                    } else {
                        return null;
                    }
                });

        GraphicsResourceManager.getInstance().registerBuiltIn(ResourceTypes.SHADER_STORAGE_BUFFER,
                KeyId.of(SketchRender.MOD_ID, "transform_index_sync"),
                () -> {
                    if (PipelineUtil.pipeline().transformStateManager() != null && PipelineUtil.pipeline().transformStateManager().matrixManager != null) {
                        return PipelineUtil.pipeline().transformStateManager().matrixManager.getSyncPipeline().indexSSBO();
                    } else {
                        return null;
                    }
                });

        GraphicsResourceManager.getInstance().registerBuiltIn(ResourceTypes.SHADER_STORAGE_BUFFER,
                KeyId.of(SketchRender.MOD_ID, "transform_output"),
                () -> {
                    if (PipelineUtil.pipeline().transformStateManager() != null && PipelineUtil.pipeline().transformStateManager().matrixManager != null) {
                        return PipelineUtil.pipeline().transformStateManager().matrixManager.getOutputSSBO();
                    } else {
                        return null;
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

        // Transform system uniforms
        uniformEvent.register(KeyId.of("u_transformCount"), ValueGetter.create((instance) -> {
            RenderContext context = (RenderContext) instance;
            if (context.transformStateManager() != null && context.transformStateManager().matrixManager != null) {
                return context.transformStateManager().matrixManager.getActiveCount();
            }
            return 0;
        }, Integer.class, RenderContext.class));

        uniformEvent.register(KeyId.of("u_batchOffset"), ValueGetter.create(() -> {
            return 0;
        }, Integer.class));

        uniformEvent.register(KeyId.of("u_batchCount"), ValueGetter.create(() -> {
            return 0;
        }, Integer.class));
    }

    public static void onPipelineInit(ProxyModEvent<GraphicsPipelineInitEvent> event) {
        GraphicsPipelineInitEvent pipeLineInitEvent = event.getWrapped();
        if (!(pipeLineInitEvent.getPipeline() instanceof McGraphicsPipeline mcPipeline)) {
            return;
        }

        switch (pipeLineInitEvent.getPhase()) {
            case EARLY -> {
                // Set async tick callback for transform data writing
                mcPipeline.asyncGraphicsTicker().setAsyncTickCompleteCallback(
                        mcPipeline.transformStateManager()::onAsyncTickComplete
                );

                // Register vanilla stages first
                MinecraftRenderStages.registerVanillaStages(mcPipeline);
            }
            case NORMAL -> {
                // Register extra stages that might be added by mods
                MinecraftRenderStages.registerExtraStages(mcPipeline);
            }
            case LATE -> {
                GraphicsResourceManager.getInstance().registerLoader(ResourceTypes.TEXTURE, new VanillaTextureLoader());
                GraphicsResourceManager.getInstance().registerLoader(ResourceTypes.FUNCTION, new FunctionGraphicsLoader(pipeLineInitEvent.getPipeline()));
                GraphicsResourceManager.getInstance().registerLoader(ResourceTypes.DRAW_CALL, new DrawCallGraphicsLoader(pipeLineInitEvent.getPipeline()));
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

        registerDepthBufferGraphics(registerEvent,
                () -> new ComputeHIZGraphics(KeyId.of(SketchRender.MOD_ID, "hierarchy_depth_buffer_first"), true).setPriority(98));
        registerDepthBufferGraphics(registerEvent,
                () -> new ComputeHIZGraphics(KeyId.of(SketchRender.MOD_ID, "hierarchy_depth_buffer_second"), false).setPriority(99));
        registerDepthBufferGraphics(registerEvent,
                () -> new ComputeEntityCullingGraphics(KeyId.of(SketchRender.MOD_ID, "cull_entity_batch")).setPriority(101));
        registerTransformGraphics(registerEvent, true);
        registerTransformGraphics(registerEvent, false);

        RenderSystem.recordRenderCall(() -> {
            registerNewPipelineCullingGraphics(registerEvent);
        });
    }

    public static void onBaseRenderFlowRegisterInit(ProxyModEvent<RenderFlowRegisterEvent> event) {
        RenderFlowRegisterEvent registerEvent = event.getWrapped();
        registerEvent.register(new ComputeFlowStrategy());
        registerEvent.register(new RasterizationFlowStrategy());
        registerEvent.register(new FunctionFlowStrategy());
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
        registerNewPipelineGraphicsAsLegacy(registerEvent, chunkGraphics);

        // Register entity culling graphics
        EntityCullingTestGraphics entityGraphics = new EntityCullingTestGraphics(
                KeyId.of(SketchRender.MOD_ID, "culling_test_entity_new"));
        registerNewPipelineGraphicsAsLegacy(registerEvent, entityGraphics);

        // Register block entity culling graphics
        BlockEntityCullingTestGraphics blockEntityGraphics = new BlockEntityCullingTestGraphics(
                KeyId.of(SketchRender.MOD_ID, "culling_test_block_entity_new"));
        registerNewPipelineGraphicsAsLegacy(registerEvent, blockEntityGraphics);

        if (!FMLEnvironment.production) {
            Random random = new Random();
            // Use transform-based cube test
            KeyId cubeTestTransform = KeyId.of(SketchRender.MOD_ID, "cube_test_transform");

            KeyId cube_geometry = KeyId.of("cube_geometry");
            KeyId sphere_geometry = KeyId.of("sphere_geometry");

            List<KeyId> stages = new ArrayList<>();
            stages.add(MinecraftRenderStages.ENTITIES.getIdentifier());
            stages.add(MinecraftRenderStages.BLOCK_ENTITIES.getIdentifier());
            stages.add(MinecraftRenderStages.DESTROY_PROGRESS.getIdentifier());
            stages.add(MinecraftRenderStages.PARTICLE.getIdentifier());
            stages.add(MinecraftRenderStages.WEATHER.getIdentifier());
            // Register PlayerGraphics first (provides parent transform for cubes)
            PlayerGraphics playerGraphics = registerPlayerGraphics(registerEvent);

            for (int i = 0; i < 5; i++) {
                KeyId randStage = stages.get(random.nextInt(stages.size()));
                boolean translucent = random.nextBoolean();

                Vector3f offset = new Vector3f(
                        randomOffset(random),
                        randomOffset(random),
                        randomOffset(random));

                Vector3f scale = new Vector3f(
                        0.5f + random.nextFloat() * 0.5f,
                        0.5f + random.nextFloat() * 0.5f,
                        0.5f + random.nextFloat() * 0.5f);

                registerTestCube(registerEvent, cubeTestTransform,
                        random.nextBoolean() ? cube_geometry : sphere_geometry,
                        offset, scale, new Vector3f(0), randStage, translucent, playerGraphics);
            }
        }
    }

    /**
     * Register the PlayerGraphics instance that provides the player transform.
     */
    private static PlayerGraphics registerPlayerGraphics(RegisterStaticGraphicsEvent registerEvent) {
        PlayerGraphics playerGraphics = new PlayerGraphics(KeyId.of(SketchRender.MOD_ID, "player_transform"));

        // Register with a simple layout (no actual rendering)
        VertexLayoutSpec layout = VertexLayoutSpec.builder().build();
        RenderParameter renderParameter = RasterizationParameter.create(layout, PrimitiveType.QUADS, Usage.STATIC_DRAW, false);

        // Register in the ENTITIES stage so it ticks with entities
        registerEvent.register(MinecraftRenderStages.ENTITIES.getIdentifier(), playerGraphics, renderParameter);

        return playerGraphics;
    }

    private static float randomOffset(Random random) {
        // 50% 正区间，50% 负区间
        if (random.nextBoolean()) {
            return 0.5f + random.nextFloat() * 4.5f; // [0.5, 5.0]
        } else {
            return -5.0f + random.nextFloat() * 4.5f; // [-5.0, -0.5]
        }
    }

    private static void registerNewPipelineGraphicsAsLegacy(RegisterStaticGraphicsEvent registerEvent,
                                                            Graphics instance) {
        VertexLayoutSpec layout = VertexLayoutSpec.builder()
                .addDynamic(DynamicTypeMesh.BASED_MESH, DefaultDataFormats.POSITION).build();
        RenderParameter renderParameter = RasterizationParameter.create(layout, PrimitiveType.QUADS, Usage.DYNAMIC_DRAW,
                false);
        registerEvent.register(MinecraftRenderStages.POST_PROGRESS.getIdentifier(), instance, renderParameter);
    }

    private static void registerTestCube(RegisterStaticGraphicsEvent registerEvent,
                                         KeyId renderSettingKey, KeyId meshName, Vector3f offset, Vector3f scale, Vector3f rotation, KeyId stage,
                                         boolean translucent, PlayerGraphics playerGraphics) {
        ResourceReference<PartialRenderSetting> renderSetting = GraphicsResourceManager.getInstance()
                .getReference(ResourceTypes.PARTIAL_RENDER_SETTING, renderSettingKey);

        // Create cube with Transform system
        CubeTestGraphics cubeTestGraphics = new CubeTestGraphics(
                KeyId.of(SketchRender.MOD_ID, "cube_test_" + UUID.randomUUID()), renderSetting, meshName,
                offset, scale, rotation);

        // Set player transform as parent (if available)
        if (playerGraphics != null) {
            cubeTestGraphics.setParentTransform(playerGraphics.getPlayerTransform());
        }

        // Vertex layout now uses transform ID instead of raw position lerp
        VertexLayoutSpec layout = VertexLayoutSpec.builder()
                .addStatic(BakedTypeMesh.BAKED_MESH, DefaultDataFormats.POSITION_UV_NORMAL)
                .addDynamicInstanced(CubeTestGraphics.TRANSFORM_ID, DefaultDataFormats.INT)
                .build();

        RenderParameter renderParameter = RasterizationParameter.create(layout, PrimitiveType.QUADS, Usage.DYNAMIC_DRAW,
                false);
        registerEvent.register(translucent ? MinecraftRenderStages.TRANSLUCENT.getIdentifier() : stage,
                cubeTestGraphics, renderParameter, translucent ? PipelineType.TRANSLUCENT : PipelineType.RASTERIZATION);
    }

    private static void registerDepthBufferGraphics(RegisterStaticGraphicsEvent registerEvent, Supplier<Graphics> instanceSupplier) {
        registerEvent.registerCompute(CullingStages.HIZ, instanceSupplier.get());
    }

    private static void registerTransformGraphics(RegisterStaticGraphicsEvent registerEvent, boolean sync) {
        Graphics graphics = new TransformComputeGraphics(KeyId.of("sketch_render", "transform_matrix_compute_" + (sync ? "sync" : "async")), KeyId.of("sketch_render", "transform_matrix_" + (sync ? "sync" : "async")), sync, PipelineUtil.pipeline().transformStateManager().matrixManager).setPriority(99);
        registerEvent.registerCompute(MinecraftRenderStages.PREPARE_FRUSTUM.getIdentifier(), graphics);
    }
}