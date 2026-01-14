package rogo.sketch.render.pipeline;

import rogo.sketch.api.graphics.Graphics;
import rogo.sketch.render.pool.InstancePoolManager;
import rogo.sketch.util.Identifier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class GraphicsBatch<C extends RenderContext> {
    private final Map<Identifier, Graphics> allGraphics = new LinkedHashMap<>();

    public GraphicsBatch() {
    }

    public void addGraphInstance(Graphics graph) {
        allGraphics.put(graph.getIdentifier(), graph);
    }

    public void tick(C context) {
        for (Graphics graph : allGraphics.values()) {
            if (graph.shouldTick()) {
                graph.tick(context);
            }
        }
    }

    public Collection<Graphics> getAllInstances() {
        return new ArrayList<>(allGraphics.values());
    }

    /**
     * Cleanup discarded instances and return them to the pool
     */
    public void cleanupDiscardedInstances(InstancePoolManager poolManager) {
        allGraphics.entrySet().removeIf(entry -> {
            Graphics instance = entry.getValue();
            if (instance.shouldDiscard()) {
                poolManager.returnInstance(instance);
                return true;
            }
            return false;
        });
    }
}