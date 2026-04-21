package rogo.sketch.module.culling;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.api.ResourceObject;
import rogo.sketch.core.api.graphics.DescriptorStability;
import rogo.sketch.core.backend.AsyncComputeRequest;
import rogo.sketch.core.backend.AsyncGpuJobHandle;
import rogo.sketch.core.extension.event.ObjectLifecycleEventBus;
import rogo.sketch.core.graphics.ecs.GraphicsBuiltinComponents;
import rogo.sketch.core.graphics.ecs.GraphicsEntityBlueprint;
import rogo.sketch.core.graphics.ecs.GraphicsEntityId;
import rogo.sketch.core.graphics.ecs.GraphicsWorld;
import rogo.sketch.core.graphics.ecs.GraphicsEntityPresets;
import rogo.sketch.core.packet.RenderPacket;
import rogo.sketch.core.packet.RenderPacketQueue;
import rogo.sketch.core.pipeline.PartialRenderSetting;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.RenderStateManager;
import rogo.sketch.core.pipeline.PipelineType;
import rogo.sketch.core.pipeline.graph.PipelinePass;
import rogo.sketch.core.pipeline.graph.scheduler.SimpleProfiler;
import rogo.sketch.core.pipeline.graph.RenderGraphBuilder;
import rogo.sketch.core.pipeline.graph.TickGraphBuilder;
import rogo.sketch.core.pipeline.graph.pass.SyncApplyPendingSettingsPass;
import rogo.sketch.core.pipeline.kernel.FrameContext;
import rogo.sketch.core.pipeline.kernel.FrameResourceHandle;
import rogo.sketch.core.pipeline.kernel.LifecyclePhase;
import rogo.sketch.core.pipeline.kernel.ModulePassDefinition;
import rogo.sketch.core.pipeline.kernel.PassExecutionContext;
import rogo.sketch.core.pipeline.kernel.ThreadDomain;
import rogo.sketch.core.pipeline.kernel.annotation.AsyncOnly;
import rogo.sketch.core.pipeline.kernel.annotation.SyncOnly;
import rogo.sketch.core.pipeline.module.macro.ModuleMacroProjector;
import rogo.sketch.core.pipeline.module.runtime.ModuleGraphAssemblyContext;
import rogo.sketch.core.pipeline.module.runtime.ModuleGraphicsLifetime;
import rogo.sketch.core.pipeline.module.runtime.ModuleRuntime;
import rogo.sketch.core.pipeline.module.runtime.ModuleRuntimeContext;
import rogo.sketch.core.pipeline.module.session.ModuleSession;
import rogo.sketch.core.pipeline.module.session.ModuleSessionContext;
import rogo.sketch.core.pipeline.module.setting.SettingChangeEvent;
import rogo.sketch.core.pipeline.parmeter.ComputeParameter;
import rogo.sketch.core.pipeline.submit.StageSubmitNode;
import rogo.sketch.core.pipeline.submit.StageWindow;
import rogo.sketch.core.resource.GraphicsResourceManager;
import rogo.sketch.core.resource.ResourceReference;
import rogo.sketch.core.resource.ResourceTypes;
import rogo.sketch.core.resource.vision.Texture;
import rogo.sketch.core.scene.SceneDatabase;
import rogo.sketch.core.scene.SceneProxy;
import rogo.sketch.core.util.KeyId;
import rogo.sketch.core.object.ObjectDespawnEvent;
import rogo.sketch.core.object.ObjectHostKind;
import rogo.sketch.core.object.ObjectSyncEvent;
import rogo.sketch.module.culling.entity.EntityMaskLifecycleController;
import rogo.sketch.module.culling.entity.EntityMaskStateStore;
import rogo.sketch.module.culling.entity.EntitySourceRegistry;
import rogo.sketch.module.culling.entity.EntityFeatureSchema;
import rogo.sketch.module.culling.entity.EntityVisibilityQueryService;
import rogo.sketch.module.transform.TransformModule;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import org.joml.Vector3i;
import org.joml.primitives.AABBf;
import java.util.function.Consumer;

public class CullingModuleRuntime implements ModuleRuntime {
    public static final KeyId HIZ_FIRST_TAG = KeyId.of("sketch", "hiz_first");
    public static final KeyId HIZ_SECOND_TAG = KeyId.of("sketch", "hiz_second");
    public static final KeyId HIZ_SNAPSHOT_COPY_TAG = KeyId.of("sketch", "hiz_snapshot_copy");
    public static final KeyId TERRAIN_CULL_TAG = KeyId.of("sketch", "terrain_cull");
    public static final KeyId COPY_COUNTER_TAG = KeyId.of("sketch", "copy_counter");
    private static final String PASS_SYNC_ENTITY_MASK_LIFECYCLE = "sync_entity_mask_lifecycle";
    private static final String PASS_PUBLISH_HIZ_TEXTURE = "publish_hiz_texture";
    private static final String PASS_PUBLISH_TERRAIN_CULL_RESOURCES = "publish_terrain_cull_resources";
    private static final String PASS_PUBLISH_TERRAIN_MESH_RESOURCES = "publish_terrain_mesh_resources";
    private static final String PASS_PUBLISH_ENTITY_CULL_RESOURCES = "publish_entity_cull_resources";
    private static final String PASS_ASYNC_PREPARE_ENTITY_SUBJECTS = "culling_async_prepare_entity_subjects";
    private static final KeyId HIZ_TEXTURE_RESOURCE_ID = KeyId.of("sketch_render", "hiz_texture");
    private static final KeyId HIZ_ASYNC_TEXTURE_RESOURCE_ID = KeyId.of("sketch_render", "hiz_async_texture");
    private static final KeyId HIZ_SOURCE_DEPTH_SNAPSHOT_RESOURCE_ID = KeyId.of("sketch_render", "hiz_source_depth_snapshot");
    private static final KeyId MAIN_DEPTH_RESOURCE_ID = KeyId.of("minecraft", "main_depth");
    private static final KeyId ENTITY_DATA_RESOURCE_ID = KeyId.of("sketch_render", "entity_data");
    private static final KeyId ENTITY_CULLING_RESULT_RESOURCE_ID = KeyId.of("sketch_render", "entity_culling_result");
    private static final KeyId ENTITY_CULL_TAG = KeyId.of("sketch", "entity_cull");
    private static final KeyId SYNC_ENTITY_MASK_FRAME_NODE_ID = KeyId.of("sketch_render", "sync_entity_mask_frame");
    private static final KeyId REFRESH_HIZ_INPUTS_NODE_ID = KeyId.of("sketch_render", "refresh_hiz_inputs");
    private static final KeyId REFRESH_TERRAIN_INPUTS_NODE_ID = KeyId.of("sketch_render", "refresh_terrain_inputs");
    private static final KeyId REFRESH_ENTITY_INPUTS_NODE_ID = KeyId.of("sketch_render", "refresh_entity_inputs");

