package rogo.sketch.backend.opengl;

import org.joml.Matrix2f;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector2i;
import org.joml.Vector3f;
import org.joml.Vector3i;
import org.joml.Vector4f;
import org.joml.Vector4i;
import org.lwjgl.opengl.GL20;
import org.lwjgl.system.MemoryUtil;
import rogo.sketch.core.api.ShaderResource;
import rogo.sketch.core.data.type.ValueType;
import rogo.sketch.core.util.KeyId;

import java.lang.Math;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Objects;
import java.util.function.Consumer;

final class OpenGLShaderUniform<T> implements ShaderResource<T> {
    private final KeyId keyId;
    private final int location;
    private final ValueType dataType;
    private final int dataCount;
    private final int program;
    private final Consumer<T> valueConsumer;
    private final IntBuffer intValues;
    private final FloatBuffer floatValues;
    private T currentValue;

    OpenGLShaderUniform(KeyId keyId, int location, ValueType dataType, int dataCount, int program) {
        this.keyId = keyId;
        this.location = location;
        this.dataType = dataType;
        this.dataCount = dataCount * dataType.getComponentCount();
        this.program = program;
        this.valueConsumer = createValueSetter();
        if (dataType.isIntegerType()) {
            this.intValues = MemoryUtil.memAllocInt(this.dataCount);
            this.floatValues = null;
        } else {
            this.intValues = null;
            this.floatValues = MemoryUtil.memAllocFloat(this.dataCount);
        }
    }

    @Override
    public KeyId id() {
        return keyId;
    }

    @Override
    public void set(T value) {
        if (Objects.equals(currentValue, value)) {
            return;
        }
        currentValue = value;
        valueConsumer.accept(value);
    }

    private Consumer<T> createValueSetter() {
        if (location == -1) {
            return value -> {
            };
        }

        int elementSize = dataType.getComponentCount();
        boolean isArray = dataCount > elementSize;
        if (isArray) {
            return createArraySetter();
        }
        return createScalarSetter();
    }

    private Consumer<T> createArraySetter() {
        return switch (dataType) {
            case FLOAT -> value -> {
                float[] arr = (float[]) value;
                floatValues.clear();
                floatValues.put(arr, 0, Math.min(arr.length, dataCount));
                floatValues.flip();
                GL20.glUniform1fv(location, floatValues);
            };
            case VEC2F -> value -> {
                Vector2f[] arr = (Vector2f[]) value;
                floatValues.clear();
                for (Vector2f v : arr) {
                    floatValues.put(v.x).put(v.y);
                }
                floatValues.flip();
                GL20.glUniform2fv(location, floatValues);
            };
            case VEC3F -> value -> {
                Vector3f[] arr = (Vector3f[]) value;
                floatValues.clear();
                for (Vector3f v : arr) {
                    floatValues.put(v.x).put(v.y).put(v.z);
                }
                floatValues.flip();
                GL20.glUniform3fv(location, floatValues);
            };
            case VEC4F -> value -> {
                Vector4f[] arr = (Vector4f[]) value;
                floatValues.clear();
                for (Vector4f v : arr) {
                    floatValues.put(v.x).put(v.y).put(v.z).put(v.w);
                }
                floatValues.flip();
                GL20.glUniform4fv(location, floatValues);
            };
            case INT -> value -> {
                int[] arr = (int[]) value;
                intValues.clear();
                intValues.put(arr, 0, Math.min(arr.length, dataCount));
                intValues.flip();
                GL20.glUniform1iv(location, intValues);
            };
            case VEC2I -> value -> {
                Vector2i[] arr = (Vector2i[]) value;
                intValues.clear();
                for (Vector2i v : arr) {
                    intValues.put(v.x).put(v.y);
                }
                intValues.flip();
                GL20.glUniform2iv(location, intValues);
            };
            case VEC3I -> value -> {
                Vector3i[] arr = (Vector3i[]) value;
                intValues.clear();
                for (Vector3i v : arr) {
                    intValues.put(v.x).put(v.y).put(v.z);
                }
                intValues.flip();
                GL20.glUniform3iv(location, intValues);
            };
            case VEC4I -> value -> {
                Vector4i[] arr = (Vector4i[]) value;
                intValues.clear();
                for (Vector4i v : arr) {
                    intValues.put(v.x).put(v.y).put(v.z).put(v.w);
                }
                intValues.flip();
                GL20.glUniform4iv(location, intValues);
            };
            case MAT2 -> value -> {
                Matrix2f[] arr = (Matrix2f[]) value;
                floatValues.clear();
                for (Matrix2f mat : arr) {
                    mat.get(floatValues);
                }
                floatValues.flip();
                GL20.glUniformMatrix2fv(location, false, floatValues);
            };
            case MAT3 -> value -> {
                Matrix3f[] arr = (Matrix3f[]) value;
                floatValues.clear();
                for (Matrix3f mat : arr) {
                    mat.get(floatValues);
                }
                floatValues.flip();
                GL20.glUniformMatrix3fv(location, false, floatValues);
            };
            case MAT4 -> value -> {
                Matrix4f[] arr = (Matrix4f[]) value;
                floatValues.clear();
                for (Matrix4f mat : arr) {
                    mat.get(floatValues);
                }
                floatValues.flip();
                GL20.glUniformMatrix4fv(location, false, floatValues);
            };
            default -> throw new UnsupportedOperationException("Uniform type not supported: " + dataType);
        };
    }

