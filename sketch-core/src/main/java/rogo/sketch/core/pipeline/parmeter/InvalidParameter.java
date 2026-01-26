package rogo.sketch.core.pipeline.parmeter;

import rogo.sketch.core.pipeline.flow.RenderFlowType;

public final class InvalidParameter extends RenderParameter {
    public static final InvalidParameter INVALID = new InvalidParameter();

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