    private final ModuleMacroProjector macroProjector = new ModuleMacroProjector()
            .projectFlag(CullingModuleDescriptor.CULL_CHUNK, "SKETCH_CULL_CHUNK")
            .projectFlag(CullingModuleDescriptor.CULL_ENTITY, "SKETCH_CULL_ENTITY")
            .projectFlag(CullingModuleDescriptor.CULL_BLOCK_ENTITY, "SKETCH_CULL_BLOCK_ENTITY");
    private final ResourceReference<PartialRenderSetting> firstHiZSetting = GraphicsResourceManager.getInstance()
            .getReference(ResourceTypes.PARTIAL_RENDER_SETTING, KeyId.of("sketch_render", "hierarchy_depth_buffer_first"));
    private final ResourceReference<PartialRenderSetting> secondHiZSetting = GraphicsResourceManager.getInstance()
            .getReference(ResourceTypes.PARTIAL_RENDER_SETTING, KeyId.of("sketch_render", "hierarchy_depth_buffer_second"));
    private final ResourceReference<PartialRenderSetting> depthSnapshotCopySetting = GraphicsResourceManager.getInstance()
            .getReference(ResourceTypes.PARTIAL_RENDER_SETTING, KeyId.of("sketch_render", "hiz_depth_snapshot_copy"));
    private final ResourceReference<PartialRenderSetting> terrainCullSetting = GraphicsResourceManager.getInstance()
            .getReference(ResourceTypes.PARTIAL_RENDER_SETTING, KeyId.of("sketch_render", "cull_chunk"));
    private final ResourceReference<PartialRenderSetting> copyCounterSetting = GraphicsResourceManager.getInstance()
            .getReference(ResourceTypes.PARTIAL_RENDER_SETTING, KeyId.of("sketch_render", "copy_counter"));
    private final ResourceReference<PartialRenderSetting> entityCullSetting = GraphicsResourceManager.getInstance()
            .getReference(ResourceTypes.PARTIAL_RENDER_SETTING, KeyId.of("sketch_render", "cull_entity_batch"));
    private final SceneDatabase sceneDatabase = new SceneDatabase();
    private final HiZResourceProducer hiZResourceProducer = new HiZResourceProducer();
    private final VisibilitySystem visibilitySystem = new VisibilitySystem();
    private final OcclusionSystem occlusionSystem = new OcclusionSystem(hiZResourceProducer);
    private final EntitySourceRegistry entitySourceRegistry = new EntitySourceRegistry(sceneDatabase);
    private final EntityMaskStateStore entityMaskStateStore = new EntityMaskStateStore();
    private final EntityMaskLifecycleController entityMaskLifecycleController =
            new EntityMaskLifecycleController(entitySourceRegistry, entityMaskStateStore, visibilitySystem);
    private final EntityVisibilityQueryService entityVisibilityQueryService =
            new EntityVisibilityQueryService(entitySourceRegistry, entityMaskStateStore);
    private final Map<Object, TrackedRootSubject> trackedRootSubjects = new IdentityHashMap<>();
    private final Object trackedRootSubjectsLock = new Object();

    private Consumer<SettingChangeEvent> settingListener;
    private GraphicsWorld graphicsWorld;
    private rogo.sketch.core.pipeline.GraphicsPipeline<?> pipeline;
    private volatile List<PreparedRootSubject> preparedRootSubjects = List.of();
    private volatile boolean chunkCullingEnabled;
    private volatile boolean entityCullingEnabled;
    private volatile boolean blockEntityCullingEnabled;
    private volatile AsyncGpuJobHandle pendingHiZJob;
    private volatile long publishedHiZEpoch;
    private long nextHiZEpoch = 1L;

    @Override
    public String id() {
        return CullingModuleDescriptor.MODULE_ID;
    }

    @Override
    public void onProcessInit(ModuleRuntimeContext context) {
        pipeline = context.pipeline();
        settingListener = event -> {
            if (id().equals(event.moduleId())) {
                macroProjector.apply(context.ownerId(), context.settings().snapshot(), context.macros());
                syncEntityMaskSettings(context);
            }
        };
        context.settings().addListener(settingListener);
        macroProjector.apply(context.ownerId(), context.settings().snapshot(), context.macros());
        syncEntityMaskSettings(context);
    }

    @Override
    public void onKernelInit(ModuleRuntimeContext context) {
        graphicsWorld = context.graphicsWorld();
        TerrainMeshResourceSet.getInstance().ensureCoreResources();
        for (FrameResourceHandle<?> handle : TerrainCullingHandles.all()) {
            context.registerFrameResourceHandle(handle);
        }
        for (FrameResourceHandle<?> handle : TerrainMeshResourceHandles.all()) {
            context.registerFrameResourceHandle(handle);
        }
        for (FrameResourceHandle<?> handle : EntityCullingHandles.all()) {
            context.registerFrameResourceHandle(handle);
        }

        context.registerBuiltInResource(ResourceTypes.TEXTURE,
                HIZ_TEXTURE_RESOURCE_ID,
                () -> currentAdapter() != null ? currentAdapter().hizTexture() : null);
        context.registerBuiltInResource(ResourceTypes.TEXTURE,
                HIZ_ASYNC_TEXTURE_RESOURCE_ID,
                () -> currentAdapter() != null ? currentAdapter().asyncHiZWriteTexture() : null);
        context.registerBuiltInResource(ResourceTypes.TEXTURE,
                HIZ_SOURCE_DEPTH_SNAPSHOT_RESOURCE_ID,
                () -> currentAdapter() != null ? currentAdapter().hizSourceDepthSnapshotTexture() : null);
        context.registerBuiltInResource(ResourceTypes.SHADER_STORAGE_BUFFER,
                ENTITY_DATA_RESOURCE_ID,
                () -> alive(entityMaskStateStore.entityDataBuffer()));
        context.registerBuiltInResource(ResourceTypes.SHADER_STORAGE_BUFFER,
                ENTITY_CULLING_RESULT_RESOURCE_ID,
                () -> alive(entityMaskStateStore.currentResultBuffer()));
        context.hostEvents().subscribe(context.ownerId(), ObjectLifecycleEventBus.OBJECT_SYNC, this::onObjectSync);
        context.hostEvents().subscribe(context.ownerId(), ObjectLifecycleEventBus.OBJECT_DESPAWN, this::onObjectDespawn);
        syncEntityMaskSettings(context);
    }

    @Override
    public void onEnable(ModuleRuntimeContext context) {
        macroProjector.apply(context.ownerId(), context.settings().snapshot(), context.macros());
        entityMaskLifecycleController.onEnable();
        syncEntityMaskSettings(context);
    }

    @Override
    public void onDisable(ModuleRuntimeContext context) {
        entityMaskLifecycleController.onDisable();
        context.clearOwnedMacros();
        clearTransientState();
    }

