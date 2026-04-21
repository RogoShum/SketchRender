package rogo.sketch.core.pipeline;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.packet.GeometryHandleKey;
import rogo.sketch.core.packet.ExecutionKey;
import rogo.sketch.core.packet.RenderPacket;
import rogo.sketch.core.extension.ExtensionHost;
import rogo.sketch.core.extension.PluginApiFacade;
import rogo.sketch.core.graphics.ecs.GraphicsBuiltinComponents;
import rogo.sketch.core.graphics.ecs.GraphicsEntityAssembler;
import rogo.sketch.core.graphics.ecs.GraphicsEntityBlueprint;
import rogo.sketch.core.graphics.ecs.GraphicsEntityId;
import rogo.sketch.core.graphics.ecs.GraphicsEntitySchema;
import rogo.sketch.core.graphics.ecs.GraphicsUniformSubject;
import rogo.sketch.core.graphics.ecs.GraphicsWorld;
import rogo.sketch.core.memory.MemoryDebugSnapshot;
import rogo.sketch.core.memory.UnifiedMemoryFabric;
import rogo.sketch.core.driver.GraphicsDriver;
import rogo.sketch.core.packet.RenderPacketQueue;
import rogo.sketch.core.event.GraphicsPipelineInitEvent;
import rogo.sketch.core.event.RegisterStaticGraphicsEvent;
import rogo.sketch.core.event.bridge.EventBusBridge;
import rogo.sketch.core.pipeline.data.*;
import rogo.sketch.core.pipeline.module.diagnostic.DiagnosticEntry;
import rogo.sketch.core.pipeline.module.diagnostic.RenderTraceConfig;
import rogo.sketch.core.pipeline.module.diagnostic.RenderTraceRecorder;
import rogo.sketch.core.pipeline.module.diagnostic.SketchDiagnostics;
import rogo.sketch.core.pipeline.flow.RenderFlowType;
import rogo.sketch.core.pipeline.flow.RenderPostProcessors;
import rogo.sketch.core.pipeline.flow.ecs.StageMembershipIndex;
import rogo.sketch.core.pipeline.flow.impl.RasterizationPostProcessor;
import rogo.sketch.core.pipeline.flow.v2.ComputeStageFlowScene;
import rogo.sketch.core.pipeline.flow.v2.FunctionStageFlowScene;
import rogo.sketch.core.pipeline.flow.v2.RasterStageFlowScene;
import rogo.sketch.core.pipeline.flow.v2.StageEntityView;
import rogo.sketch.core.pipeline.flow.v2.StageFlowScene;
import rogo.sketch.core.pipeline.kernel.PipelineKernel;
import rogo.sketch.core.pipeline.module.metric.MetricSnapshot;
import rogo.sketch.core.pipeline.module.runtime.ModuleRuntimeHost;
import rogo.sketch.core.pipeline.parmeter.RenderParameter;
import rogo.sketch.core.pipeline.submit.StageSubmitNode;
import rogo.sketch.core.pipeline.submit.StageWindow;
import rogo.sketch.core.resource.GraphicsResourceManager;
import rogo.sketch.core.resource.ResourceTypes;
import rogo.sketch.core.util.KeyId;
import rogo.sketch.core.util.OrderedList;
import rogo.sketch.core.util.RenderTargetUtil;
import rogo.sketch.core.pipeline.data.FrameDataDomain;
import rogo.sketch.core.pipeline.data.GeometryFrameData;
import rogo.sketch.core.vertex.MeshResidencyPool;
import rogo.sketch.core.vertex.GeometryResourceCoordinator;

import java.util.*;
import java.util.function.Supplier;

public class GraphicsPipeline<C extends RenderContext> {
    private static final KeyId IMMEDIATE_STAGE_ID = KeyId.of("sketch_render", "immediate");

    private final OrderedList<GraphicsStage> stages;
    private final Map<GraphicsStage, GraphicsBatchGroup<C>> passMap = new LinkedHashMap<>();
    private final Map<KeyId, GraphicsStage> idToStage = new LinkedHashMap<>();
    private final RenderStateManager renderStateManager = new RenderStateManager();
    private final RenderPacketQueue<C> renderPacketQueue;
    private final Map<KeyId, PipelineType> pipelineTypes = new HashMap<>();
    private final Map<PipelineType, GeometryResourceCoordinator> resourceManagers = new LinkedHashMap<>();
    private final Map<PipelineType, MeshResidencyPool> meshResidencyPools = new LinkedHashMap<>();
    private final Map<PipelineType, FrameDataStore> frameDataStores = new LinkedHashMap<>();
    private final RenderTraceConfig renderTraceConfig = new RenderTraceConfig();
    private final RenderTraceRecorder renderTraceRecorder = new RenderTraceRecorder(renderTraceConfig);
    private final Map<KeyId, GraphicsEntityId> installedFunctionResources = new LinkedHashMap<>();
    private final Map<KeyId, GraphicsEntityId> installedDrawCallResources = new LinkedHashMap<>();
    private final Map<KeyId, EnumMap<StageWindow, List<StageSubmitNode>>> stageSubmitNodes = new LinkedHashMap<>();
    private final Map<String, List<StageSubmitNode>> ownedStageSubmitNodes = new LinkedHashMap<>();
    private final GraphicsWorld graphicsWorld = new GraphicsWorld();
    private final GraphicsEntityAssembler graphicsEntityAssembler = new GraphicsEntityAssembler(graphicsWorld);
    private final ExtensionHost extensionHost = new ExtensionHost(this);
    private final StageMembershipIndex stageMembershipIndex = new StageMembershipIndex();
    private final Map<KeyId, Long> stageEntityOrderHints = new LinkedHashMap<>();

