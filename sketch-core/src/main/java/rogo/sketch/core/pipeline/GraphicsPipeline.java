package rogo.sketch.core.pipeline;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.packet.RenderPacket;
import rogo.sketch.core.extension.ExtensionHost;
import rogo.sketch.core.extension.PluginApiFacade;
import rogo.sketch.core.graphics.ecs.GraphicsBuiltinComponents;
import rogo.sketch.core.graphics.ecs.GraphicsEntityAssembler;
import rogo.sketch.core.graphics.ecs.GraphicsEntityBlueprint;
import rogo.sketch.core.graphics.ecs.GraphicsEntityId;
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
import rogo.sketch.core.pipeline.flow.ecs.StageMembershipIndex;
import rogo.sketch.core.pipeline.flow.v2.StageEntityView;
import rogo.sketch.core.pipeline.kernel.PipelineKernel;
import rogo.sketch.core.pipeline.module.metric.MetricSnapshot;
import rogo.sketch.core.pipeline.module.runtime.ModuleRuntimeHost;
import rogo.sketch.core.pipeline.submit.StageSubmitNode;
import rogo.sketch.core.pipeline.submit.StageWindow;
import rogo.sketch.core.resource.GraphicsResourceManager;
import rogo.sketch.core.util.KeyId;
import rogo.sketch.core.util.OrderedList;
import rogo.sketch.core.util.RenderTargetUtil;
import rogo.sketch.core.pipeline.data.FrameDataDomain;
import rogo.sketch.core.pipeline.data.GeometryFrameData;
import rogo.sketch.core.vertex.MeshResidencyPool;
import rogo.sketch.core.vertex.GeometryResourceCoordinator;
import rogo.sketch.core.backend.LogicalResourceRegistryBinder;

import java.util.*;

public class GraphicsPipeline<C extends RenderContext> {
    private static final KeyId IMMEDIATE_STAGE_ID = KeyId.of("sketch_render", "immediate");

    private final OrderedList<GraphicsStage> stages;
    private final Map<GraphicsStage, GraphicsBatchGroup<C>> passMap = new LinkedHashMap<>();
    private final Map<KeyId, GraphicsStage> idToStage = new LinkedHashMap<>();
    private final RenderStateManager renderStateManager;
    private final RenderPacketQueue<C> renderPacketQueue;
    private final Map<KeyId, PipelineType> pipelineTypes = new HashMap<>();
    private final PipelineDataHub pipelineDataHub = new PipelineDataHub();
    private final RenderTraceConfig renderTraceConfig = new RenderTraceConfig();
    private final RenderTraceRecorder renderTraceRecorder = new RenderTraceRecorder(renderTraceConfig);
    private final PipelineResourceBridge pipelineResourceBridge = new PipelineResourceBridge();
    private final StageSubmitRegistry stageSubmitRegistry = new StageSubmitRegistry();
    private final GraphicsEntityManager graphicsEntityManager = new GraphicsEntityManager();
    private final ExtensionHost extensionHost = new ExtensionHost(this);
    private final StageMembershipIndex stageMembershipIndex = new StageMembershipIndex();
    private final Map<KeyId, Long> stageEntityOrderHints = new LinkedHashMap<>();

    private final PipelineConfig config;
    private final GraphicsResourceManager resourceManager;
    private final ImmediateRenderer<C> immediateRenderer;
    private C currentContext;
    private int currentFrameTick = 0;
    private int currentLogicTick = 0;
    private int currentLogicTickInSeconds = 0;
    private boolean[] nextTick = new boolean[20];
    private boolean initialized = false;
    private boolean initializedStaticGraphics = false;

    // New architecture: kernel
    private PipelineKernel<C> kernel;

    public GraphicsPipeline(PipelineConfig config, GraphicsResourceManager resourceManager, C defaultContext) {
        this.config = config;
        this.resourceManager = Objects.requireNonNull(resourceManager, "resourceManager");
        this.renderStateManager = new RenderStateManager(resourceManager);
        bindResourceManagerToBackend();
        this.stages = new OrderedList<>(config.isThrowOnSortFail());
        this.currentContext = defaultContext;
        initPipelineData();
        this.renderPacketQueue = new RenderPacketQueue<>(this);
        this.immediateRenderer = new ImmediateRenderer<>(
                this,
                IMMEDIATE_STAGE_ID,
                graphicsEntityManager.graphicsWorld(),
                graphicsEntityManager.graphicsEntityAssembler(),
                renderPacketQueue,
                renderStateManager,
                renderTraceRecorder);
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
        pipelineDataHub.initializePipeline(pipelineType);
    }

    public PipelineConfig getConfig() {
        return config;
    }

    public GraphicsResourceManager resourceManager() {
        return resourceManager;
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
            stageSubmitRegistry.onStageRegistered(stage.getIdentifier());
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
        immediateRenderer.renderImmediate(blueprint, contextOverride, currentContext, graphicsEntityManager::snapshotEntity);
    }

    public List<RenderPacket> buildImmediatePackets(GraphicsEntityBlueprint blueprint) {
        return buildImmediatePackets(blueprint, currentContext);
    }

