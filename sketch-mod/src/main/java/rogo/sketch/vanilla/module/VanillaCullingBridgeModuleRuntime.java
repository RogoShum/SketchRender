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
import rogo.sketch.compat.sodium.MeshResource;
import rogo.sketch.core.api.graphics.DescriptorStability;
import rogo.sketch.core.data.MeshIndexMode;
import rogo.sketch.core.data.PrimitiveType;
import rogo.sketch.core.data.format.VertexLayoutSpec;
import rogo.sketch.core.graphics.ecs.GraphicsBuiltinComponents;
import rogo.sketch.core.graphics.ecs.GraphicsEntityBlueprint;
import rogo.sketch.core.graphics.ecs.GraphicsEntityPresets;
import rogo.sketch.core.graphics.ecs.GraphicsUniformSubject;
import rogo.sketch.core.model.DynamicMesh;
import rogo.sketch.core.pipeline.PipelineType;
import rogo.sketch.core.pipeline.PartialRenderSetting;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.module.metric.MetricDescriptor;
import rogo.sketch.core.pipeline.module.metric.MetricKind;
import rogo.sketch.core.pipeline.module.runtime.ModuleGraphicsLifetime;
import rogo.sketch.core.pipeline.module.runtime.ModuleRuntime;
import rogo.sketch.core.pipeline.module.runtime.ModuleRuntimeContext;
import rogo.sketch.core.pipeline.module.session.ModuleSession;
import rogo.sketch.core.pipeline.module.session.ModuleSessionContext;
import rogo.sketch.core.pipeline.module.setting.SettingChangeEvent;
import rogo.sketch.core.pipeline.parmeter.RasterizationParameter;
import rogo.sketch.core.resource.ResourceReference;
import rogo.sketch.core.resource.descriptor.BufferUpdatePolicy;
import rogo.sketch.core.shader.uniform.UniformCaptureTiming;
import rogo.sketch.core.shader.uniform.UniformUpdateDomain;
import rogo.sketch.core.shader.uniform.ValueGetter;
import rogo.sketch.core.util.KeyId;
import rogo.sketch.core.vertex.DefaultDataFormats;
import rogo.sketch.feature.culling.MinecraftCullingDebugState;
import rogo.sketch.feature.culling.CullingVisibilitySettingsSnapshot;
import rogo.sketch.feature.culling.MinecraftHiZState;
import rogo.sketch.feature.culling.MinecraftEntityVisibilityBridge;
import rogo.sketch.feature.culling.MinecraftShaderCapabilityService;
import rogo.sketch.mixin.AccessorFrustum;
import rogo.sketch.mixin.AccessorMinecraft;
import rogo.sketch.module.culling.CullingHostRegistry;
import rogo.sketch.module.culling.CullingModuleDescriptor;
import rogo.sketch.module.culling.CullingModuleRuntime;
import rogo.sketch.module.culling.entity.EntityVisibilityQueryService;
import rogo.sketch.vanilla.McRenderContext;
import rogo.sketch.vanilla.MinecraftHostAdapter;
import rogo.sketch.vanilla.MinecraftRenderStages;
import rogo.sketch.vanilla.PipelineUtil;
import rogo.sketch.vanilla.event.MinecraftHostEventContracts;
import rogo.sketch.util.OcclusionCullerThread;

import java.util.function.Consumer;

public class VanillaCullingBridgeModuleRuntime implements ModuleRuntime {
    private static final KeyId DEBUG_RESOURCE_ORIGIN = KeyId.of("sketch", "runtime_debug");
    public static final KeyId ENTITY_HIDDEN_METRIC = KeyId.of("sketch_render", "entity_hidden_count");
    public static final KeyId ENTITY_TOTAL_METRIC = KeyId.of("sketch_render", "entity_total_count");
    public static final KeyId BLOCK_ENTITY_HIDDEN_METRIC = KeyId.of("sketch_render", "block_entity_hidden_count");
    public static final KeyId BLOCK_ENTITY_TOTAL_METRIC = KeyId.of("sketch_render", "block_entity_total_count");
    private Consumer<SettingChangeEvent> settingListener;
    private final MinecraftCullingDebugState debugState = MinecraftCullingDebugState.getInstance();
    private final MinecraftHiZState hiZState = MinecraftHiZState.getInstance();
    private final MinecraftShaderCapabilityService shaderCapabilities = MinecraftShaderCapabilityService.getInstance();
    private ModuleRuntimeContext runtimeContext;