    private final PipelineConfig config;
    private C currentContext;
    private int currentFrameTick = 0;
    private int currentLogicTick = 0;
    private int currentLogicTickInSeconds = 0;
    private boolean[] nextTick = new boolean[20];
    private boolean initialized = false;
    private boolean initializedStaticGraphics = false;

    // New architecture: kernel
    private PipelineKernel<C> kernel;

    public GraphicsPipeline(PipelineConfig config, C defaultContext) {
        this.config = config;
        this.stages = new OrderedList<>(config.isThrowOnSortFail());
        this.currentContext = defaultContext;
        initPipelineData();
        this.renderPacketQueue = new RenderPacketQueue<>(this);
    }

    private void initPipelineData() {
        // Initialize resource managers and data stores for each pipeline type
        initializePipeline(PipelineType.COMPUTE);
        initializePipeline(PipelineType.FUNCTION);
        initializePipeline(PipelineType.RASTERIZATION);
        initializePipeline(PipelineType.TRANSLUCENT);
    }

    private void initializePipeline(PipelineType pipelineType) {
        pipelineTypes.put(pipelineType.getIdentifier(), pipelineType);
        MeshResidencyPool meshResidencyPool = new MeshResidencyPool("mesh-residency-" + pipelineType.getIdentifier());
        GeometryResourceCoordinator manager = new GeometryResourceCoordinator(meshResidencyPool);
        PipelineDataStore renderStore = new PipelineDataStore();
        PipelineDataStore asyncStore = new PipelineDataStore();

        // Register standard pipeline data
        registerDefaultPipelineData(renderStore);
        registerDefaultPipelineData(asyncStore);

        FrameDataStore frameStore = new FrameDataStore(renderStore, asyncStore);

        resourceManagers.put(pipelineType, manager);
        meshResidencyPools.put(pipelineType, meshResidencyPool);
        frameDataStores.put(pipelineType, frameStore);
    }

    private void registerDefaultPipelineData(PipelineDataStore dataStore) {
        dataStore.register(KeyId.of("indirect_buffers"), new IndirectBufferData());
        dataStore.register(IndirectPlanData.KEY, new IndirectPlanData());
        dataStore.register(KeyId.of("instanced_offsets"), new InstancedOffsetData());
        dataStore.register(GeometryFrameData.KEY, new GeometryFrameData());
    }

    public PipelineConfig getConfig() {
        return config;
    }

    /**
     * Get all registered pipeline types sorted by priority.
     *
     * @return List of pipeline types in priority order
     */
    public List<PipelineType> getPipelineTypes() {
        List<PipelineType> sorted = new ArrayList<>(pipelineTypes.values());
        sorted.sort(Comparator.comparingInt(PipelineType::getPriority));
        return sorted;
    }

    /**
     * Register a stage. Only if added successfully, a GraphPass will be created.
     *
     * @return true if added successfully, false if delayed
     */
    public boolean registerStage(GraphicsStage stage) {
        boolean added = stages.add(stage, stage.getOrderRequirement(),
                (s, req) -> passMap.put(s, new GraphicsBatchGroup<>(this, s.getIdentifier())));
        if (added) {
            idToStage.put(stage.getIdentifier(), stage);
            stageSubmitNodes.computeIfAbsent(stage.getIdentifier(), ignored -> new EnumMap<>(StageWindow.class));
        }
        return added;
    }

    /**
     * Get the ordered list of stages.
     */
    public List<GraphicsStage> getOrderedStages() {
        return stages.getOrderedList();
    }

    /**
     * Get a specific stage by identifier.
     *
     * @param stageId Stage identifier
     * @return GraphicsStage or null if not found
     */
    public GraphicsStage getStage(KeyId stageId) {
        return idToStage.get(stageId);
    }

    /**
     * Get the list of pending (delayed) stages.
     */
    public List<OrderedList.PendingElement<GraphicsStage>> getPendingStages() {
        return stages.getPendingElements();
    }

    public void renderImmediate(GraphicsEntityBlueprint blueprint) {
        renderImmediate(blueprint, currentContext);
    }

    public void renderImmediate(GraphicsEntityBlueprint blueprint, @Nullable C contextOverride) {
        List<RenderPacket> packets = buildImmediatePackets(blueprint, contextOverride);
        C context = contextOverride != null ? contextOverride : currentContext;
        if (context == null || packets.isEmpty()) {
            return;
        }
        for (RenderPacket packet : packets) {
            renderPacketQueue.executeImmediate(packet, renderStateManager, context);
        }
    }

    public List<RenderPacket> buildImmediatePackets(GraphicsEntityBlueprint blueprint) {
        return buildImmediatePackets(blueprint, currentContext);
    }

