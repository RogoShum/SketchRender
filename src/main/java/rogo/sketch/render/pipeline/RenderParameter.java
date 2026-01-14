package rogo.sketch.render.pipeline;

import rogo.sketch.render.data.PrimitiveType;
import rogo.sketch.render.data.format.ComponentSpec;
import rogo.sketch.render.data.format.DataFormat;
import rogo.sketch.render.data.format.VertexLayoutSpec;
import rogo.sketch.render.pipeline.flow.RenderFlowType;

import javax.annotation.Nullable;

public abstract class RenderParameter {
    public static final RenderParameter INVALID = new InvalidParameter();

    public abstract RenderFlowType getFlowType();

    public boolean isInvalid() {
        return this == INVALID;
    }

    public boolean isRasterization() {
        return this instanceof RasterizationParameter;
    }

    @Nullable
    public VertexLayoutSpec getLayout() {
        return this instanceof RasterizationParameter rp ? rp.getLayout() : null;
    }

    @Deprecated

    @Nullable
    public PrimitiveType primitiveType() {
        return this instanceof RasterizationParameter rp ? rp.primitiveType() : null;
    }

    @Override
    public abstract boolean equals(Object o);

    @Override
    public abstract int hashCode();

    private static final class InvalidParameter extends RenderParameter {

        @Override
        public RenderFlowType getFlowType() {
            return RenderFlowType.RASTERIZATION; // Default to rasterization
        }

        @Override
        public boolean equals(Object o) {
            return this == o;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(this);
        }

        @Override
        public String toString() {
            return "RenderParameter.INVALID";
        }
    }
}