    @Override
    public String id() {
        return VanillaCullingBridgeModuleDescriptor.MODULE_ID;
    }

    @Override
    public void onProcessInit(ModuleRuntimeContext context) {
        runtimeContext = context;
        settingListener = event -> {
            if (isCullingSettingChange(event.settingId())) {
                applyDebugSettings(context);
                refreshVisibilitySettingsSnapshot(context);
            }
        };
        context.settings().addListener(settingListener);
        applyDebugSettings(context);
        refreshVisibilitySettingsSnapshot(context);
    }

    @Override
    public void onKernelInit(ModuleRuntimeContext context) {
        context.setGlobalFlag("IRIS_ENABLE", SketchRender.hasIris());
        context.setGlobalFlag("SODIUM_ENABLE", SketchRender.hasSodium());
        CullingHostRegistry.register(new VanillaCullingHostAdapter());
        context.hostEvents().subscribe(context.ownerId(), MinecraftHostEventContracts.WORLD_ENTER, this::onWorldEnter);
        context.hostEvents().subscribe(context.ownerId(), MinecraftHostEventContracts.WORLD_LEAVE, this::onWorldLeave);
        context.hostEvents().subscribe(context.ownerId(), MinecraftHostEventContracts.RESOURCE_RELOAD, this::onResourceReload);
        context.hostEvents().subscribe(context.ownerId(), MinecraftHostEventContracts.RENDER_STAGE_PRE, this::onRenderStagePre);

        context.registerMetric(new MetricDescriptor(ENTITY_HIDDEN_METRIC, id(), MetricKind.COUNT, "metric.culling.entity_hidden", null), debugState::entityHiddenCount);
        context.registerMetric(new MetricDescriptor(ENTITY_TOTAL_METRIC, id(), MetricKind.COUNT, "metric.culling.entity_total", null), debugState::entityTotalCount);
        context.registerMetric(new MetricDescriptor(BLOCK_ENTITY_HIDDEN_METRIC, id(), MetricKind.COUNT, "metric.culling.block_hidden", null), debugState::blockEntityHiddenCount);
        context.registerMetric(new MetricDescriptor(BLOCK_ENTITY_TOTAL_METRIC, id(), MetricKind.COUNT, "metric.culling.block_total", null), debugState::blockEntityTotalCount);

        registerUniforms(context);
    }

    @Override
    public void onEnable(ModuleRuntimeContext context) {
        applyDebugSettings(context);
        refreshVisibilitySettingsSnapshot(context);
    }

    @Override
    public void onDisable(ModuleRuntimeContext context) {
        debugState.clearCheckingFlags();
        refreshVisibilitySettingsSnapshot(context);
    }

    @Override
    public void onShutdown(ModuleRuntimeContext context) {
        if (settingListener != null) {
            context.settings().removeListener(settingListener);
            settingListener = null;
        }
        runtimeContext = null;
        CullingHostRegistry.clear();
        debugState.clearCheckingFlags();
        MinecraftEntityVisibilityBridge.updateSettingsSnapshot(CullingVisibilitySettingsSnapshot.disabled());
    }

    private void onWorldEnter(MinecraftHostEventContracts.WorldEnterEvent event) {
        debugState.resetFrameCounters();
    }

    private void onWorldLeave(MinecraftHostEventContracts.WorldLeaveEvent event) {
        debugState.resetFrameCounters();
        shaderCapabilities.cleanup();
    }

    private void onResourceReload(MinecraftHostEventContracts.ResourceReloadEvent event) {
        shaderCapabilities.checkShader();
    }

    private void onRenderStagePre(MinecraftHostEventContracts.RenderStagePreEvent event) {
        if (event.stageId().equals(MinecraftRenderStages.SKY.getIdentifier())) {
            hiZState.onSkyStage(event.pipeline().anyNextTick(), event.pipeline().isNextLoop());
            debugState.resetFrameCounters();
            return;
        }

        if (event.stageId().equals(MinecraftRenderStages.RENDER_START.getIdentifier())) {
            hiZState.populateLevelContext(event.context());
            shaderCapabilities.checkShader();
            MinecraftHostAdapter.getInstance().onRenderStart(event.pipeline());
            if (runtimeContext != null) {
                refreshVisibilitySettingsSnapshot(runtimeContext);
            }
            return;
        }

        if (event.stageId().equals(MinecraftRenderStages.RENDER_END.getIdentifier())) {
            debugState.updateFps(((AccessorMinecraft) Minecraft.getInstance()).getFps());
            shaderCapabilities.flushPendingRuntimeReset();
            if (isAnyCullingEnabled()) {
                MeshResource.updateDistance(Minecraft.getInstance().options.getEffectiveRenderDistance());
            }
            OcclusionCullerThread.notifyUpdate();
        }
    }

