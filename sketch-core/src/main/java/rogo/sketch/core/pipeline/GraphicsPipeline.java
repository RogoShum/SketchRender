package rogo.sketch.core.pipeline;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.RenderHelper;
import rogo.sketch.core.api.graphics.Graphics;
import rogo.sketch.core.command.RenderCommandQueue;
import rogo.sketch.core.driver.GraphicsDriver;
import rogo.sketch.core.event.GraphicsPipelineInitEvent;
import rogo.sketch.core.event.RegisterStaticGraphicsEvent;
import rogo.sketch.core.event.bridge.EventBusBridge;
import rogo.sketch.core.pipeline.container.GraphicsContainer;
import rogo.sketch.core.pipeline.data.*;
import rogo.sketch.core.pipeline.module.diagnostic.DiagnosticEntry;
import rogo.sketch.core.pipeline.module.diagnostic.SketchDiagnostics;
import rogo.sketch.core.pipeline.flow.container.DefaultBatchContainers;
import rogo.sketch.core.pipeline.kernel.PipelineKernel;
import rogo.sketch.core.pipeline.module.metric.MetricSnapshot;
import rogo.sketch.core.pipeline.module.PipelineModule;
import rogo.sketch.core.pipeline.module.runtime.ModuleRuntimeHost;
import rogo.sketch.core.pipeline.parmeter.ComputeParameter;
import rogo.sketch.core.pipeline.parmeter.FunctionParameter;
import rogo.sketch.core.pipeline.parmeter.RenderParameter;
import rogo.sketch.core.pool.InstancePoolManager;
import rogo.sketch.core.util.KeyId;
import rogo.sketch.core.util.OrderedList;
import rogo.sketch.core.util.RenderTargetUtil;
import rogo.sketch.core.vertex.VertexResourceManager;

import java.util.*;
import java.util.function.Supplier;

public class GraphicsPipeline<C extends RenderContext> {
    private final OrderedList<GraphicsStage> stages;
    private final Map<GraphicsStage, GraphicsBatchGroup<C>> passMap = new LinkedHashMap<>();
    private final Map<KeyId, GraphicsStage> idToStage = new LinkedHashMap<>();
    private final RenderStateManager renderStateManager = new RenderStateManager();
    private final InstancePoolManager poolManager = InstancePoolManager.getInstance();
    private final RenderCommandQueue<C> renderCommandQueue;
    private final RenderHelper renderHelper;
    private final Map<KeyId, PipelineType> pipelineTypes = new HashMap<>();
    private final Map<PipelineType, VertexResourceManager> resourceManagers = new LinkedHashMap<>();
    private final Map<PipelineType, PipelineDataStore> pipelineDataStores = new LinkedHashMap<>();
    private final Map<PipelineType, FrameDataStore> frameDataStores = new LinkedHashMap<>();

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
        this.renderCommandQueue = new RenderCommandQueue<>(this);
        this.renderHelper = new RenderHelper(this);
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
        VertexResourceManager manager = new VertexResourceManager();
        PipelineDataStore renderStore = new PipelineDataStore();
        PipelineDataStore asyncStore = new PipelineDataStore();

        // Register standard pipeline data
        registerDefaultPipelineData(renderStore);
        registerDefaultPipelineData(asyncStore);

        FrameDataStore frameStore = new FrameDataStore(renderStore, asyncStore);
        PipelineDataStore threadAwareStore = new ThreadAwarePipelineDataStore(frameStore);

