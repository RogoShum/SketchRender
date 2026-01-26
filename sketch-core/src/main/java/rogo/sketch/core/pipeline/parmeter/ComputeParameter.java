package rogo.sketch.core.pipeline.parmeter;

import rogo.sketch.core.pipeline.flow.RenderFlowType;

public class ComputeParameter extends RenderParameter {
    public static final ComputeParameter COMPUTE_PARAMETER = new ComputeParameter();

    public ComputeParameter() {
    }

    @Override
    public RenderFlowType getFlowType() {
        return RenderFlowType.COMPUTE;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ComputeParameter;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }
}