package rogo.sketch.core.pipeline.graph;

import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.kernel.ThreadDomain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Internal node in the compiled render graph DAG.
 * Wraps a {@link PipelinePass} and tracks its dependency edges.
 *
 * @param <C> Concrete RenderContext type
 */
public final class PassNode<C extends RenderContext> {
    private final PipelinePass<C> pass;
    private final List<PassNode<C>> dependencies = new ArrayList<>();
    private final List<PassNode<C>> dependents = new ArrayList<>();

    public PassNode(PipelinePass<C> pass) {
        this.pass = pass;
    }

    public PipelinePass<C> pass() { return pass; }
    public String name() { return pass.name(); }
    public ThreadDomain threadDomain() { return pass.threadDomain(); }

    public List<PassNode<C>> dependencies() {
        return Collections.unmodifiableList(dependencies);
    }

    public List<PassNode<C>> dependents() {
        return Collections.unmodifiableList(dependents);
    }

    /**
     * Add a dependency edge: this node depends on {@code other}.
     */
    void addDependency(PassNode<C> other) {
        if (!dependencies.contains(other)) {
            dependencies.add(other);
            other.dependents.add(this);
        }
    }
}

