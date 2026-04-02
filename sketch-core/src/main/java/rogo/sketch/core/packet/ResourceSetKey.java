package rogo.sketch.core.packet;

import rogo.sketch.core.shader.uniform.ResourceUniformSet;
import rogo.sketch.core.util.KeyId;

import java.util.Objects;

public record ResourceSetKey(
        KeyId resourceLayoutKey,
        int resourceBindingHash,
        int resourceUniformHash
) {
    private static final KeyId EMPTY_LAYOUT = KeyId.of("sketch:empty_resource_layout");
    private static final ResourceSetKey EMPTY = new ResourceSetKey(EMPTY_LAYOUT, 0, 0);

    public ResourceSetKey {
        resourceLayoutKey = resourceLayoutKey != null ? resourceLayoutKey : EMPTY_LAYOUT;
    }

    public static ResourceSetKey empty() {
        return EMPTY;
    }

    public static ResourceSetKey from(ResourceBindingPlan bindingPlan, ResourceUniformSet resourceUniforms) {
        ResourceBindingPlan compiledPlan = bindingPlan != null ? bindingPlan : ResourceBindingPlan.empty();
        ResourceUniformSet uniformSet = resourceUniforms != null ? resourceUniforms : ResourceUniformSet.empty();
        return new ResourceSetKey(
                compiledPlan.layoutKey(),
                compiledPlan.resourceBindingHash(),
                Objects.hash(uniformSet.legacySnapshot()));
    }

    public boolean isEmpty() {
        return resourceBindingHash == 0
                && resourceUniformHash == 0
                && EMPTY_LAYOUT.equals(resourceLayoutKey);
    }
}