    @Override
    public void onShutdown(ModuleRuntimeContext context) {
        if (settingListener != null) {
            context.settings().removeListener(settingListener);
            settingListener = null;
        }
        context.clearOwnedMacros();
        entityMaskLifecycleController.shutdown();
        synchronized (trackedRootSubjectsLock) {
            trackedRootSubjects.clear();
        }
        preparedRootSubjects = List.of();
        graphicsWorld = null;
        pipeline = null;
        TerrainMeshResourceSet.getInstance().disposeOwnedResources();
    }

    @Override
    public <C extends RenderContext> void contributeToTickGraph(TickGraphBuilder<C> builder) {
        if (builder.hasPostTickGlAsyncPass("transform_async_tick_collect")) {
            builder.addPostTickGlAsyncPass(new AsyncPrepareEntitySubjectsPass<>(), "transform_async_tick_collect");
        } else {
            builder.addPostTickGlAsyncPass(new AsyncPrepareEntitySubjectsPass<>());
        }
    }

    @Override
    public <C extends RenderContext> void contributeToFrameGraph(RenderGraphBuilder<C> builder) {
        builder.addPass(new SyncEntityMaskLifecyclePass<>(), SyncApplyPendingSettingsPass.NAME);
        builder.addPass(new PublishHiZTexturePass<>(), PASS_SYNC_ENTITY_MASK_LIFECYCLE);
        builder.addPass(new PublishTerrainCullResourcesPass<>(), PASS_PUBLISH_HIZ_TEXTURE);
        builder.addPass(new PublishTerrainMeshResourcesPass<>(), PASS_PUBLISH_TERRAIN_CULL_RESOURCES);
        builder.addPass(new PublishEntityCullResourcesPass<>(), PASS_PUBLISH_TERRAIN_MESH_RESOURCES);
    }

    @Override
    public void describeFrameResources(ModuleGraphAssemblyContext context) {
        for (var handle : TerrainCullingHandles.all()) {
            context.registerFrameResourceHandle(handle);
        }
        for (var handle : TerrainMeshResourceHandles.all()) {
            context.registerFrameResourceHandle(handle);
        }
        for (var handle : EntityCullingHandles.all()) {
            context.registerFrameResourceHandle(handle);
        }
    }

    @Override
    public void contributeModulePasses(ModuleGraphAssemblyContext context) {
        context.registerModulePass(new ModulePassDefinition(
                id(),
                PASS_SYNC_ENTITY_MASK_LIFECYCLE,
                LifecyclePhase.SYNC_PRE_BUILD,
                ThreadDomain.SYNC,
                List.of(),
                List.of(),
                ignored -> {}));
        context.registerModulePass(new ModulePassDefinition(
                id(),
                PASS_PUBLISH_HIZ_TEXTURE,
                LifecyclePhase.SYNC_PRE_BUILD,
                ThreadDomain.SYNC,
                List.of(),
                List.of(TerrainCullingHandles.HIZ_TEXTURE, TerrainCullingHandles.HIZ_DEPTH_INFO),
                ignored -> {}));
        context.registerModulePass(new ModulePassDefinition(
                id(),
                PASS_PUBLISH_TERRAIN_CULL_RESOURCES,
                LifecyclePhase.SYNC_PRE_BUILD,
                ThreadDomain.SYNC,
                List.of(),
                List.of(
                        TerrainCullingHandles.TERRAIN_CULL_INPUT_SSBO,
                        TerrainCullingHandles.TERRAIN_CULL_RESULT_SSBO,
                        TerrainCullingHandles.TERRAIN_INDIRECT_ARGS),
                ignored -> {}));
        context.registerModulePass(new ModulePassDefinition(
                id(),
                PASS_PUBLISH_TERRAIN_MESH_RESOURCES,
                LifecyclePhase.SYNC_PRE_BUILD,
                ThreadDomain.SYNC,
                List.of(),
                List.of(
                        TerrainMeshResourceHandles.TERRAIN_INDIRECT_COMMANDS,
                        TerrainMeshResourceHandles.TERRAIN_REGION_INDEX,
                        TerrainMeshResourceHandles.TERRAIN_CULL_COUNTER,
                        TerrainMeshResourceHandles.TERRAIN_ELEMENT_COUNTER,
                        TerrainMeshResourceHandles.TERRAIN_MAX_ELEMENT_READBACK,
                        TerrainMeshResourceHandles.TERRAIN_MESH_DATA),
                ignored -> {}));
        context.registerModulePass(new ModulePassDefinition(
                id(),
                PASS_PUBLISH_ENTITY_CULL_RESOURCES,
                LifecyclePhase.SYNC_PRE_BUILD,
                ThreadDomain.SYNC,
                List.of(),
                List.of(
                        EntityCullingHandles.ENTITY_CULL_INPUT_SSBO,
                        EntityCullingHandles.ENTITY_CULL_RESULT_SSBO,
                        EntityCullingHandles.ENTITY_INDIRECT_ARGS),
                ignored -> {}));
        context.registerModulePass(new ModulePassDefinition(
                id(),
                PASS_ASYNC_PREPARE_ENTITY_SUBJECTS,
                LifecyclePhase.ASYNC_POST_BUILD,
                ThreadDomain.ASYNC,
                List.of(),
                List.of(),
                ignored -> {}));
    }

    @Override
    public ModuleSession createSession() {
        return new ModuleSession() {
            @Override
            public String id() {
                return "culling_session";
            }

            @Override
            public void onEnable(ModuleSessionContext context) {
                installSubmitNodes(context);
                installComputeGraphics(context);
            }

            @Override
            public void onWorldEnter(ModuleSessionContext context) {
                entityMaskLifecycleController.onWorldEnter();
            }

            @Override
            public void onDisable(ModuleSessionContext context) {
                clearTransientState();
            }

            @Override
            public void onWorldLeave(ModuleSessionContext context) {
                entityMaskLifecycleController.onWorldLeave();
                clearTransientState();
            }
        };
    }

