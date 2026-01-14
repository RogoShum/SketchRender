package rogo.sketch.render.pipeline.flow;

import rogo.sketch.SketchRender;
import rogo.sketch.util.Identifier;

import java.util.Objects;

/**
 * Represents a render flow type. This is an extensible class that can be
 * subclassed
 * by third-party mods to add custom flow types.
 * <p>
 * Built-in flow types:
 * <ul>
 * <li>{@link #RASTERIZATION} - Standard rasterization rendering pipeline</li>
 * <li>{@link #COMPUTE} - Compute shader processing pipeline</li>
 * </ul>
 */
public class RenderFlowType {
    /**
     * Standard rasterization rendering pipeline.
     * Used for traditional vertex/fragment shader rendering with geometry batching.
     */
    public static final RenderFlowType RASTERIZATION = new RenderFlowType(Identifier.of(SketchRender.MOD_ID, "rasterization"));

    /**
     * Compute shader processing pipeline.
     * Used for compute shader dispatch operations without geometry batching.
     */
    public static final RenderFlowType COMPUTE = new RenderFlowType(Identifier.of(SketchRender.MOD_ID, "compute"));

    private final Identifier id;

    /**
     * Create a new render flow type.
     *
     * @param id Unique identifier for this flow type
     */
    protected RenderFlowType(Identifier id) {
        this.id = Objects.requireNonNull(id, "RenderFlowType id cannot be null");
    }

    /**
     * Get the unique identifier for this flow type.
     *
     * @return The flow type identifier
     */
    public Identifier getId() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        RenderFlowType that = (RenderFlowType) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "RenderFlowType{" + id + "}";
    }
}