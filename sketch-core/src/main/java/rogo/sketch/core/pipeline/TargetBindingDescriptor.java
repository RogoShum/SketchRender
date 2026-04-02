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
        renderTargetId = renderTargetId != null ? renderTargetId : TargetBinding.DEFAULT_RENDER_TARGET;
        drawBuffers = drawBuffers != null ? List.copyOf(drawBuffers) : List.of();
        passCompatibilityKey = passCompatibilityKey != null
                ? passCompatibilityKey
                : KeyId.of("sketch:pass_compat_" + Integer.toHexString(java.util.Objects.hash(renderTargetId, drawBuffers, clearColor, clearDepth)));
    }

    public static TargetBindingDescriptor from(TargetBinding binding) {
        TargetBinding targetBinding = binding != null ? binding : TargetBinding.defaultTarget();
        return new TargetBindingDescriptor(
                targetBinding.renderTargetId(),
                targetBinding.drawBuffers(),
                targetBinding.clearColor(),
                targetBinding.clearDepth(),
                null);
    }
}