        resourceManagers.put(pipelineType, manager);
        frameDataStores.put(pipelineType, frameStore);
        pipelineDataStores.put(pipelineType, threadAwareStore);
    }

    private void registerDefaultPipelineData(PipelineDataStore dataStore) {
        dataStore.register(KeyId.of("indirect_buffers"), new IndirectBufferData());
        dataStore.register(KeyId.of("instanced_offsets"), new InstancedOffsetData());
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
        }
    }

    /**
     * Render stages between 'fromStage' (exclusive) and 'toStage' (exclusive).
     * Only renders the stages strictly between fromId and toId.
     */
    public void renderStagesBetween(KeyId fromId, KeyId toId) {
        List<GraphicsStage> ordered = stages.getOrderedList();
        GraphicsStage fromStage = idToStage.get(fromId);
        GraphicsStage toStage = idToStage.get(toId);
        int fromIdx = ordered.indexOf(fromStage);
        int toIdx = ordered.indexOf(toStage);
        for (int i = fromIdx + 1; i < toIdx; i++) {
            renderCommandQueue.executeStage(ordered.get(i).getIdentifier(), this.renderStateManager,
                    this.currentContext);
        }

        renderStage(toId);
    }

    /**
     * Render a single stage.
     */
    public void renderStage(KeyId id) {
        renderCommandQueue.executeStage(id, this.renderStateManager, this.currentContext);
    }

    public void renderStagesBefore(KeyId id) {
        List<GraphicsStage> ordered = stages.getOrderedList();
        GraphicsStage stage = idToStage.get(id);
        int idx = ordered.indexOf(stage);
        for (int i = 0; i < idx; i++) {
            renderCommandQueue.executeStage(ordered.get(i).getIdentifier(), this.renderStateManager,
                    this.currentContext);
        }

        renderStage(id);
    }

    public void renderStagesAfter(KeyId id) {
        renderStage(id);

        List<GraphicsStage> ordered = stages.getOrderedList();
        GraphicsStage stage = idToStage.get(id);
        int idx = ordered.indexOf(stage);
        for (int i = idx + 1; i < ordered.size(); i++) {
            renderCommandQueue.executeStage(ordered.get(i).getIdentifier(), this.renderStateManager,
                    this.currentContext);
        }
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
        if (kernel != null && kernel.moduleRegistry().isInitialized()) {
            kernel.moduleRegistry().onResourceReload();
        }
    }

    public void shutdown() {
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

    public RenderHelper renderHelper() {
        return renderHelper;
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
     * Get the render command queue (for new pipeline)
     */
    public RenderCommandQueue<C> getRenderCommandQueue() {
        return renderCommandQueue;
    }

    public PipelineDataStore getPipelineDataStore(PipelineType pipelineType) {
        return pipelineDataStores.computeIfAbsent(pipelineType, pt -> {
            initializePipeline(pt);
            return pipelineDataStores.get(pt);
        });
    }

    public VertexResourceManager getVertexResourceManager(PipelineType pipelineType) {
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
    public Collection<PipelineDataStore> getAllPipelineDataStores() {
        return pipelineDataStores.values();
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

    public void materializePendingVertexResources() {
        for (VertexResourceManager manager : resourceManagers.values()) {
            manager.materializePending();
        }
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
        for (GraphicsBatchGroup<C> group : passMap.values()) {
            group.removeGraphicsInstance(graphics);
        }
        notifyGraphicsRemoved(graphics);
    }

    private static final class ThreadAwarePipelineDataStore extends PipelineDataStore {
        private final FrameDataStore frameDataStore;

        private ThreadAwarePipelineDataStore(FrameDataStore frameDataStore) {
            this.frameDataStore = frameDataStore;
        }

        private PipelineDataStore activeStore() {
            return GraphicsDriver.getCurrentAPI().isMainThread() ? frameDataStore.readBuffer() : frameDataStore.writeBuffer();
        }

        @Override
        public void register(KeyId key, RenderPipelineData data) {
            // Keep both buffers schema-consistent.
            frameDataStore.readBuffer().register(key, data);
            frameDataStore.writeBuffer().register(key, duplicateForWriteBuffer(data));
        }

        @Override
        public <T extends RenderPipelineData> T get(KeyId key) {
            return activeStore().get(key);
        }

        @Override
        public void reset() {
            frameDataStore.resetAll();
        }

        @Override
        public java.util.Collection<RenderPipelineData> getAll() {
            return activeStore().getAll();
        }

        private RenderPipelineData duplicateForWriteBuffer(
                RenderPipelineData data) {
            if (data == null) {
                return null;
            }
            try {
                java.lang.reflect.Constructor<?> ctor = data.getClass().getDeclaredConstructor();
                ctor.setAccessible(true);
                return (RenderPipelineData) ctor.newInstance();
            } catch (Exception ignored) {
                // Fallback: share instance if no default constructor available.
                return data;
            }
        }
    }
}