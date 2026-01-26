package rogo.sketch.core.pipeline;

import rogo.sketch.core.api.graphics.Graphics;
import rogo.sketch.core.command.RenderCommand;
import rogo.sketch.core.command.RenderCommandQueue;
import rogo.sketch.core.event.GraphicsPipelineInitEvent;
import rogo.sketch.core.event.RegisterStaticGraphicsEvent;
import rogo.sketch.core.event.bridge.EventBusBridge;
import rogo.sketch.core.pipeline.async.AsyncRenderManager;
import rogo.sketch.core.pipeline.data.IndirectBufferData;
import rogo.sketch.core.pipeline.data.InstancedOffsetData;
import rogo.sketch.core.pipeline.data.PipelineDataStore;
import rogo.sketch.core.pipeline.flow.RenderFlowRegistry;
import rogo.sketch.core.pipeline.flow.RenderFlowStrategy;
import rogo.sketch.core.pipeline.flow.RenderPostProcessor;
import rogo.sketch.core.pipeline.flow.RenderPostProcessors;
import rogo.sketch.core.pipeline.parmeter.ComputeParameter;
import rogo.sketch.core.pipeline.parmeter.FunctionParameter;
import rogo.sketch.core.pipeline.parmeter.RenderParameter;
import rogo.sketch.core.pool.InstancePoolManager;
import rogo.sketch.core.util.KeyId;
import rogo.sketch.core.util.OrderedList;
import rogo.sketch.core.util.RenderTargetUtil;
import rogo.sketch.core.vertex.VertexResourceManager;

import java.util.*;

public class GraphicsPipeline<C extends RenderContext> {
    private final OrderedList<GraphicsStage> stages;
    private final Map<GraphicsStage, GraphicsBatchGroup<C>> passMap = new LinkedHashMap<>();
    private final Map<KeyId, GraphicsStage> idToStage = new LinkedHashMap<>();
    private final RenderStateManager renderStateManager = new RenderStateManager();
    private final InstancePoolManager poolManager = InstancePoolManager.getInstance();
    private final AsyncRenderManager asyncManager = AsyncRenderManager.getInstance();
    private final RenderCommandQueue<C> renderCommandQueue;
    private final Map<KeyId, PipelineType> pipelineTypes = new HashMap<>();
    private final Map<PipelineType, VertexResourceManager> resourceManagers = new LinkedHashMap<>();
    private final Map<PipelineType, PipelineDataStore> pipelineDataStores = new LinkedHashMap<>();

    private final PipelineConfig config;
    private C currentContext;
    private int currentFrameTick = 0;
    private boolean initialized = false;
    private boolean initializedStaticGraphics = false;

    public GraphicsPipeline(PipelineConfig config, C defaultContext) {
        this.config = config;
        this.stages = new OrderedList<>(config.isThrowOnSortFail());
        this.currentContext = defaultContext;
        initPipelineData();
        this.renderCommandQueue = new RenderCommandQueue<>(this);
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
        PipelineDataStore dataStore = new PipelineDataStore();

        // Register standard pipeline data
        dataStore.register(KeyId.of("indirect_buffers"), new IndirectBufferData());
        dataStore.register(KeyId.of("instanced_offsets"), new InstancedOffsetData());

        resourceManagers.put(pipelineType, manager);
        pipelineDataStores.put(pipelineType, dataStore);
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
        addGraphInstance(stageId, graph, ComputeParameter.COMPUTE_PARAMETER, PipelineType.COMPUTE, GraphicsBatch.PRIORITY_CONTAINER);
    }

    public void addFunction(KeyId stageId, Graphics graph) {
        addGraphInstance(stageId, graph, FunctionParameter.FUNCTION_PARAMETER, PipelineType.FUNCTION, GraphicsBatch.PRIORITY_CONTAINER);
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
        addGraphInstance(stageId, graph, renderParameter, pipelineType, GraphicsBatch.DEFAULT_CONTAINER);
    }

    /**
     * Add a GraphInstance to a specific stage with pipeline type and container
     * type.
     */
    public void addGraphInstance(KeyId stageId, Graphics graph, RenderParameter renderParameter, PipelineType pipelineType, KeyId containerType) {
        GraphicsStage stage = idToStage.get(stageId);
        if (stage != null) {
            passMap.get(stage).addGraphInstance(graph, renderParameter, pipelineTypes.get(pipelineType.getIdentifier()), containerType);
        }
    }

