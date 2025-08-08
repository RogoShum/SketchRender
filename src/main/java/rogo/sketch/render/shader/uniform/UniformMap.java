package rogo.sketch.render.shader.uniform;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL20;
import rogo.sketch.util.Identifier;

import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Modern uniform management system that replaces UnsafeUniformMap
 */
public class UniformMap {
    private final Map<String, ShaderUniform<?>> uniforms = new HashMap<>();
    private final int program;

    public UniformMap(int program) {
        this.program = program;
        collectUniforms();
    }

    /**
     * Automatically collect all uniforms from the shader program
     */
    private void collectUniforms() {
        int uniformCount = GL20.glGetProgrami(program, GL20.GL_ACTIVE_UNIFORMS);

        for (int i = 0; i < uniformCount; i++) {
            IntBuffer size = BufferUtils.createIntBuffer(1);
            IntBuffer type = BufferUtils.createIntBuffer(1);
            String uniformName = GL20.glGetActiveUniform(program, i, size, type);

            // Skip array indices and sampler uniforms for now
            if (uniformName.contains("[")) {
                uniformName = uniformName.substring(0, uniformName.indexOf('['));
            }

            int location = GL20.glGetUniformLocation(program, uniformName);
            if (location != -1) {
                // Use the type we already got from glGetActiveUniform
                DataType dataType = inferUniformType(type.get(0));
                if (dataType != null) {
                    Identifier id = Identifier.valueOf(uniformName);
                    ShaderUniform<?> uniform = createTypedUniform(id, location, dataType);
                    uniforms.put(uniformName, uniform);
                }
            }
        }
    }

    /**
     * Infer the data type of a uniform from OpenGL type constant
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
            case GL20.GL_FLOAT_MAT2 -> DataType.MAT2;
            case GL20.GL_FLOAT_MAT3 -> DataType.MAT3;
            case GL20.GL_FLOAT_MAT4 -> DataType.MAT4;
            case GL20.GL_SAMPLER_2D, GL20.GL_SAMPLER_CUBE -> DataType.INT; // Samplers are int handles
            default -> {
                System.err.println("Unknown uniform type: " + glType);
                yield null;
            }
        };
    }

    /**
     * Create a typed uniform based on the data type
     */
    @SuppressWarnings("unchecked")
    private <T> ShaderUniform<T> createTypedUniform(Identifier id, int location, DataType dataType) {
        return new ShaderUniform<>(id, location, dataType, program);
    }

    /**
     * Get a uniform by name
     */
    @SuppressWarnings("unchecked")
    public <T> ShaderUniform<T> getUniform(String name) {
        return (ShaderUniform<T>) uniforms.get(name);
    }

    /**
     * Get a uniform by identifier
     */
    public <T> ShaderUniform<T> getUniform(Identifier identifier) {
        return getUniform(identifier.toString());
    }

    /**
     * Check if a uniform exists
     */
    public boolean hasUniform(String name) {
        return uniforms.containsKey(name);
    }

    /**
     * Check if a uniform exists
     */
    public boolean hasUniform(Identifier identifier) {
        return hasUniform(identifier.toString());
    }

    /**
     * Get all uniform names
     */
    public Set<String> getUniformNames() {
        return uniforms.keySet();
    }

    /**
     * Get all uniforms
     */
    public Map<String, ShaderUniform<?>> getAllUniforms() {
        return new HashMap<>(uniforms);
    }

    /**
     * Force upload all cached uniform values (useful after context switches)
     */
    public void forceUploadAll() {
        for (ShaderUniform<?> uniform : uniforms.values()) {
            uniform.forceUpload();
        }
    }

    /**
     * Set a uniform value by name
     */
    @SuppressWarnings("unchecked")
    public <T> void setUniform(String name, T value) {
        ShaderUniform<T> uniform = getUniform(name);
        if (uniform != null) {
            uniform.set(value);
        }
    }

    /**
     * Set a uniform value by identifier
     */
    public <T> void setUniform(Identifier identifier, T value) {
        setUniform(identifier.toString(), value);
    }

    /**
     * Manually add a uniform (for custom handling)
     */
    public void addUniform(String name, ShaderUniform<?> uniform) {
        uniforms.put(name, uniform);
    }

    /**
     * Remove a uniform
     */
    public void removeUniform(String name) {
        uniforms.remove(name);
    }

    /**
     * Get the program ID this uniform map belongs to
     */
    public int getProgram() {
        return program;
    }

    /**
     * Print all uniforms for debugging
     */
    public void printUniforms() {
        System.out.println("Uniforms for program " + program + ":");
        for (Map.Entry<String, ShaderUniform<?>> entry : uniforms.entrySet()) {
            ShaderUniform<?> uniform = entry.getValue();
            System.out.printf("  %s: location=%d, type=%s%n",
                    entry.getKey(), uniform.getLocation(), uniform.getDataType());
        }
    }
}