    private void installSubmitNodes(ModuleSessionContext context) {
        CullingHostAdapter adapter = currentAdapter();
        if (adapter == null) {
            return;
        }
        KeyId frameSyncStageId = adapter.frameSyncStageId();
        KeyId terrainStageId = adapter.terrainStageId();
        KeyId hizStageId = adapter.hizStageId();
        context.registerStageSubmitNode(new StageSubmitNode(
                context.ownerId(),
                SYNC_ENTITY_MASK_FRAME_NODE_ID,
                frameSyncStageId,
                StageWindow.PRE_STAGE_UPLOAD,
                StageSubmitNode.NodeType.UPLOAD,
                List.of(),
                List.of(EntityCullingHandles.ENTITY_CULL_RESULT_SSBO.id()),
                5,
                (pipeline, queue, manager, renderContext) ->
                        entityMaskLifecycleController.beginFrame(pipeline.currentLogicTick(), pipeline.isNextLoop()),
                false));
        context.registerStageSubmitNode(new StageSubmitNode(
                context.ownerId(),
                REFRESH_TERRAIN_INPUTS_NODE_ID,
                terrainStageId,
                StageWindow.PRE_STAGE_UPLOAD,
                StageSubmitNode.NodeType.UPLOAD,
                List.of(),
                List.of(
                        TerrainCullingHandles.TERRAIN_CULL_INPUT_SSBO.id(),
                        TerrainCullingHandles.TERRAIN_CULL_RESULT_SSBO.id(),
                        TerrainCullingHandles.TERRAIN_INDIRECT_ARGS.id()),
                20,
                (pipeline, queue, manager, renderContext) -> syncTerrainInputs(adapter, renderContext),
                false));
        context.registerStageSubmitNode(new StageSubmitNode(
                context.ownerId(),
                REFRESH_HIZ_INPUTS_NODE_ID,
                hizStageId,
                StageWindow.PRE_STAGE_UPLOAD,
                StageSubmitNode.NodeType.UPLOAD,
                List.of(),
                List.of(TerrainCullingHandles.HIZ_TEXTURE.id(), TerrainCullingHandles.HIZ_DEPTH_INFO.id()),
                10,
                (pipeline, queue, manager, renderContext) -> triggerAsyncHiZCompute(queue, manager, renderContext),
                false));
        context.registerStageSubmitNode(new StageSubmitNode(
                context.ownerId(),
                REFRESH_ENTITY_INPUTS_NODE_ID,
                hizStageId,
                StageWindow.PRE_STAGE_UPLOAD,
                StageSubmitNode.NodeType.UPLOAD,
                List.of(),
                List.of(EntityCullingHandles.ENTITY_CULL_INPUT_SSBO.id()),
                30,
                (pipeline, queue, manager, renderContext) -> refreshEntityInputs(renderContext, pipeline.isNextLoop()),
                false));
    }

    private void installComputeGraphics(ModuleSessionContext context) {
        CullingHostAdapter adapter = currentAdapter();
        if (adapter == null) {
            return;
        }
        KeyId terrainStageId = adapter.terrainStageId();
        registerComputeIfPresent(context, terrainStageId, buildTerrainCullComputeBlueprint());
        registerComputeIfPresent(context, terrainStageId, buildCopyCounterComputeBlueprint());
        registerComputeIfPresent(context, adapter.hizStageId(), buildEntityCullComputeBlueprint());
    }

    private void registerComputeIfPresent(ModuleSessionContext context, KeyId stageId, GraphicsEntityBlueprint blueprint) {
        if (blueprint != null) {
            context.registerGraphicsEntity(blueprint, ModuleGraphicsLifetime.SESSION);
        }
    }

    private void publishHiZTexture(PassExecutionContext context) {
        SimpleProfiler.get().begin("PublishHiZTexture", Thread.currentThread().getName());
        promoteCompletedHiZJobIfReady();
        CullingHostAdapter adapter = currentAdapter();
        Texture hizTexture = adapter != null ? hiZResourceProducer.hizTexture(adapter) : null;
        Vector3i[] hizDepthInfo = adapter != null ? hiZResourceProducer.depthInfo(adapter) : new Vector3i[0];
        context.publish(TerrainCullingHandles.HIZ_TEXTURE, context.frameEpoch(), hizTexture);
        context.publish(TerrainCullingHandles.HIZ_DEPTH_INFO, context.frameEpoch(), hizDepthInfo);
        SimpleProfiler.get().end("PublishHiZTexture", Thread.currentThread().getName());
    }

    private void publishTerrainCullingResources(PassExecutionContext context) {
        TerrainMeshResourceSet terrainResources = TerrainMeshResourceSet.getInstance();
        ResourceObject terrainCullInput = alive(terrainResources.regionIndexBuffer());
        ResourceObject terrainCullResult = alive(terrainResources.cullingCounter());
        ResourceObject terrainIndirectArgs = alive(terrainResources.indirectCommands());
        context.publish(TerrainCullingHandles.TERRAIN_CULL_INPUT_SSBO, context.frameEpoch(), terrainCullInput);
        context.publish(TerrainCullingHandles.TERRAIN_CULL_RESULT_SSBO, context.frameEpoch(), terrainCullResult);
        context.publish(TerrainCullingHandles.TERRAIN_INDIRECT_ARGS, context.frameEpoch(), terrainIndirectArgs);
    }

    private void publishTerrainMeshResources(PassExecutionContext context) {
        TerrainMeshResourceSet terrainResources = TerrainMeshResourceSet.getInstance();
        context.publish(TerrainMeshResourceHandles.TERRAIN_INDIRECT_COMMANDS, context.frameEpoch(),
                terrainResources.indirectCommands());
        context.publish(TerrainMeshResourceHandles.TERRAIN_REGION_INDEX, context.frameEpoch(),
                terrainResources.regionIndexBuffer());
        context.publish(TerrainMeshResourceHandles.TERRAIN_CULL_COUNTER, context.frameEpoch(),
                terrainResources.cullingCounter());
        context.publish(TerrainMeshResourceHandles.TERRAIN_ELEMENT_COUNTER, context.frameEpoch(),
                terrainResources.elementCounter());
        context.publish(TerrainMeshResourceHandles.TERRAIN_MAX_ELEMENT_READBACK, context.frameEpoch(),
                terrainResources.maxElementReadbackBuffer());
        context.publish(TerrainMeshResourceHandles.TERRAIN_MESH_DATA, context.frameEpoch(),
                terrainResources.meshDataBuffer());
    }

    private void publishEntityCullingResources(PassExecutionContext context) {
        ResourceObject entityCullInput = alive(entityMaskStateStore.entityDataBuffer());
        ResourceObject entityCullResult = alive(entityMaskStateStore.currentResultBuffer());
        context.publish(EntityCullingHandles.ENTITY_CULL_INPUT_SSBO, context.frameEpoch(), entityCullInput);
        context.publish(EntityCullingHandles.ENTITY_CULL_RESULT_SSBO, context.frameEpoch(), entityCullResult);
        context.publish(EntityCullingHandles.ENTITY_INDIRECT_ARGS, context.frameEpoch(), null);
    }

    public EntityVisibilityQueryService entityVisibilityQueries() {
        return entityVisibilityQueryService;
    }

    public EntityMaskStateStore entityMaskStateStore() {
        return entityMaskStateStore;
    }

    public VisibilitySystem visibilitySystem() {
        return visibilitySystem;
    }

    public OcclusionSystem occlusionSystem() {
        return occlusionSystem;
    }

    public SceneDatabase sceneDatabase() {
        return sceneDatabase;
    }

    public boolean chunkCullingEnabled() {
        return chunkCullingEnabled;
    }

