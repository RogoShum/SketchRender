package rogo.sketch.backend.opengl;

import rogo.sketch.core.backend.BackendInstalledShaderProgram;
import rogo.sketch.core.shader.ShaderProgramHandle;
import rogo.sketch.core.shader.uniform.UniformHookGroup;
import rogo.sketch.core.shader.variant.ShaderProgramInterfaceSpec;
import rogo.sketch.core.shader.variant.ShaderVariantKey;
import rogo.sketch.core.shader.variant.ShaderVariantSpec;
import rogo.sketch.core.util.KeyId;

/**
 * OpenGL runtime shader program handle backed by a compiled GL program object.
 */
public final class OpenGLShaderProgramHandle implements BackendInstalledShaderProgram {
    private final Shader shader;
    private final ShaderVariantSpec variantSpec;

    public OpenGLShaderProgramHandle(Shader shader, ShaderVariantSpec variantSpec) {
        this.shader = shader;
        this.variantSpec = variantSpec;
    }

    public Shader shader() {
        return shader;
    }

    @Override
    public rogo.sketch.core.shader.ComputeShader computeShaderAdapter() {
        return shader instanceof ComputeShader computeShader ? computeShader : null;
    }

    @Override
    public KeyId templateId() {
        return variantSpec.templateId();
    }

    @Override
    public ShaderVariantKey variantKey() {
        return variantSpec.variantKey();
    }

    @Override
    public ShaderProgramInterfaceSpec interfaceSpec() {
        return variantSpec.interfaceSpec();
    }

    @Override
    public UniformHookGroup uniformHooks() {
        return shader.getUniformHookGroup();
    }

    @Override
    public int getHandle() {
        return shader.getHandle();
    }

    @Override
    public KeyId getIdentifier() {
        return shader.getIdentifier();
    }

    @Override
    public void dispose() {
        shader.dispose();
    }

    @Override
    public boolean isDisposed() {
        return shader.isDisposed();
    }
}

