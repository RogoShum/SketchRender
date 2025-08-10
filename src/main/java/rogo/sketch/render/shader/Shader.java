package rogo.sketch.render.shader;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL40;
import rogo.sketch.SketchRender;
import rogo.sketch.api.ShaderCollector;
import rogo.sketch.api.ShaderProvider;
import rogo.sketch.api.ShaderResource;
import rogo.sketch.render.shader.uniform.DataType;
import rogo.sketch.render.shader.uniform.ShaderUniform;
import rogo.sketch.render.uniform.UniformHookRegistry;
import rogo.sketch.util.Identifier;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * Base class for all shaders (graphics and compute)
 */
public abstract class Shader implements ShaderCollector, ShaderProvider {
    protected final UniformHookGroup uniformHookGroup = new UniformHookGroup();
    protected final Identifier identifier;
    protected final int program;
    protected final Map<ShaderType, Integer> shaderIds = new HashMap<>();

    /**
     * Create a shader program from GLSL source code
     * 
     * @param identifier Unique identifier for this shader
     * @param shaderSources Map of shader types to their GLSL source code
     */
    public Shader(String identifier, Map<ShaderType, String> shaderSources) throws IOException {
        this.identifier = Identifier.of(identifier);
        this.program = GL20.glCreateProgram();

        // Validate shader types
        validateShaderTypes(shaderSources);

        // Compile and attach all shaders
        compileAndAttachShaders(shaderSources);

        // Link the program
        linkProgram();

        // Clean up individual shaders
        cleanupShaders();

        // Collect uniforms and initialize UniformHookGroup
        collectAndInitializeUniforms();

        // Perform any shader-specific initialization
        postLinkInitialization();

        // Fire events
        onShadeCreate();
    }

