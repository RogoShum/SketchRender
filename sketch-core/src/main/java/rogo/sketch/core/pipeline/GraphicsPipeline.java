package rogo.sketch.core.pipeline;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.packet.GeometryHandleKey;
import rogo.sketch.core.packet.PipelineStateKey;
import rogo.sketch.core.packet.RenderPacket;
import rogo.sketch.core.api.graphics.Graphics;
import rogo.sketch.core.driver.GraphicsDriver;
import rogo.sketch.core.packet.RenderPacketQueue;
import rogo.sketch.core.event.GraphicsPipelineInitEvent;
import rogo.sketch.core.event.RegisterStaticGraphicsEvent;
import rogo.sketch.core.event.bridge.EventBusBridge;
import rogo.sketch.core.pipeline.container.GraphicsContainer;
import rogo.sketch.core.pipeline.data.*;
import rogo.sketch.core.pipeline.module.diagnostic.DiagnosticEntry;
import rogo.sketch.core.pipeline.module.diagnostic.RenderTraceConfig;
import rogo.sketch.core.pipeline.module.diagnostic.RenderTraceRecorder;
import rogo.sketch.core.pipeline.module.diagnostic.SketchDiagnostics;
import rogo.sketch.core.pipeline.flow.RenderFlowType;
import rogo.sketch.core.pipeline.flow.RenderPostProcessors;
import rogo.sketch.core.pipeline.flow.container.DefaultBatchContainers;
import rogo.sketch.core.pipeline.flow.impl.RasterizationPostProcessor;
import rogo.sketch.core.pipeline.flow.v2.ComputeStageFlowScene;
import rogo.sketch.core.pipeline.flow.v2.FunctionStageFlowScene;
import rogo.sketch.core.pipeline.flow.v2.RasterStageFlowScene;
import rogo.sketch.core.pipeline.flow.v2.StageFlowScene;
import rogo.sketch.core.pipeline.kernel.PipelineKernel;
import rogo.sketch.core.pipeline.module.metric.MetricSnapshot;
import rogo.sketch.core.pipeline.module.PipelineModule;
import rogo.sketch.core.pipeline.module.runtime.ModuleRuntimeHost;
import rogo.sketch.core.pipeline.parmeter.ComputeParameter;
import rogo.sketch.core.pipeline.parmeter.FunctionParameter;
import rogo.sketch.core.pipeline.parmeter.RenderParameter;
import rogo.sketch.core.pool.InstancePoolManager;
import rogo.sketch.core.resource.GraphicsResourceManager;
import rogo.sketch.core.resource.ResourceTypes;
import rogo.sketch.core.util.KeyId;
import rogo.sketch.core.util.OrderedList;
import rogo.sketch.core.util.RenderTargetUtil;
import rogo.sketch.core.pipeline.data.FrameDataDomain;
import rogo.sketch.core.pipeline.data.GeometryFrameData;
import rogo.sketch.core.vertex.GeometryResourceCoordinator;
import rogo.sketch.core.instance.DrawCallGraphics;
import rogo.sketch.core.instance.FunctionGraphics;

import java.util.*;
import java.util.function.Supplier;

public class GraphicsPipeline<C extends RenderContext> {
    private static final KeyId IMMEDIATE_STAGE_ID = KeyId.of("sketch_render", "immediate");

    private final OrderedList<GraphicsStage> stages;
    private final Map<GraphicsStage, GraphicsBatchGroup<C>> passMap = new LinkedHashMap<>();
    private final Map<KeyId, GraphicsStage> idToStage = new LinkedHashMap<>();
    private final Set<Graphics> auxiliaryGraphics = Collections.newSetFromMap(new IdentityHashMap<>());
    private final RenderStateManager renderStateManager = new RenderStateManager();
    private final InstancePoolManager poolManager = InstancePoolManager.getInstance();
    private final RenderPacketQueue<C> renderPacketQueue;
    private final Map<KeyId, PipelineType> pipelineTypes = new HashMap<>();
    private final Map<PipelineType, GeometryResourceCoordinator> resourceManagers = new LinkedHashMap<>();
    private final Map<PipelineType, FrameDataStore> frameDataStores = new LinkedHashMap<>();
    private final RenderTraceConfig renderTraceConfig = new RenderTraceConfig();
    private final RenderTraceRecorder renderTraceRecorder = new RenderTraceRecorder(renderTraceConfig);
    private final Map<KeyId, FunctionGraphics> installedFunctionResources = new LinkedHashMap<>();
    private final Map<KeyId, DrawCallGraphics> installedDrawCallResources = new LinkedHashMap<>();

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
        GeometryResourceCoordinator manager = new GeometryResourceCoordinator();
        PipelineDataStore renderStore = new PipelineDataStore();
        PipelineDataStore asyncStore = new PipelineDataStore();

