package rogo.sketch.core.shader;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import rogo.sketch.core.api.ShaderProvider;
import rogo.sketch.core.driver.GraphicsDriver;
import rogo.sketch.core.driver.internal.IGLShaderStrategy;
import rogo.sketch.core.shader.preprocessor.PreprocessorResult;
import rogo.sketch.core.shader.preprocessor.ShaderPreprocessor;
import rogo.sketch.core.shader.preprocessor.ShaderPreprocessorException;
import rogo.sketch.core.shader.preprocessor.ShaderResourceProvider;
import rogo.sketch.core.shader.uniform.ShaderUniform;
import rogo.sketch.core.shader.uniform.UniformHookGroup;
import rogo.sketch.core.shader.vertex.ShaderVertexLayout;
import rogo.sketch.core.util.KeyId;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Base class for all shaders (graphics and compute).
 * <p>
 * This class handles shader compilation and linking. Uniform discovery
 * and resource binding discovery are delegated to {@link ShaderResourceHelper}
 * to keep this class focused on core shader functionality.
 */
public abstract class Shader implements ShaderProvider {
    private Map<KeyId, Map<KeyId, Integer>> resourceBindings;
    protected UniformHookGroup uniformHookGroup;
    protected final KeyId keyId;
    protected final int program;
    protected final Map<ShaderType, Integer> shaderIds = new HashMap<>();
    protected boolean disposed = false;

    /**
     * Get the shader strategy from the current graphics API
     */
    protected static IGLShaderStrategy getShaderStrategy() {
        return GraphicsDriver.getCurrentAPI().getShaderStrategy();
    }

    /**
     * Create a shader program from GLSL source code
     *
     * @param keyId         Unique identifier for this shader
     * @param shaderSources Map of shader types to their GLSL source code
     */
    public Shader(KeyId keyId, Map<ShaderType, String> shaderSources) throws IOException {
        this.keyId = keyId;
        this.program = getShaderStrategy().createProgram();

        validateShaderTypes(shaderSources);
        compileAndAttachShaders(shaderSources);
        linkProgram();
        cleanupShaders();

        collectAndInitializeUniforms();
        postLinkInitialization();
    }

    /**
     * Create a shader program with preprocessing support
     *
     * @param keyId            Unique identifier for this shader
     * @param shaderSources    Map of shader types to their GLSL source code (original, unprocessed)
     * @param preprocessor     Shader preprocessor for handling imports and macros
     * @param resourceProvider Resource provider for loading imported files
     */
    public Shader(KeyId keyId,
                  Map<ShaderType, String> shaderSources,
                  ShaderPreprocessor preprocessor,
                  Function<KeyId, Optional<InputStream>> resourceProvider) throws IOException {
        this(keyId, shaderSources, preprocessor, resourceProvider, null, null);
    }

    /**
     * Create a shader program with preprocessing support and macros
     *
     * @param keyId            Unique identifier for this shader
     * @param shaderSources    Map of shader types to their GLSL source code (original, unprocessed)
     * @param preprocessor     Shader preprocessor for handling imports and macros
     * @param resourceProvider Resource provider for loading imported files
     * @param macros           Map of macro names to values (can be null for empty macros)
     */
    public Shader(KeyId keyId,
                  Map<ShaderType, String> shaderSources,
                  ShaderPreprocessor preprocessor,
                  Function<KeyId, Optional<InputStream>> resourceProvider,
                  Map<String, String> macros,
                  ShaderVertexLayout shaderVertexLayout) throws IOException {
        this.keyId = keyId;
        this.program = getShaderStrategy().createProgram();

        // Preprocess all sources before compilation
        Map<ShaderType, String> processedSources = preprocessSources(keyId, shaderSources, preprocessor, resourceProvider, macros, shaderVertexLayout);

        validateShaderTypes(processedSources);
        compileAndAttachShaders(processedSources);
        linkProgram();
        cleanupShaders();

        collectAndInitializeUniforms();
        postLinkInitialization();
    }

    /**
     * Create a shader program with a single shader type
     */
    public Shader(KeyId keyId, ShaderType type, String source) throws IOException {
        this(keyId, Map.of(type, source));
    }

