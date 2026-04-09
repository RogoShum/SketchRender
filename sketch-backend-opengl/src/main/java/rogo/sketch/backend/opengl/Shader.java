package rogo.sketch.backend.opengl;

import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL43;
import rogo.sketch.core.api.ShaderProvider;
import rogo.sketch.backend.opengl.driver.GraphicsAPI;
import rogo.sketch.backend.opengl.internal.IGLShaderStrategy;
import rogo.sketch.core.resource.ResourceTypes;
import rogo.sketch.core.shader.ProgramReflectionRegistry;
import rogo.sketch.core.shader.ProgramReflectionService;
import rogo.sketch.core.shader.ShaderType;
import rogo.sketch.core.shader.preprocessor.PreprocessorResult;
import rogo.sketch.core.shader.preprocessor.ShaderPreprocessor;
import rogo.sketch.core.shader.preprocessor.ShaderPreprocessorException;
import rogo.sketch.core.shader.preprocessor.ShaderResourceProvider;
import rogo.sketch.core.shader.uniform.UniformHookGroup;
import rogo.sketch.core.shader.vertex.ShaderVertexLayout;
import rogo.sketch.core.util.KeyId;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public abstract class Shader implements ShaderProvider {
    private final GraphicsAPI api;
    private Map<KeyId, Map<KeyId, Integer>> resourceBindings;
    protected UniformHookGroup uniformHookGroup;
    protected final KeyId keyId;
    protected final int program;
    protected final Map<ShaderType, Integer> shaderIds = new HashMap<>();
    protected boolean disposed = false;

    protected Shader(GraphicsAPI api, KeyId keyId, Map<ShaderType, String> shaderSources) throws IOException {
        this(api, keyId, shaderSources, Map.of());
    }

    protected Shader(GraphicsAPI api, KeyId keyId, Map<ShaderType, String> shaderSources, Map<String, Integer> attributeBindings) throws IOException {
        this.api = api;
        this.keyId = keyId;
        this.program = shaderStrategy().createProgram();

        validateShaderTypes(shaderSources);
        compileAndAttachShaders(shaderSources);
        bindAttributeLocations(attributeBindings);
        linkProgram();
        cleanupShaders();

        collectAndInitializeUniforms();
        postLinkInitialization();
    }

    protected Shader(
            GraphicsAPI api,
            KeyId keyId,
            Map<ShaderType, String> shaderSources,
            ShaderPreprocessor preprocessor,
            Function<KeyId, Optional<InputStream>> resourceProvider,
            Map<String, String> macros,
            ShaderVertexLayout shaderVertexLayout,
            Map<String, Integer> attributeBindings) throws IOException {
        this.api = api;
        this.keyId = keyId;
        this.program = shaderStrategy().createProgram();

        Map<ShaderType, String> processedSources = preprocessSources(
                keyId,
                shaderSources,
                preprocessor,
                resourceProvider,
                macros,
                shaderVertexLayout);

        validateShaderTypes(processedSources);
        compileAndAttachShaders(processedSources);
        bindAttributeLocations(attributeBindings);
        linkProgram();
        cleanupShaders();

        collectAndInitializeUniforms();
        postLinkInitialization();
    }

    protected Shader(GraphicsAPI api, KeyId keyId, ShaderType type, String source) throws IOException {
        this(api, keyId, Map.of(type, source), Map.of());
    }

    protected Shader(
            GraphicsAPI api,
            KeyId keyId,
            ShaderType type,
            String source,
            ShaderPreprocessor preprocessor,
            Function<KeyId, Optional<InputStream>> resourceProvider,
            Map<String, String> macros,
            ShaderVertexLayout shaderVertexLayout) throws IOException {
        this(api, keyId, Map.of(type, source), preprocessor, resourceProvider, macros, shaderVertexLayout, Map.of());
    }

    protected final GraphicsAPI api() {
        return api;
    }

    protected final IGLShaderStrategy shaderStrategy() {
        return api.getShaderStrategy();
    }

    protected static Map<ShaderType, String> preprocessSources(
            KeyId keyId,
            Map<ShaderType, String> originalSources,
            ShaderPreprocessor preprocessor,
            Function<KeyId, Optional<InputStream>> resourceProvider,
            Map<String, String> macros,
            ShaderVertexLayout shaderVertexLayout) throws IOException {
        try {
            if (preprocessor != null && resourceProvider != null) {
                ShaderResourceProvider adapter = ShaderResourceProvider.fromGenericProvider(resourceProvider);
                preprocessor.setResourceProvider(adapter);
            }

            Map<String, String> macroMap = macros != null ? macros : new HashMap<>();
            Map<ShaderType, String> processedSources = new HashMap<>();
            for (Map.Entry<ShaderType, String> entry : originalSources.entrySet()) {
                PreprocessorResult result = preprocessor.process(entry.getValue(), keyId, macroMap, shaderVertexLayout);
                if (result == null) {
                    throw new IOException("Preprocessor returned null result for " + entry.getKey() + " shader");
                }
                processedSources.put(entry.getKey(), result.processedSource());
            }
            return processedSources;
        } catch (ShaderPreprocessorException e) {
            throw new IOException("Shader preprocessing failed for " + keyId, e);
        }
    }

    protected void compileAndAttachShaders(Map<ShaderType, String> shaderSources) throws IOException {
        IGLShaderStrategy strategy = shaderStrategy();
        for (Map.Entry<ShaderType, String> entry : shaderSources.entrySet()) {
            ShaderType type = entry.getKey();
            String source = entry.getValue();
            int shaderId = compileShader(type, source, keyId.toString());
            shaderIds.put(type, shaderId);
            strategy.attachShader(program, shaderId);
        }
    }

    protected void bindAttributeLocations(Map<String, Integer> attributeBindings) {
        if (attributeBindings == null || attributeBindings.isEmpty()) {
            return;
        }
        IGLShaderStrategy strategy = shaderStrategy();
        for (Map.Entry<String, Integer> entry : attributeBindings.entrySet()) {
            String attributeName = entry.getKey();
            Integer location = entry.getValue();
            if (attributeName == null || attributeName.isBlank() || location == null || location < 0) {
                continue;
            }
            strategy.bindAttribLocation(program, location, attributeName);
        }
    }

    private int compileShader(ShaderType type, String source, String shaderName) throws IOException {
        IGLShaderStrategy strategy = shaderStrategy();
        int shaderId = strategy.createShader(OpenGLShaderTypeMappings.toGlType(type));
        strategy.shaderSource(shaderId, source);
        strategy.compileShader(shaderId);
        if (strategy.getShaderi(shaderId, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String error = strategy.getShaderInfoLog(shaderId);
            strategy.deleteShader(shaderId);
            throw new IOException("Error compiling " + type.name().toLowerCase() + " shader [" + shaderName + "]:\n" + error);
        }
        return shaderId;
    }

    protected void linkProgram() throws IOException {
        IGLShaderStrategy strategy = shaderStrategy();
        strategy.linkProgram(program);
        if (strategy.getProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            String error = strategy.getProgramInfoLog(program);
            throw new IOException("Error linking shader program [" + keyId + "]:\n" + error);
        }
    }

    protected void cleanupShaders() {
        IGLShaderStrategy strategy = shaderStrategy();
        for (int shaderId : shaderIds.values()) {
            strategy.detachShader(program, shaderId);
            strategy.deleteShader(shaderId);
        }
        shaderIds.clear();
    }

    protected void collectAndInitializeUniforms() {
        bind();
        ProgramReflectionService reflection = ProgramReflectionRegistry.get();
        Map<String, rogo.sketch.core.api.ShaderResource<?>> uniforms = reflection.collectUniforms(program);
        this.uniformHookGroup = reflection.initializeHooks(program, uniforms);
        unbind();
    }

    private void discoverResourceBindings() {
        bind();
        this.resourceBindings = ProgramReflectionRegistry.get().discoverResourceBindings(program);
        unbind();
    }

    protected void postLinkInitialization() {
        discoverResourceBindings();
    }

    public void configureDeclaredResourceBindings(Map<KeyId, Map<KeyId, Integer>> declaredBindings) {
        if (declaredBindings == null || declaredBindings.isEmpty()) {
            discoverResourceBindings();
            return;
        }

        bind();
        try {
            applyUniformBindings(declaredBindings.get(ResourceTypes.TEXTURE));
            applyUniformBindings(declaredBindings.get(ResourceTypes.IMAGE));
            applyUniformBlockBindings(declaredBindings.get(ResourceTypes.UNIFORM_BUFFER));
            applyShaderStorageBlockBindings(declaredBindings.get(ResourceTypes.STORAGE_BUFFER));
        } finally {
            unbind();
        }
        discoverResourceBindings();
    }

    protected abstract void validateShaderTypes(Map<ShaderType, String> shaderSources);

    public void bind() {
        shaderStrategy().useProgram(program);
    }

    public void unbind() {
        shaderStrategy().useProgram(0);
    }

    @Override
    public int getHandle() {
        return program;
    }

    @Override
    public KeyId getIdentifier() {
        return keyId;
    }

    @Override
    public UniformHookGroup getUniformHookGroup() {
        return uniformHookGroup;
    }

    @Override
    public Map<KeyId, Map<KeyId, Integer>> getResourceBindings() {
        return resourceBindings;
    }

    @Override
    public void dispose() {
        if (!disposed && program > 0) {
            shaderStrategy().deleteProgram(program);
            disposed = true;
        }
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }

    private void applyUniformBindings(Map<KeyId, Integer> bindings) {
        if (bindings == null || bindings.isEmpty()) {
            return;
        }
        IGLShaderStrategy strategy = shaderStrategy();
        for (Map.Entry<KeyId, Integer> entry : bindings.entrySet()) {
            int location = findUniformLocation(strategy, entry.getKey().toString());
            if (location >= 0) {
                strategy.uniform1i(program, location, entry.getValue());
            }
        }
    }

    private void applyUniformBlockBindings(Map<KeyId, Integer> bindings) {
        if (bindings == null || bindings.isEmpty()) {
            return;
        }
        IGLShaderStrategy strategy = shaderStrategy();
        for (Map.Entry<KeyId, Integer> entry : bindings.entrySet()) {
            int blockIndex = strategy.getUniformBlockIndex(program, entry.getKey().toString());
            if (blockIndex != GL31.GL_INVALID_INDEX) {
                strategy.uniformBlockBinding(program, blockIndex, entry.getValue());
            }
        }
    }

    private void applyShaderStorageBlockBindings(Map<KeyId, Integer> bindings) {
        if (bindings == null || bindings.isEmpty() || !GL.getCapabilities().OpenGL43) {
            return;
        }
        for (Map.Entry<KeyId, Integer> entry : bindings.entrySet()) {
            int blockIndex = GL43.glGetProgramResourceIndex(program, GL43.GL_SHADER_STORAGE_BLOCK, entry.getKey().toString());
            if (blockIndex != GL43.GL_INVALID_INDEX) {
                GL43.glShaderStorageBlockBinding(program, blockIndex, entry.getValue());
            }
        }
    }

    private int findUniformLocation(IGLShaderStrategy strategy, String uniformName) {
        int location = strategy.getUniformLocation(program, uniformName);
        if (location >= 0) {
            return location;
        }
        return strategy.getUniformLocation(program, uniformName + "[0]");
    }
}