    public boolean entityCullingEnabled() {
        return entityCullingEnabled;
    }

    public boolean blockEntityCullingEnabled() {
        return blockEntityCullingEnabled;
    }

    public boolean anyCullingRuntimeEnabled() {
        return anyCullingEnabled();
    }

    public Vector3i[] currentHiZDepthInfo() {
        CullingHostAdapter adapter = currentAdapter();
        return adapter != null ? hiZResourceProducer.depthInfo(adapter) : new Vector3i[0];
    }

    private void onObjectSync(ObjectSyncEvent event) {
        if (event == null) {
            return;
        }
        synchronized (trackedRootSubjectsLock) {
            trackedRootSubjects.put(
                    event.hostObject(),
                    new TrackedRootSubject(event.hostKind(), event.rootEntityId()));
        }
    }

    private void onObjectDespawn(ObjectDespawnEvent event) {
        if (event == null) {
            return;
        }
        if (event.hostKind() == ObjectHostKind.ENTITY || event.hostKind() == ObjectHostKind.BLOCK_ENTITY) {
            synchronized (trackedRootSubjectsLock) {
                trackedRootSubjects.remove(event.hostObject());
            }
            entitySourceRegistry.remove(event.hostObject());
        }
    }

    private void syncEntityMaskSettings(ModuleRuntimeContext context) {
        chunkCullingEnabled = context.settings().getBoolean(CullingModuleDescriptor.CULL_CHUNK, true);
        entityCullingEnabled = context.settings().getBoolean(CullingModuleDescriptor.CULL_ENTITY, true);
        blockEntityCullingEnabled = context.settings().getBoolean(CullingModuleDescriptor.CULL_BLOCK_ENTITY, true);
        entityMaskLifecycleController.syncSettings(entityCullingEnabled, blockEntityCullingEnabled);
    }

    private void syncEntityMaskLifecycle() {
        entityMaskLifecycleController.syncSettings(entityCullingEnabled, blockEntityCullingEnabled);
    }

    private void triggerAsyncHiZCompute(
            RenderPacketQueue<?> queue,
            RenderStateManager manager,
            RenderContext renderContext) {
        CullingHostAdapter adapter = currentAdapter();
        if (adapter == null || pipeline == null || renderContext == null || queue == null || manager == null) {
            return;
        }

        promoteCompletedHiZJobIfReady();
        String threadName = Thread.currentThread().getName();
        SimpleProfiler.get().begin("RefreshHiZInputs", threadName);
        try {
            adapter.refreshHiZInputs(renderContext);
        } finally {
            SimpleProfiler.get().end("RefreshHiZInputs", threadName);
        }

        AsyncGpuJobHandle currentPending = pendingHiZJob;
        if (currentPending != null && !currentPending.isDone()) {
            return;
        }
        freezeCurrentDepthSnapshot(queue, manager, renderContext, adapter);

        GraphicsEntityBlueprint firstBlueprint = buildFirstHiZComputeBlueprint();
        GraphicsEntityBlueprint secondBlueprint = buildSecondHiZComputeBlueprint();
        List<RenderPacket> packets = new ArrayList<>();
        SimpleProfiler.get().begin("BuildHiZImmediatePackets", threadName);
        try {
            packets.addAll(buildImmediatePackets(firstBlueprint, renderContext));
            packets.addAll(buildImmediatePackets(secondBlueprint, renderContext));
        } finally {
            SimpleProfiler.get().end("BuildHiZImmediatePackets", threadName);
        }
        if (packets.isEmpty()) {
            return;
        }

        long epoch = nextHiZEpoch++;
        adapter.onAsyncHiZSubmitted(epoch);
        String submitProfile = "SubmitHiZ:" + epoch;
        SimpleProfiler.get().begin(submitProfile, threadName);
        try {
            pendingHiZJob = submitAsyncHiZPackets(renderContext, packets, epoch);
        } finally {
            SimpleProfiler.get().end(submitProfile, threadName);
        }
    }

