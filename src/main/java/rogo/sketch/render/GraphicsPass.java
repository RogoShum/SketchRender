package rogo.sketch.render;

import rogo.sketch.util.Identifier;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class GraphicsPass<C extends RenderContext> {
    private final Map<Identifier, GraphicsInstance<C>> instances = new HashMap<>();

    public GraphicsPass() {
    }

    public GraphicsInstance<C> getInstance(Identifier id) {
        return instances.get(id);
    }

    public void addGraphInstance(GraphicsInstance<C> graph) {
        instances.put(graph.getIdentifier(), graph);
    }

    /**
     * Get all GraphInstances in this pass.
     */
    public Collection<GraphicsInstance<C>> getAllInstances() {
        return instances.values();
    }

    /**
     * Render all GraphInstances in this pass.
     */
    public void render(C context) {
        for (GraphicsInstance<C> instance : instances.values()) {
            if (instance.shouldRender()) {
                context.shaderProvider().getUniformHookGroup().updateUniforms(instance);
                instance.render(context);
            }
        }
    }
}