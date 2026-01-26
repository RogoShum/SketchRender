package rogo.sketch.core.pipeline.parmeter;

import rogo.sketch.core.pipeline.flow.RenderFlowType;

public class FunctionParameter extends RenderParameter {
    public static final FunctionParameter FUNCTION_PARAMETER = new FunctionParameter();

    public FunctionParameter() {
    }

    @Override
    public RenderFlowType getFlowType() {
        return RenderFlowType.FUNCTION;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof FunctionParameter;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }
}