    public List<RenderPacket> buildImmediatePackets(GraphicsEntityBlueprint blueprint, @Nullable C contextOverride) {
        if (blueprint == null || blueprint.isDisposed()) {
            return List.of();
        }

        C context = contextOverride != null ? contextOverride : currentContext;
        if (context == null) {
            return List.of();
        }

        GraphicsBuiltinComponents.StageBindingComponent stageBinding = blueprint.component(GraphicsBuiltinComponents.STAGE_BINDING);
        if (stageBinding == null || stageBinding.renderParameter() == null || stageBinding.renderParameter().isInvalid()) {
            return List.of();
        }

        GraphicsEntityId entityId = graphicsEntityAssembler.spawn(withImmediateStageBinding(blueprint));
        StageEntityView immediateView = new StageEntityView(
                IMMEDIATE_STAGE_ID,
                stageBinding.pipelineType(),
                List.of(snapshotEntity(entityId)));

        PipelineType pipelineType = stageBinding.pipelineType();
        StageFlowScene<C> immediateScene = createImmediateStageScene(pipelineType);
        RenderPostProcessors postProcessors = new RenderPostProcessors();
        if (pipelineType == PipelineType.RASTERIZATION || pipelineType == PipelineType.TRANSLUCENT) {
            postProcessors.register(RenderFlowType.RASTERIZATION, new RasterizationPostProcessor());
        }

        try {
            immediateScene.prepareForFrame(graphicsWorld, immediateView, context);

            Map<ExecutionKey, List<RenderPacket>> packets = immediateScene.createRenderPackets(
                    immediateView,
                    pipelineType.getDefaultFlowType(),
                    postProcessors,
                    context);
            if (packets.isEmpty()) {
                return List.of();
            }

            GraphicsDriver.runtime().installImmediateGeometryBindings(this, pipelineType, postProcessors);
            postProcessors.executeAllExcept(RenderFlowType.RASTERIZATION);

            List<RenderPacket> flattenedPackets = new ArrayList<>();
            for (List<RenderPacket> statePackets : packets.values()) {
                flattenedPackets.addAll(statePackets);
            }
            GraphicsDriver.runtime().installImmediateResourceBindings(flattenedPackets);
            return List.copyOf(flattenedPackets);
        } finally {
            graphicsEntityAssembler.destroy(entityId);
        }
    }

    /**
     * Render stages between 'fromStage' (exclusive) and 'toStage' (exclusive).
     * Only renders the stages strictly between fromId and toId.
     */
    public void renderStagesBetween(KeyId fromId, KeyId toId) {
        renderOrderedStages(collectStageIdsBetweenExclusiveInclusive(fromId, toId), "between");
    }

    /**
     * Render the inclusive range {@code (fromExclusive, toInclusive]} as a single execution scope.
     */
    public void renderStageRange(KeyId fromExclusive, KeyId toInclusive) {
        renderOrderedStages(collectStageIdsBetweenExclusiveInclusive(fromExclusive, toInclusive), "range");
    }

    /**
     * Render a single stage.
     */
    public void renderStage(KeyId id) {
        renderOrderedStages(List.of(id), "single");
    }

    public void renderStagesBefore(KeyId id) {
        renderOrderedStages(collectStageIdsBeforeInclusive(id), "before");
    }

    public void renderStagesAfter(KeyId id) {
        renderOrderedStages(collectStageIdsAfterInclusive(id), "after");
    }

    public void resetRenderContext(C context) {
        this.currentContext = context;
        this.resizeRenderTargets(context.windowWidth, context.windowHeight);
    }

    protected void resizeRenderTargets(int windowWidth, int windowHeight) {
        RenderTargetUtil.resizeRT(windowWidth, windowHeight);
    }

    /**
     * Initialize the pipeline and post initialization events
     */
    public void initPipeline() {
        if (initialized) {
            return;
        }

        createKernel();
        EventBusBridge.post(new GraphicsPipelineInitEvent(this, GraphicsPipelineInitEvent.InitPhase.EARLY));
        EventBusBridge.post(new GraphicsPipelineInitEvent(this, GraphicsPipelineInitEvent.InitPhase.NORMAL));
        EventBusBridge.post(new GraphicsPipelineInitEvent(this, GraphicsPipelineInitEvent.InitPhase.LATE));
        kernel.moduleRegistry().processInitialize(this);

        initialized = true;
    }

    public void initKernel() {
        kernel.initialize();
    }

    public void initStaticGraphics() {
        if (initializedStaticGraphics) {
            return;
        }

        EventBusBridge.post(new RegisterStaticGraphicsEvent(this));

        initializedStaticGraphics = true;
    }

    public void tickFrame() {
        this.currentFrameTick++;
        int thisTick = this.currentLogicTick % 20;
        this.nextTick = new boolean[20];

        if (this.currentLogicTickInSeconds != thisTick) {
            this.currentLogicTickInSeconds = thisTick;
            this.nextTick[thisTick] = true;
        }
    }

    public void tickLogic() {
        this.currentLogicTick++;
    }

    /**
     * Tick all stages with async support
     */
    public void tickGraphics() {
        for (GraphicsStage stage : stages.getOrderedList()) {
            GraphicsBatchGroup<C> group = passMap.get(stage);
            if (group != null) {
                group.tick(currentContext);
            }
        }
    }

    public void asyncTickGraphics() {
        for (GraphicsStage stage : stages.getOrderedList()) {
            GraphicsBatchGroup<C> group = passMap.get(stage);
            if (group != null) {
                group.asyncTick(currentContext);
            }
        }
    }

    public void prepareNextFrameStageViews() {
        for (GraphicsStage stage : stages.getOrderedList()) {
            GraphicsBatchGroup<C> group = passMap.get(stage);
            if (group != null) {
                group.prepareNextFrameStageViews();
            }
        }
    }

    public void swapGraphicsData() {
        for (GraphicsStage stage : stages.getOrderedList()) {
            GraphicsBatchGroup<C> group = passMap.get(stage);
            if (group != null) {
                group.swapData();
            }
        }
    }

