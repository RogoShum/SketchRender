package rogo.sketchrender.render;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class GraphicsPassGroup<C extends RenderContext> {
    private final Map<RenderSetting, GraphicsPass<C>> groups = new LinkedHashMap<>();

    public void addGraphInstance(GraphicsInstance<C> instance, RenderSetting setting) {
        GraphicsPass<C> group = groups.computeIfAbsent(setting, s -> new GraphicsPass<>());
        group.addGraphInstance(instance);
    }

    public void render(C context) {
        for (Map.Entry<RenderSetting, GraphicsPass<C>> entry : groups.entrySet()) {
            RenderSetting setting = entry.getKey();
            GraphicsPass<C> group = entry.getValue();
            group.render(context);
        }
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