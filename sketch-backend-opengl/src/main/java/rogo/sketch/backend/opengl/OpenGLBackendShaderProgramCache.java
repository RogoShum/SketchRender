package rogo.sketch.backend.opengl;

import rogo.sketch.core.backend.BackendShaderProgramCache;
import rogo.sketch.backend.opengl.driver.GraphicsAPI;
import rogo.sketch.core.shader.ShaderType;
import rogo.sketch.core.shader.ShaderProgramHandle;
import rogo.sketch.core.shader.variant.ShaderTemplate;
import rogo.sketch.core.shader.variant.ShaderVariantKey;
import rogo.sketch.core.shader.variant.ShaderVariantSpec;
import rogo.sketch.core.util.KeyId;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OpenGL compiled-program cache facade.
 */
public final class OpenGLBackendShaderProgramCache implements BackendShaderProgramCache {
    private final Map<CacheKey, OpenGLShaderProgramHandle> programCache = new ConcurrentHashMap<>();
    private final GraphicsAPI api;

    public OpenGLBackendShaderProgramCache(GraphicsAPI api) {
        this.api = api;
    }

    @Override
    public ShaderProgramHandle resolveProgram(ShaderTemplate template, ShaderVariantKey variantKey) throws IOException {
        if (template == null) {
            return null;
        }
        api.assertGLContext("OpenGLBackendShaderProgramCache.resolveProgram");
        ShaderVariantKey normalizedVariantKey = variantKey != null ? variantKey : ShaderVariantKey.EMPTY;
        long variantEpoch = template.variantEpoch();
        CacheKey cacheKey = new CacheKey(template.getTemplateId(), normalizedVariantKey, variantEpoch);

        OpenGLShaderProgramHandle cached = programCache.get(cacheKey);
        if (cached != null && !cached.isDisposed()) {
            return cached;
        }

        synchronized (programCache) {
            cached = programCache.get(cacheKey);
            if (cached != null && !cached.isDisposed()) {
                return cached;
            }

            ShaderVariantSpec variantSpec = template.resolveVariantSpec(normalizedVariantKey);
            OpenGLShaderProgramHandle compiled = compile(template, variantSpec);
            programCache.entrySet().removeIf(entry ->
                    entry.getKey().templateId().equals(template.getTemplateId())
                            && entry.getKey().variantKey().equals(normalizedVariantKey)
                            && entry.getKey().epoch() != variantEpoch);
            programCache.put(cacheKey, compiled);
            return compiled;
        }
    }

    private OpenGLShaderProgramHandle compile(ShaderTemplate template, ShaderVariantSpec variantSpec) throws IOException {
        KeyId variantId = createVariantId(template.getTemplateId(), variantSpec.variantKey());
        Map<ShaderType, String> processedSources = variantSpec.processedSources();
        Shader shader;
        if (processedSources.containsKey(ShaderType.COMPUTE)) {
            shader = new ComputeShader(api, variantId, processedSources.get(ShaderType.COMPUTE));
        } else {
            shader = new GraphicsShader(api, variantId, processedSources, variantSpec.interfaceSpec().activeVertexLayout());
        }
        shader.configureDeclaredResourceBindings(variantSpec.interfaceSpec().resourceBindings());
        return new OpenGLShaderProgramHandle(shader, variantSpec);
    }

    private static KeyId createVariantId(KeyId templateId, ShaderVariantKey variantKey) {
        if (variantKey == null || variantKey.isEmpty()) {
            return templateId;
        }
        return KeyId.of(templateId + "#" + variantKey.toCompactString());
    }

    private record CacheKey(KeyId templateId, ShaderVariantKey variantKey, long epoch) {
    }
}

