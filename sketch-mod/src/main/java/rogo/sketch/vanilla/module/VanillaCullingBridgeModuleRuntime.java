package rogo.sketch.vanilla.module;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.joml.*;
import rogo.sketch.Config;
import rogo.sketch.SketchRender;
import rogo.sketch.core.api.graphics.Graphics;
import rogo.sketch.core.data.PrimitiveType;
import rogo.sketch.core.data.Usage;
import rogo.sketch.core.data.format.VertexLayoutSpec;
import rogo.sketch.core.pipeline.PipelineType;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.module.metric.MetricDescriptor;
import rogo.sketch.core.pipeline.module.metric.MetricKind;
import rogo.sketch.core.pipeline.module.runtime.ModuleGraphicsLifetime;
import rogo.sketch.core.pipeline.module.runtime.ModuleRuntime;
import rogo.sketch.core.pipeline.module.runtime.ModuleRuntimeContext;
import rogo.sketch.core.pipeline.module.session.ModuleSession;
import rogo.sketch.core.pipeline.module.session.ModuleSessionContext;
import rogo.sketch.core.pipeline.parmeter.RasterizationParameter;
import rogo.sketch.core.resource.ResourceTypes;
import rogo.sketch.core.shader.uniform.ValueGetter;
import rogo.sketch.core.util.KeyId;
import rogo.sketch.core.vertex.DefaultDataFormats;
import rogo.sketch.feature.culling.CullingStages;
import rogo.sketch.feature.culling.CullingStateManager;
import rogo.sketch.feature.culling.graphics.*;
import rogo.sketch.mixin.AccessorFrustum;
import rogo.sketch.vanilla.McRenderContext;
import rogo.sketch.vanilla.MinecraftRenderStages;

public class VanillaCullingBridgeModuleRuntime implements ModuleRuntime {
    public static final KeyId ENTITY_HIDDEN_METRIC = KeyId.of("sketch_render", "entity_hidden_count");
    public static final KeyId ENTITY_TOTAL_METRIC = KeyId.of("sketch_render", "entity_total_count");
    public static final KeyId BLOCK_ENTITY_HIDDEN_METRIC = KeyId.of("sketch_render", "block_entity_hidden_count");
    public static final KeyId BLOCK_ENTITY_TOTAL_METRIC = KeyId.of("sketch_render", "block_entity_total_count");

    @Override
    public String id() {
        return VanillaCullingBridgeModuleDescriptor.MODULE_ID;
    }

    @Override
    public void onKernelInit(ModuleRuntimeContext context) {
        context.setGlobalFlag("IRIS_ENABLE", SketchRender.hasIris());
        context.setGlobalFlag("SODIUM_ENABLE", SketchRender.hasSodium());
        context.registerBuiltInResource(ResourceTypes.TEXTURE,
                KeyId.of(SketchRender.MOD_ID, "hiz_texture"),
                () -> CullingStateManager.DEPTH_BUFFER_TARGET != null ? CullingStateManager.DEPTH_BUFFER_TARGET.texture() : null);
        context.registerBuiltInResource(ResourceTypes.SHADER_STORAGE_BUFFER,
                KeyId.of(SketchRender.MOD_ID, "entity_data"),
                () -> CullingStateManager.ENTITY_CULLING_MASK != null && !CullingStateManager.ENTITY_CULLING_MASK.getEntityDataSSBO().isDisposed()
                        ? CullingStateManager.ENTITY_CULLING_MASK.getEntityDataSSBO()
                        : null);
        context.registerBuiltInResource(ResourceTypes.SHADER_STORAGE_BUFFER,
                KeyId.of(SketchRender.MOD_ID, "entity_culling_result"),
                () -> CullingStateManager.ENTITY_CULLING_MASK != null && !CullingStateManager.ENTITY_CULLING_MASK.getEntityCullingResult().isDisposed()
                        ? CullingStateManager.ENTITY_CULLING_MASK.getEntityCullingResult()
                        : null);

        context.registerMetric(new MetricDescriptor(ENTITY_HIDDEN_METRIC, id(), MetricKind.COUNT, "metric.culling.entity_hidden", null), () -> CullingStateManager.ENTITY_CULLING);
        context.registerMetric(new MetricDescriptor(ENTITY_TOTAL_METRIC, id(), MetricKind.COUNT, "metric.culling.entity_total", null), () -> CullingStateManager.ENTITY_COUNT);
        context.registerMetric(new MetricDescriptor(BLOCK_ENTITY_HIDDEN_METRIC, id(), MetricKind.COUNT, "metric.culling.block_hidden", null), () -> CullingStateManager.BLOCK_CULLING);
        context.registerMetric(new MetricDescriptor(BLOCK_ENTITY_TOTAL_METRIC, id(), MetricKind.COUNT, "metric.culling.block_total", null), () -> CullingStateManager.BLOCK_COUNT);

        registerUniforms(context);
    }

