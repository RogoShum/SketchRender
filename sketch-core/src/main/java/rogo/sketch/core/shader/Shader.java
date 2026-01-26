package rogo.sketch.core.shader;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import rogo.sketch.core.api.ShaderProvider;
import rogo.sketch.core.api.ShaderResource;
import rogo.sketch.core.resource.ResourceTypes;
import rogo.sketch.core.shader.config.ShaderConfiguration;
import rogo.sketch.core.shader.config.ShaderConfigurationManager;
import rogo.sketch.core.shader.preprocessor.PreprocessorResult;
import rogo.sketch.core.shader.preprocessor.ShaderPreprocessor;
import rogo.sketch.core.shader.preprocessor.ShaderPreprocessorException;
import rogo.sketch.core.shader.preprocessor.ShaderResourceProvider;
import rogo.sketch.core.shader.uniform.ShaderUniform;
import rogo.sketch.core.shader.uniform.UniformHookGroup;
import rogo.sketch.core.shader.uniform.UniformHookRegistry;
import rogo.sketch.core.data.DataType;
import rogo.sketch.core.util.KeyId;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Base class for all shaders (graphics and compute)
 */
public abstract class Shader implements ShaderProvider {
    private final Map<KeyId, Map<KeyId, Integer>> resourceBindings = new HashMap<>();
    protected final UniformHookGroup uniformHookGroup = new UniformHookGroup();
    protected final KeyId keyId;
    protected final int program;
    protected final Map<ShaderType, Integer> shaderIds = new HashMap<>();
    protected boolean disposed = false;