    public List<RenderPacket> buildImmediatePackets(GraphicsEntityBlueprint blueprint, @Nullable C contextOverride) {
        return immediateRenderer.buildImmediatePackets(blueprint, contextOverride, currentContext, graphicsEntityManager::snapshotEntity);
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
        RenderTargetUtil.resizeRT(resourceManager, windowWidth, windowHeight);
    }

    /**
     * Initialize the pipeline and post initialization events
     */
    public void initPipeline() {
        if (initialized) {
            return;
        }

        bindResourceManagerToBackend();
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
        pipelineResourceBridge.installPipelineResources(resourceManager, this::spawnGraphicsEntity);
    }

    public void shutdown() {
        renderTraceRecorder.flushAll();
        leaveWorld();
        if (kernel != null) {
            kernel.cleanup();
            kernel = null;
        }
        stageSubmitRegistry.clear();
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
        return pipelineDataHub.pipelineDataStore(pipelineType, domain);
    }

    public GeometryResourceCoordinator getGeometryResourceCoordinator(PipelineType pipelineType) {
        return pipelineDataHub.geometryResourceCoordinator(pipelineType);
    }

    public MeshResidencyPool getMeshResidencyPool(PipelineType pipelineType) {
        return pipelineDataHub.meshResidencyPool(pipelineType);
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
        return pipelineDataHub.allPipelineDataStores(domain);
    }

    public Collection<FrameDataStore> getAllFrameDataStores() {
        return pipelineDataHub.allFrameDataStores();
    }

    public void swapFrameDataStores() {
        pipelineDataHub.swapFrameDataStores();
    }


    public FrameDataStore getFrameDataStore(PipelineType pipelineType) {
        return pipelineDataHub.frameDataStore(pipelineType);
    }

    public void materializePendingGeometryBindings() {
        GraphicsDriver.renderDevice().materializePendingGeometryResources(this);
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
        stageSubmitRegistry.register(ownerId, node);
    }

    public void unregisterStageSubmitNodes(String ownerId) {
        stageSubmitRegistry.unregisterOwned(ownerId);
    }

    public List<StageSubmitNode> stageSubmitNodes(KeyId stageId, StageWindow window) {
        return stageSubmitRegistry.nodes(stageId, window);
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
        return graphicsEntityManager.graphicsWorld();
    }

    public GraphicsEntityAssembler graphicsEntityAssembler() {
        return graphicsEntityManager.graphicsEntityAssembler();
    }

    public GraphicsEntityManager graphicsEntityManager() {
        return graphicsEntityManager;
    }

    public ExtensionHost extensionHost() {
        return extensionHost;
    }

    public PluginApiFacade pluginApiFacade() {
        return extensionHost.pluginApiFacade();
    }

    public GraphicsEntityId spawnGraphicsEntity(GraphicsEntityBlueprint blueprint) {
        GraphicsEntityId entityId = graphicsEntityManager.spawn(blueprint);
        registerSpawnedEntity(entityId);
        return entityId;
    }

    public void destroyGraphicsEntity(GraphicsEntityId entityId) {
        if (kernel != null && kernel.moduleRegistry().isInitialized()) {
            kernel.moduleRegistry().onEntityDestroyed(entityId);
        }
        stageMembershipIndex.unregister(entityId);
        graphicsEntityManager.destroy(entityId);
    }

    public StageMembershipIndex stageMembershipIndex() {
        return stageMembershipIndex;
    }

    public StageEntityView.Entry snapshotEntity(GraphicsEntityId entityId) {
        return graphicsEntityManager.snapshotEntity(entityId);
    }

    public StageEntityView.Entry snapshotEntityIfPresent(GraphicsEntityId entityId) {
        return graphicsEntityManager.snapshotEntityIfPresent(entityId);
    }

    public List<StageEntityView.Entry> snapshotEntitiesIfPresent(List<GraphicsEntityId> entityIds) {
        return graphicsEntityManager.snapshotEntitiesIfPresent(entityIds);
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

    private void clearInstalledPipelineResources() {
        pipelineResourceBridge.clearInstalledPipelineResources(this::destroyGraphicsEntity);
    }

    private void bindResourceManagerToBackend() {
        if (GraphicsDriver.resourceAllocator() instanceof LogicalResourceRegistryBinder binder) {
            binder.bindLogicalResourceRegistry(resourceManager);
        }
    }

    private long nextOrderHint(KeyId stageId) {
        long next = stageEntityOrderHints.getOrDefault(stageId, 0L);
        stageEntityOrderHints.put(stageId, next + 1L);
        return next;
    }

    private void registerSpawnedEntity(GraphicsEntityId entityId) {
        GraphicsBuiltinComponents.StageBindingComponent stageBinding =
                graphicsEntityManager.graphicsWorld().component(entityId, GraphicsBuiltinComponents.STAGE_BINDING);
        if (stageBinding != null) {
            stageMembershipIndex.register(entityId, stageBinding);
        }
        if (kernel != null && kernel.moduleRegistry().isInitialized()) {
            kernel.moduleRegistry().onEntitySpawned(entityId);
        }
    }

}

