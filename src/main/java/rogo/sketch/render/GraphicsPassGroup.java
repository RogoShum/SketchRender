package rogo.sketch.render;

import rogo.sketch.util.Identifier;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class GraphicsPassGroup<C extends RenderContext> {
    private final Identifier stageIdentifier;
    private final Map<RenderSetting, GraphicsPass<C>> groups = new LinkedHashMap<>();

    public GraphicsPassGroup(Identifier stageIdentifier) {
        this.stageIdentifier = stageIdentifier;
    }

    public void addGraphInstance(GraphicsInstance<C> instance, RenderSetting setting) {
        GraphicsPass<C> group = groups.computeIfAbsent(setting, s -> new GraphicsPass<>());
        group.addGraphInstance(instance);
    }

    public void tick() {

    }

    public void render(RenderStateManager manager, C context) {
        context.preStage(stageIdentifier);
        for (Map.Entry<RenderSetting, GraphicsPass<C>> entry : groups.entrySet()) {
            manager.accept(entry.getKey(), context);
            context.shaderProvider().getUniformHookGroup().updateUniforms(context);
            GraphicsPass<C> group = entry.getValue();
            group.render(context);
        }
        context.postStage(stageIdentifier);
    }

    public GraphicsPass<C> getPass(RenderSetting setting) {
        return groups.get(setting);
    }

    public Collection<GraphicsPass<C>> getPasses() {
        return groups.values();
    }

    public void clear() {
        groups.clear();
    }
}