    /**
     * Cleanup discarded instances and return them to pools
     */
    public void cleanupInstances() {
        for (GraphicsBatchGroup<C> group : passMap.values()) {
            group.cleanupDiscardedInstances();
        }
    }

    public void enterWorld() {
        if (kernel != null && kernel.moduleRegistry().isInitialized()) {
            kernel.moduleRegistry().enterWorld();
        }
    }

    public void leaveWorld() {
        if (kernel != null && kernel.moduleRegistry().isInitialized()) {
            kernel.moduleRegistry().leaveWorld();
        }

        currentLogicTick = 0;
        currentFrameTick = 0;
    }

    public void onResourceReload() {
        installPipelineResources();
        if (kernel != null && kernel.moduleRegistry().isInitialized()) {
            kernel.moduleRegistry().onResourceReload();
        }
    }

    public void installPipelineResources() {
        clearInstalledPipelineResources();
        installFunctionResources();
        installDrawCallResources();
    }

    public void shutdown() {
        renderTraceRecorder.flushAll();
        leaveWorld();
        if (kernel != null) {
            kernel.cleanup();
            kernel = null;
        }
        stageSubmitNodes.clear();
        ownedStageSubmitNodes.clear();
        initialized = false;
        initializedStaticGraphics = false;
        currentLogicTick = 0;
        currentFrameTick = 0;
        stageMembershipIndex.clear();
        stageEntityOrderHints.clear();
    }

    public boolean anyNextTick() {
        for (int i = 0; i < 20; ++i) {
            if (nextTick[i])
                return true;
        }
        return false;
    }

    public boolean isNextLoop() {
        return nextTick[0];
    }

    public int currentFrameTick() {
        return currentFrameTick;
    }

    public int currentLogicTick() {
        return currentLogicTick;
    }

    /**
     * Check if the pipeline is initialized
     */
    public boolean isInitialized() {
        return initialized;
    }

    public RenderStateManager renderStateManager() {
        return renderStateManager;
    }

    public C currentContext() {
        return currentContext;
    }

    public record PipelineStats(int totalStages, int pendingStages, int totalInstances,
                                boolean initialized) {
        @Override
        public String toString() {
            return String.format("Pipeline[stages=%d, pending=%d, instances=%d, init=%s]",
                    totalStages, pendingStages, totalInstances, initialized);
        }
    }

    /**
     * Get the render packet queue (for new pipeline)
     */
    public RenderPacketQueue<C> getRenderPacketQueue() {
        return renderPacketQueue;
    }

    public PipelineDataStore getPipelineDataStore(PipelineType pipelineType, FrameDataDomain domain) {
        FrameDataStore frameDataStore = frameDataStores.computeIfAbsent(pipelineType, pt -> {
            initializePipeline(pt);
            return frameDataStores.get(pt);
        });
        return frameDataStore.buffer(domain);
    }

    public GeometryResourceCoordinator getGeometryResourceCoordinator(PipelineType pipelineType) {
        return resourceManagers.computeIfAbsent(pipelineType, pt -> {
            initializePipeline(pt);
            return resourceManagers.get(pt);
        });
    }

    public MeshResidencyPool getMeshResidencyPool(PipelineType pipelineType) {
        return meshResidencyPools.computeIfAbsent(pipelineType, pt -> {
            initializePipeline(pt);
            return meshResidencyPools.get(pt);
        });
    }

    public KeyId getNextStage(KeyId stageId) {
        List<GraphicsStage> orderedList = this.stages.getOrderedList();
        int size = orderedList.size();

        for (int i = 0; i < size; i++) {
            GraphicsStage stage = orderedList.get(i);
            if (stage.getIdentifier().equals(stageId)) {
                if (i == orderedList.size() - 1) {
                    return orderedList.get(0).getIdentifier();
                } else {
                    return orderedList.get(i + 1).getIdentifier();
                }
            }
        }

        return stageId;
    }

    // ==================== New Architecture Accessors ====================

    /**
     * Get the batch group for a specific stage.
     */
    public GraphicsBatchGroup<C> getBatchGroup(GraphicsStage stage) {
        return passMap.get(stage);
    }

    /**
     * Get all pipeline data stores (for passes that need to reset them).
     */
    public Collection<PipelineDataStore> getAllPipelineDataStores(FrameDataDomain domain) {
        List<PipelineDataStore> stores = new ArrayList<>(frameDataStores.size());
        for (FrameDataStore frameDataStore : frameDataStores.values()) {
            stores.add(frameDataStore.buffer(domain));
        }
        return stores;
    }

    public Collection<FrameDataStore> getAllFrameDataStores() {
        return frameDataStores.values();
    }

    public void swapFrameDataStores() {
        for (FrameDataStore dataStore : frameDataStores.values()) {
            dataStore.swap();
        }
    }


    public FrameDataStore getFrameDataStore(PipelineType pipelineType) {
        return frameDataStores.get(pipelineType);
    }

    public void materializePendingGeometryBindings() {
        GraphicsDriver.runtime().materializePendingGeometryResources(this);
    }

    /**
     * Get or create the PipelineKernel for the new architecture.
     */
    protected PipelineKernel<C> createKernel() {
        if (kernel == null) {
            kernel = new PipelineKernel<>(this);
        }
        return kernel;
    }

    /**
     * Get the existing kernel, or null if not created.
     */
    public PipelineKernel<C> kernel() {
        return kernel;
    }

    public ModuleRuntimeHost runtimeHost() {
        return kernel != null ? kernel.moduleRegistry().runtimeHost() : null;
    }

