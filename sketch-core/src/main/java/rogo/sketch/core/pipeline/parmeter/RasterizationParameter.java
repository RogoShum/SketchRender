package rogo.sketch.core.pipeline.parmeter;

import org.jetbrains.annotations.NotNull;
import rogo.sketch.core.data.MeshIndexMode;
import rogo.sketch.core.data.PrimitiveType;
import rogo.sketch.core.data.format.ComponentSpec;
import rogo.sketch.core.data.format.VertexLayoutSpec;
import rogo.sketch.core.pipeline.flow.RenderFlowType;
import rogo.sketch.core.data.layout.StructLayout;
import rogo.sketch.core.resource.descriptor.BufferUpdatePolicy;

import java.util.Objects;

/**
 * Render parameter for rasterization rendering pipeline.
 * <p>
 * Contains all parameters needed for traditional vertex/fragment shader
 * rendering:
 * <ul>
 * <li>Data format - Vertex attribute layout</li>
 * <li>Primitive type - Triangle, quad, etc.</li>
 * <li>Buffer update policy - immutable, dynamic, stream</li>
 * <li>Sorting - Whether to enable depth sorting</li>
 * </ul>
 * </p>
 */
public class RasterizationParameter extends RenderParameter {
    private final VertexLayoutSpec layout;
    private final PrimitiveType primitiveType;
    private final MeshIndexMode indexMode;
    private final BufferUpdatePolicy updatePolicy;
    private final boolean enableSorting;
    private final BuilderBatchKey builderBatchKey;
    private final BuilderKey[] builderKeys;
    private final int hash;

    /**
     * Primary constructor with explicit VertexLayoutSpec.
     */
    public RasterizationParameter(
            VertexLayoutSpec layout,
            PrimitiveType primitiveType,
            MeshIndexMode indexMode,
            BufferUpdatePolicy updatePolicy,
            boolean enableSorting) {
        this.layout = Objects.requireNonNull(layout);
        this.primitiveType = Objects.requireNonNull(primitiveType);
        this.indexMode = Objects.requireNonNull(indexMode);
        this.builderBatchKey = new BuilderBatchKey(layout, primitiveType);
        this.builderKeys = new BuilderKey[layout.getDynamicSpecs().length];

        for (int i = 0; i < layout.getDynamicSpecs().length; i++) {
            ComponentSpec spec = layout.getDynamicSpecs()[i];
            builderKeys[i] = new BuilderKey(spec.getFormat(), primitiveType, spec.isInstanced());
        }

        this.updatePolicy = Objects.requireNonNull(updatePolicy);
        this.enableSorting = enableSorting;
        this.hash = Objects.hash(layout, primitiveType, indexMode, updatePolicy, enableSorting);
    }

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

    @Override
    @NotNull
    public MeshIndexMode indexMode() {
        return indexMode;
    }

    public BuilderBatchKey builderBatchKey() {
        return builderBatchKey;
    }

    public BuilderKey[] builderKeys() {
        return builderKeys;
    }

    //todo
    public BufferUpdatePolicy updatePolicy() {
        return updatePolicy;
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
                indexMode == that.indexMode &&
                updatePolicy == that.updatePolicy;
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
                ", indexMode=" + indexMode +
                ", updatePolicy=" + updatePolicy +
                ", enableSorting=" + enableSorting +
                '}';
    }

    /**
     * Create parameter with full layout specification.
     */
    public static RasterizationParameter create(
            VertexLayoutSpec layout,
            PrimitiveType primitiveType,
            MeshIndexMode indexMode,
            BufferUpdatePolicy updatePolicy,
            boolean enableSorting) {
        return new RasterizationParameter(layout, primitiveType, indexMode, updatePolicy, enableSorting);
    }

    public record BuilderKey(StructLayout format, PrimitiveType primitiveType, boolean instanced){}

    public record BuilderBatchKey(VertexLayoutSpec spec, PrimitiveType primitiveType){}
}

