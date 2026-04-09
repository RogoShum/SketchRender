package rogo.sketch.core.backend;

import rogo.sketch.core.shader.ShaderProgramHandle;
import rogo.sketch.core.shader.variant.ShaderTemplate;
import rogo.sketch.core.shader.variant.ShaderVariantKey;

import java.io.IOException;

/**
 * Backend-specific cache for compiled shader program variants.
 */
public interface BackendShaderProgramCache {
    BackendShaderProgramCache NO_OP = (template, variantKey) -> null;

    ShaderProgramHandle resolveProgram(ShaderTemplate template, ShaderVariantKey variantKey) throws IOException;
}

