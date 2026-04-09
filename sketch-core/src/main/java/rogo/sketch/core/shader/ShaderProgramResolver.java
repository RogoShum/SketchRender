package rogo.sketch.core.shader;

import rogo.sketch.core.api.ShaderProvider;
import rogo.sketch.core.backend.BackendShaderProgramCache;
import rogo.sketch.core.driver.GraphicsDriver;
import rogo.sketch.core.driver.state.component.ShaderState;
import rogo.sketch.core.pipeline.CompiledRenderSetting;
import rogo.sketch.core.pipeline.RenderSetting;
import rogo.sketch.core.resource.ResourceReference;
import rogo.sketch.core.resource.ResourceTypes;
import rogo.sketch.core.shader.uniform.UniformHookGroup;
import rogo.sketch.core.shader.variant.ShaderProgramInterfaceSpec;
import rogo.sketch.core.shader.variant.ShaderTemplate;
import rogo.sketch.core.shader.variant.ShaderVariantKey;
import rogo.sketch.core.util.KeyId;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

/**
 * Resolves live shader program handles when the active backend exposes a
 * compiled program cache/runtime for them.
 */
public final class ShaderProgramResolver {
    private static final KeyId COMPAT_PROGRAM_ID = KeyId.of("sketch:compat_program");

    private ShaderProgramResolver() {
    }

    public static ShaderProgramHandle resolveProgramHandle(CompiledRenderSetting compiledRenderSetting) throws IOException {
        if (compiledRenderSetting == null) {
            return null;
        }
        return resolveProgramHandle(compiledRenderSetting.renderSetting());
    }

    public static ShaderProgramHandle resolveProgramHandle(RenderSetting renderSetting) throws IOException {
        if (renderSetting == null
                || renderSetting.renderState() == null
                || !(renderSetting.renderState().get(ResourceTypes.SHADER_TEMPLATE) instanceof ShaderState shaderState)) {
            return null;
        }
        return resolveProgramHandle(shaderState);
    }

    public static ShaderProgramHandle resolveProgramHandleIfAvailable(CompiledRenderSetting compiledRenderSetting) {
        try {
            return resolveProgramHandle(compiledRenderSetting);
        } catch (IOException ignored) {
            return null;
        }
    }

    public static ShaderProgramHandle resolveProgramHandleIfAvailable(RenderSetting renderSetting) {
        try {
            return resolveProgramHandle(renderSetting);
        } catch (IOException ignored) {
            return null;
        }
    }

    public static ShaderProgramHandle resolveProgramHandle(ShaderState shaderState) throws IOException {
        if (shaderState == null) {
            return null;
        }

        ResourceReference<ShaderTemplate> reference = shaderState.getTemplate();
        if (reference == null || !reference.isAvailable()) {
            return null;
        }
        ShaderTemplate shaderTemplate = reference.get();
        if (shaderTemplate == null || shaderTemplate.isDisposed()) {
            return null;
        }

        ShaderVariantKey variantKey = shaderState.getVariantKey();
        BackendShaderProgramCache shaderProgramCache = GraphicsDriver.runtime() != null
                ? GraphicsDriver.runtime().shaderProgramCache()
                : BackendShaderProgramCache.NO_OP;
        return shaderProgramCache.resolveProgram(shaderTemplate, variantKey);
    }

    public static ComputeShader resolveComputeShader(ShaderProvider shaderProvider) {
        if (shaderProvider instanceof ComputeShader computeShader) {
            return computeShader;
        }
        ShaderProgramHandle handle = adaptProgramHandle(shaderProvider);
        if (handle != null) {
            return handle.computeShaderAdapter();
        }
        return null;
    }

    public static ShaderProgramHandle adaptProgramHandle(ShaderProvider shaderProvider) {
        if (shaderProvider instanceof ShaderProgramHandle handle) {
            return handle;
        }
        if (shaderProvider == null) {
            return EmptyShaderProgramHandle.INSTANCE;
        }
        return new CompatShaderProgramHandle(shaderProvider);
    }

    private record CompatShaderProgramHandle(ShaderProvider delegate) implements ShaderProgramHandle {
        @Override
        public KeyId templateId() {
            return delegate.getIdentifier();
        }

        @Override
        public ShaderVariantKey variantKey() {
            return ShaderVariantKey.EMPTY;
        }

        @Override
        public KeyId getIdentifier() {
            return delegate.getIdentifier();
        }

        @Override
        public ShaderProgramInterfaceSpec interfaceSpec() {
            return new ShaderProgramInterfaceSpec(
                    rogo.sketch.core.shader.vertex.ActiveShaderVertexLayout.empty(),
                    delegate.getResourceBindings(),
                    rogo.sketch.core.shader.variant.ShaderUniformSchema.empty());
        }

        @Override
        public UniformHookGroup uniformHooks() {
            return delegate.getUniformHookGroup();
        }

        @Override
        public int getHandle() {
            return delegate.getHandle();
        }

        @Override
        public void dispose() {
            delegate.dispose();
        }

        @Override
        public boolean isDisposed() {
            return delegate.isDisposed();
        }
    }

    private enum EmptyShaderProgramHandle implements ShaderProgramHandle {
        INSTANCE;

        private final UniformHookGroup uniformHookGroup = new UniformHookGroup();

        @Override
        public KeyId templateId() {
            return COMPAT_PROGRAM_ID;
        }

        @Override
        public ShaderVariantKey variantKey() {
            return ShaderVariantKey.EMPTY;
        }

        @Override
        public KeyId getIdentifier() {
            return COMPAT_PROGRAM_ID;
        }

        @Override
        public ShaderProgramInterfaceSpec interfaceSpec() {
            return ShaderProgramInterfaceSpec.empty();
        }

        @Override
        public UniformHookGroup uniformHooks() {
            return uniformHookGroup;
        }

        @Override
        public int getHandle() {
            return 0;
        }

        @Override
        public void dispose() {
        }

        @Override
        public boolean isDisposed() {
            return false;
        }
    }
}