    private void freezeCurrentDepthSnapshot(
            RenderPacketQueue<?> queue,
            RenderStateManager manager,
            RenderContext renderContext,
            CullingHostAdapter adapter) {
        Texture snapshotTexture = adapter.hizSourceDepthSnapshotTexture();
        if (snapshotTexture == null || snapshotTexture.isDisposed()) {
            return;
        }
        GraphicsEntityBlueprint snapshotCopyBlueprint = buildDepthSnapshotCopyComputeBlueprint(snapshotTexture);
        String profileName = "FreezeHiZSnapshot";
        SimpleProfiler.get().begin(profileName, Thread.currentThread().getName());
        try {
            executeImmediatePackets(queue, buildImmediatePackets(snapshotCopyBlueprint, renderContext), manager, renderContext);
        } finally {
            SimpleProfiler.get().end(profileName, Thread.currentThread().getName());
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void executeImmediatePackets(
            RenderPacketQueue<?> queue,
            List<RenderPacket> packets,
            RenderStateManager manager,
            RenderContext renderContext) {
        RenderPacketQueue rawQueue = (RenderPacketQueue) queue;
        for (int i = 0; i < packets.size(); i++) {
            RenderPacket packet = packets.get(i);
            if (packet != null) {
                rawQueue.executeImmediate(packet, manager, renderContext);
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private AsyncGpuJobHandle submitAsyncHiZPackets(RenderContext renderContext, List<RenderPacket> packets, long epoch) {
        if (pipeline == null || pipeline.kernel() == null) {
            return null;
        }
        return pipeline.kernel().asyncGpuScheduler().submitCompute(new AsyncComputeRequest(
                pipeline,
                renderContext,
                packets,
                HIZ_TEXTURE_RESOURCE_ID,
                epoch,
                null));
    }

    private void promoteCompletedHiZJobIfReady() {
        AsyncGpuJobHandle currentPending = pendingHiZJob;
        if (currentPending == null || !currentPending.isDone()) {
            return;
        }
        String profileName = "PromoteHiZ:" + currentPending.epoch();
        SimpleProfiler.get().begin(profileName, Thread.currentThread().getName());
        currentPending.await();
        if (currentPending.epoch() > publishedHiZEpoch) {
            CullingHostAdapter adapter = currentAdapter();
            if (adapter != null) {
                adapter.onAsyncHiZCompleted(currentPending.epoch());
            }
            publishedHiZEpoch = currentPending.epoch();
        }
        pendingHiZJob = null;
        SimpleProfiler.get().end(profileName, Thread.currentThread().getName());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private List<RenderPacket> buildImmediatePackets(@Nullable GraphicsEntityBlueprint blueprint, RenderContext renderContext) {
        if (blueprint == null || pipeline == null) {
            return List.of();
        }
        return ((rogo.sketch.core.pipeline.GraphicsPipeline) pipeline).buildImmediatePackets(blueprint, renderContext);
    }

    private @Nullable GraphicsEntityBlueprint buildEntityCullComputeBlueprint() {
        CullingHostAdapter adapter = currentAdapter();
        if (adapter == null) {
            return null;
        }
        GraphicsEntityBlueprint.Builder builder = GraphicsEntityPresets.compute(
                KeyId.of("sketch_render", "cull_entity_batch"),
                adapter.hizStageId(),
                ComputeParameter.COMPUTE_PARAMETER,
                101,
                () -> entityMaskLifecycleController.isActive()
                        && entityMaskLifecycleController.dispatchGroupCount() > 0
                        && currentAdapter() != null
                        && currentAdapter().entityCullingHostActive(),
                () -> false,
                DescriptorStability.DYNAMIC,
                () -> GraphicsEntityPresets.partialDescriptorVersion(resolvePartial(entityCullSetting)),
                renderParameter -> GraphicsEntityPresets.compilePartialDescriptor(renderParameter, resolvePartial(entityCullSetting)),
                dispatchContext -> {
                    int dispatchGroups = entityMaskLifecycleController.dispatchGroupCount();
                    if (dispatchGroups <= 0) {
                        return;
                    }
                    dispatchContext.dispatch(dispatchGroups, 1, 1);
                });
        return GraphicsEntityPresets.withTags(builder, ENTITY_CULL_TAG).build();
    }

    private @Nullable GraphicsEntityBlueprint buildFirstHiZComputeBlueprint() {
        CullingHostAdapter adapter = currentAdapter();
        if (adapter == null) {
            return null;
        }
        return buildComputeBlueprint(
                KeyId.of("sketch_render", "hierarchy_depth_buffer_first"),
                adapter.hizStageId(),
                98,
                HIZ_FIRST_TAG,
                this::shouldRenderFirstHiZ,
                () -> resolvePartial(firstHiZSetting),
                dispatchContext -> {
                    int[] groups = hizGroups(true);
                    if (groups[0] <= 0 || groups[1] <= 0) {
                        return;
                    }
                    dispatchContext.dispatch(groups[0], groups[1], 1);
                    dispatchContext.allBarriers();
                });
    }

    private @Nullable GraphicsEntityBlueprint buildDepthSnapshotCopyComputeBlueprint(Texture snapshotTexture) {
        CullingHostAdapter adapter = currentAdapter();
        if (adapter == null || snapshotTexture == null || snapshotTexture.isDisposed()) {
            return null;
        }
        return buildComputeBlueprint(
                KeyId.of("sketch_render", "hiz_depth_snapshot_copy"),
                adapter.hizStageId(),
                97,
                HIZ_SNAPSHOT_COPY_TAG,
                () -> true,
                () -> resolvePartial(depthSnapshotCopySetting),
                dispatchContext -> {
                    int width = Math.max(1, snapshotTexture.getCurrentWidth());
                    int height = Math.max(1, snapshotTexture.getCurrentHeight());
                    int groupsX = (width + 15) / 16;
                    int groupsY = (height + 15) / 16;
                    dispatchContext.dispatch(groupsX, groupsY, 1);
                    dispatchContext.allBarriers();
                });
    }

    private @Nullable GraphicsEntityBlueprint buildSecondHiZComputeBlueprint() {
        CullingHostAdapter adapter = currentAdapter();
        if (adapter == null) {
            return null;
        }
        return buildComputeBlueprint(
                KeyId.of("sketch_render", "hierarchy_depth_buffer_second"),
                adapter.hizStageId(),
                99,
                HIZ_SECOND_TAG,
                this::shouldRenderSecondHiZ,
                () -> resolvePartial(secondHiZSetting),
                dispatchContext -> {
                    int[] groups = hizGroups(false);
                    if (groups[0] <= 0 || groups[1] <= 0) {
                        return;
                    }
                    dispatchContext.dispatch(groups[0], groups[1], 1);
                    dispatchContext.allBarriers();
                });
    }

    private @Nullable GraphicsEntityBlueprint buildTerrainCullComputeBlueprint() {
        CullingHostAdapter adapter = currentAdapter();
        if (adapter == null) {
            return null;
        }
        return buildComputeBlueprint(
                KeyId.of("sketch_render", "culling_chunk"),
                adapter.terrainStageId(),
                100,
                TERRAIN_CULL_TAG,
                this::shouldRenderTerrainCull,
                () -> resolvePartial(terrainCullSetting),
                dispatchContext -> {
                    int orderedRegionSize = TerrainMeshResourceSet.getInstance().orderedRegionSize();
                    if (orderedRegionSize <= 0) {
                        return;
                    }
                    dispatchContext.dispatch(orderedRegionSize, 3, 1);
                    dispatchContext.allBarriers();
                });
    }

    private @Nullable GraphicsEntityBlueprint buildCopyCounterComputeBlueprint() {
        CullingHostAdapter adapter = currentAdapter();
        if (adapter == null) {
            return null;
        }
        return buildComputeBlueprint(
                KeyId.of("sketch_render", "copy_counter"),
                adapter.terrainStageId(),
                102,
                COPY_COUNTER_TAG,
                this::shouldRenderTerrainCull,
                () -> resolvePartial(copyCounterSetting),
                dispatchContext -> {
                    if (TerrainMeshResourceSet.getInstance().orderedRegionSize() <= 0) {
                        return;
                    }
                    dispatchContext.dispatch(1, 1, 1);
                    dispatchContext.shaderStorageBarrier();
                });
    }

    private GraphicsEntityBlueprint buildComputeBlueprint(
            KeyId identifier,
            KeyId stageId,
            int priority,
            KeyId tag,
            java.util.function.BooleanSupplier shouldRender,
            java.util.function.Supplier<PartialRenderSetting> partialSupplier,
            rogo.sketch.core.api.graphics.ComputeDispatchCommand dispatchCommand) {
        GraphicsEntityBlueprint.Builder builder = GraphicsEntityPresets.compute(
                identifier,
                stageId,
                ComputeParameter.COMPUTE_PARAMETER,
                priority,
                shouldRender,
                () -> false,
                DescriptorStability.DYNAMIC,
                () -> GraphicsEntityPresets.partialDescriptorVersion(partialSupplier.get()),
                renderParameter -> GraphicsEntityPresets.compilePartialDescriptor(renderParameter, partialSupplier.get()),
                dispatchCommand);
        return GraphicsEntityPresets.withTags(builder, tag).build();
    }

    private boolean shouldRenderFirstHiZ() {
        CullingHostAdapter adapter = currentAdapter();
        return adapter != null
                && !adapter.freezeHiZUpdates()
                && occlusionSystem.shouldRenderHiZ(adapter, anyCullingEnabled());
    }

    private boolean shouldRenderSecondHiZ() {
        int[] groups = hizGroups(false);
        return shouldRenderFirstHiZ() && groups[0] > 0 && groups[1] > 0;
    }

    private boolean shouldRenderTerrainCull() {
        CullingHostAdapter adapter = currentAdapter();
        return occlusionSystem.shouldRenderTerrain(adapter, chunkCullingEnabled, TerrainMeshResourceSet.getInstance());
    }

    private boolean anyCullingEnabled() {
        return chunkCullingEnabled || entityCullingEnabled || blockEntityCullingEnabled;
    }

    private int[] hizGroups(boolean first) {
        return occlusionSystem.hiZDispatchGroups(currentAdapter(), first);
    }

    private PartialRenderSetting resolvePartial(ResourceReference<PartialRenderSetting> reference) {
        return reference != null && reference.isAvailable() ? reference.get() : PartialRenderSetting.EMPTY;
    }

    private CullingHostAdapter currentAdapter() {
        return CullingHostRegistry.current();
    }

    private void syncTerrainInputs(CullingHostAdapter adapter, RenderContext renderContext) {
        if (adapter == null) {
            sceneDatabase.replaceTerrainRegionProxies(List.of());
            occlusionSystem.clearTerrainInputs(TerrainMeshResourceSet.getInstance());
            return;
        }
        if (!chunkCullingEnabled || !adapter.terrainCullingHostActive()) {
            sceneDatabase.replaceTerrainRegionProxies(List.of());
            occlusionSystem.clearTerrainInputs(TerrainMeshResourceSet.getInstance());
            adapter.commitVisibleTerrainRegions(List.of());
            return;
        }
        List<TerrainRegionSource> terrainSources = adapter.refreshTerrainInputs(renderContext);
        sceneDatabase.replaceTerrainRegionProxies(terrainSources);
        List<TerrainRegionSource> visibleTerrainRegions = visibilitySystem.collectVisibleTerrainRegions(
                sceneDatabase,
                terrainSources,
                renderContext);
        List<TerrainRegionSource> committedRegions = occlusionSystem.buildVisibleTerrainInputs(
                visibleTerrainRegions,
                TerrainMeshResourceSet.getInstance());
        adapter.commitVisibleTerrainRegions(committedRegions);
    }

    private @Nullable ResourceObject alive(@Nullable ResourceObject resourceObject) {
        if (resourceObject == null || resourceObject.isDisposed()) {
            return null;
        }
        return resourceObject;
    }

    private void refreshEntityInputs(RenderContext renderContext, boolean nextLoop) {
        if (!nextLoop) {
            return;
        }
        applyPreparedRootSubjects();
        entityMaskLifecycleController.refreshEntityInputs(renderContext, true);
    }

    private void prepareTrackedRootSubjectsAsync() {
        if (graphicsWorld == null) {
            preparedRootSubjects = List.of();
            return;
        }
        List<Map.Entry<Object, TrackedRootSubject>> trackedEntries;
        synchronized (trackedRootSubjectsLock) {
            if (trackedRootSubjects.isEmpty()) {
                preparedRootSubjects = List.of();
                return;
            }
            trackedEntries = new ArrayList<>(trackedRootSubjects.entrySet());
        }
        List<PreparedRootSubject> prepared = new ArrayList<>(trackedEntries.size());
        for (Map.Entry<Object, TrackedRootSubject> entry : trackedEntries) {
            Object hostObject = entry.getKey();
            TrackedRootSubject tracked = entry.getValue();
            if (!isSubjectKindEnabled(tracked.hostKind())) {
                prepared.add(PreparedRootSubject.remove(hostObject));
                continue;
            }
            SubjectSnapshot snapshot = resolvePreparedRootSubjectSnapshot(tracked.rootEntityId());
            if (snapshot == null) {
                prepared.add(PreparedRootSubject.remove(hostObject));
                continue;
            }

            GraphicsBuiltinComponents.LifecycleComponent lifecycle = snapshot.lifecycle();
            if (lifecycle != null && (lifecycle.shouldDiscard() || !lifecycle.shouldRender())) {
                prepared.add(PreparedRootSubject.remove(hostObject));
                continue;
            }

            GraphicsBuiltinComponents.BoundsComponent boundsComponent = snapshot.bounds();
            AABBf bounds = boundsComponent != null && boundsComponent.reader() != null
                    ? boundsComponent.reader().readBounds()
                    : null;
            if (bounds == null) {
                prepared.add(PreparedRootSubject.remove(hostObject));
                continue;
            }

            GraphicsBuiltinComponents.ObjectFlagsComponent flagsComponent = snapshot.objectFlags();
            int flags = flagsComponent != null ? flagsComponent.flags() : EntityFeatureSchema.FLAG_NONE;

            prepared.add(PreparedRootSubject.upsert(
                    hostObject,
                    toSubjectKind(tracked.hostKind()),
                    new AABBf(bounds),
                    flags,
                    tracked.rootEntityId(),
                    0));
        }
        preparedRootSubjects = List.copyOf(prepared);
    }

    private void applyPreparedRootSubjects() {
        List<PreparedRootSubject> prepared = preparedRootSubjects;
        if (prepared.isEmpty()) {
            return;
        }
        for (PreparedRootSubject subject : prepared) {
            if (subject == null || subject.hostObject() == null) {
                continue;
            }
            if (subject.removeOnly()) {
                entitySourceRegistry.remove(subject.hostObject());
                continue;
            }
            if (!isStillTracked(subject.hostObject(), subject.transformEntityId())) {
                continue;
            }
            entitySourceRegistry.upsert(
                    subject.hostObject(),
                    subject.subjectKind(),
                    subject.bounds(),
                    subject.flags(),
                    subject.transformEntityId(),
                    subject.logicTick());
        }
    }

    private @Nullable SubjectSnapshot resolvePreparedRootSubjectSnapshot(GraphicsEntityId entityId) {
        TransformModule transformModule = resolveTransformModule();
        if (transformModule != null && transformModule.updateCoordinator() != null) {
            var stateSnapshot = transformModule.updateCoordinator().rootSubjectStateSnapshot(entityId);
            if (stateSnapshot != null) {
                return new SubjectSnapshot(stateSnapshot.lifecycle(), stateSnapshot.bounds(), stateSnapshot.objectFlags());
            }
        }
        GraphicsWorld.RootSubjectSnapshot graphicsSnapshot = graphicsWorld.rootSubjectSnapshot(entityId);
        return graphicsSnapshot != null ? new SubjectSnapshot(
                graphicsSnapshot.lifecycle(),
                graphicsSnapshot.bounds(),
                graphicsSnapshot.objectFlags()) : null;
    }

    private @Nullable TransformModule resolveTransformModule() {
        if (pipeline == null) {
            return null;
        }
        return pipeline.getModuleByName(TransformModule.MODULE_NAME);
    }

    private boolean isStillTracked(Object hostObject, @Nullable GraphicsEntityId rootEntityId) {
        synchronized (trackedRootSubjectsLock) {
            TrackedRootSubject tracked = trackedRootSubjects.get(hostObject);
            return tracked != null && tracked.rootEntityId().equals(rootEntityId);
        }
    }

    private boolean isSubjectKindEnabled(ObjectHostKind hostKind) {
        return switch (hostKind) {
            case ENTITY -> entityCullingEnabled;
            case BLOCK_ENTITY -> blockEntityCullingEnabled;
        };
    }

    private static EntityFeatureSchema.SubjectKind toSubjectKind(ObjectHostKind hostKind) {
        return switch (hostKind) {
            case ENTITY -> EntityFeatureSchema.SubjectKind.ENTITY;
            case BLOCK_ENTITY -> EntityFeatureSchema.SubjectKind.BLOCK_ENTITY;
        };
    }

    private void clearTransientState() {
        String threadName = Thread.currentThread().getName();
        AsyncGpuJobHandle currentPending = pendingHiZJob;
        if (currentPending != null) {
            SimpleProfiler.get().begin("ClearHiZAwaitPending", threadName);
            try {
                currentPending.await();
            } finally {
                SimpleProfiler.get().end("ClearHiZAwaitPending", threadName);
            }
        }
        pendingHiZJob = null;
        publishedHiZEpoch = 0L;
        nextHiZEpoch = 1L;
        synchronized (trackedRootSubjectsLock) {
            trackedRootSubjects.clear();
        }
        preparedRootSubjects = List.of();
        sceneDatabase.clear(SceneProxy.Kind.TERRAIN_REGION);
        occlusionSystem.clearTerrainInputs(TerrainMeshResourceSet.getInstance());
        CullingHostAdapter adapter = currentAdapter();
        if (adapter != null) {
            adapter.commitVisibleTerrainRegions(List.of());
            SimpleProfiler.get().begin("ClearHiZAdapterState", threadName);
            try {
                adapter.clearTransientState();
            } finally {
                SimpleProfiler.get().end("ClearHiZAdapterState", threadName);
            }
        }
    }

    private class PublishHiZTexturePass<C extends RenderContext> implements PipelinePass<C> {
        @Override
        public String name() {
            return PASS_PUBLISH_HIZ_TEXTURE;
        }

        @Override
        public ThreadDomain threadDomain() {
            return ThreadDomain.SYNC;
        }

        @Override
        @SyncOnly("Publish Hi-Z texture handle for culling stages")
        public void execute(FrameContext<C> ctx) {
            publishHiZTexture(ctx.passExecutionContext(id(), PASS_PUBLISH_HIZ_TEXTURE));
        }
    }

    private class SyncEntityMaskLifecyclePass<C extends RenderContext> implements PipelinePass<C> {
        @Override
        public String name() {
            return PASS_SYNC_ENTITY_MASK_LIFECYCLE;
        }

        @Override
        public ThreadDomain threadDomain() {
            return ThreadDomain.SYNC;
        }

        @Override
        @SyncOnly("Synchronize entity mask lifetime with committed module settings")
        public void execute(FrameContext<C> ctx) {
            syncEntityMaskLifecycle();
        }
    }

    private class PublishTerrainCullResourcesPass<C extends RenderContext> implements PipelinePass<C> {
        @Override
        public String name() {
            return PASS_PUBLISH_TERRAIN_CULL_RESOURCES;
        }

        @Override
        public ThreadDomain threadDomain() {
            return ThreadDomain.SYNC;
        }

        @Override
        @SyncOnly("Publish terrain culling resource handles for stage upload/dispatch")
        public void execute(FrameContext<C> ctx) {
            publishTerrainCullingResources(ctx.passExecutionContext(id(), PASS_PUBLISH_TERRAIN_CULL_RESOURCES));
        }
    }

    private class PublishEntityCullResourcesPass<C extends RenderContext> implements PipelinePass<C> {
        @Override
        public String name() {
            return PASS_PUBLISH_ENTITY_CULL_RESOURCES;
        }

        @Override
        public ThreadDomain threadDomain() {
            return ThreadDomain.SYNC;
        }

        @Override
        @SyncOnly("Publish entity culling resource handles for stage upload/dispatch")
        public void execute(FrameContext<C> ctx) {
            publishEntityCullingResources(ctx.passExecutionContext(id(), PASS_PUBLISH_ENTITY_CULL_RESOURCES));
        }
    }

    private class PublishTerrainMeshResourcesPass<C extends RenderContext> implements PipelinePass<C> {
        @Override
        public String name() {
            return PASS_PUBLISH_TERRAIN_MESH_RESOURCES;
        }

        @Override
        public ThreadDomain threadDomain() {
            return ThreadDomain.SYNC;
        }

        @Override
        @SyncOnly("Publish terrain mesh resource ownership handles")
        public void execute(FrameContext<C> ctx) {
            publishTerrainMeshResources(ctx.passExecutionContext(id(), PASS_PUBLISH_TERRAIN_MESH_RESOURCES));
        }
    }

    private class AsyncPrepareEntitySubjectsPass<C extends RenderContext> implements PipelinePass<C> {
        @Override
        public String name() {
            return PASS_ASYNC_PREPARE_ENTITY_SUBJECTS;
        }

        @Override
        public ThreadDomain threadDomain() {
            return ThreadDomain.ASYNC;
        }

        @Override
        @AsyncOnly("Prepare entity culling subject snapshots on the tick worker")
        public void execute(FrameContext<C> ctx) {
            prepareTrackedRootSubjectsAsync();
        }
    }

    private record TrackedRootSubject(
            ObjectHostKind hostKind,
            rogo.sketch.core.graphics.ecs.GraphicsEntityId rootEntityId
    ) {
    }

    private record PreparedRootSubject(
            Object hostObject,
            @Nullable EntityFeatureSchema.SubjectKind subjectKind,
            @Nullable AABBf bounds,
            int flags,
            @Nullable GraphicsEntityId transformEntityId,
            int logicTick,
            boolean removeOnly
    ) {
        private static PreparedRootSubject remove(Object hostObject) {
            return new PreparedRootSubject(hostObject, null, null, EntityFeatureSchema.FLAG_NONE, null, 0, true);
        }

        private static PreparedRootSubject upsert(
                Object hostObject,
                EntityFeatureSchema.SubjectKind subjectKind,
                AABBf bounds,
                int flags,
                GraphicsEntityId transformEntityId,
                int logicTick) {
            return new PreparedRootSubject(hostObject, subjectKind, bounds, flags, transformEntityId, logicTick, false);
        }
    }

    private record SubjectSnapshot(
            @Nullable GraphicsBuiltinComponents.LifecycleComponent lifecycle,
            @Nullable GraphicsBuiltinComponents.BoundsComponent bounds,
            @Nullable GraphicsBuiltinComponents.ObjectFlagsComponent objectFlags
    ) {
    }
}
