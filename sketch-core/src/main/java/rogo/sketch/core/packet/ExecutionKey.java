package rogo.sketch.core.packet;

import rogo.sketch.core.pipeline.parmeter.RenderParameter;
import rogo.sketch.core.shader.variant.ShaderVariantKey;
import rogo.sketch.core.util.KeyId;

public interface ExecutionKey {
    KeyId DEFAULT_RENDER_TARGET = KeyId.of("sketch:default_render_target");
    KeyId EMPTY_VERTEX_LAYOUT = KeyId.of("sketch:empty_vertex_layout");
    KeyId EMPTY_RESOURCE_LAYOUT = KeyId.of("sketch:empty_resource_layout");
    KeyId UNBOUND_SHADER = KeyId.of("sketch:unbound_shader");

    ExecutionDomain domain();

    default RenderParameter renderParameter() {
        return null;
    }

    default boolean shouldSwitchRenderState() {
        return false;
    }

    default KeyId shaderId() {
        return UNBOUND_SHADER;
    }

    default ShaderVariantKey shaderVariantKey() {
        return ShaderVariantKey.EMPTY;
    }

    default KeyId vertexLayoutKey() {
        return EMPTY_VERTEX_LAYOUT;
    }

    default KeyId renderTargetKey() {
        return DEFAULT_RENDER_TARGET;
    }

    default KeyId resourceLayoutKey() {
        return EMPTY_RESOURCE_LAYOUT;
    }

    default int rasterStateSignature() {
        return 0;
    }

    default ResourceBindingPlan bindingPlan() {
        return ResourceBindingPlan.empty();
    }
}
