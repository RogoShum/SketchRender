package rogo.sketch.render.shader;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import rogo.sketch.SketchRender;
import rogo.sketch.api.ShaderCollector;
import rogo.sketch.api.ShaderProvider;
import rogo.sketch.api.ShaderResource;
import rogo.sketch.render.resource.ResourceTypes;
import rogo.sketch.render.shader.uniform.DataType;
import rogo.sketch.render.shader.uniform.ShaderUniform;
import rogo.sketch.render.uniform.UniformHookRegistry;
import rogo.sketch.util.Identifier;

import java.io.IOException;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * Base class for all shaders (graphics and compute)
 */
public abstract class Shader implements ShaderCollector, ShaderProvider {
    private final Map<Identifier, Map<Identifier, Integer>> resourceBindings = new HashMap<>();
    protected final UniformHookGroup uniformHookGroup = new UniformHookGroup();
    protected final Identifier identifier;
    protected final int program;
    protected final Map<ShaderType, Integer> shaderIds = new HashMap<>();

    /**
     * Create a shader program from GLSL source code
     *
     * @param identifier    Unique identifier for this shader
     * @param shaderSources Map of shader types to their GLSL source code
     */
    public Shader(Identifier identifier, Map<ShaderType, String> shaderSources) throws IOException {
        this.identifier = identifier;
        this.program = GL20.glCreateProgram();

        validateShaderTypes(shaderSources);
        compileAndAttachShaders(shaderSources);
        linkProgram();
        cleanupShaders();

        collectAndInitializeUniforms();
        postLinkInitialization();

        onShadeCreate();
    }

    /**
     * Create a shader program with a single shader type
     */
    public Shader(Identifier identifier, ShaderType type, String source) throws IOException {
        this(identifier, Map.of(type, source));
    }

    private void compileAndAttachShaders(Map<ShaderType, String> shaderSources) throws IOException {
        for (Map.Entry<ShaderType, String> entry : shaderSources.entrySet()) {
            ShaderType type = entry.getKey();
            String source = entry.getValue();

            int shaderId = compileShader(type, source, identifier.toString());
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

    private void linkProgram() throws IOException {
        GL20.glLinkProgram(program);

        if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            String error = GL20.glGetProgramInfoLog(program);
            throw new IOException("Error linking shader program [" + identifier + "]:\n" + error);
        }
    }

    private void cleanupShaders() {
        for (int shaderId : shaderIds.values()) {
            GL20.glDetachShader(program, shaderId);
            GL20.glDeleteShader(shaderId);
        }
        shaderIds.clear();
    }

    private void collectAndInitializeUniforms() {
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
        Map<String, ShaderResource<?>> uniforms = new HashMap<>();
        Map<Identifier, Integer> textureBindings = new HashMap<>();
        Map<Identifier, Integer> imageBindings = new HashMap<>();

        // Reusable buffers to avoid repeated allocations
        IntBuffer sizeBuffer = BufferUtils.createIntBuffer(1);
        IntBuffer typeBuffer = BufferUtils.createIntBuffer(1);

        int uniformCount = GL20.glGetProgrami(program, GL20.GL_ACTIVE_UNIFORMS);

        for (int i = 0; i < uniformCount; i++) {
            sizeBuffer.clear();
            typeBuffer.clear();

            String uniformName = GL20.glGetActiveUniform(program, i, sizeBuffer, typeBuffer);
            int location = GL20.glGetUniformLocation(program, uniformName);
            int glType = typeBuffer.get(0);

            if (location >= 0) {
                // Create shader uniform for regular uniforms
                DataType dataType = inferUniformType(glType);
                if (dataType != null) {
                    Identifier uniformId = Identifier.of(uniformName);
                    ShaderUniform<?> shaderUniform = new ShaderUniform<>(uniformId, location, dataType, program);
                    uniforms.put(uniformName, shaderUniform);
                }

                if (isSamplerType(glType)) {
                    textureBindings.put(Identifier.of(uniformName), location);
                    System.out.println("Discovered Texture: " + uniformName + " -> location " + location);
                }

                // Check for image uniforms (requires OpenGL 4.2+)
                if (GL.getCapabilities().OpenGL42 && isImageType(glType)) {
                    int unit = GL20.glGetUniformi(program, location);
                    imageBindings.put(Identifier.of(uniformName), unit);
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

        return new UnifiedUniformInfo(uniforms, textureBindings, imageBindings);
    }

    /**
     * Container for unified uniform discovery results
     */
    private record UnifiedUniformInfo(Map<String, ShaderResource<?>> uniformMap,
                                      Map<Identifier, Integer> textureBindings,
                                      Map<Identifier, Integer> imageBindings) {
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
            Map<Identifier, Integer> ssboBindings = resourceBindings.get(ResourceTypes.SHADER_STORAGE_BUFFER);

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
                ssboBindings.put(Identifier.of(blockName), binding);
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
                Map<Identifier, Integer> uniformBlock = resourceBindings.get(ResourceTypes.UNIFORM_BLOCK);

                for (int i = 0; i < numUBOs; i++) {
                    String blockName = GL31.glGetActiveUniformBlockName(program, i);

                    // Get binding point
                    IntBuffer bindingBuffer = BufferUtils.createIntBuffer(1);
                    GL31.glGetActiveUniformBlockiv(program, i, GL31.GL_UNIFORM_BLOCK_BINDING, bindingBuffer);
                    int binding = bindingBuffer.get(0);

                    uniformBlock.put(Identifier.of(blockName), binding);
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
            case GL20.GL_FLOAT_VEC2 -> DataType.VEC2;
            case GL20.GL_FLOAT_VEC3 -> DataType.VEC3;
            case GL20.GL_FLOAT_VEC4 -> DataType.VEC4;
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
    public Identifier getIdentifier() {
        return identifier;
    }

    @Override
    public UniformHookGroup getUniformHookGroup() {
        return uniformHookGroup;
    }

    @Override
    public Map<Identifier, Map<Identifier, Integer>> getResourceBindings() {
        return resourceBindings;
    }

    @Override
    public void onShadeCreate() {
        SketchRender.getShaderManager().onShaderLoad(this);
    }

    /**
     * Dispose of all OpenGL resources
     */
    @Override
    public void dispose() {
        if (program > 0) {
            GL20.glDeleteProgram(program);
        }
    }

    @Override
    public void close() {
        dispose();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" +
                "identifier=" + identifier +
                ", program=" + program +
                '}';
    }
}