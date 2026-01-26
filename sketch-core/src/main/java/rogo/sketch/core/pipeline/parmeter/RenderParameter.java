package rogo.sketch.core.pipeline.parmeter;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.data.PrimitiveType;
import rogo.sketch.core.data.format.VertexLayoutSpec;
import rogo.sketch.core.pipeline.flow.RenderFlowType;


public abstract class RenderParameter {

    public abstract RenderFlowType getFlowType();

    public boolean isInvalid() {
        return this == InvalidParameter.INVALID;
    }

    public boolean isRasterization() {
        return this instanceof RasterizationParameter;
    }

    @Nullable
    public VertexLayoutSpec getLayout() {
        return this instanceof RasterizationParameter rp ? rp.getLayout() : null;
    }

    @Nullable
    public PrimitiveType primitiveType() {
        return this instanceof RasterizationParameter rp ? rp.primitiveType() : null;
    }

    @Override
    public abstract boolean equals(Object o);

    @Override
    public abstract int hashCode();
}