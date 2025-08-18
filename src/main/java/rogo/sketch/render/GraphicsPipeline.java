package rogo.sketch.render;

import rogo.sketch.api.GraphicsInstance;
import rogo.sketch.event.GraphicsPipelineInitEvent;
import rogo.sketch.event.bridge.EventBusBridge;
import rogo.sketch.render.async.AsyncRenderManager;
import rogo.sketch.render.pool.InstancePoolManager;
import rogo.sketch.util.Identifier;
import rogo.sketch.util.OrderedList;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GraphicsPipeline<C extends RenderContext> {
    private final OrderedList<GraphicsStage> stages;
    private final Map<GraphicsStage, GraphicsPassGroup<C>> passMap = new LinkedHashMap<>();
    private final Map<Identifier, GraphicsStage> idToStage = new LinkedHashMap<>();
    private final RenderStateManager renderStateManager = new RenderStateManager();
    private final InstancePoolManager poolManager = InstancePoolManager.getInstance();
    private final AsyncRenderManager asyncManager = AsyncRenderManager.getInstance();
    private C currentContext;
    private boolean initialized = false;

    public GraphicsPipeline(boolean throwOnSortFail, C defaultContext) {
        this.stages = new OrderedList<>(throwOnSortFail);
        this.currentContext = defaultContext;
        // Don't initialize immediately, wait for explicit call
    }

    /**
     * Register a stage. Only if added successfully, a GraphPass will be created.
     *
     * @return true if added successfully, false if delayed
     */
    public boolean registerStage(GraphicsStage stage) {
        boolean added = stages.add(stage, stage.getOrderRequirement(), (s, req) -> passMap.put(s, new GraphicsPassGroup<>(s.getIdentifier())));
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
     * Get the list of pending (delayed) stages.
     */
    public List<OrderedList.PendingElement<GraphicsStage>> getPendingStages() {
        return stages.getPendingElements();
    }

    public GraphicsStage getStage(Identifier id) {
        return idToStage.get(id);
    }

    /**
     * Add a GraphInstance to a specific stage.
     */
    public void addGraphInstance(Identifier stageId, GraphicsInstance graph, RenderSetting renderSetting) {
        GraphicsStage stage = idToStage.get(stageId);
        if (stage != null) {
            passMap.get(stage).addGraphInstance(graph, renderSetting);
        }
    }

    /**
     * Render all stages in order.
     */
    public void renderAllStages() {
        for (GraphicsStage stage : stages.getOrderedList()) {
            GraphicsPassGroup<C> group = passMap.get(stage);
            group.render(renderStateManager, currentContext);
        }
    }

    /**
     * Render stages between 'fromStage' (exclusive) and 'toStage' (exclusive).
     * Only renders the stages strictly between fromId and toId.
     */
    public void renderStagesBetween(Identifier fromId, Identifier toId) {
        List<GraphicsStage> ordered = stages.getOrderedList();
        GraphicsStage fromStage = idToStage.get(fromId);
        GraphicsStage toStage = idToStage.get(toId);
        int fromIdx = ordered.indexOf(fromStage);
        int toIdx = ordered.indexOf(toStage);
        for (int i = fromIdx + 1; i < toIdx; i++) {
            GraphicsPassGroup<C> passGroup = passMap.get(ordered.get(i));
            if (passGroup != null) passGroup.render(renderStateManager, currentContext);
        }
    }

    /**
     * Render a single stage.
     */
    public void renderStage(Identifier id) {
        GraphicsStage stage = idToStage.get(id);
        if (stage != null) {
            GraphicsPassGroup<C> passGroup = passMap.get(stage);
            if (passGroup != null) {
                passGroup.render(renderStateManager, currentContext);
            }
        }
    }

    public void renderStagesBefore(Identifier id) {
        List<GraphicsStage> ordered = stages.getOrderedList();
        GraphicsStage stage = idToStage.get(id);
        int idx = ordered.indexOf(stage);
        for (int i = 0; i < idx; i++) {
            GraphicsPassGroup<C> passGroup = passMap.get(ordered.get(i));
            if (passGroup != null) passGroup.render(renderStateManager, currentContext);
        }
    }

    public void renderStagesAfter(Identifier id) {
        List<GraphicsStage> ordered = stages.getOrderedList();
        GraphicsStage stage = idToStage.get(id);
        int idx = ordered.indexOf(stage);
        for (int i = idx + 1; i < ordered.size(); i++) {
            GraphicsPassGroup<C> passGroup = passMap.get(ordered.get(i));
            if (passGroup != null) passGroup.render(renderStateManager, currentContext);
        }
    }

    public void resetRenderContext(C context) {
        this.currentContext = context;
    }

    public void resetRenderState() {
        this.renderStateManager().reset();
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

    /**
     * Add a GraphInstance from the instance pool to a specific stage
     */
    public void addPooledGraphInstance(Identifier stageId, Class<? extends GraphicsInstance> instanceType, RenderSetting renderSetting) {
        if (!poolManager.isPoolingEnabled()) {
            throw new IllegalStateException("Instance pooling is not enabled");
        }

        GraphicsInstance instance = poolManager.borrowInstance(instanceType);
        addGraphInstance(stageId, instance, renderSetting);
    }

    /**
     * Add a GraphInstance from a named pool to a specific stage
     */
    public void addNamedPoolGraphInstance(Identifier stageId, Identifier poolName, RenderSetting renderSetting) {
        if (!poolManager.isPoolingEnabled()) {
            throw new IllegalStateException("Instance pooling is not enabled");
        }

        GraphicsInstance instance = poolManager.borrowInstance(poolName);
        addGraphInstance(stageId, instance, renderSetting);
    }

    /**
     * Tick all stages with async support
     */
    public void tickAllStages() {
        for (GraphicsStage stage : stages.getOrderedList()) {
            GraphicsPassGroup<C> group = passMap.get(stage);
            if (group != null) {
                group.tick(currentContext);
            }
        }
    }

    /**
     * Cleanup discarded instances and return them to pools
     */
    public void cleanupInstances() {
        for (GraphicsPassGroup<C> group : passMap.values()) {
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

    /**
     * Get pipeline statistics
     */
    public PipelineStats getStats() {
        int totalStages = stages.getOrderedList().size();
        int pendingStages = stages.getPendingElements().size();
        int totalInstances = passMap.values().stream()
                .mapToInt(group -> group.getPasses().stream()
                        .mapToInt(pass -> pass.getAllInstances().size())
                        .sum())
                .sum();

        return new PipelineStats(totalStages, pendingStages, totalInstances, initialized);
    }

    public record PipelineStats(int totalStages, int pendingStages, int totalInstances, boolean initialized) {
        @Override
        public String toString() {
            return String.format("Pipeline[stages=%d, pending=%d, instances=%d, init=%s]",
                    totalStages, pendingStages, totalInstances, initialized);
        }
    }
}