    public List<DiagnosticEntry> diagnosticsSnapshot() {
        return SketchDiagnostics.get().snapshot();
    }

    public RenderTraceConfig renderTraceConfig() {
        return renderTraceConfig;
    }

    public RenderTraceRecorder renderTraceRecorder() {
        return renderTraceRecorder;
    }

    public MetricSnapshot metricSnapshot() {
        ModuleRuntimeHost host = runtimeHost();
        return host != null ? host.metricSnapshot() : new MetricSnapshot(Collections.emptyMap());
    }

    public MemoryDebugSnapshot memoryDebugSnapshot() {
        return UnifiedMemoryFabric.get().snapshot();
    }

    public void registerStageSubmitNode(String ownerId, StageSubmitNode node) {
        if (node == null || node.stageId() == null || node.window() == null) {
            return;
        }
        EnumMap<StageWindow, List<StageSubmitNode>> windows = stageSubmitNodes.computeIfAbsent(
                node.stageId(),
                ignored -> new EnumMap<>(StageWindow.class));
        List<StageSubmitNode> nodes = new ArrayList<>(windows.getOrDefault(node.window(), List.of()));
        nodes.add(node);
        nodes.sort(Comparator.comparingInt(StageSubmitNode::sortHint).thenComparing(n -> n.nodeId().toString()));
        windows.put(node.window(), List.copyOf(nodes));
        if (ownerId != null) {
            ownedStageSubmitNodes.computeIfAbsent(ownerId, ignored -> new ArrayList<>()).add(node);
        }
    }

    public void unregisterStageSubmitNodes(String ownerId) {
        if (ownerId == null) {
            return;
        }
        List<StageSubmitNode> ownedNodes = ownedStageSubmitNodes.remove(ownerId);
        if (ownedNodes == null || ownedNodes.isEmpty()) {
            return;
        }
        for (StageSubmitNode node : ownedNodes) {
            EnumMap<StageWindow, List<StageSubmitNode>> windows = stageSubmitNodes.get(node.stageId());
            if (windows == null) {
                continue;
            }
            List<StageSubmitNode> existing = windows.get(node.window());
            if (existing == null || existing.isEmpty()) {
                continue;
            }
            List<StageSubmitNode> updated = new ArrayList<>(existing);
            updated.removeIf(candidate -> candidate.nodeId().equals(node.nodeId()) && ownerId.equals(candidate.ownerId()));
            if (updated.isEmpty()) {
                windows.remove(node.window());
            } else {
                windows.put(node.window(), List.copyOf(updated));
            }
        }
    }

    public List<StageSubmitNode> stageSubmitNodes(KeyId stageId, StageWindow window) {
        EnumMap<StageWindow, List<StageSubmitNode>> windows = stageSubmitNodes.get(stageId);
        if (windows == null) {
            return List.of();
        }
        return windows.getOrDefault(window, List.of());
    }

    @Nullable
    @SuppressWarnings("unchecked")
    public <M> M getModuleByName(String name) {
        if (kernel.moduleRegistry().containsModule(name)) {
            return (M) kernel.moduleRegistry().moduleByName(name);
        }

        return null;
    }

    public GraphicsWorld graphicsWorld() {
        return graphicsWorld;
    }

    public GraphicsEntityAssembler graphicsEntityAssembler() {
        return graphicsEntityAssembler;
    }

    public ExtensionHost extensionHost() {
        return extensionHost;
    }

    public PluginApiFacade pluginApiFacade() {
        return extensionHost.pluginApiFacade();
    }

    public GraphicsEntityId spawnGraphicsEntity(GraphicsEntityBlueprint blueprint) {
        GraphicsEntityId entityId = graphicsEntityAssembler.spawn(blueprint);
        registerSpawnedEntity(entityId);
        return entityId;
    }

    public void destroyGraphicsEntity(GraphicsEntityId entityId) {
        if (kernel != null && kernel.moduleRegistry().isInitialized()) {
            kernel.moduleRegistry().onEntityDestroyed(entityId);
        }
        stageMembershipIndex.unregister(entityId);
        graphicsEntityAssembler.destroy(entityId);
    }

    public StageMembershipIndex stageMembershipIndex() {
        return stageMembershipIndex;
    }

    public StageEntityView.Entry snapshotEntity(GraphicsEntityId entityId) {
        GraphicsWorld.StageEntitySnapshot snapshot = graphicsWorld.stageEntitySnapshot(entityId);
        if (snapshot == null) {
            throw new IllegalArgumentException("Unknown graphics entity: " + entityId);
        }
        return snapshotEntity(snapshot);
    }

    public StageEntityView.Entry snapshotEntityIfPresent(GraphicsEntityId entityId) {
        GraphicsWorld.StageEntitySnapshot snapshot = graphicsWorld.stageEntitySnapshot(entityId);
        return snapshot != null ? snapshotEntity(snapshot) : null;
    }

    public List<StageEntityView.Entry> snapshotEntitiesIfPresent(List<GraphicsEntityId> entityIds) {
        if (entityIds == null || entityIds.isEmpty()) {
            return List.of();
        }
        List<GraphicsWorld.StageEntitySnapshot> snapshots = graphicsWorld.stageEntitySnapshots(entityIds);
        if (snapshots.isEmpty()) {
            return List.of();
        }
        List<StageEntityView.Entry> entries = new ArrayList<>(snapshots.size());
        for (GraphicsWorld.StageEntitySnapshot snapshot : snapshots) {
            if (snapshot != null) {
                entries.add(snapshotEntity(snapshot));
            }
        }
        return entries.isEmpty() ? List.of() : List.copyOf(entries);
    }

