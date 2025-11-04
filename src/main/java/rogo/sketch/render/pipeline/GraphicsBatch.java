package rogo.sketch.render.pipeline;

import rogo.sketch.api.graphics.GraphicsInstance;
import rogo.sketch.api.graphics.IndependentVertexProvider;
import rogo.sketch.render.data.filler.VertexFiller;
import rogo.sketch.render.instance.SharedVertexGraphics;
import rogo.sketch.render.pool.InstancePoolManager;
import rogo.sketch.render.vertex.VertexResourcePair;
import rogo.sketch.util.Identifier;

import java.util.*;

public class GraphicsBatch<C extends RenderContext> {
    // Legacy mode: Simplified to use only GraphicsInstance for all types
    private final Map<Identifier, GraphicsInstance> allGraphics = new LinkedHashMap<>();

    // Keep these for backward compatibility but deprecated
    @Deprecated
    private final Map<Identifier, SharedVertexGraphics> sharedGraphics = new LinkedHashMap<>();
    @Deprecated
    private final Map<Identifier, IndependentVertexProvider> independentGraphics = new LinkedHashMap<>();
    @Deprecated
    private final Map<Identifier, GraphicsInstance> customGraphics = new LinkedHashMap<>();

    public GraphicsBatch() {
    }

    public void addGraphInstance(GraphicsInstance graph) {
        // Legacy mode: All graphics instances are treated uniformly
        allGraphics.put(graph.getIdentifier(), graph);

        // Backward compatibility: Still populate the old maps for existing code
        if (graph instanceof SharedVertexGraphics sharedVertexGraphics) {
            sharedGraphics.put(graph.getIdentifier(), sharedVertexGraphics);
        } else if (graph instanceof IndependentVertexProvider independentVertexProvider) {
            independentGraphics.put(graph.getIdentifier(), independentVertexProvider);
        } else {
            customGraphics.put(graph.getIdentifier(), graph);
        }
    }

    public void tick(C context) {
        // Legacy mode: Single unified loop for all graphics instances
        for (GraphicsInstance graph : allGraphics.values()) {
            if (graph.shouldTick()) {
                graph.tick(context);
            }
        }
    }

    public boolean fillSharedVertexForBatch(VertexFiller filler, List<GraphicsInstance> batchInstances) {
        boolean result = false;

        for (GraphicsInstance instance : batchInstances) {
            if (instance instanceof SharedVertexGraphics sharedInstance && instance.shouldRender()) {
                sharedInstance.fillVertexData(filler);
                result = true;
            }
        }

        return result;
    }

    public Collection<GraphicsInstance> getSharedInstances() {
        return new ArrayList<>(sharedGraphics.values());
    }

    public Collection<GraphicsInstance> getIndependentInstances() {
        return new ArrayList<>(independentGraphics.values());
    }

    public Collection<GraphicsInstance> getCustomInstances() {
        return new ArrayList<>(customGraphics.values());
    }

    public Collection<GraphicsInstance> getAllInstances() {
        // Legacy mode: Return all instances from unified collection
        return new ArrayList<>(allGraphics.values());
    }

    public List<VertexResourcePair> fillIndependentVertexForBatch(List<GraphicsInstance> batchInstances) {
        List<VertexResourcePair> result = new ArrayList<>();

        for (GraphicsInstance instance : batchInstances) {
            if (instance instanceof IndependentVertexProvider independentInstance && instance.shouldRender()) {
                independentInstance.fillVertexData();
                result.addAll(independentInstance.getVertexResources());
            }
        }

        return result;
    }


    public void executeCustomBatch(List<GraphicsInstance> batchInstances, C context) {
        for (GraphicsInstance instance : batchInstances) {
            if (customGraphics.containsValue(instance) && instance.shouldRender()) {
                instance.afterDraw(context);
            }
        }
    }

    public boolean containsShared() {
        return !sharedGraphics.isEmpty();
    }

    public boolean containsIndependent() {
        return !independentGraphics.isEmpty();
    }

    public boolean containsCustom() {
        return !customGraphics.isEmpty();
    }

    /**
     * Cleanup discarded instances and return them to the pool
     */
    public void cleanupDiscardedInstances(InstancePoolManager poolManager) {
        // Legacy mode: Single unified cleanup loop
        allGraphics.entrySet().removeIf(entry -> {
            GraphicsInstance instance = entry.getValue();
            if (instance.shouldDiscard()) {
                poolManager.returnInstance(instance);
                return true;
            }
            return false;
        });

        // Also cleanup the deprecated collections for consistency
        sharedGraphics.entrySet().removeIf(entry -> entry.getValue().shouldDiscard());
        independentGraphics.entrySet().removeIf(entry -> entry.getValue().shouldDiscard());
        customGraphics.entrySet().removeIf(entry -> entry.getValue().shouldDiscard());
    }
}