        // Register standard pipeline data
        registerDefaultPipelineData(renderStore);
        registerDefaultPipelineData(asyncStore);

        FrameDataStore frameStore = new FrameDataStore(renderStore, asyncStore);

        resourceManagers.put(pipelineType, manager);
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

    public void addCompute(KeyId stageId, Graphics graph) {
        addGraphInstance(stageId, graph, ComputeParameter.COMPUTE_PARAMETER, PipelineType.COMPUTE, DefaultBatchContainers.PRIORITY);
    }

    public void addFunction(KeyId stageId, Graphics graph) {
        addGraphInstance(stageId, graph, FunctionParameter.FUNCTION_PARAMETER, PipelineType.FUNCTION, DefaultBatchContainers.PRIORITY);
    }

    /**
     * Attach auxiliary graphics that should participate in module/session
     * lifecycle without entering any stage flow or packet compilation path.
     */
    public void attachAuxiliaryGraphics(Graphics graphics) {
        if (graphics == null) {
            return;
        }
        auxiliaryGraphics.add(graphics);
        if (kernel != null && kernel.moduleRegistry().isInitialized()) {
            kernel.moduleRegistry().onGraphicsAdded(graphics, null, null);
        }
    }

    public void renderImmediate(Graphics instance, RenderParameter renderParameter) {
        if (instance == null || renderParameter == null || renderParameter.isInvalid()) {
            return;
        }

        C context = currentContext;
        if (context == null) {
            return;
        }

        PipelineType pipelineType = pipelineTypeFor(renderParameter.getFlowType());
        StageFlowScene<C> immediateScene = createImmediateStageScene(pipelineType);
        RenderPostProcessors postProcessors = new RenderPostProcessors();
        if (pipelineType == PipelineType.RASTERIZATION || pipelineType == PipelineType.TRANSLUCENT) {
            postProcessors.register(RenderFlowType.RASTERIZATION, new RasterizationPostProcessor());
        }

        try {
            immediateScene.registerGraphicsInstance(instance, renderParameter, DefaultBatchContainers.DEFAULT, null);
            immediateScene.prepareForFrame();

            Map<PipelineStateKey, List<RenderPacket>> packets = immediateScene.createRenderPackets(
                    IMMEDIATE_STAGE_ID,
                    pipelineType.getDefaultFlowType(),
                    postProcessors,
                    context);
            if (packets.isEmpty()) {
                return;
            }

            GraphicsDriver.runtime().installImmediateGeometryBindings(this, pipelineType, postProcessors);
            postProcessors.executeAllExcept(RenderFlowType.RASTERIZATION);

            for (List<RenderPacket> statePackets : packets.values()) {
                for (RenderPacket packet : statePackets) {
                    renderPacketQueue.executeImmediate(packet, renderStateManager, context);
                }
            }
        } finally {
            immediateScene.clear();
        }
    }

    /**
     * Add a GraphInstance to a specific stage with default rasterization pipeline.
     */
    public void addGraphInstance(KeyId stageId, Graphics graph, RenderParameter renderParameter) {
        addGraphInstance(stageId, graph, renderParameter, PipelineType.RASTERIZATION);
    }

    /**
     * Add a GraphInstance to a specific stage with specified pipeline type.
     */
    public void addGraphInstance(KeyId stageId, Graphics graph, RenderParameter renderParameter, PipelineType pipelineType) {
        addGraphInstance(stageId, graph, renderParameter, pipelineType, DefaultBatchContainers.DEFAULT);
    }

    /**
     * Add a GraphInstance to a specific stage with pipeline type and container
     * type.
     */
    public void addGraphInstance(KeyId stageId, Graphics graph, RenderParameter renderParameter, PipelineType pipelineType, KeyId containerType) {
        addGraphInstance(stageId, graph, renderParameter, pipelineType, containerType, null);
    }