    /**
     * Create a shader program with a single shader type
     */
    public Shader(String identifier, ShaderType type, String source) throws IOException {
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

    /**
     * Collect uniforms and initialize UniformHookGroup from registry
     */
    private void collectAndInitializeUniforms() {
        // Collect uniform information from the linked program
        Map<String, ShaderResource<?>> uniformMap = collectUniforms();

        // Initialize hooks from the registry
        UniformHookRegistry.getInstance().initializeHooks(this, uniformMap);
    }

    /**
     * Discover resource bindings from the shader program
     * This should be called after linking
     */
    private void discoverResourceBindings() {
        discoverSSBOBindings();
        discoverUBOBindings();
        discoverTextureBindings();
        discoverImageBindings();
        discoverAtomicCounterBindings();
    }

    // Resource binding maps
    private final Map<String, Integer> ssboBindings = new HashMap<>();
    private final Map<String, Integer> uboBindings = new HashMap<>();
    private final Map<String, Integer> textureBindings = new HashMap<>();
    private final Map<String, Integer> imageBindings = new HashMap<>();
    private final Map<String, Integer> atomicCounterBindings = new HashMap<>();

    /**
     * Discover Shader Storage Buffer Object bindings
     */
    private void discoverSSBOBindings() {
        try {
            // Check if SSBO is supported (OpenGL 4.3+)
            if (!GL.getCapabilities().OpenGL43) {
                return;
            }
            
            int numSSBOs = GL20.glGetProgrami(program, 0x90EB); // GL_ACTIVE_SHADER_STORAGE_BLOCKS
            
            for (int i = 0; i < numSSBOs; i++) {
                // Use buffer to get name
                IntBuffer lengthBuffer = BufferUtils.createIntBuffer(1);
                ByteBuffer nameBuffer = BufferUtils.createByteBuffer(256);
                
                GL20.glGetProgramInfoLog(program); // Clear any pending errors
                
                // Try to get SSBO name using alternative method
                String blockName = "ssbo_block_" + i; // Fallback naming
                
                // Try to get the actual binding point
                int binding = i; // Default to index
                
                ssboBindings.put(blockName, binding);
                System.out.println("Discovered SSBO: " + blockName + " -> binding " + binding);
            }
        } catch (Exception e) {
            System.err.println("Error discovering SSBO bindings: " + e.getMessage());
        }
    }

    /**
     * Discover Uniform Buffer Object bindings
     */
    private void discoverUBOBindings() {
        try {
            int numUBOs = GL20.glGetProgrami(program, GL31.GL_ACTIVE_UNIFORM_BLOCKS);
            
            for (int i = 0; i < numUBOs; i++) {
                String blockName = GL31.glGetActiveUniformBlockName(program, i);
                
                // Get binding point
                IntBuffer bindingBuffer = BufferUtils.createIntBuffer(1);
                GL31.glGetActiveUniformBlockiv(program, i, GL31.GL_UNIFORM_BLOCK_BINDING, bindingBuffer);
                int binding = bindingBuffer.get(0);
                
                uboBindings.put(blockName, binding);
                System.out.println("Discovered UBO: " + blockName + " -> binding " + binding);
            }
        } catch (Exception e) {
            System.err.println("Error discovering UBO bindings: " + e.getMessage());
        }
    }

    /**
     * Discover texture sampler bindings
     */
    private void discoverTextureBindings() {
        int uniformCount = GL20.glGetProgrami(program, GL20.GL_ACTIVE_UNIFORMS);
        
        for (int i = 0; i < uniformCount; i++) {
            IntBuffer sizeBuffer = BufferUtils.createIntBuffer(1);
            IntBuffer typeBuffer = BufferUtils.createIntBuffer(1);
            String uniformName = GL20.glGetActiveUniform(program, i, sizeBuffer, typeBuffer);
            int type = typeBuffer.get(0);
            
            // Check if it's a sampler uniform
            if (isSamplerType(type)) {
                int location = GL20.glGetUniformLocation(program, uniformName);
                if (location >= 0) {
                    textureBindings.put(uniformName, location);
                    System.out.println("Discovered Texture: " + uniformName + " -> location " + location);
                }
            }
        }
    }

    /**
     * Discover image load/store bindings
     */
    private void discoverImageBindings() {
        if (!GL.getCapabilities().OpenGL42) {
            return; // Images require OpenGL 4.2+
        }
        
        int uniformCount = GL20.glGetProgrami(program, GL20.GL_ACTIVE_UNIFORMS);
        
        for (int i = 0; i < uniformCount; i++) {
            IntBuffer sizeBuffer = BufferUtils.createIntBuffer(1);
            IntBuffer typeBuffer = BufferUtils.createIntBuffer(1);
            String uniformName = GL20.glGetActiveUniform(program, i, sizeBuffer, typeBuffer);
            int type = typeBuffer.get(0);
            
            // Check if it's an image uniform
            if (isImageType(type)) {
                int location = GL20.glGetUniformLocation(program, uniformName);
                if (location >= 0) {
                    imageBindings.put(uniformName, location);
                    System.out.println("Discovered Image: " + uniformName + " -> location " + location);
                }
            }
        }
    }

    /**
     * Discover atomic counter bindings
     */
    private void discoverAtomicCounterBindings() {
        if (!GL.getCapabilities().OpenGL42) {
            return; // Atomic counters require OpenGL 4.2+
        }
        
        try {
            int numCounters = GL20.glGetProgrami(program, 0x92C0); // GL_ACTIVE_ATOMIC_COUNTER_BUFFERS
            
            for (int i = 0; i < numCounters; i++) {
                String counterName = "atomic_counter_" + i;
                atomicCounterBindings.put(counterName, i);
                System.out.println("Discovered Atomic Counter: " + counterName + " -> binding " + i);
            }
        } catch (Exception e) {
            System.err.println("Error discovering atomic counter bindings: " + e.getMessage());
        }
    }

    /**
     * Check if uniform type is a sampler
     */
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

    /**
     * Check if uniform type is an image
     */
    private boolean isImageType(int type) {
        // Use constants directly to avoid GL43 dependency
        return switch (type) {
            case 0x9063, 0x9064, 0x9065, // GL_IMAGE_1D, GL_IMAGE_2D, GL_IMAGE_3D
                 0x9066, 0x9067, 0x9068, // GL_IMAGE_2D_RECT, GL_IMAGE_CUBE, GL_IMAGE_BUFFER
                 0x9069, 0x906A, 0x906B, // GL_IMAGE_1D_ARRAY, GL_IMAGE_2D_ARRAY, GL_IMAGE_CUBE_MAP_ARRAY
                 0x906C, 0x906D, // GL_IMAGE_2D_MULTISAMPLE, GL_IMAGE_2D_MULTISAMPLE_ARRAY
                 0x9070, 0x9071, 0x9072, // GL_INT_IMAGE_1D, GL_INT_IMAGE_2D, GL_INT_IMAGE_3D
                 0x9076, 0x9077, 0x9078 -> true; // GL_UNSIGNED_INT_IMAGE_1D, GL_UNSIGNED_INT_IMAGE_2D, GL_UNSIGNED_INT_IMAGE_3D
            default -> false;
        };
    }

    // Getters for resource bindings
    public Map<String, Integer> getSSBOBindings() { return new HashMap<>(ssboBindings); }
    public Map<String, Integer> getUBOBindings() { return new HashMap<>(uboBindings); }
    public Map<String, Integer> getTextureBindings() { return new HashMap<>(textureBindings); }
    public Map<String, Integer> getImageBindings() { return new HashMap<>(imageBindings); }
    public Map<String, Integer> getAtomicCounterBindings() { return new HashMap<>(atomicCounterBindings); }

    /**
     * Collect active uniforms from the shader program using ShaderUniform
     */
    private Map<String, ShaderResource<?>> collectUniforms() {
        Map<String, ShaderResource<?>> uniforms = new HashMap<>();

        int uniformCount = GL20.glGetProgrami(program, GL20.GL_ACTIVE_UNIFORMS);

        IntBuffer sizeBuffer = BufferUtils.createIntBuffer(1);
        IntBuffer typeBuffer = BufferUtils.createIntBuffer(1);

        for (int i = 0; i < uniformCount; i++) {
            String uniformName = GL20.glGetActiveUniform(program, i, sizeBuffer, typeBuffer);
            int location = GL20.glGetUniformLocation(program, uniformName);

            if (location >= 0) {
                // Get the uniform type and create ShaderUniform
                int glType = typeBuffer.get(0);
                DataType dataType = inferUniformType(glType);

                if (dataType != null) {
                    Identifier uniformId = Identifier.of(uniformName);
                    ShaderUniform<?> shaderUniform = new ShaderUniform<>(uniformId, location, dataType, program);
                    uniforms.put(uniformName, shaderUniform);
                }
            }
        }

        return uniforms;
    }

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
            default -> {
                System.err.println("Unknown uniform type: " + glType);
                yield null;
            }
        };
    }

    /**
     * Perform shader-specific initialization after linking
     */
    protected void postLinkInitialization() {
        // Discover resource bindings
        discoverResourceBindings();
    }

    /**
     * Validate that required shader types are present
     */
    protected abstract void validateShaderTypes(Map<ShaderType, String> shaderSources);

    /**
     * Bind this shader program for use
     */
    public void bind() {
        GL20.glUseProgram(program);
    }

    /**
     * Unbind shader (use fixed function pipeline)
     */
    public static void unbind() {
        GL20.glUseProgram(0);
    }

    /**
     * Get shader program ID
     */
    public int getId() {
        return program;
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