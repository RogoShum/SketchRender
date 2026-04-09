package rogo.sketch.core.api.graphics;

import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.shader.ComputeShader;

import java.util.function.BiConsumer;

public interface DispatchProvider {
    default ComputeDispatchCommand getDispatchOperation() {
        BiConsumer<RenderContext, ComputeShader> legacyDispatch = getDispatchCommand();
        if (legacyDispatch == null) {
            return null;
        }
        return dispatchContext -> legacyDispatch.accept(
                dispatchContext.renderContext(),
                dispatchContext.programHandle() != null
                        ? dispatchContext.programHandle().computeShaderAdapter()
                        : null);
    }

    @Deprecated(forRemoval = false)
    default BiConsumer<RenderContext, ComputeShader> getDispatchCommand() {
        return null;
    }
}

