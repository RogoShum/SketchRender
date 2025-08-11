package rogo.sketch.render;

import rogo.sketch.api.GraphicsInstance;
import rogo.sketch.util.Identifier;
import rogo.sketch.util.OrderedList;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GraphicsPipeline<C extends RenderContext> {
    private final OrderedList<GraphicsStage> stages;
    private final Map<GraphicsStage, GraphicsPassGroup<C>> passMap = new HashMap<>();
    private final Map<Identifier, GraphicsStage> idToStage = new HashMap<>();
    private final RenderStateManager renderStateManager = new RenderStateManager();
    private C currentContext;

    public GraphicsPipeline(boolean throwOnSortFail, C defaultContext) {
        this.stages = new OrderedList<>(throwOnSortFail);
        this.currentContext = defaultContext;
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
    public void addGraphInstance(Identifier id, GraphicsInstance graph, RenderSetting renderSetting) {
        GraphicsStage stage = idToStage.get(id);
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
}