package rogo.sketch.core.pipeline.flow.v2;

import rogo.sketch.core.instance.StandardFunctionGraphics;
import rogo.sketch.core.pipeline.CompiledRenderSetting;

import java.util.List;

public record FunctionCommandSlice(
        CompiledRenderSetting compiledRenderSetting,
        List<Invocation> invocations
) {
    public FunctionCommandSlice {
        invocations = invocations != null ? List.copyOf(invocations) : List.of();
    }

    public record Invocation(
            FunctionInstanceStore.Entry entry,
            StandardFunctionGraphics.Command command
    ) {
    }
}