    private StageEntityView.Entry snapshotEntity(GraphicsWorld.StageEntitySnapshot snapshot) {
        GraphicsBuiltinComponents.StageBindingComponent stageBinding = snapshot.stageBinding();
        GraphicsBuiltinComponents.ContainerHintComponent containerHint = snapshot.containerHint();
        GraphicsBuiltinComponents.IdentityComponent identity = snapshot.identity();
        GraphicsBuiltinComponents.ResourceOriginComponent resourceOrigin = snapshot.resourceOrigin();
        GraphicsBuiltinComponents.GraphicsTagsComponent tags = snapshot.tags();
        GraphicsEntitySchema schema = snapshot.schema();
        GraphicsUniformSubject uniformSubject = new GraphicsUniformSubject(
                snapshot.entityId(),
                identity,
                resourceOrigin,
                tags,
                schema,
                stageBinding != null ? stageBinding.stageId() : null,
                stageBinding != null ? stageBinding.pipelineType() : null,
                stageBinding != null ? stageBinding.renderParameter() : null,
                componentType -> resolveSnapshotComponent(snapshot, componentType),
                () -> buildComponentSnapshot(snapshot));
        return new StageEntityView.Entry(
                snapshot.entityId(),
                schema,
                schema.capabilityView(),
                identity,
                resourceOrigin,
                tags,
                uniformSubject,
                snapshot.lifecycle(),
                stageBinding,
                containerHint,
                snapshot.rasterRenderable(),
                snapshot.computeDispatch(),
                snapshot.functionInvoke(),
                snapshot.submissionCapability(),
                snapshot.bounds(),
                snapshot.preparedMesh(),
                snapshot.renderDescriptor(),
                snapshot.instanceVertexAuthoring(),
                snapshot.transformBinding(),
                snapshot.tickDriver(),
                snapshot.asyncTickDriver(),
                snapshot.descriptorVersion(),
                snapshot.geometryVersion(),
                snapshot.boundsVersion());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void putComponent(
            Map<rogo.sketch.core.graphics.ecs.GraphicsComponentType<?>, Object> snapshot,
            rogo.sketch.core.graphics.ecs.GraphicsComponentType componentType,
            Object value) {
        if (snapshot == null || componentType == null || value == null) {
            return;
        }
        snapshot.put(componentType, value);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Object resolveSnapshotComponent(
            GraphicsWorld.StageEntitySnapshot snapshot,
            rogo.sketch.core.graphics.ecs.GraphicsComponentType<?> componentType) {
        if (snapshot == null || componentType == null) {
            return null;
        }
        if (componentType.equals(GraphicsBuiltinComponents.IDENTITY)) {
            return snapshot.identity();
        }
        if (componentType.equals(GraphicsBuiltinComponents.RESOURCE_ORIGIN)) {
            return snapshot.resourceOrigin();
        }
        if (componentType.equals(GraphicsBuiltinComponents.GRAPHICS_TAGS)) {
            return snapshot.tags();
        }
        if (componentType.equals(GraphicsBuiltinComponents.OBJECT_FLAGS)) {
            return snapshot.objectFlags();
        }
        if (componentType.equals(GraphicsBuiltinComponents.LIFECYCLE)) {
            return snapshot.lifecycle();
        }
        if (componentType.equals(GraphicsBuiltinComponents.STAGE_BINDING)) {
            return snapshot.stageBinding();
        }
        if (componentType.equals(GraphicsBuiltinComponents.CONTAINER_HINT)) {
            return snapshot.containerHint();
        }
        if (componentType.equals(GraphicsBuiltinComponents.RASTER_RENDERABLE)) {
            return snapshot.rasterRenderable();
        }
        if (componentType.equals(GraphicsBuiltinComponents.COMPUTE_DISPATCH)) {
            return snapshot.computeDispatch();
        }
        if (componentType.equals(GraphicsBuiltinComponents.FUNCTION_INVOKE)) {
            return snapshot.functionInvoke();
        }
        if (componentType.equals(GraphicsBuiltinComponents.SUBMISSION_CAPABILITY)) {
            return snapshot.submissionCapability();
        }
        if (componentType.equals(GraphicsBuiltinComponents.BOUNDS)) {
            return snapshot.bounds();
        }
        if (componentType.equals(GraphicsBuiltinComponents.PREPARED_MESH)) {
            return snapshot.preparedMesh();
        }
        if (componentType.equals(GraphicsBuiltinComponents.RENDER_DESCRIPTOR)) {
            return snapshot.renderDescriptor();
        }
        if (componentType.equals(GraphicsBuiltinComponents.INSTANCE_VERTEX_AUTHORING)) {
            return snapshot.instanceVertexAuthoring();
        }
        if (componentType.equals(GraphicsBuiltinComponents.TRANSFORM_BINDING)) {
            return snapshot.transformBinding();
        }
        if (componentType.equals(GraphicsBuiltinComponents.TRANSFORM_HIERARCHY)) {
            return snapshot.transformHierarchy();
        }
        if (componentType.equals(GraphicsBuiltinComponents.TICK_DRIVER)) {
            return snapshot.tickDriver();
        }
        if (componentType.equals(GraphicsBuiltinComponents.ASYNC_TICK_DRIVER)) {
            return snapshot.asyncTickDriver();
        }
        if (componentType.equals(GraphicsBuiltinComponents.DESCRIPTOR_VERSION)) {
            return snapshot.descriptorVersion();
        }
        if (componentType.equals(GraphicsBuiltinComponents.GEOMETRY_VERSION)) {
            return snapshot.geometryVersion();
        }
        if (componentType.equals(GraphicsBuiltinComponents.BOUNDS_VERSION)) {
            return snapshot.boundsVersion();
        }
        return null;
    }

    private Map<rogo.sketch.core.graphics.ecs.GraphicsComponentType<?>, Object> buildComponentSnapshot(
            GraphicsWorld.StageEntitySnapshot snapshot) {
        Map<rogo.sketch.core.graphics.ecs.GraphicsComponentType<?>, Object> componentSnapshot = new LinkedHashMap<>();
        putComponent(componentSnapshot, GraphicsBuiltinComponents.IDENTITY, snapshot.identity());
        putComponent(componentSnapshot, GraphicsBuiltinComponents.RESOURCE_ORIGIN, snapshot.resourceOrigin());
        putComponent(componentSnapshot, GraphicsBuiltinComponents.GRAPHICS_TAGS, snapshot.tags());
        putComponent(componentSnapshot, GraphicsBuiltinComponents.OBJECT_FLAGS, snapshot.objectFlags());
        putComponent(componentSnapshot, GraphicsBuiltinComponents.LIFECYCLE, snapshot.lifecycle());
        putComponent(componentSnapshot, GraphicsBuiltinComponents.STAGE_BINDING, snapshot.stageBinding());
        putComponent(componentSnapshot, GraphicsBuiltinComponents.CONTAINER_HINT, snapshot.containerHint());
        putComponent(componentSnapshot, GraphicsBuiltinComponents.RASTER_RENDERABLE, snapshot.rasterRenderable());
        putComponent(componentSnapshot, GraphicsBuiltinComponents.COMPUTE_DISPATCH, snapshot.computeDispatch());
        putComponent(componentSnapshot, GraphicsBuiltinComponents.FUNCTION_INVOKE, snapshot.functionInvoke());
        putComponent(componentSnapshot, GraphicsBuiltinComponents.SUBMISSION_CAPABILITY, snapshot.submissionCapability());
        putComponent(componentSnapshot, GraphicsBuiltinComponents.BOUNDS, snapshot.bounds());
        putComponent(componentSnapshot, GraphicsBuiltinComponents.PREPARED_MESH, snapshot.preparedMesh());
        putComponent(componentSnapshot, GraphicsBuiltinComponents.RENDER_DESCRIPTOR, snapshot.renderDescriptor());
        putComponent(componentSnapshot, GraphicsBuiltinComponents.INSTANCE_VERTEX_AUTHORING, snapshot.instanceVertexAuthoring());
        putComponent(componentSnapshot, GraphicsBuiltinComponents.TRANSFORM_BINDING, snapshot.transformBinding());
        putComponent(componentSnapshot, GraphicsBuiltinComponents.TRANSFORM_HIERARCHY, snapshot.transformHierarchy());
        putComponent(componentSnapshot, GraphicsBuiltinComponents.TICK_DRIVER, snapshot.tickDriver());
        putComponent(componentSnapshot, GraphicsBuiltinComponents.ASYNC_TICK_DRIVER, snapshot.asyncTickDriver());
        putComponent(componentSnapshot, GraphicsBuiltinComponents.DESCRIPTOR_VERSION, snapshot.descriptorVersion());
        putComponent(componentSnapshot, GraphicsBuiltinComponents.GEOMETRY_VERSION, snapshot.geometryVersion());
        putComponent(componentSnapshot, GraphicsBuiltinComponents.BOUNDS_VERSION, snapshot.boundsVersion());
        return componentSnapshot;
    }

    private void renderOrderedStages(List<KeyId> stageIds, String scopeLabel) {
        renderPacketQueue.executeStageRange(stageIds, this.renderStateManager, this.currentContext, scopeLabel);
    }

    private List<KeyId> collectStageIdsBetweenExclusiveInclusive(KeyId fromExclusive, KeyId toInclusive) {
        List<GraphicsStage> ordered = stages.getOrderedList();
        int toIndex = indexOfStage(ordered, toInclusive);
        if (toIndex < 0) {
            return List.of();
        }

        int fromIndex = indexOfStage(ordered, fromExclusive);
        int start = Math.max(fromIndex + 1, 0);
        List<KeyId> stageIds = new ArrayList<>();
        if (start > toIndex) {
            stageIds.add(toInclusive);
            return List.copyOf(stageIds);
        }

        for (int i = start; i <= toIndex; i++) {
            stageIds.add(ordered.get(i).getIdentifier());
        }
        return List.copyOf(stageIds);
    }

    private List<KeyId> collectStageIdsBeforeInclusive(KeyId stageId) {
        List<GraphicsStage> ordered = stages.getOrderedList();
        int index = indexOfStage(ordered, stageId);
        if (index < 0) {
            return List.of(stageId);
        }

        List<KeyId> stageIds = new ArrayList<>(index + 1);
        for (int i = 0; i <= index; i++) {
            stageIds.add(ordered.get(i).getIdentifier());
        }
        return List.copyOf(stageIds);
    }

    private List<KeyId> collectStageIdsAfterInclusive(KeyId stageId) {
        List<GraphicsStage> ordered = stages.getOrderedList();
        int index = indexOfStage(ordered, stageId);
        if (index < 0) {
            return List.of(stageId);
        }

        List<KeyId> stageIds = new ArrayList<>(ordered.size() - index);
        for (int i = index; i < ordered.size(); i++) {
            stageIds.add(ordered.get(i).getIdentifier());
        }
        return List.copyOf(stageIds);
    }

    private int indexOfStage(List<GraphicsStage> ordered, KeyId stageId) {
        GraphicsStage stage = idToStage.get(stageId);
        return stage != null ? ordered.indexOf(stage) : -1;
    }

    private StageFlowScene<C> createImmediateStageScene(PipelineType pipelineType) {
        GeometryResourceCoordinator resourceManager = getGeometryResourceCoordinator(pipelineType);
        if (pipelineType == PipelineType.RASTERIZATION || pipelineType == PipelineType.TRANSLUCENT) {
            return new RasterStageFlowScene<>(
                    IMMEDIATE_STAGE_ID,
                    pipelineType,
                    resourceManager,
                    () -> getPipelineDataStore(pipelineType, FrameDataDomain.SYNC_READ),
                    renderTraceRecorder);
        }
        if (pipelineType == PipelineType.COMPUTE) {
            return new ComputeStageFlowScene<>(pipelineType);
        }
        if (pipelineType == PipelineType.FUNCTION) {
            return new FunctionStageFlowScene<>(pipelineType);
        }
        throw new IllegalArgumentException("Unsupported immediate pipeline type: " + pipelineType);
    }

    private void clearInstalledPipelineResources() {
        for (GraphicsEntityId entityId : installedFunctionResources.values()) {
            destroyGraphicsEntity(entityId);
        }
        installedFunctionResources.clear();
        for (GraphicsEntityId entityId : installedDrawCallResources.values()) {
            destroyGraphicsEntity(entityId);
        }
        installedDrawCallResources.clear();
    }

    private void installFunctionResources() {
        Map<KeyId, GraphicsEntityBlueprint> resources = GraphicsResourceManager.getInstance().getResourcesOfType(ResourceTypes.FUNCTION);
        if (resources.isEmpty()) {
            return;
        }
        List<Map.Entry<KeyId, GraphicsEntityBlueprint>> orderedEntries = new ArrayList<>(resources.entrySet());
        orderedEntries.sort(Map.Entry.comparingByKey());
        for (Map.Entry<KeyId, GraphicsEntityBlueprint> entry : orderedEntries) {
            GraphicsEntityBlueprint blueprint = entry.getValue();
            if (blueprint == null || blueprint.isDisposed()) {
                continue;
            }
            GraphicsEntityId entityId = spawnGraphicsEntity(blueprint);
            installedFunctionResources.put(entry.getKey(), entityId);
        }
    }

    private void installDrawCallResources() {
        Map<KeyId, GraphicsEntityBlueprint> resources = GraphicsResourceManager.getInstance().getResourcesOfType(ResourceTypes.DRAW_CALL);
        if (resources.isEmpty()) {
            return;
        }
        List<Map.Entry<KeyId, GraphicsEntityBlueprint>> orderedEntries = new ArrayList<>(resources.entrySet());
        orderedEntries.sort(Map.Entry.comparingByKey());
        for (Map.Entry<KeyId, GraphicsEntityBlueprint> entry : orderedEntries) {
            GraphicsEntityBlueprint blueprint = entry.getValue();
            GraphicsBuiltinComponents.StageBindingComponent stageBinding =
                    blueprint != null ? blueprint.component(GraphicsBuiltinComponents.STAGE_BINDING) : null;
            if (blueprint == null || blueprint.isDisposed() || stageBinding == null || stageBinding.renderParameter() == null) {
                continue;
            }
            GraphicsEntityId entityId = spawnGraphicsEntity(blueprint);
            installedDrawCallResources.put(entry.getKey(), entityId);
        }
    }

    private long nextOrderHint(KeyId stageId) {
        long next = stageEntityOrderHints.getOrDefault(stageId, 0L);
        stageEntityOrderHints.put(stageId, next + 1L);
        return next;
    }

    private void registerSpawnedEntity(GraphicsEntityId entityId) {
        GraphicsBuiltinComponents.StageBindingComponent stageBinding =
                graphicsWorld.component(entityId, GraphicsBuiltinComponents.STAGE_BINDING);
        if (stageBinding != null) {
            stageMembershipIndex.register(entityId, stageBinding);
        }
        if (kernel != null && kernel.moduleRegistry().isInitialized()) {
            kernel.moduleRegistry().onEntitySpawned(entityId);
        }
    }

    private GraphicsEntityBlueprint withImmediateStageBinding(GraphicsEntityBlueprint blueprint) {
        GraphicsEntityBlueprint.Builder builder = GraphicsEntityBlueprint.builder();
        for (Map.Entry<rogo.sketch.core.graphics.ecs.GraphicsComponentType<?>, Object> entry : blueprint.components().entrySet()) {
            copyComponent(builder, entry.getKey(), entry.getValue());
        }
        GraphicsBuiltinComponents.StageBindingComponent existing = blueprint.component(GraphicsBuiltinComponents.STAGE_BINDING);
        if (existing != null) {
            builder.put(
                    GraphicsBuiltinComponents.STAGE_BINDING,
                    new GraphicsBuiltinComponents.StageBindingComponent(
                            IMMEDIATE_STAGE_ID,
                            existing.pipelineType(),
                            existing.renderParameter()));
        }
        return builder.build();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void copyComponent(
            GraphicsEntityBlueprint.Builder builder,
            rogo.sketch.core.graphics.ecs.GraphicsComponentType componentType,
            Object value) {
        builder.put(componentType, value);
    }

}

