package rogo.sketch.core.pipeline;

import rogo.sketch.core.util.KeyId;

import java.util.List;

public record TargetBindingDescriptor(
        KeyId renderTargetId,
        List<Object> drawBuffers,
        Boolean clearColor,
        Boolean clearDepth,
        KeyId passCompatibilityKey
) {
    public TargetBindingDescriptor {
        renderTargetId = renderTargetId != null ? renderTargetId : PipelineConfig.DEFAULT_RENDER_TARGET_ID;
        drawBuffers = drawBuffers != null ? List.copyOf(drawBuffers) : List.of();
        passCompatibilityKey = passCompatibilityKey != null
                ? passCompatibilityKey
                : KeyId.of("sketch:pass_target_" + Integer.toHexString(java.util.Objects.hash(renderTargetId, drawBuffers, clearColor, clearDepth)));
    }

    public static TargetBindingDescriptor from(TargetBinding binding) {
        TargetBinding targetBinding = binding != null
                ? binding
                : new TargetBinding(PipelineConfig.DEFAULT_RENDER_TARGET_ID, List.of(), null, null);
        return new TargetBindingDescriptor(
                targetBinding.renderTargetId(),
                targetBinding.drawBuffers(),
                targetBinding.clearColor(),
                targetBinding.clearDepth(),
                null);
    }
}
