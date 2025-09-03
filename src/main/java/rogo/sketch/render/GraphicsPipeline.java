package rogo.sketch.render;

import rogo.sketch.api.GraphicsInstance;
import rogo.sketch.event.GraphicsPipelineInitEvent;
import rogo.sketch.event.RegisterStaticGraphicsEvent;
import rogo.sketch.event.bridge.EventBusBridge;
import rogo.sketch.render.async.AsyncRenderManager;
import rogo.sketch.render.command.RenderCommand;
import rogo.sketch.render.command.RenderCommandQueue;
import rogo.sketch.render.information.GraphicsInformation;
import rogo.sketch.render.information.InfoCollector;
import rogo.sketch.render.information.RenderList;
import rogo.sketch.render.pool.InstancePoolManager;
import rogo.sketch.render.vertex.AsyncVertexFiller;
import rogo.sketch.util.Identifier;
import rogo.sketch.util.OrderedList;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class GraphicsPipeline<C extends RenderContext> {
    private final OrderedList<GraphicsStage> stages;
    private final Map<GraphicsStage, GraphicsPassGroup<C>> passMap = new LinkedHashMap<>();
    private final Map<Identifier, GraphicsStage> idToStage = new LinkedHashMap<>();
    private final RenderStateManager renderStateManager = new RenderStateManager();
    private final InstancePoolManager poolManager = InstancePoolManager.getInstance();
    private final AsyncRenderManager asyncManager = AsyncRenderManager.getInstance();
    private final AsyncVertexFiller vertexFiller = AsyncVertexFiller.getInstance();
    private final RenderCommandQueue renderCommandQueue = new RenderCommandQueue();
    private C currentContext;
    private boolean initialized = false;
    private boolean initializedStaticGraphics = false;
    private boolean useNewPipeline = true; // Toggle for new three-stage pipeline

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
        boolean added = stages.add(stage, stage.getOrderRequirement(), (s, req) -> passMap.put(s, new GraphicsPassGroup<>(this, s.getIdentifier())));
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
        if (useNewPipeline) {
            renderAllStagesThreePhase();
        } else {
            renderAllStagesLegacy();
        }
    }
    
    /**
     * Legacy direct rendering approach
     */
    private void renderAllStagesLegacy() {
        for (GraphicsStage stage : stages.getOrderedList()) {
            GraphicsPassGroup<C> group = passMap.get(stage);
            group.render(renderStateManager, currentContext);
        }
    }
    
    /**
     * New three-stage rendering approach: collect data, fill vertex buffers, batch render
     */
    private void renderAllStagesThreePhase() {
        try {
            // Stage 1: Data Collection
            List<GraphicsInformation> collectedData = collectRenderData();
            
            // Stage 2: Organize and Fill Vertex Buffers
            RenderList renderList = RenderList.organize(collectedData);
            CompletableFuture<List<AsyncVertexFiller.FilledVertexResource>> filledResourcesFuture = 
                    vertexFiller.fillVertexBuffersAsync(renderList);
            
            // Stage 3: Create Render Commands and Execute
            List<AsyncVertexFiller.FilledVertexResource> filledResources = filledResourcesFuture.join();
            List<RenderCommand> renderCommands = createRenderCommands(filledResources);
            
            // Clear previous commands and add new ones
            renderCommandQueue.clear();
            renderCommandQueue.addCommands(renderCommands);
            
            // Execute all render commands
            renderCommandQueue.executeAll();
            
        } catch (Exception e) {
            // Fallback to legacy rendering if something goes wrong
            renderAllStagesLegacy();
        }
    }

    /**
     * Render stages between 'fromStage' (exclusive) and 'toStage' (exclusive).
     * Only renders the stages strictly between fromId and toId.
     */
    public void renderStagesBetween(Identifier fromId, Identifier toId) {
        if (useNewPipeline) {
            renderCommandQueue.executeStagesBetween(fromId, toId);
        } else {
            List<GraphicsStage> ordered = stages.getOrderedList();
            GraphicsStage fromStage = idToStage.get(fromId);
            GraphicsStage toStage = idToStage.get(toId);
            int fromIdx = ordered.indexOf(fromStage);
            int toIdx = ordered.indexOf(toStage);
            for (int i = fromIdx + 1; i < toIdx; i++) {
                GraphicsPassGroup<C> passGroup = passMap.get(ordered.get(i));
                if (passGroup != null) passGroup.render(renderStateManager, currentContext);
            }

            renderStage(toId);
        }
    }

    /**
     * Render a single stage.
     */
    public void renderStage(Identifier id) {
        if (useNewPipeline) {
            renderCommandQueue.executeStage(id);
        } else {
            GraphicsStage stage = idToStage.get(id);
            if (stage != null) {
                GraphicsPassGroup<C> passGroup = passMap.get(stage);
                if (passGroup != null) {
                    passGroup.render(renderStateManager, currentContext);
                }
            }
        }
    }

    public void renderStagesBefore(Identifier id) {
        if (useNewPipeline) {
            renderCommandQueue.executeStagesBefore(id);
            renderCommandQueue.executeStage(id);
        } else {
            List<GraphicsStage> ordered = stages.getOrderedList();
            GraphicsStage stage = idToStage.get(id);
            int idx = ordered.indexOf(stage);
            for (int i = 0; i < idx; i++) {
                GraphicsPassGroup<C> passGroup = passMap.get(ordered.get(i));
                if (passGroup != null) passGroup.render(renderStateManager, currentContext);
            }

            renderStage(id);
        }
    }

    public void renderStagesAfter(Identifier id) {
        if (useNewPipeline) {
            renderCommandQueue.executeStage(id);
            renderCommandQueue.executeStagesAfter(id);
        } else {
            renderStage(id);

            List<GraphicsStage> ordered = stages.getOrderedList();
            GraphicsStage stage = idToStage.get(id);
            int idx = ordered.indexOf(stage);
            for (int i = idx + 1; i < ordered.size(); i++) {
                GraphicsPassGroup<C> passGroup = passMap.get(ordered.get(i));
                if (passGroup != null) passGroup.render(renderStateManager, currentContext);
            }
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

    public record PipelineStats(int totalStages, int pendingStages, int totalInstances,
                                boolean initialized) {
        @Override
        public String toString() {
            return String.format("Pipeline[stages=%d, pending=%d, instances=%d, init=%s]",
                    totalStages, pendingStages, totalInstances, initialized);
        }
    }
    
    /**
     * Collect render data from all stages - Stage 1 of three-stage pipeline
     */
    private List<GraphicsInformation> collectRenderData() {
        List<GraphicsInformation> allData = new ArrayList<>();
        
        for (GraphicsStage stage : stages.getOrderedList()) {
            GraphicsPassGroup<C> passGroup = passMap.get(stage);
            if (passGroup != null) {
                // Collect all instances from this stage
                List<GraphicsInstance> stageInstances = new ArrayList<>();
                for (GraphicsPass<C> pass : passGroup.getPasses()) {
                    stageInstances.addAll(pass.getAllInstances());
                }
                
                // Use InfoCollector to extract render information
                List<GraphicsInformation> stageData = InfoCollector.collectRenderInfo(stageInstances, currentContext);
                allData.addAll(stageData);
            }
        }
        
        return allData;
    }
    
    /**
     * Create render commands from filled vertex resources - Stage 3 of three-stage pipeline
     */
    private List<RenderCommand> createRenderCommands(List<AsyncVertexFiller.FilledVertexResource> filledResources) {
        List<RenderCommand> commands = new ArrayList<>();
        
        for (AsyncVertexFiller.FilledVertexResource filledResource : filledResources) {
            // Group instances by stage
            Map<Identifier, List<GraphicsInformation>> instancesByStage = new LinkedHashMap<>();
            
            for (GraphicsInformation info : filledResource.getBatch().getInstances()) {
                Identifier stageId = findStageForInstance(info.getInstance());
                if (stageId != null) {
                    instancesByStage.computeIfAbsent(stageId, k -> new ArrayList<>()).add(info);
                }
            }
            
            // Create render commands for each stage
            for (Map.Entry<Identifier, List<GraphicsInformation>> entry : instancesByStage.entrySet()) {
                Identifier stageId = entry.getKey();
                List<GraphicsInformation> stageInstances = entry.getValue();
                
                if (!stageInstances.isEmpty()) {
                    RenderCommand command = RenderCommand.createFromFilledResource(
                            filledResource.getVertexResource(),
                            stageInstances,
                            stageId
                    );
                    commands.add(command);
                }
            }
        }
        
        return commands;
    }
    
    /**
     * Find which stage a graphics instance belongs to
     */
    private Identifier findStageForInstance(GraphicsInstance instance) {
        for (Map.Entry<GraphicsStage, GraphicsPassGroup<C>> entry : passMap.entrySet()) {
            GraphicsPassGroup<C> passGroup = entry.getValue();
            for (GraphicsPass<C> pass : passGroup.getPasses()) {
                if (pass.getAllInstances().contains(instance)) {
                    return entry.getKey().getIdentifier();
                }
            }
        }
        return null;
    }
    
    /**
     * Set whether to use the new three-stage pipeline or legacy direct rendering
     */
    public void setUseNewPipeline(boolean useNewPipeline) {
        this.useNewPipeline = useNewPipeline;
    }
    
    /**
     * Check if using the new three-stage pipeline
     */
    public boolean isUsingNewPipeline() {
        return useNewPipeline;
    }
    
    /**
     * Get the render command queue (for new pipeline)
     */
    public RenderCommandQueue getRenderCommandQueue() {
        return renderCommandQueue;
    }
}