    public void computeAllRenderCommand() {
        try {
            // Reset all pipeline data stores
            for (PipelineDataStore dataStore : pipelineDataStores.values()) {
                dataStore.reset();
            }

            RenderPostProcessors postProcessors = new RenderPostProcessors();
            for (RenderFlowStrategy strategy : RenderFlowRegistry.getInstance().getAllStrategies()) {
                RenderPostProcessor processor = strategy.createPostProcessor();
                if (processor != null) {
                    postProcessors.register(strategy.getFlowType(), processor);
                }
            }

            // Create render commands for all pipeline types
            Map<PipelineType, Map<RenderSetting, List<RenderCommand>>> allRenderCommands = createRenderCommandsByPipeline(
                    postProcessors);

            renderCommandQueue.clear();
            // Add commands for each pipeline type
            for (Map.Entry<PipelineType, Map<RenderSetting, List<RenderCommand>>> entry : allRenderCommands
                    .entrySet()) {
                renderCommandQueue.addCommands(entry.getKey(), entry.getValue());
            }

            postProcessors.executeAll();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Create render commands from all stages grouped by pipeline type
     */
    private Map<PipelineType, Map<RenderSetting, List<RenderCommand>>> createRenderCommandsByPipeline(
            RenderPostProcessors postProcessors) {
        Map<PipelineType, Map<RenderSetting, List<RenderCommand>>> allCommands = new LinkedHashMap<>();

        for (GraphicsStage stage : stages.getOrderedList()) {
            GraphicsBatchGroup<C> batchGroup = passMap.get(stage);
            if (batchGroup != null) {
                Map<PipelineType, Map<RenderSetting, List<RenderCommand>>> stageCommands = batchGroup
                        .createAllRenderCommands(currentContext, postProcessors);

                // Merge into allCommands by pipeline type
                for (Map.Entry<PipelineType, Map<RenderSetting, List<RenderCommand>>> pipelineEntry : stageCommands
                        .entrySet()) {
                    PipelineType pipelineType = pipelineEntry.getKey();
                    Map<RenderSetting, List<RenderCommand>> commands = pipelineEntry.getValue();
                    Map<RenderSetting, List<RenderCommand>> pipelineCommands = allCommands.computeIfAbsent(pipelineType,
                            k -> new LinkedHashMap<>());

                    for (Map.Entry<RenderSetting, List<RenderCommand>> entry : commands.entrySet()) {
                        pipelineCommands.computeIfAbsent(entry.getKey(), k -> new ArrayList<>())
                                .addAll(entry.getValue());
                    }
                }
            }
        }

        return allCommands;
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
        this.currentFrameTick++;
        this.resizeRenderTargets(context.windowWidth, context.windowHeight);
    }

    protected void resizeRenderTargets(int windowWidth, int windowHeight) {
        RenderTargetUtil.resizeRT(windowWidth, windowHeight);
    }

    /**
     * Initialize the pipeline and post initialization events
     */
    public void initialize() {
        if (initialized) {
            return;
        }

        EventBusBridge.post(new GraphicsPipelineInitEvent(this, GraphicsPipelineInitEvent.InitPhase.EARLY));
        EventBusBridge.post(new GraphicsPipelineInitEvent(this, GraphicsPipelineInitEvent.InitPhase.NORMAL));
        EventBusBridge.post(new GraphicsPipelineInitEvent(this, GraphicsPipelineInitEvent.InitPhase.LATE));

        initialized = true;
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

    /**
     * Tick all stages with async support
     */
    public void tickAllStages() {
        for (GraphicsStage stage : stages.getOrderedList()) {
            GraphicsBatchGroup<C> group = passMap.get(stage);
            if (group != null) {
                group.tick(currentContext);
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

    /**
     * Get the instance pool manager
     */
    public InstancePoolManager poolManager() {
        return poolManager;
    }

    /**
     * Get the async render manager
     */
    public AsyncRenderManager asyncManager() {
        return asyncManager;
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
}