package rogo.sketch.render.pipeline;

import rogo.sketch.api.graphics.Graphics;
import rogo.sketch.event.GraphicsPipelineInitEvent;
import rogo.sketch.event.RegisterStaticGraphicsEvent;
import rogo.sketch.event.bridge.EventBusBridge;
import rogo.sketch.render.command.RenderCommand;
import rogo.sketch.render.command.RenderCommandQueue;
import rogo.sketch.render.pipeline.async.AsyncRenderManager;
import rogo.sketch.render.pool.InstancePoolManager;
import rogo.sketch.util.Identifier;
import rogo.sketch.util.OrderedList;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GraphicsPipeline<C extends RenderContext> {
    private final OrderedList<GraphicsStage> stages;
    private final Map<GraphicsStage, GraphicsBatchGroup<C>> passMap = new LinkedHashMap<>();
    private final Map<Identifier, GraphicsStage> idToStage = new LinkedHashMap<>();
    private final RenderStateManager renderStateManager = new RenderStateManager();
    private final InstancePoolManager poolManager = InstancePoolManager.getInstance();
    private final AsyncRenderManager asyncManager = AsyncRenderManager.getInstance();
    private final RenderCommandQueue<C> renderCommandQueue = new RenderCommandQueue<>(this);
    private final PipelineConfig config;
    private C currentContext;
    private boolean initialized = false;
    private boolean initializedStaticGraphics = false;

    public GraphicsPipeline(PipelineConfig config, C defaultContext) {
        this.config = config;
        this.stages = new OrderedList<>(config.isThrowOnSortFail());
        this.currentContext = defaultContext;
    }

    public GraphicsPipeline(boolean throwOnSortFail, C defaultContext) {
        this.config = new PipelineConfig();
        this.config.setThrowOnSortFail(throwOnSortFail);
        this.stages = new OrderedList<>(throwOnSortFail);
        this.currentContext = defaultContext;
    }

    public PipelineConfig getConfig() {
        return config;
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
    public void addGraphInstance(Identifier stageId, Graphics graph, RenderSetting renderSetting) {
        GraphicsStage stage = idToStage.get(stageId);
        if (stage != null) {
            passMap.get(stage).addGraphInstance(graph, renderSetting);
        }
    }

    public void computeAllRenderCommand() {
        try {
            List<RenderCommand> renderCommands = createRenderCommands();
            renderCommandQueue.clear();
            renderCommandQueue.addCommands(renderCommands);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Create render commands from all stages
     */
    private List<RenderCommand> createRenderCommands() {
        List<RenderCommand> allCommands = new ArrayList<>();

        for (GraphicsStage stage : stages.getOrderedList()) {
            GraphicsBatchGroup<C> batchGroup = passMap.get(stage);
            if (batchGroup != null) {
                // Let GraphicsbatchGroup create its own render commands
                List<RenderCommand> stageCommands = batchGroup.createRenderCommands(currentContext);
                allCommands.addAll(stageCommands);
            }
        }

        return allCommands;
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
            renderCommandQueue.executeStage(ordered.get(i).getIdentifier(), this.renderStateManager, this.currentContext);
        }

        renderStage(toId);
    }

    /**
     * Render a single stage.
     */
    public void renderStage(Identifier id) {
        renderCommandQueue.executeStage(id, this.renderStateManager, this.currentContext);
    }

    public void renderStagesBefore(Identifier id) {
        List<GraphicsStage> ordered = stages.getOrderedList();
        GraphicsStage stage = idToStage.get(id);
        int idx = ordered.indexOf(stage);
        for (int i = 0; i < idx; i++) {
            renderCommandQueue.executeStage(ordered.get(i).getIdentifier(), this.renderStateManager, this.currentContext);
        }

        renderStage(id);
    }

    public void renderStagesAfter(Identifier id) {
        renderStage(id);

        List<GraphicsStage> ordered = stages.getOrderedList();
        GraphicsStage stage = idToStage.get(id);
        int idx = ordered.indexOf(stage);
        for (int i = idx + 1; i < ordered.size(); i++) {
            renderCommandQueue.executeStage(ordered.get(i).getIdentifier(), this.renderStateManager, this.currentContext);
        }
    }

    public void resetRenderContext(C context) {
        this.currentContext = context;
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
    public void addPooledGraphInstance(Identifier stageId, Class<? extends Graphics> instanceType,
                                       RenderSetting renderSetting) {
        if (!poolManager.isPoolingEnabled()) {
            throw new IllegalStateException("Instance pooling is not enabled");
        }

        Graphics instance = poolManager.borrowInstance(instanceType);
        addGraphInstance(stageId, instance, renderSetting);
    }

    /**
     * Add a GraphInstance from a named pool to a specific stage
     */
    public void addNamedPoolGraphInstance(Identifier stageId, Identifier poolName, RenderSetting renderSetting) {
        if (!poolManager.isPoolingEnabled()) {
            throw new IllegalStateException("Instance pooling is not enabled");
        }

        Graphics instance = poolManager.borrowInstance(poolName);
        addGraphInstance(stageId, instance, renderSetting);
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
}