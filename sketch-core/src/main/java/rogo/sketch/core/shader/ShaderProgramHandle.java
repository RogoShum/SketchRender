package rogo.sketch.core.shader;

import rogo.sketch.core.api.ShaderProvider;
import rogo.sketch.core.shader.uniform.UniformHookGroup;
import rogo.sketch.core.shader.variant.ShaderProgramInterfaceSpec;
import rogo.sketch.core.shader.variant.ShaderVariantKey;
import rogo.sketch.core.util.KeyId;

/**
 * Backend-facing handle for a compiled shader program variant.
 * <p>
 * Core systems should prefer this indirection over concrete OpenGL compiled
 * program classes when they need a live program object.
 * </p>
 */
public interface ShaderProgramHandle extends ShaderProvider {
    KeyId templateId();

    ShaderVariantKey variantKey();

    ShaderProgramInterfaceSpec interfaceSpec();

    UniformHookGroup uniformHooks();

    default int programHandle() {
        return getHandle();
    }

    default ComputeShader computeShaderAdapter() {
        return null;
    }

    @Override
    default UniformHookGroup getUniformHookGroup() {
        return uniformHooks();
    }

    @Override
    default java.util.Map<KeyId, java.util.Map<KeyId, Integer>> getResourceBindings() {
        return interfaceSpec().resourceBindings();
    }
}