    private void applyDebugSettings(ModuleRuntimeContext context) {
        debugState.setCheckingFlags(
                context.settings().isActive(VanillaCullingBridgeModuleDescriptor.DEBUG_CULL)
                        && context.settings().getBoolean(VanillaCullingBridgeModuleDescriptor.DEBUG_CULL, false),
                context.settings().isActive(VanillaCullingBridgeModuleDescriptor.DEBUG_TEXTURE)
                        && context.settings().getBoolean(VanillaCullingBridgeModuleDescriptor.DEBUG_TEXTURE, false));
    }

    private void refreshVisibilitySettingsSnapshot(ModuleRuntimeContext context) {
        EntityVisibilityQueryService queries = currentCullingRuntime() != null
                ? currentCullingRuntime().entityVisibilityQueries()
                : null;
        MinecraftEntityVisibilityBridge.updateSettingsSnapshot(new CullingVisibilitySettingsSnapshot(
                context.settings().isActive(CullingModuleDescriptor.CULL_ENTITY)
                        && context.settings().getBoolean(CullingModuleDescriptor.CULL_ENTITY, false),
                context.settings().isActive(CullingModuleDescriptor.CULL_BLOCK_ENTITY)
                        && context.settings().getBoolean(CullingModuleDescriptor.CULL_BLOCK_ENTITY, false),
                context.settings().isActive(CullingModuleDescriptor.ASYNC_CHUNK_REBUILD)
                        && context.settings().getBoolean(CullingModuleDescriptor.ASYNC_CHUNK_REBUILD, false),
                Config.cachedEntitySkipSet(),
                Config.cachedBlockEntitySkipSet(),
                queries));
    }

