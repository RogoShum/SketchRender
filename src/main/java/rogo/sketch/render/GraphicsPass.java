package rogo.sketch.render;

import rogo.sketch.api.GraphicsInstance;
import rogo.sketch.api.IndependentVertexProvider;
import rogo.sketch.render.data.filler.VertexFiller;
import rogo.sketch.render.instance.SharedVertexGraphics;
import rogo.sketch.render.vertex.VertexResourcePair;
import rogo.sketch.util.Identifier;

import java.util.*;

public class GraphicsPass<C extends RenderContext> {
    private final Set<GraphicsInstance> activatedGraphics = new HashSet<>();
    private final Map<Identifier, SharedVertexGraphics> sharedGraphics = new HashMap<>();
    private final Map<Identifier, IndependentVertexProvider> independentGraphics = new HashMap<>();
    private final Map<Identifier, GraphicsInstance> customGraphics = new HashMap<>();

    public GraphicsPass() {
    }

    public void refresh() {
        activatedGraphics.clear();
    }

    public void addGraphInstance(GraphicsInstance graph) {
        if (graph instanceof SharedVertexGraphics sharedVertexGraphics) {
            sharedGraphics.put(graph.getIdentifier(), sharedVertexGraphics);
        } else if (graph instanceof IndependentVertexProvider independentVertexProvider) {
            independentGraphics.put(graph.getIdentifier(), independentVertexProvider);
        } else {
            customGraphics.put(graph.getIdentifier(), graph);
        }
    }

    public void tick(C context) {
        for (Identifier id : sharedGraphics.keySet()) {
            GraphicsInstance graph = sharedGraphics.get(id);
            if (graph.shouldTick()) {
                graph.tick(context);
            }
        }

        for (Identifier id : independentGraphics.keySet()) {
            GraphicsInstance graph = independentGraphics.get(id);
            if (graph.shouldTick()) {
                graph.tick(context);
            }
        }

        for (Identifier id : customGraphics.keySet()) {
            GraphicsInstance graph = customGraphics.get(id);
            if (graph.shouldTick()) {
                graph.tick(context);
            }
        }
    }

    public boolean fillSharedVertex(VertexFiller filler) {
        boolean result = false;
        for (SharedVertexGraphics instance : sharedGraphics.values()) {
            if (instance.shouldRender()) {
                instance.fillVertexData(filler);
                activatedGraphics.add(instance);
                result = true;
            }
        }

        return result;
    }

    public List<VertexResourcePair> fillIndependentVertex() {
        List<VertexResourcePair> result = new ArrayList<>();
        for (IndependentVertexProvider instance : independentGraphics.values()) {
            if (instance.shouldRender()) {
                instance.fillVertexData();
                result.addAll(instance.getVertexResources());
                activatedGraphics.add(instance);
            }
        }

        return result;
    }

    public void endCustom() {
        for (GraphicsInstance instance : customGraphics.values()) {
            instance.endDraw();
        }
    }

    public void endDraw() {
        for (GraphicsInstance instance : activatedGraphics) {
            instance.endDraw();
        }

        activatedGraphics.clear();
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
}