    /**
     * Create a shader program with a single shader type and preprocessing support
     */
    public Shader(KeyId keyId,
                  ShaderType type,
                  String source,
                  ShaderPreprocessor preprocessor,
                  Function<KeyId, Optional<InputStream>> resourceProvider) throws IOException {
        this(keyId, Map.of(type, source), preprocessor, resourceProvider, null, null);
    }

    /**
     * Create a shader program with a single shader type, preprocessing support and macros
     */
    public Shader(KeyId keyId,
                  ShaderType type,
                  String source,
                  ShaderPreprocessor preprocessor,
                  Function<KeyId, Optional<InputStream>> resourceProvider,
                  Map<String, String> macros,
                  ShaderVertexLayout shaderVertexLayout) throws IOException {
        this(keyId, Map.of(type, source), preprocessor, resourceProvider, macros, shaderVertexLayout);
    }

    /**
     * Preprocess shader sources with macros
     */
    protected static Map<ShaderType, String> preprocessSources(KeyId keyId,
                                                               Map<ShaderType, String> originalSources,
                                                               ShaderPreprocessor preprocessor,
                                                               Function<KeyId, Optional<InputStream>> resourceProvider,
                                                               Map<String, String> macros,
                                                               ShaderVertexLayout shaderVertexLayout) throws IOException {
        try {
            // Set up the resource provider for the preprocessor
            if (preprocessor != null && resourceProvider != null) {
                ShaderResourceProvider adapter = ShaderResourceProvider.fromGenericProvider(resourceProvider);
                preprocessor.setResourceProvider(adapter);
            }

            // Use empty map if macros is null
            Map<String, String> macroMap = macros != null ? macros : new HashMap<>();

            Map<ShaderType, String> processedSources = new HashMap<>();

            // Preprocess each source
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
        IGLShaderStrategy strategy = getShaderStrategy();
        for (Map.Entry<ShaderType, String> entry : shaderSources.entrySet()) {
            ShaderType type = entry.getKey();
            String source = entry.getValue();

            int shaderId = compileShader(type, source, keyId.toString());
            shaderIds.put(type, shaderId);
            strategy.attachShader(program, shaderId);
        }
    }

    private int compileShader(ShaderType type, String source, String shaderName) throws IOException {
        IGLShaderStrategy strategy = getShaderStrategy();
        int shaderId = strategy.createShader(type.getGLType());
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
        IGLShaderStrategy strategy = getShaderStrategy();
        strategy.linkProgram(program);

        if (strategy.getProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            String error = strategy.getProgramInfoLog(program);
            throw new IOException("Error linking shader program [" + keyId + "]:\n" + error);
        }
    }

    protected void cleanupShaders() {
        IGLShaderStrategy strategy = getShaderStrategy();
        for (int shaderId : shaderIds.values()) {
            strategy.detachShader(program, shaderId);
            strategy.deleteShader(shaderId);
        }
        shaderIds.clear();
    }

    /**
     * Collect uniforms and initialize hooks using ShaderResourceHelper.
     */
    protected void collectAndInitializeUniforms() {
        bind();
        Map<String, ShaderUniform<?>> uniforms = ShaderResourceHelper.collectUniforms(program);
        this.uniformHookGroup = ShaderResourceHelper.initializeHooks(program, uniforms);
        unbind();
    }

    /**
     * Discover resource bindings using ShaderResourceHelper.
     */
    private void discoverResourceBindings() {
        bind();
        this.resourceBindings = ShaderResourceHelper.discoverResourceBindings(program);
        unbind();
    }

    protected void postLinkInitialization() {
        discoverResourceBindings();
    }

    protected abstract void validateShaderTypes(Map<ShaderType, String> shaderSources);

    public void bind() {
        getShaderStrategy().useProgram(program);
    }

    public static void unbind() {
        GraphicsDriver.getCurrentAPI().getShaderStrategy().useProgram(0);
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

    /**
     * Dispose of all GPU resources
     */
    @Override
    public void dispose() {
        if (program > 0) {
            getShaderStrategy().deleteProgram(program);
            disposed = true;
        }
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" +
                "identifier=" + keyId +
                ", program=" + program +
                '}';
    }
}