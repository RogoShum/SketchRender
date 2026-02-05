package rogo.sketch.core.pipeline.parmeter;

import org.jetbrains.annotations.NotNull;
import rogo.sketch.core.data.PrimitiveType;
import rogo.sketch.core.data.Usage;
import rogo.sketch.core.data.format.ComponentSpec;
import rogo.sketch.core.data.format.DataFormat;
import rogo.sketch.core.data.format.VertexLayoutSpec;
import rogo.sketch.core.pipeline.flow.RenderFlowType;

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
    private final BuilderBatchKey builderBatchKey;
    private final BuilderKey[] builderKeys;
    private final int hash;

    /**
     * Primary constructor with explicit VertexLayoutSpec.
     */
    public RasterizationParameter(VertexLayoutSpec layout, PrimitiveType primitiveType, Usage usage, boolean enableSorting) {
        this.layout = Objects.requireNonNull(layout);
        this.primitiveType = Objects.requireNonNull(primitiveType);
        this.builderBatchKey = new BuilderBatchKey(layout, primitiveType);
        this.builderKeys = new BuilderKey[layout.getDynamicSpecs().length];

        for (int i = 0; i < layout.getDynamicSpecs().length; i++) {
            ComponentSpec spec = layout.getDynamicSpecs()[i];
            builderKeys[i] = new BuilderKey(spec.getFormat(), primitiveType, spec.isInstanced());
        }

        this.usage = Objects.requireNonNull(usage);
        this.enableSorting = enableSorting;
        this.hash = Objects.hash(layout, primitiveType, usage, enableSorting);
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
    @NotNull
    public VertexLayoutSpec getLayout() {
        return layout;
    }

    @Override
    @NotNull
    public PrimitiveType primitiveType() {
        return primitiveType;
    }

    public BuilderBatchKey builderBatchKey() {
        return builderBatchKey;
    }

    public BuilderKey[] builderKeys() {
        return builderKeys;
    }

    //todo
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
        return hash;
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

    public record BuilderKey(DataFormat format, PrimitiveType primitiveType, boolean instanced){}

    public record BuilderBatchKey(VertexLayoutSpec spec, PrimitiveType primitiveType){}
}