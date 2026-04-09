package rogo.sketch.core.api.graphics;

@FunctionalInterface
public interface ComputeDispatchCommand {
    void dispatch(ComputeDispatchContext context);
}

