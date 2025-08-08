package rogo.sketch.render.shader;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import rogo.sketch.SketchRender;
import rogo.sketch.api.ShaderCollector;
import rogo.sketch.api.ShaderProvider;
import rogo.sketch.api.ShaderResource;
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
    protected abstract void postLinkInitialization();

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