    private boolean isCullingSettingChange(KeyId settingId) {
        return VanillaCullingBridgeModuleDescriptor.DEBUG_CULL.equals(settingId)
                || VanillaCullingBridgeModuleDescriptor.DEBUG_TEXTURE.equals(settingId)
                || KeyId.of("sketch", CullingModuleDescriptor.MODULE_ID + "_enabled").equals(settingId);
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
        }, Vector3f.class, UniformCaptureTiming.FRAME_SYNC, McRenderContext.class));

        context.registerUniform(KeyId.of("sketch_cullingFrustum"), ValueGetter.create((instance) -> {
            McRenderContext renderContext = (McRenderContext) instance;
            if (renderContext.cullingFrustum() != null) {
                return SketchRender.getFrustumPlanes(((AccessorFrustum) renderContext.cullingFrustum()).frustumIntersection());
            }
            return null;
        }, Vector4f[].class, UniformCaptureTiming.FRAME_SYNC, McRenderContext.class));

        context.registerUniform(KeyId.of("sketch_entityCount"), ValueGetter.create(this::subjectCount, Integer.class, UniformCaptureTiming.FRAME_SYNC));
        context.registerUniform(KeyId.of("sketch_cullingTerrain"), ValueGetter.create(() ->
                (!isChunkCullingEnabled() || shaderCapabilities.renderingShadowPass()) ? 0 : 1, Integer.class, UniformCaptureTiming.FRAME_SYNC));
        context.registerUniform(KeyId.of("sketch_checkCulling"), ValueGetter.create(() -> debugState.checkingCull() ? 1 : 0, Integer.class, UniformCaptureTiming.FRAME_SYNC));

        context.registerUniform(KeyId.of("sketch_levelMinPos"), ValueGetter.create((instance) -> ((McRenderContext) instance).get(MinecraftHiZState.LEVEL_MIN_POS), Integer.class, UniformCaptureTiming.FRAME_SYNC, McRenderContext.class));
        context.registerUniform(KeyId.of("sketch_levelPosRange"), ValueGetter.create((instance) -> ((McRenderContext) instance).get(MinecraftHiZState.LEVEL_POS_RANGE), Integer.class, UniformCaptureTiming.FRAME_SYNC, McRenderContext.class));
        context.registerUniform(KeyId.of("sketch_levelSectionRange"), ValueGetter.create((instance) -> ((McRenderContext) instance).get(MinecraftHiZState.LEVEL_SECTION_RANGE), Integer.class, UniformCaptureTiming.FRAME_SYNC, McRenderContext.class));
        context.registerUniform(KeyId.of("sketch_cameraOffset"), ValueGetter.create((instance) -> {
            McRenderContext renderContext = (McRenderContext) instance;
            Vec3 pos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            Integer minPos = renderContext.get(MinecraftHiZState.LEVEL_MIN_POS);
            BlockPos blockPos = new BlockPos((int) pos.x >> 4, (minPos != null ? minPos : 0) >> 4, (int) pos.z >> 4);
            return new Vector3i(blockPos.getX(), blockPos.getY(), blockPos.getZ());
        }, Vector3i.class, UniformCaptureTiming.FRAME_SYNC, McRenderContext.class));
        context.registerUniform(KeyId.of("sketch_cameraPos"), ValueGetter.create(() -> {
            Vec3 pos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            return new Vector3i((int) pos.x, (int) pos.y, (int) pos.z);
        }, Vector3i.class, UniformCaptureTiming.FRAME_SYNC));
        context.registerUniform(KeyId.of("sketch_cullingViewMat"), ValueGetter.create((instance) -> ((RenderContext) instance).viewMatrix(), Matrix4f.class, UniformCaptureTiming.FRAME_SYNC, RenderContext.class));
        context.registerUniform(KeyId.of("sketch_cullingProjMat"), ValueGetter.create((instance) -> ((RenderContext) instance).projectionMatrix(), Matrix4f.class, UniformCaptureTiming.FRAME_SYNC, RenderContext.class));
        context.registerUniform(KeyId.of("sketch_cullingCameraPos"), ValueGetter.create((instance) -> {
            RenderContext renderContext = (RenderContext) instance;
            return new Vector3f(renderContext.cameraPosition());
        }, Vector3f.class, UniformCaptureTiming.FRAME_SYNC, RenderContext.class));
        context.registerUniform(KeyId.of("sketch_cullingCameraDir"), ValueGetter.create((instance) -> {
            RenderContext renderContext = (RenderContext) instance;
            return new Vector3f(renderContext.cameraDirection());
        }, Vector3f.class, UniformCaptureTiming.FRAME_SYNC, RenderContext.class));

        context.registerUniform(KeyId.of("sketch_depthSize"), ValueGetter.create(() -> {
            return currentHiZDepthInfo();
        }, Vector3i[].class, UniformCaptureTiming.FRAME_SYNC));
        context.registerUniform(KeyId.of("sketch_linerDepth"), ValueGetter.create((instance) -> {
            GraphicsUniformSubject subject = (GraphicsUniformSubject) instance;
            return subject.hasTag(CullingModuleRuntime.HIZ_FIRST_TAG) ? 1 : 0;
        }, Integer.class, UniformUpdateDomain.BUILD_SNAPSHOT, GraphicsUniformSubject.class));
        context.registerUniform(KeyId.of("sketch_cullingFineness"), ValueGetter.create(() -> (float) Config.getDepthUpdateDelay(), Float.class, UniformCaptureTiming.FRAME_SYNC));
        context.registerUniform(KeyId.of("sketch_screenSize"), ValueGetter.create((instance) -> {
            GraphicsUniformSubject subject = (GraphicsUniformSubject) instance;
            if (subject.hasTag(CullingModuleRuntime.HIZ_SECOND_TAG)) {
                Vector3i[] info = currentHiZDepthInfo();
                if (info.length > 0) {
                    Vector3i screenSize = info[java.lang.Math.min(3, info.length - 1)];
                    return new Vector2i(screenSize.x, screenSize.y);
                }
            }
            RenderTarget screen = Minecraft.getInstance().getMainRenderTarget();
            return new Vector2i(screen.width, screen.height);
        }, Vector2i.class, UniformUpdateDomain.BUILD_SNAPSHOT, GraphicsUniformSubject.class));
        context.registerUniform(KeyId.of("sketch_testPos"), ValueGetter.create(() -> {
            if (SketchRender.testPos != null) {
                return new Vector4f(SketchRender.testPos.getX(), SketchRender.testPos.getY(), SketchRender.testPos.getZ(), 1);
            }
            return new Vector4f(0, 0, 0, 0);
        }, Vector4f.class, UniformCaptureTiming.FRAME_SYNC));
        context.registerUniform(KeyId.of("sketch_testEntityPos"), ValueGetter.create(() -> {
            if (SketchRender.testEntity != null) {
                return new Vector4f((float) SketchRender.testEntity.position().x, (float) SketchRender.testEntity.position().y, (float) SketchRender.testEntity.position().z, 1);
            }
            return new Vector4f(0, 0, 0, 0);
        }, Vector4f.class, UniformCaptureTiming.FRAME_SYNC));
        context.registerUniform(KeyId.of("sketch_testEntityAABB"), ValueGetter.create(() -> {
            if (SketchRender.testEntity != null) {
                AABB aabb = SketchRender.getObjectAABB(SketchRender.testEntity);
                return new Vector3f((float) aabb.getXsize(), (float) aabb.getYsize(), (float) aabb.getZsize());
            }
            return new Vector3f(0, 0, 0);
        }, Vector3f.class, UniformCaptureTiming.FRAME_SYNC));
        context.registerUniform(KeyId.of("sketch_testBlockEntityPos"), ValueGetter.create(() -> {
            if (SketchRender.testBlockEntity != null) {
                return new Vector4f((float) SketchRender.testBlockEntity.getBlockPos().getX() + 0.5f,
                        (float) SketchRender.testBlockEntity.getBlockPos().getY(),
                        (float) SketchRender.testBlockEntity.getBlockPos().getZ() + 0.5f, 1);
            }
            return new Vector4f(0, 0, 0, 0);
        }, Vector4f.class, UniformCaptureTiming.FRAME_SYNC));
        context.registerUniform(KeyId.of("sketch_testBlockEntityAABB"), ValueGetter.create(() -> {
            if (SketchRender.testBlockEntity != null) {
                AABB aabb = SketchRender.getObjectAABB(SketchRender.testBlockEntity);
                return new Vector3f((float) aabb.getXsize(), (float) aabb.getYsize(), (float) aabb.getZsize());
            }
            return new Vector3f(0, 0, 0);
        }, Vector3f.class, UniformCaptureTiming.FRAME_SYNC));
    }

    @Override
    public ModuleSession createSession() {
        return new ModuleSession() {
            @Override
            public String id() {
                return "vanilla_culling_session";
            }

            @Override
            public void onEnable(ModuleSessionContext context) {
                VertexLayoutSpec layout = VertexLayoutSpec.builder()
                        .addDynamic(rogo.sketch.core.api.model.DynamicTypeMesh.BASED_MESH, DefaultDataFormats.POSITION)
                        .build();
                rogo.sketch.core.pipeline.parmeter.RenderParameter renderParameter =
                        RasterizationParameter.create(
                                layout,
                                PrimitiveType.TRIANGLES,
                                MeshIndexMode.NONE,
                                BufferUpdatePolicy.DYNAMIC,
                                false);
                context.registerGraphicsEntity(
                        createDebugQuadBlueprint(
                                KeyId.of(SketchRender.MOD_ID, "culling_test_chunk_new"),
                                KeyId.of(SketchRender.MOD_ID, "culling_test_chunk"),
                                KeyId.of("sketch", "culling_debug_chunk"),
                                renderParameter,
                                () -> isAnyCullingEnabled() && debugState.checkingTexture()
                                        && debugState.debugMode() > 0 && SketchRender.testPos != null),
                        ModuleGraphicsLifetime.SESSION);
                context.registerGraphicsEntity(
                        createDebugQuadBlueprint(
                                KeyId.of(SketchRender.MOD_ID, "culling_test_entity_new"),
                                KeyId.of(SketchRender.MOD_ID, "culling_test_entity"),
                                KeyId.of("sketch", "culling_debug_entity"),
                                renderParameter,
                                () -> isAnyCullingEnabled() && debugState.checkingTexture()
                                        && debugState.debugMode() > 0 && SketchRender.testEntity != null),
                        ModuleGraphicsLifetime.SESSION);
                context.registerGraphicsEntity(
                        createDebugQuadBlueprint(
                                KeyId.of(SketchRender.MOD_ID, "culling_test_block_entity_new"),
                                KeyId.of(SketchRender.MOD_ID, "culling_test_block_entity"),
                                KeyId.of("sketch", "culling_debug_block_entity"),
                                renderParameter,
                                () -> isAnyCullingEnabled() && debugState.checkingTexture()
                                        && debugState.debugMode() > 0 && SketchRender.testBlockEntity != null),
                        ModuleGraphicsLifetime.SESSION);
            }
        };
    }

    private GraphicsEntityBlueprint createDebugQuadBlueprint(
            KeyId identifier,
            KeyId partialSettingId,
            KeyId tag,
            rogo.sketch.core.pipeline.parmeter.RenderParameter renderParameter,
            java.util.function.BooleanSupplier shouldRender) {
        DynamicMesh mesh = new DynamicMesh(
                KeyId.of("mesh"),
                DefaultDataFormats.POSITION,
                PrimitiveType.TRIANGLES,
                6,
                0,
                filler -> filler
                        .put(-1.0f, -1.0f, 0.0f)
                        .put(1.0f, -1.0f, 0.0f)
                        .put(1.0f, 1.0f, 0.0f)
                        .put(-1.0f, -1.0f, 0.0f)
                        .put(1.0f, 1.0f, 0.0f)
                        .put(-1.0f, 1.0f, 0.0f));
        ModuleRuntimeContext context = runtimeContext;
        ResourceReference<PartialRenderSetting> partialReference = context != null
                ? context.resourceManager().getReference(rogo.sketch.core.resource.ResourceTypes.PARTIAL_RENDER_SETTING, partialSettingId)
                : null;
        GraphicsEntityBlueprint.Builder builder = GraphicsEntityPresets.raster(
                identifier,
                MinecraftRenderStages.POST_PROGRESS.getIdentifier(),
                PipelineType.RASTERIZATION,
                renderParameter,
                rogo.sketch.core.pipeline.flow.ecs.GraphicsContainerHints.DEFAULT,
                Long.valueOf(0L),
                0L,
                0,
                shouldRender,
                () -> false,
                rogo.sketch.core.api.graphics.SubmissionCapability.DIRECT_BATCHABLE,
                DescriptorStability.DYNAMIC,
                () -> GraphicsEntityPresets.partialDescriptorVersion(resolvePartial(partialReference)),
                parameter -> GraphicsEntityPresets.compilePartialDescriptor(
                        context != null ? context.resourceManager() : null,
                        parameter,
                        resolvePartial(partialReference)));
        builder.put(GraphicsBuiltinComponents.PREPARED_MESH, new GraphicsBuiltinComponents.PreparedMeshComponent(() -> mesh));
        GraphicsEntityPresets.withResourceOrigin(builder, DEBUG_RESOURCE_ORIGIN);
        GraphicsEntityPresets.withTags(builder, tag);
        return builder.build();
    }

    private PartialRenderSetting resolvePartial(ResourceReference<PartialRenderSetting> partialReference) {
        return partialReference != null && partialReference.isAvailable() ? partialReference.get() : PartialRenderSetting.EMPTY;
    }

    private int subjectCount() {
        CullingModuleRuntime runtime = currentCullingRuntime();
        return runtime != null ? runtime.entityVisibilityQueries().subjectCount() : 0;
    }

    private Vector3i[] currentHiZDepthInfo() {
        CullingModuleRuntime runtime = currentCullingRuntime();
        return runtime != null ? runtime.currentHiZDepthInfo() : hiZState.depthBufferInformation();
    }

    private CullingModuleRuntime currentCullingRuntime() {
        if (PipelineUtil.pipeline() == null || PipelineUtil.pipeline().runtimeHost() == null) {
            return null;
        }
        if (PipelineUtil.pipeline().runtimeHost().runtimeById(CullingModuleDescriptor.MODULE_ID) instanceof CullingModuleRuntime runtime) {
            return runtime;
        }
        return null;
    }

    private boolean isChunkCullingEnabled() {
        CullingModuleRuntime runtime = currentCullingRuntime();
        return runtime != null ? runtime.chunkCullingEnabled() : Config.getCullChunk();
    }

    private boolean isAnyCullingEnabled() {
        CullingModuleRuntime runtime = currentCullingRuntime();
        return runtime != null ? runtime.anyCullingRuntimeEnabled() : (Config.getCullChunk() || Config.doEntityCulling());
    }
}