    public void addGraphInstance(
            KeyId stageId,
            Graphics graph,
            RenderParameter renderParameter,
            PipelineType pipelineType,
            KeyId containerType,
            @Nullable
            Supplier<? extends GraphicsContainer<? extends RenderContext>> containerSupplier) {
        GraphicsStage stage = idToStage.get(stageId);
        if (stage != null) {
            passMap.get(stage).addGraphInstance(
                    graph,
                    renderParameter,
                    pipelineTypes.get(pipelineType.getIdentifier()),
                    containerType,
                    containerSupplier);

            if (kernel != null && kernel.moduleRegistry().isInitialized()) {
                kernel.moduleRegistry().onGraphicsAdded(graph, renderParameter, containerType);
            }
            return;
        }

        SketchDiagnostics.get().warn(
                "graphics-pipeline",
                "Failed to register graphics "
                        + (graph != null ? graph.getIdentifier() : "<null>")
                        + " because stage " + stageId + " is not registered");
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

    /**
     * Add a GraphInstance from the instance pool to a specific stage
     */
    public void addPooledGraphInstance(KeyId stageId, Class<? extends Graphics> instanceType,
                                       RenderParameter renderParameter) {
        if (!poolManager.isPoolingEnabled()) {
            throw new IllegalStateException("Instance pooling is not enabled");
        }

        Graphics instance = poolManager.borrowInstance(instanceType);
        addGraphInstance(stageId, instance, renderParameter);
    }

    /**
     * Add a GraphInstance from a named pool to a specific stage
     */
    public void addNamedPoolGraphInstance(KeyId stageId, KeyId poolName, RenderParameter renderParameter) {
        if (!poolManager.isPoolingEnabled()) {
            throw new IllegalStateException("Instance pooling is not enabled");
        }

        Graphics instance = poolManager.borrowInstance(poolName);
        addGraphInstance(stageId, instance, renderParameter);
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
        initialized = false;
        initializedStaticGraphics = false;
        currentLogicTick = 0;
        currentFrameTick = 0;
    }

    /**
     * Get the instance pool manager
     */
    public InstancePoolManager poolManager() {
        return poolManager;
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

    @Nullable
    @SuppressWarnings("unchecked")
    public <M> M getModuleByName(String name) {
        if (kernel.moduleRegistry().containsModule(name)) {
            return (M) kernel.moduleRegistry().moduleByName(name);
        }

        return null;
    }

    public void notifyGraphicsRemoved(Graphics graphics) {
        if (graphics == null) {
            return;
        }
        if (kernel != null && kernel.moduleRegistry().isInitialized()) {
            kernel.moduleRegistry().onGraphicsRemoved(graphics);
        }
    }

    public void removeGraphInstance(Graphics graphics) {
        if (graphics == null) {
            return;
        }
        boolean auxiliaryRemoved = auxiliaryGraphics.remove(graphics);
        for (GraphicsBatchGroup<C> group : passMap.values()) {
            group.removeGraphicsInstance(graphics);
        }
        notifyGraphicsRemoved(graphics);
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

    private PipelineType pipelineTypeFor(RenderFlowType flowType) {
        if (RenderFlowType.COMPUTE.equals(flowType)) {
            return PipelineType.COMPUTE;
        }
        if (RenderFlowType.FUNCTION.equals(flowType)) {
            return PipelineType.FUNCTION;
        }
        return PipelineType.RASTERIZATION;
    }

    private void clearInstalledPipelineResources() {
        for (FunctionGraphics graphics : installedFunctionResources.values()) {
            removeGraphInstance(graphics);
        }
        installedFunctionResources.clear();
        for (DrawCallGraphics graphics : installedDrawCallResources.values()) {
            removeGraphInstance(graphics);
        }
        installedDrawCallResources.clear();
    }

    private void installFunctionResources() {
        Map<KeyId, FunctionGraphics> resources = GraphicsResourceManager.getInstance().getResourcesOfType(ResourceTypes.FUNCTION);
        if (resources.isEmpty()) {
            return;
        }
        List<Map.Entry<KeyId, FunctionGraphics>> orderedEntries = new ArrayList<>(resources.entrySet());
        orderedEntries.sort(Map.Entry.comparingByKey());
        for (Map.Entry<KeyId, FunctionGraphics> entry : orderedEntries) {
            FunctionGraphics graphics = entry.getValue();
            if (graphics == null || graphics.stageId() == null) {
                continue;
            }
            addFunction(graphics.stageId(), graphics);
            installedFunctionResources.put(entry.getKey(), graphics);
        }
    }

    private void installDrawCallResources() {
        Map<KeyId, DrawCallGraphics> resources = GraphicsResourceManager.getInstance().getResourcesOfType(ResourceTypes.DRAW_CALL);
        if (resources.isEmpty()) {
            return;
        }
        List<Map.Entry<KeyId, DrawCallGraphics>> orderedEntries = new ArrayList<>(resources.entrySet());
        orderedEntries.sort(Map.Entry.comparingByKey());
        for (Map.Entry<KeyId, DrawCallGraphics> entry : orderedEntries) {
            DrawCallGraphics graphics = entry.getValue();
            if (graphics == null || graphics.stageId() == null || graphics.renderParameter() == null) {
                continue;
            }
            addGraphInstance(graphics.stageId(), graphics, graphics.renderParameter(), PipelineType.RASTERIZATION);
            installedDrawCallResources.put(entry.getKey(), graphics);
        }
    }

}