    private void registerUniforms(ModuleRuntimeContext context) {
        context.registerUniform(KeyId.of("sketch_frustumPos"), ValueGetter.create((instance) -> {
            McRenderContext renderContext = (McRenderContext) instance;
            if (renderContext.cullingFrustum() != null) {
                return new Vector3f(
                        (float) ((AccessorFrustum) renderContext.cullingFrustum()).camX(),
                        (float) ((AccessorFrustum) renderContext.cullingFrustum()).camY(),
                        (float) ((AccessorFrustum) renderContext.cullingFrustum()).camZ());
            }
            return null;
        }, Vector3f.class, McRenderContext.class));

        context.registerUniform(KeyId.of("sketch_cullingFrustum"), ValueGetter.create((instance) -> {
            McRenderContext renderContext = (McRenderContext) instance;
            if (renderContext.cullingFrustum() != null) {
                return SketchRender.getFrustumPlanes(((AccessorFrustum) renderContext.cullingFrustum()).frustumIntersection());
            }
            return null;
        }, Vector4f[].class, McRenderContext.class));

        context.registerUniform(KeyId.of("sketch_entityCount"), ValueGetter.create(() ->
                CullingStateManager.ENTITY_CULLING_MASK == null ? 0 : CullingStateManager.ENTITY_CULLING_MASK.getEntityMap().size(), Integer.class));
        context.registerUniform(KeyId.of("sketch_cullingTerrain"), ValueGetter.create(() ->
                (!Config.getCullChunk() || (CullingStateManager.SHADER_LOADER != null && CullingStateManager.SHADER_LOADER.renderingShadowPass())) ? 0 : 1, Integer.class));
        context.registerUniform(KeyId.of("sketch_checkCulling"), ValueGetter.create(() -> CullingStateManager.CHECKING_CULL ? 1 : 0, Integer.class));

        context.registerUniform(KeyId.of("sketch_levelMinPos"), ValueGetter.create((instance) -> ((McRenderContext) instance).get(CullingStateManager.LEVEL_MIN_POS_ID), Integer.class, McRenderContext.class));
        context.registerUniform(KeyId.of("sketch_levelPosRange"), ValueGetter.create((instance) -> ((McRenderContext) instance).get(CullingStateManager.LEVEL_POS_RANGE_ID), Integer.class, McRenderContext.class));
        context.registerUniform(KeyId.of("sketch_levelSectionRange"), ValueGetter.create((instance) -> ((McRenderContext) instance).get(CullingStateManager.LEVEL_SECTION_RANGE_ID), Integer.class, McRenderContext.class));
        context.registerUniform(KeyId.of("sketch_cameraOffset"), ValueGetter.create((instance) -> {
            McRenderContext renderContext = (McRenderContext) instance;
            Vec3 pos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            BlockPos blockPos = new BlockPos((int) pos.x >> 4, renderContext.<Integer>get(CullingStateManager.LEVEL_MIN_POS_ID) >> 4, (int) pos.z >> 4);
            return new Vector3i(blockPos.getX(), blockPos.getY(), blockPos.getZ());
        }, Vector3i.class, McRenderContext.class));
        context.registerUniform(KeyId.of("sketch_cameraPos"), ValueGetter.create(() -> {
            Vec3 pos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            return new Vector3i((int) pos.x, (int) pos.y, (int) pos.z);
        }, Vector3i.class));
        context.registerUniform(KeyId.of("sketch_cullingViewMat"), ValueGetter.create((instance) -> ((RenderContext) instance).viewMatrix(), Matrix4f.class, RenderContext.class));
        context.registerUniform(KeyId.of("sketch_cullingProjMat"), ValueGetter.create((instance) -> ((RenderContext) instance).projectionMatrix(), Matrix4f.class, RenderContext.class));
        context.registerUniform(KeyId.of("sketch_cullingCameraPos"), ValueGetter.create(() -> Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().toVector3f(), Vector3f.class));
        context.registerUniform(KeyId.of("sketch_cullingCameraDir"), ValueGetter.create(() -> Minecraft.getInstance().gameRenderer.getMainCamera().getLookVector(), Vector3f.class));

        context.registerUniform(KeyId.of("sketch_depthSize"), ValueGetter.create(() -> {
            Vector3i[] array = new Vector3i[CullingStateManager.DEPTH_SIZE];
            System.arraycopy(CullingStateManager.DEPTH_BUFFER_INFORMATION, 0, array, 0, CullingStateManager.DEPTH_SIZE);
            return array;
        }, Vector3i[].class));
        context.registerUniform(KeyId.of("sketch_linerDepth"), ValueGetter.create((instance) -> ((ComputeHIZGraphics) instance).first() ? 1 : 0, Integer.class, ComputeHIZGraphics.class));
        context.registerUniform(KeyId.of("sketch_cullingFineness"), ValueGetter.create(() -> (float) Config.getDepthUpdateDelay(), Float.class));
        context.registerUniform(KeyId.of("sketch_screenSize"), ValueGetter.create((instance) -> {
            ComputeHIZGraphics hizGraphics = (ComputeHIZGraphics) instance;
            if (!hizGraphics.first()) {
                Vector3i screenSize = CullingStateManager.DEPTH_BUFFER_INFORMATION[3];
                return new Vector2i(screenSize.x, screenSize.y);
            }
            RenderTarget screen = Minecraft.getInstance().getMainRenderTarget();
            return new Vector2i(screen.width, screen.height);
        }, Vector2i.class, ComputeHIZGraphics.class));
        context.registerUniform(KeyId.of("sketch_testPos"), ValueGetter.create(() -> {
            if (SketchRender.testPos != null) {
                return new Vector4f(SketchRender.testPos.getX(), SketchRender.testPos.getY(), SketchRender.testPos.getZ(), 1);
            }
            return new Vector4f(0, 0, 0, 0);
        }, Vector4f.class));
        context.registerUniform(KeyId.of("sketch_testEntityPos"), ValueGetter.create(() -> {
            if (SketchRender.testEntity != null) {
                return new Vector4f((float) SketchRender.testEntity.position().x, (float) SketchRender.testEntity.position().y, (float) SketchRender.testEntity.position().z, 1);
            }
            return new Vector4f(0, 0, 0, 0);
        }, Vector4f.class));
        context.registerUniform(KeyId.of("sketch_testEntityAABB"), ValueGetter.create(() -> {
            if (SketchRender.testEntity != null) {
                AABB aabb = SketchRender.getObjectAABB(SketchRender.testEntity);
                return new Vector3f((float) aabb.getXsize(), (float) aabb.getYsize(), (float) aabb.getZsize());
            }
            return new Vector3f(0, 0, 0);
        }, Vector3f.class));
        context.registerUniform(KeyId.of("sketch_testBlockEntityPos"), ValueGetter.create(() -> {
            if (SketchRender.testBlockEntity != null) {
                return new Vector4f((float) SketchRender.testBlockEntity.getBlockPos().getX() + 0.5f,
                        (float) SketchRender.testBlockEntity.getBlockPos().getY(),
                        (float) SketchRender.testBlockEntity.getBlockPos().getZ() + 0.5f, 1);
            }
            return new Vector4f(0, 0, 0, 0);
        }, Vector4f.class));
        context.registerUniform(KeyId.of("sketch_testBlockEntityAABB"), ValueGetter.create(() -> {
            if (SketchRender.testBlockEntity != null) {
                AABB aabb = SketchRender.getObjectAABB(SketchRender.testBlockEntity);
                return new Vector3f((float) aabb.getXsize(), (float) aabb.getYsize(), (float) aabb.getZsize());
            }
            return new Vector3f(0, 0, 0);
        }, Vector3f.class));
    }

