package rogo.sketch.render.pipeline;

import rogo.sketch.render.data.PrimitiveType;
import rogo.sketch.render.data.Usage;
import rogo.sketch.render.data.format.VertexLayoutSpec;
import rogo.sketch.render.pipeline.flow.RenderFlowType;

import java.util.Objects;

/**
 * Render parameter for rasterization rendering pipeline.
 * <p>
 * Contains all parameters needed for traditional vertex/fragment shader
 * rendering:
 * <ul>
 * <li>Data format - Vertex attribute layout</li>
 * <li>Primitive type - Triangle, quad, etc.</li>
 * <li>Usage - Static, dynamic, stream</li>
 * <li>Sorting - Whether to enable depth sorting</li>
 * </ul>
 * </p>
 */
public class RasterizationParameter extends RenderParameter {
    private final VertexLayoutSpec layout;
    private final PrimitiveType primitiveType;
    private final Usage usage;
    private final boolean enableSorting;

    /**
     * Primary constructor with explicit VertexLayoutSpec.
     */
    public RasterizationParameter(
            VertexLayoutSpec layout,
            PrimitiveType primitiveType,
            Usage usage,
            boolean enableSorting) {
        this.layout = Objects.requireNonNull(layout);
        this.primitiveType = Objects.requireNonNull(primitiveType);
        this.usage = Objects.requireNonNull(usage);
        this.enableSorting = enableSorting;
    }

    /**
     * Legacy constructor taking DataFormat.
     * Creates a default VertexLayoutSpec with one mutable component at Binding 0.
     */
    /**
     * Legacy constructor taking DataFormat.
     * Creates a default VertexLayoutSpec with one mutable component at Binding 0.
     */
    @Override
    public RenderFlowType getFlowType() {
        return RenderFlowType.RASTERIZATION;
    }

    @Override
    public VertexLayoutSpec getLayout() {
        return layout;
    }

    public PrimitiveType primitiveType() {
        return primitiveType;
    }

    public Usage usage() {
        return usage;
    }

    public boolean enableSorting() {
        return enableSorting;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof RasterizationParameter that))
            return false;
        return enableSorting == that.enableSorting &&
                Objects.equals(layout, that.layout) &&
                primitiveType == that.primitiveType &&
                usage == that.usage;
    }

    @Override
    public int hashCode() {
        return Objects.hash(layout, primitiveType, usage, enableSorting);
    }

    @Override
    public String toString() {
        return "RasterizationParameter{" +
                "layout=" + layout +
                ", primitiveType=" + primitiveType +
                ", usage=" + usage +
                ", enableSorting=" + enableSorting +
                '}';
    }

    /**
     * Create parameter with full layout specification.
     */
    public static RasterizationParameter create(
            VertexLayoutSpec layout,
            PrimitiveType primitiveType,
            Usage usage,
            boolean enableSorting) {
        return new RasterizationParameter(layout, primitiveType, usage, enableSorting);
    }
}
