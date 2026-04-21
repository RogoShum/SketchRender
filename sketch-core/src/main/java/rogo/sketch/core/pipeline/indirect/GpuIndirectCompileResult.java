package rogo.sketch.core.pipeline.indirect;

import rogo.sketch.core.packet.draw.IndirectCommandRange;
import rogo.sketch.core.util.KeyId;

import java.util.List;

public record GpuIndirectCompileResult(
        boolean handled,
        List<KeyId> readResources,
        List<KeyId> writeResources,
        IndirectCommandRange indirectCommandRange,
        String reason
) {
    public GpuIndirectCompileResult {
        readResources = readResources != null ? List.copyOf(readResources) : List.of();
        writeResources = writeResources != null ? List.copyOf(writeResources) : List.of();
        reason = reason != null ? reason : "";
    }

    public static GpuIndirectCompileResult unhandled(String reason) {
        return new GpuIndirectCompileResult(false, List.of(), List.of(), null, reason);
    }
}