    /**
     * Create a shader program from GLSL source code
     *
     * @param keyId    Unique identifier for this shader
     * @param shaderSources Map of shader types to their GLSL source code
     */
    public Shader(KeyId keyId, Map<ShaderType, String> shaderSources) throws IOException {
        this.keyId = keyId;
        this.program = GL20.glCreateProgram();

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
     * @param keyId       Unique identifier for this shader
     * @param shaderSources    Map of shader types to their GLSL source code (original, unprocessed)
     * @param preprocessor     Shader preprocessor for handling imports and macros
     * @param resourceProvider Resource provider for loading imported files
     */
    public Shader(KeyId keyId,
                  Map<ShaderType, String> shaderSources,
                  ShaderPreprocessor preprocessor,
                  Function<KeyId, Optional<InputStream>> resourceProvider) throws IOException {
        this.keyId = keyId;
        this.program = GL20.glCreateProgram();

        // Preprocess all sources before compilation
        Map<ShaderType, String> processedSources = preprocessSources(keyId, shaderSources, preprocessor, resourceProvider);

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
        this(keyId, Map.of(type, source), preprocessor, resourceProvider);
    }

    /**
     * Preprocess shader sources with current configuration
     */
    protected static Map<ShaderType, String> preprocessSources(KeyId keyId,
                                                               Map<ShaderType, String> originalSources,
                                                               ShaderPreprocessor preprocessor,
                                                               Function<KeyId, Optional<InputStream>> resourceProvider) throws IOException {
        try {
            // Set up the resource provider for the preprocessor
            if (preprocessor != null && resourceProvider != null) {
                ShaderResourceProvider adapter = ShaderResourceProvider.fromGenericProvider(resourceProvider);
                preprocessor.setResourceProvider(adapter);
            }

            // Get current configuration
            ShaderConfiguration config =
                    ShaderConfigurationManager.getInstance().getConfiguration(keyId);

            Map<ShaderType, String> processedSources = new HashMap<>();

            // Preprocess each source
            for (Map.Entry<ShaderType, String> entry : originalSources.entrySet()) {
                PreprocessorResult result =
                        preprocessor.process(entry.getValue(), keyId, config.getMacros());
                processedSources.put(entry.getKey(), result.processedSource());
            }

            return processedSources;

        } catch (ShaderPreprocessorException e) {
            throw new IOException("Shader preprocessing failed for " + keyId, e);
        }
    }

    protected void compileAndAttachShaders(Map<ShaderType, String> shaderSources) throws IOException {
        for (Map.Entry<ShaderType, String> entry : shaderSources.entrySet()) {
            ShaderType type = entry.getKey();
            String source = entry.getValue();

            int shaderId = compileShader(type, source, keyId.toString());
            shaderIds.put(type, shaderId);
            GL20.glAttachShader(program, shaderId);
        }
    }

    private int compileShader(ShaderType type, String source, String shaderName) throws IOException {
        int shaderId = GL20.glCreateShader(type.getGLType());
        GL20.glShaderSource(shaderId, source);
        GL20.glCompileShader(shaderId);

        if (GL20.glGetShaderi(shaderId, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String error = GL20.glGetShaderInfoLog(shaderId);
            GL20.glDeleteShader(shaderId);
            throw new IOException("Error compiling " + type.name().toLowerCase() + " shader [" + shaderName + "]:\n" + error);
        }

        return shaderId;
    }

    protected void linkProgram() throws IOException {
        GL20.glLinkProgram(program);

        if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            String error = GL20.glGetProgramInfoLog(program);
            throw new IOException("Error linking shader program [" + keyId + "]:\n" + error);
        }
    }

    protected void cleanupShaders() {
        for (int shaderId : shaderIds.values()) {
            GL20.glDetachShader(program, shaderId);
            GL20.glDeleteShader(shaderId);
        }
        shaderIds.clear();
    }

    protected void collectAndInitializeUniforms() {
        UnifiedUniformInfo uniformInfo = discoverAllUniforms();
        UniformHookRegistry.getInstance().initializeHooks(this, uniformInfo.uniformMap);
    }

    private void discoverResourceBindings() {
        discoverSSBOBindings();
        discoverUBOBindings();
    }

    /**
     * Unified uniform and resource discovery to eliminate redundant GL queries
     */
    private UnifiedUniformInfo discoverAllUniforms() {
        bind();
        Map<String, ShaderResource<?>> uniforms = new HashMap<>();
        Map<KeyId, Integer> textureBindings = new HashMap<>();
        Map<KeyId, Integer> imageBindings = new HashMap<>();

        // Reusable buffers to avoid repeated allocations
        IntBuffer sizeBuffer = BufferUtils.createIntBuffer(1);
        IntBuffer typeBuffer = BufferUtils.createIntBuffer(1);

        int uniformCount = GL20.glGetProgrami(program, GL20.GL_ACTIVE_UNIFORMS);
        int nextTextureUnit = 0;

        for (int i = 0; i < uniformCount; i++) {
            sizeBuffer.clear();
            typeBuffer.clear();

            String uniformName = GL20.glGetActiveUniform(program, i, sizeBuffer, typeBuffer);
            int location = GL20.glGetUniformLocation(program, uniformName);
            uniformName = uniformName.replaceFirst("\\[0]$", "");
            int glType = typeBuffer.get(0);
            int glSize = sizeBuffer.get(0);

            if (location >= 0) {
                // Create shader uniform for regular uniforms
                DataType dataType = inferUniformType(glType);
                if (dataType != null) {
                    KeyId uniformId = KeyId.of(uniformName);
                    ShaderUniform<?> shaderUniform = new ShaderUniform<>(uniformId, location, dataType, glSize, program);
                    uniforms.put(uniformName, shaderUniform);
                }

                if (isSamplerType(glType)) {
                    int unit = nextTextureUnit++;
                    GL20.glUniform1i(location, unit);
                    textureBindings.put(KeyId.of(uniformName), unit);
                    System.out.println("Discovered Texture: " + uniformName + " -> unit " + unit);
                }

                // Check for image uniforms (requires OpenGL 4.2+)
                if (GL.getCapabilities().OpenGL42 && isImageType(glType)) {
                    int unit = GL20.glGetUniformi(program, location);
                    imageBindings.put(KeyId.of(uniformName), unit);
                    System.out.println("Discovered Image: " + uniformName + " -> unit " + unit);
                }
            }
        }

        // Store discovered bindings
        if (!textureBindings.isEmpty()) {
            resourceBindings.put(ResourceTypes.TEXTURE, textureBindings);
        }

        if (!imageBindings.isEmpty()) {
            resourceBindings.put(ResourceTypes.IMAGE_BUFFER, imageBindings);
        }

        unbind();

        return new UnifiedUniformInfo(uniforms, textureBindings, imageBindings);
    }

    /**
     * Container for unified uniform discovery results
     */
    private record UnifiedUniformInfo(Map<String, ShaderResource<?>> uniformMap,
                                      Map<KeyId, Integer> textureBindings,
                                      Map<KeyId, Integer> imageBindings) {
    }

    /**
     * Discover Shader Storage Buffer Object bindings
     */
    private void discoverSSBOBindings() {
        if (!GL.getCapabilities().OpenGL43) return;

        int numSSBOs = GL43.glGetProgramInterfacei(program,
                GL43.GL_SHADER_STORAGE_BLOCK,
                GL43.GL_ACTIVE_RESOURCES);

        if (numSSBOs > 0) {
            resourceBindings.put(ResourceTypes.SHADER_STORAGE_BUFFER, new HashMap<>());
            Map<KeyId, Integer> ssboBindings = resourceBindings.get(ResourceTypes.SHADER_STORAGE_BUFFER);

            IntBuffer props = BufferUtils.createIntBuffer(1).put(0, GL43.GL_BUFFER_BINDING); // query property
            IntBuffer params = BufferUtils.createIntBuffer(1);

            for (int i = 0; i < numSSBOs; i++) {
                String blockName = GL43.glGetProgramResourceName(program, GL43.GL_SHADER_STORAGE_BLOCK, i, 256);

                params.clear();
                GL43.glGetProgramResourceiv(program,
                        GL43.GL_SHADER_STORAGE_BLOCK,
                        i,
                        props,     // props contains GL_BUFFER_BINDING
                        null,      // length not needed
                        params);   // result written here

                int binding = params.get(0);
                ssboBindings.put(KeyId.of(blockName), binding);
                System.out.println("Discovered SSBO: " + blockName + " -> binding " + binding);
            }
        }
    }

    /**
     * Discover Uniform Buffer Object bindings
     */
    private void discoverUBOBindings() {
        try {
            int numUBOs = GL20.glGetProgrami(program, GL31.GL_ACTIVE_UNIFORM_BLOCKS);

            if (numUBOs > 0) {
                resourceBindings.put(ResourceTypes.UNIFORM_BLOCK, new HashMap<>());
                Map<KeyId, Integer> uniformBlock = resourceBindings.get(ResourceTypes.UNIFORM_BLOCK);

                for (int i = 0; i < numUBOs; i++) {
                    String blockName = GL31.glGetActiveUniformBlockName(program, i);

                    // Get binding point
                    IntBuffer bindingBuffer = BufferUtils.createIntBuffer(1);
                    GL31.glGetActiveUniformBlockiv(program, i, GL31.GL_UNIFORM_BLOCK_BINDING, bindingBuffer);
                    int binding = bindingBuffer.get(0);

                    uniformBlock.put(KeyId.of(blockName), binding);
                    System.out.println("Discovered UBO: " + blockName + " -> binding " + binding);
                }
            }
        } catch (Exception e) {
            System.err.println("Error discovering UBO bindings: " + e.getMessage());
        }
    }

    // Texture and image binding discovery moved to discoverAllUniforms() for efficiency

    private boolean isSamplerType(int type) {
        return switch (type) {
            case GL20.GL_SAMPLER_1D, GL20.GL_SAMPLER_2D, GL20.GL_SAMPLER_3D,
                 GL20.GL_SAMPLER_CUBE, GL30.GL_SAMPLER_1D_ARRAY, GL30.GL_SAMPLER_2D_ARRAY,
                 GL31.GL_SAMPLER_2D_RECT, GL32.GL_SAMPLER_2D_MULTISAMPLE,
                 GL32.GL_SAMPLER_2D_MULTISAMPLE_ARRAY, GL40.GL_SAMPLER_CUBE_MAP_ARRAY,
                 GL30.GL_SAMPLER_1D_SHADOW, GL30.GL_SAMPLER_2D_SHADOW,
                 GL30.GL_SAMPLER_CUBE_SHADOW, GL30.GL_SAMPLER_1D_ARRAY_SHADOW,
                 GL30.GL_SAMPLER_2D_ARRAY_SHADOW, GL40.GL_SAMPLER_CUBE_MAP_ARRAY_SHADOW,
                 GL31.GL_SAMPLER_2D_RECT_SHADOW, GL30.GL_INT_SAMPLER_1D,
                 GL30.GL_INT_SAMPLER_2D, GL30.GL_INT_SAMPLER_3D,
                 GL30.GL_UNSIGNED_INT_SAMPLER_1D, GL30.GL_UNSIGNED_INT_SAMPLER_2D,
                 GL30.GL_UNSIGNED_INT_SAMPLER_3D -> true;
            default -> false;
        };
    }

    private boolean isImageType(int type) {
        return switch (type) {
            case GL42.GL_IMAGE_1D, GL42.GL_IMAGE_2D, GL42.GL_IMAGE_3D, GL42.GL_IMAGE_2D_RECT, GL42.GL_IMAGE_CUBE,
                 GL42.GL_IMAGE_BUFFER, GL42.GL_IMAGE_1D_ARRAY, GL42.GL_IMAGE_2D_ARRAY, GL42.GL_IMAGE_2D_MULTISAMPLE,
                 GL42.GL_IMAGE_2D_MULTISAMPLE_ARRAY, GL42.GL_INT_IMAGE_1D, GL42.GL_INT_IMAGE_2D, GL42.GL_INT_IMAGE_3D,
                 GL42.GL_INT_IMAGE_2D_RECT, GL42.GL_INT_IMAGE_CUBE, GL42.GL_INT_IMAGE_BUFFER,
                 GL42.GL_INT_IMAGE_1D_ARRAY, GL42.GL_INT_IMAGE_2D_ARRAY, GL42.GL_UNSIGNED_INT_IMAGE_1D,
                 GL42.GL_UNSIGNED_INT_IMAGE_2D, GL42.GL_UNSIGNED_INT_IMAGE_3D, GL42.GL_UNSIGNED_INT_IMAGE_2D_RECT,
                 GL42.GL_UNSIGNED_INT_IMAGE_CUBE, GL42.GL_UNSIGNED_INT_IMAGE_BUFFER,
                 GL42.GL_UNSIGNED_INT_IMAGE_1D_ARRAY, GL42.GL_UNSIGNED_INT_IMAGE_2D_ARRAY -> true;
            default -> false;
        };
    }

    // Uniform collection moved to discoverAllUniforms() to avoid redundant GL queries

    /**
     * Infer DataType from OpenGL uniform type
     */
    private DataType inferUniformType(int glType) {
        return switch (glType) {
            case GL20.GL_FLOAT -> DataType.FLOAT;
            case GL20.GL_FLOAT_VEC2 -> DataType.VEC2F;
            case GL20.GL_FLOAT_VEC3 -> DataType.VEC3F;
            case GL20.GL_FLOAT_VEC4 -> DataType.VEC4F;
            case GL20.GL_INT -> DataType.INT;
            case GL20.GL_INT_VEC2 -> DataType.VEC2I;
            case GL20.GL_INT_VEC3 -> DataType.VEC3I;
            case GL20.GL_INT_VEC4 -> DataType.VEC4I;
            case GL20.GL_UNSIGNED_INT -> DataType.UINT;
            case GL30.GL_UNSIGNED_INT_VEC2 -> DataType.VEC2UI;
            case GL30.GL_UNSIGNED_INT_VEC3 -> DataType.VEC3UI;
            case GL30.GL_UNSIGNED_INT_VEC4 -> DataType.VEC4UI;
            case GL20.GL_FLOAT_MAT2 -> DataType.MAT2;
            case GL20.GL_FLOAT_MAT3 -> DataType.MAT3;
            case GL20.GL_FLOAT_MAT4 -> DataType.MAT4;
            default -> null;
        };
    }

    protected void postLinkInitialization() {
        discoverResourceBindings();
    }

    protected abstract void validateShaderTypes(Map<ShaderType, String> shaderSources);

    public void bind() {
        GL20.glUseProgram(program);
    }

    public static void unbind() {
        GL20.glUseProgram(0);
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
     * Dispose of all OpenGL resources
     */
    @Override
    public void dispose() {
        if (program > 0) {
            GL20.glDeleteProgram(program);
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