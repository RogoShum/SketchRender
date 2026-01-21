package rogo.sketch.render.pipeline;

import rogo.sketch.render.pipeline.flow.RenderFlowType;

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
        return 0;
    }
}