    @Override
    public ModuleSession createSession() {
        return new ModuleSession() {
            @Override
            public String id() {
                return "vanilla_culling_session";
            }

            @Override
            public void onWorldEnter(ModuleSessionContext context) {
                context.registerCompute(CullingStages.HIZ, new ComputeHIZGraphics(KeyId.of(SketchRender.MOD_ID, "hierarchy_depth_buffer_first"), true).setPriority(98), ModuleGraphicsLifetime.SESSION);
                context.registerCompute(CullingStages.HIZ, new ComputeHIZGraphics(KeyId.of(SketchRender.MOD_ID, "hierarchy_depth_buffer_second"), false).setPriority(99), ModuleGraphicsLifetime.SESSION);
                context.registerCompute(CullingStages.HIZ, new ComputeEntityCullingGraphics(KeyId.of(SketchRender.MOD_ID, "cull_entity_batch")).setPriority(101), ModuleGraphicsLifetime.SESSION);

                VertexLayoutSpec layout = VertexLayoutSpec.builder()
                        .addDynamic(rogo.sketch.core.api.model.DynamicTypeMesh.BASED_MESH, DefaultDataFormats.POSITION)
                        .build();
                rogo.sketch.core.pipeline.parmeter.RenderParameter renderParameter =
                        RasterizationParameter.create(layout, PrimitiveType.QUADS, Usage.DYNAMIC_DRAW, false);
                context.registerGraphics(MinecraftRenderStages.POST_PROGRESS.getIdentifier(),
                        new ChunkCullingTestGraphics(KeyId.of(SketchRender.MOD_ID, "culling_test_chunk_new")),
                        renderParameter, PipelineType.RASTERIZATION, ModuleGraphicsLifetime.SESSION);
                context.registerGraphics(MinecraftRenderStages.POST_PROGRESS.getIdentifier(),
                        new EntityCullingTestGraphics(KeyId.of(SketchRender.MOD_ID, "culling_test_entity_new")),
                        renderParameter, PipelineType.RASTERIZATION, ModuleGraphicsLifetime.SESSION);
                context.registerGraphics(MinecraftRenderStages.POST_PROGRESS.getIdentifier(),
                        new BlockEntityCullingTestGraphics(KeyId.of(SketchRender.MOD_ID, "culling_test_block_entity_new")),
                        renderParameter, PipelineType.RASTERIZATION, ModuleGraphicsLifetime.SESSION);
            }
        };
    }
}
