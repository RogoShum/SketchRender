package rogo.sketch.render.shader.uniform;

import org.joml.*;
import org.lwjgl.opengl.GL20;
import org.lwjgl.system.MemoryUtil;
import rogo.sketch.api.ShaderResource;
import rogo.sketch.render.data.DataType;
import rogo.sketch.util.Identifier;

import java.lang.Math;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Objects;
import java.util.function.Consumer;

public class ShaderUniform<T> implements ShaderResource<T> {
    private final Identifier identifier;
    private final int location;
    private final DataType dataType;
    private final int dataCount;
    private final int program;
    private final Consumer<T> valueConsumer;
    private final IntBuffer intValues;
    private final FloatBuffer floatValues;
    private T currentValue;

    public ShaderUniform(Identifier identifier, int location, DataType dataType, int dataCount, int program) {
        this.identifier = identifier;
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
    public Identifier id() {
        return identifier;
    }

    @Override
    public void set(T value) {
        if (Objects.equals(currentValue, value)) {
            return; // No change needed
        }

        currentValue = value;
        valueConsumer.accept(value);
    }

    private Consumer<T> createValueSetter() {
        if (location == -1) {
            return (v) -> {
            };
        }

        int elementSize = dataType.getComponentCount();
        boolean isArray = dataCount > elementSize;

        if (isArray) {
            switch (dataType) {
                case FLOAT -> {
                    return (value) -> {
                        float[] arr = (float[]) value;
                        floatValues.clear();
                        floatValues.put(arr, 0, Math.min(arr.length, dataCount));
                        floatValues.flip();
                        GL20.glUniform1fv(location, floatValues);
                    };
                }
                case VEC2 -> {
                    return (value) -> {
                        Vector2f[] arr = (Vector2f[]) value;
                        floatValues.clear();
                        for (Vector2f v : arr) floatValues.put(v.x).put(v.y);
                        floatValues.flip();
                        GL20.glUniform2fv(location, floatValues);
                    };
                }
                case VEC3 -> {
                    return (value) -> {
                        Vector3f[] arr = (Vector3f[]) value;
                        floatValues.clear();
                        for (Vector3f v : arr) floatValues.put(v.x).put(v.y).put(v.z);
                        floatValues.flip();
                        GL20.glUniform3fv(location, floatValues);
                    };
                }
                case VEC4 -> {
                    return (value) -> {
                        Vector4f[] arr = (Vector4f[]) value;
                        floatValues.clear();
                        for (Vector4f v : arr) floatValues.put(v.x).put(v.y).put(v.z).put(v.w);
                        floatValues.flip();
                        GL20.glUniform4fv(location, floatValues);
                    };
                }
                case INT -> {
                    return (value) -> {
                        int[] arr = (int[]) value;
                        intValues.clear();
                        intValues.put(arr, 0, Math.min(arr.length, dataCount));
                        intValues.flip();
                        GL20.glUniform1iv(location, intValues);
                    };
                }
                case VEC2I -> {
                    return (value) -> {
                        Vector2i[] arr = (Vector2i[]) value;
                        intValues.clear();
                        for (Vector2i v : arr) intValues.put(v.x).put(v.y);
                        intValues.flip();
                        GL20.glUniform2iv(location, intValues);
                    };
                }
                case VEC3I -> {
                    return (value) -> {
                        Vector3i[] arr = (Vector3i[]) value;
                        intValues.clear();
                        for (Vector3i v : arr) intValues.put(v.x).put(v.y).put(v.z);
                        intValues.flip();
                        GL20.glUniform3iv(location, intValues);
                    };
                }
                case VEC4I -> {
                    return (value) -> {
                        Vector4i[] arr = (Vector4i[]) value;
                        intValues.clear();
                        for (Vector4i v : arr) intValues.put(v.x).put(v.y).put(v.z).put(v.w);
                        intValues.flip();
                        GL20.glUniform4iv(location, intValues);
                    };
                }
                case MAT2 -> {
                    return (value) -> {
                        Matrix2f[] arr = (Matrix2f[]) value;
                        floatValues.clear();
                        for (Matrix2f mat : arr) mat.get(floatValues);
                        floatValues.flip();
                        GL20.glUniformMatrix2fv(location, false, floatValues);
                    };
                }
                case MAT3 -> {
                    return (value) -> {
                        Matrix3f[] arr = (Matrix3f[]) value;
                        floatValues.clear();
                        for (Matrix3f mat : arr) mat.get(floatValues);
                        floatValues.flip();
                        GL20.glUniformMatrix3fv(location, false, floatValues);
                    };
                }
                case MAT4 -> {
                    return (value) -> {
                        Matrix4f[] arr = (Matrix4f[]) value;
                        floatValues.clear();
                        for (Matrix4f mat : arr) mat.get(floatValues);
                        floatValues.flip();
                        GL20.glUniformMatrix4fv(location, false, floatValues);
                    };
                }
                default -> throw new UnsupportedOperationException("Uniform type not supported: " + dataType);
            }
        } else {
            switch (dataType) {
                case FLOAT -> {
                    return (value) -> GL20.glUniform1f(location, (Float) value);
                }
                case VEC2 -> {
                    return (value) -> {
                        Vector2f vec = (Vector2f) value;
                        GL20.glUniform2f(location, vec.x, vec.y);
                    };
                }
                case VEC3 -> {
                    return (value) -> {
                        Vector3f vec = (Vector3f) value;
                        GL20.glUniform3f(location, vec.x, vec.y, vec.z);
                    };
                }
                case VEC4 -> {
                    return (value) -> {
                        Vector4f vec = (Vector4f) value;
                        GL20.glUniform4f(location, vec.x, vec.y, vec.z, vec.w);
                    };
                }
                case INT -> {
                    return (value) -> {
                        GL20.glUniform1i(location, (Integer) value);
                    };
                }
                case VEC2I -> {
                    return (value) -> {
                        Vector2i vec = (Vector2i) value;
                        GL20.glUniform2i(location, vec.x, vec.y);
                    };
                }
                case VEC3I -> {
                    return (value) -> {
                        Vector3i vec = (Vector3i) value;
                        GL20.glUniform3i(location, vec.x, vec.y, vec.z);
                    };
                }
                case VEC4I -> {
                    return (value) -> {
                        Vector4i vec = (Vector4i) value;
                        GL20.glUniform4i(location, vec.x, vec.y, vec.z, vec.w);
                    };
                }
                case MAT2 -> {
                    return (value) -> {
                        Matrix2f mat = (Matrix2f) value;
                        float[] buffer = new float[4];
                        mat.get(buffer);
                        GL20.glUniformMatrix2fv(location, false, buffer);
                    };
                }
                case MAT3 -> {
                    return (value) -> {
                        Matrix3f mat = (Matrix3f) value;
                        float[] buffer = new float[9];
                        mat.get(buffer);
                        GL20.glUniformMatrix3fv(location, false, buffer);
                    };
                }
                case MAT4 -> {
                    return (value) -> {
                        Matrix4f mat = (Matrix4f) value;
                        float[] buffer = new float[16];
                        mat.get(buffer);
                        GL20.glUniformMatrix4fv(location, false, buffer);
                    };
                }
                default -> throw new UnsupportedOperationException("Uniform type not supported: " + dataType);
            }
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
