package rogo.sketch.core.packet;

import rogo.sketch.core.pipeline.parmeter.RenderParameter;
import rogo.sketch.core.shader.variant.ShaderVariantKey;

public interface ExecutionKey {
    ExecutionDomain domain();

    default RenderParameter renderParameter() {
        return null;
    }

    default boolean shouldSwitchRenderState() {
        return false;
    }

    default rogo.sketch.core.util.KeyId shaderId() {
        return null;
    }

    default ShaderVariantKey shaderVariantKey() {
        return ShaderVariantKey.EMPTY;
    }

    default rogo.sketch.core.util.KeyId vertexLayoutKey() {
        return null;
    }

    default rogo.sketch.core.util.KeyId renderTargetKey() {
        return null;
    }

    default rogo.sketch.core.util.KeyId resourceLayoutKey() {
        return ResourceBindingPlan.empty().layoutKey();
    }

    default int rasterStateSignature() {
        return 0;
    }

    default ResourceBindingPlan bindingPlan() {
        return ResourceBindingPlan.empty();
    }
}
