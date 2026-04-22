package rogo.sketch.core.pipeline.graph;

import rogo.sketch.core.pipeline.GraphicsPipeline;
import rogo.sketch.core.pipeline.RenderContext;

import java.util.*;

/**
 * Builder for constructing a render graph declaratively.
 * <p>
 * Passes are registered with {@link #addPass(PipelinePass, String...)},
 * where the varargs specify the names of passes this pass depends on.
 * Call {@link #compile()} to produce an immutable {@link CompiledRenderGraph}.
 * </p>
 *
 * @param <C> Concrete RenderContext type
 */
public class RenderGraphBuilder<C extends RenderContext> {
    private final GraphicsPipeline<C> pipeline;
    private final Map<String, PipelinePass<C>> passes = new LinkedHashMap<>();
    private final Map<String, List<String>> dependencyMap = new LinkedHashMap<>();

    public RenderGraphBuilder(GraphicsPipeline<C> pipeline) {
        this.pipeline = pipeline;
    }

    /**
     * Add a pass with explicit dependency names.
     *
     * @param pass         The pass to add
     * @param dependsOn    Names of passes this pass depends on
     * @return this builder for chaining
     */
    public RenderGraphBuilder<C> addPass(PipelinePass<C> pass, String... dependsOn) {
        String name = pass.name();
        if (passes.containsKey(name)) {
            throw new IllegalArgumentException("Duplicate pass name: " + name);
        }
        passes.put(name, pass);
        dependencyMap.put(name, dependsOn.length > 0 ? Arrays.asList(dependsOn) : Collections.emptyList());
        return this;
    }

    /**
     * Check if a pass with the given name is already registered.
     */
    public boolean hasPass(String name) {
        return passes.containsKey(name);
    }

    public List<String> passNames() {
        return List.copyOf(passes.keySet());
    }

    public GraphicsPipeline<C> pipeline() {
        return pipeline;
    }

    /**
     * Compile the builder into an immutable, topologically-sorted render graph.
     *
     * @return The compiled render graph
     * @throws IllegalStateException if there are unresolved dependencies or cycles
     */
    public CompiledRenderGraph<C> compile() {
        // Build PassNode graph
        Map<String, PassNode<C>> nodes = new LinkedHashMap<>();
        for (Map.Entry<String, PipelinePass<C>> entry : passes.entrySet()) {
            nodes.put(entry.getKey(), new PassNode<>(entry.getValue()));
        }

        // Wire dependency edges
        for (Map.Entry<String, List<String>> entry : dependencyMap.entrySet()) {
            PassNode<C> node = nodes.get(entry.getKey());
            for (String dep : entry.getValue()) {
                PassNode<C> depNode = nodes.get(dep);
                if (depNode == null) {
                    throw new IllegalStateException(
                            "Pass '" + entry.getKey() + "' depends on unknown pass '" + dep + "'");
                }
                node.addDependency(depNode);
            }
        }

        // Topological sort (Kahn's algorithm)
        List<PassNode<C>> sorted = topologicalSort(nodes.values());

        return new CompiledRenderGraph<>(sorted, pipeline);
    }

    private List<PassNode<C>> topologicalSort(Collection<PassNode<C>> allNodes) {
        Map<PassNode<C>, Integer> inDegree = new IdentityHashMap<>();
        for (PassNode<C> node : allNodes) {
            inDegree.put(node, node.dependencies().size());
        }

        Queue<PassNode<C>> ready = new ArrayDeque<>();
        for (PassNode<C> node : allNodes) {
            if (inDegree.get(node) == 0) {
                ready.add(node);
            }
        }

        List<PassNode<C>> result = new ArrayList<>();
        while (!ready.isEmpty()) {
            PassNode<C> node = ready.poll();
            result.add(node);
            for (PassNode<C> dep : node.dependents()) {
                int remaining = inDegree.get(dep) - 1;
                inDegree.put(dep, remaining);
                if (remaining == 0) {
                    ready.add(dep);
                }
            }
        }

        if (result.size() != allNodes.size()) {
            throw new IllegalStateException(
                    "Render graph contains a cycle! Sorted " + result.size() +
                    " of " + allNodes.size() + " passes.");
        }

        return result;
    }
}

