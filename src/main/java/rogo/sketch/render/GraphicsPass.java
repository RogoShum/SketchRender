package rogo.sketch.render;

import rogo.sketch.render.data.filler.VertexFiller;
import rogo.sketch.render.vertex.VertexResourceProvider;
import rogo.sketch.render.vertex.VertexResourceType;
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

    public boolean fillVertex(VertexFiller filler) {
        boolean result = false;
        for (GraphicsInstance<C> instance : instances.values()) {
            if (instance.shouldRender()) {
                instance.fillVertex(filler);
                result = true;
            }
        }

        return result;
    }
    
    /**
     * Fill vertex data only from instances that use shared resources
     */
    public boolean fillVertexForShared(VertexFiller filler) {
        boolean result = false;
        for (GraphicsInstance<C> instance : instances.values()) {
            if (instance.shouldRender() && 
                instance.getVertexResourceType() == VertexResourceType.SHARED_DYNAMIC) {
                instance.fillVertex(filler);
                result = true;
            }
        }
        return result;
    }
    
    /**
     * Check if this pass contains a specific vertex resource provider
     */
    public boolean containsProvider(VertexResourceProvider provider) {
        for (GraphicsInstance<C> instance : instances.values()) {
            if (instance.isVertexResourceProvider() && 
                instance.asVertexResourceProvider() == provider) {
                return true;
            }
        }
        return false;
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