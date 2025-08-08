package rogo.sketch.render.shader.uniform;

import org.joml.*;
import org.lwjgl.opengl.GL20;
import rogo.sketch.api.ShaderResource;
import rogo.sketch.util.Identifier;


import java.util.Objects;

/**
 * Modern uniform handling system to replace McUniformWrapper
 */
public class ShaderUniform<T> implements ShaderResource<T> {
    private final Identifier identifier;
    private final int location;
    private final DataType dataType;
    private final int program;
    private T currentValue;

    public ShaderUniform(Identifier identifier, int location, DataType dataType, int program) {
        this.identifier = identifier;
        this.location = location;
        this.dataType = dataType;
        this.program = program;
    }

    @Override
    public Identifier id() {
        return identifier;
    }

    @Override
    public void set(T value) {
        if (Objects.equals(currentValue, value)) {
            return; // No change needed
        }
        
        currentValue = value;
        uploadValue(value);
    }

    private void uploadValue(T value) {
        if (location == -1) {
            return; // Uniform was optimized out or doesn't exist
        }

        switch (dataType) {
            case FLOAT -> GL20.glUniform1f(location, (Float) value);
            case VEC2 -> {
                Vector2f vec = (Vector2f) value;
                GL20.glUniform2f(location, vec.x, vec.y);
            }
            case VEC3 -> {
                Vector3f vec = (Vector3f) value;
                GL20.glUniform3f(location, vec.x, vec.y, vec.z);
            }
            case VEC4 -> {
                Vector4f vec = (Vector4f) value;
                GL20.glUniform4f(location, vec.x, vec.y, vec.z, vec.w);
            }
            case INT -> GL20.glUniform1i(location, (Integer) value);
            case VEC2I -> {
                Vector2i vec = (Vector2i) value;
                GL20.glUniform2i(location, vec.x, vec.y);
            }
            case VEC3I -> {
                Vector3i vec = (Vector3i) value;
                GL20.glUniform3i(location, vec.x, vec.y, vec.z);
            }
            case VEC4I -> {
                Vector4i vec = (Vector4i) value;
                GL20.glUniform4i(location, vec.x, vec.y, vec.z, vec.w);
            }
            case MAT2 -> {
                Matrix2f mat = (Matrix2f) value;
                float[] buffer = new float[4];
                mat.get(buffer);
                GL20.glUniformMatrix2fv(location, false, buffer);
            }
            case MAT3 -> {
                Matrix3f mat = (Matrix3f) value;
                float[] buffer = new float[9];
                mat.get(buffer);
                GL20.glUniformMatrix3fv(location, false, buffer);
            }
            case MAT4 -> {
                Matrix4f mat = (Matrix4f) value;
                float[] buffer = new float[16];
                mat.get(buffer);
                GL20.glUniformMatrix4fv(location, false, buffer);
            }
            case UINT -> GL20.glUniform1i(location, (Integer) value);
            case VEC2UI -> {
                Vector2i vec = (Vector2i) value;
                GL20.glUniform2i(location, vec.x, vec.y);
            }
            case VEC3UI -> {
                Vector3i vec = (Vector3i) value;
                GL20.glUniform3i(location, vec.x, vec.y, vec.z);
            }
            case VEC4UI -> {
                Vector4i vec = (Vector4i) value;
                GL20.glUniform4i(location, vec.x, vec.y, vec.z, vec.w);
            }
            default -> throw new UnsupportedOperationException("Uniform type not supported: " + dataType);
        }
    }

    /**
     * Force upload the current value (useful for context switches)
     */
    public void forceUpload() {
        if (currentValue != null) {
            T temp = currentValue;
            currentValue = null;
            set(temp);
        }
    }

    /**
     * Get the current cached value
     */
    public T getCurrentValue() {
        return currentValue;
    }

    public int getLocation() {
        return location;
    }

    public DataType getDataType() {
        return dataType;
    }

    public int getProgram() {
        return program;
    }

    /**
     * Check if this uniform exists in the shader (location != -1)
     */
    public boolean exists() {
        return location != -1;
    }

    @Override
    public String toString() {
        return "ShaderUniform{" +
               "identifier=" + identifier +
               ", location=" + location +
               ", dataType=" + dataType +
               ", currentValue=" + currentValue +
               '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ShaderUniform<?> that = (ShaderUniform<?>) o;
        return location == that.location &&
               program == that.program &&
               Objects.equals(identifier, that.identifier) &&
               dataType == that.dataType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(identifier, location, dataType, program);
    }
}
