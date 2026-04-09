package rogo.sketch.core.pipeline.flow.v2;

import rogo.sketch.core.packet.ResourceSetKey;
import rogo.sketch.core.pipeline.CompiledRenderSetting;
import rogo.sketch.core.shader.uniform.UniformValueSnapshot;

import java.util.List;

public record ComputeDispatchSlice(
        CompiledRenderSetting compiledRenderSetting,
        ResourceSetKey resourceSetKey,
        UniformValueSnapshot uniformSnapshot,
        List<ComputeInstanceStore.Entry> entries
) {
    public ComputeDispatchSlice {
        uniformSnapshot = uniformSnapshot != null ? uniformSnapshot : UniformValueSnapshot.empty();
        entries = entries != null ? List.copyOf(entries) : List.of();
    }
}