    private Consumer<T> createScalarSetter() {
        return switch (dataType) {
            case FLOAT -> value -> GL20.glUniform1f(location, (Float) value);
            case VEC2F -> value -> {
                Vector2f vec = (Vector2f) value;
                GL20.glUniform2f(location, vec.x, vec.y);
            };
            case VEC3F -> value -> {
                Vector3f vec = (Vector3f) value;
                GL20.glUniform3f(location, vec.x, vec.y, vec.z);
            };
            case VEC4F -> value -> {
                Vector4f vec = (Vector4f) value;
                GL20.glUniform4f(location, vec.x, vec.y, vec.z, vec.w);
            };
            case INT -> value -> GL20.glUniform1i(location, (Integer) value);
            case VEC2I -> value -> {
                Vector2i vec = (Vector2i) value;
                GL20.glUniform2i(location, vec.x, vec.y);
            };
            case VEC3I -> value -> {
                Vector3i vec = (Vector3i) value;
                GL20.glUniform3i(location, vec.x, vec.y, vec.z);
            };
            case VEC4I -> value -> {
                Vector4i vec = (Vector4i) value;
                GL20.glUniform4i(location, vec.x, vec.y, vec.z, vec.w);
            };
            case MAT2 -> value -> {
                Matrix2f mat = (Matrix2f) value;
                float[] buffer = new float[4];
                mat.get(buffer);
                GL20.glUniformMatrix2fv(location, false, buffer);
            };
            case MAT3 -> value -> {
                Matrix3f mat = (Matrix3f) value;
                float[] buffer = new float[9];
                mat.get(buffer);
                GL20.glUniformMatrix3fv(location, false, buffer);
            };
            case MAT4 -> value -> {
                Matrix4f mat = (Matrix4f) value;
                float[] buffer = new float[16];
                mat.get(buffer);
                GL20.glUniformMatrix4fv(location, false, buffer);
            };
            default -> throw new UnsupportedOperationException("Uniform type not supported: " + dataType);
        };
    }

    public void forceUpload() {
        if (currentValue != null) {
            T temp = currentValue;
            currentValue = null;
            set(temp);
        }
    }

    public T currentValue() {
        return currentValue;
    }

    public int location() {
        return location;
    }

    public ValueType dataType() {
        return dataType;
    }

    public int program() {
        return program;
    }

    public boolean exists() {
        return location != -1;
    }
}

