package rogo.sketch.vanilla.uniform;

import com.mojang.blaze3d.shaders.Uniform;
import org.joml.*;
import rogo.sketch.api.ShaderResource;
import rogo.sketch.util.Identifier;

import java.lang.Math;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;

public class McUniformWrapperFactory {
    private static final Map<Class<?>, UniformApplier<?>> appliers = new HashMap<>();

    static {
        // Register JOML vector types & Integer/Float with direct uniform set methods
        register(Integer.class, Uniform::set);
        register(Vector2i.class, (u, v) -> u.set(v.x, v.y));
        register(Vector3i.class, (u, v) -> u.set(v.x, v.y, v.z));
        register(Vector4i.class, (u, v) -> u.set(v.x, v.y, v.z, v.w));

        register(Float.class, Uniform::set);
        register(Vector2f.class, (u, v) -> u.set(v.x, v.y));
        register(Vector3f.class, Uniform::set);
        register(Vector4f.class, Uniform::set);

        register(Matrix2f.class, (u, m) -> u.setMat2x2(
                m.m00(), m.m01(),
                m.m10(), m.m11()
        ));
        register(Matrix3f.class, Uniform::set);
        register(Matrix4f.class, Uniform::set);

        register(int[].class, (u, arr) -> {
            IntBuffer buf = u.getIntBuffer();
            buf.clear();
            buf.put(arr, 0, Math.min(arr.length, u.getCount()));
            buf.flip();
        });

        register(Vector2i[].class, (u, arr) -> {
            IntBuffer buf = u.getIntBuffer();
            buf.clear();
            for (Vector2i v : arr) buf.put(v.x).put(v.y);
            buf.flip();
        });

        register(Vector3i[].class, (u, arr) -> {
            IntBuffer buf = u.getIntBuffer();
            buf.clear();
            for (Vector3i v : arr) buf.put(v.x).put(v.y).put(v.z);
            buf.flip();
        });

        register(Vector4i[].class, (u, arr) -> {
            IntBuffer buf = u.getIntBuffer();
            buf.clear();
            for (Vector4i v : arr) buf.put(v.x).put(v.y).put(v.z).put(v.w);
            buf.flip();
        });

        register(float[].class, (u, arr) -> {
            FloatBuffer buf = u.getFloatBuffer();
            buf.clear();
            buf.put(arr, 0, Math.min(arr.length, u.getCount()));
            buf.flip();
        });

        register(Vector2f[].class, (u, arr) -> {
            FloatBuffer buf = u.getFloatBuffer();
            buf.clear();
            for (Vector2f v : arr) buf.put(v.x).put(v.y);
            buf.flip();
        });

        register(Vector3f[].class, (u, arr) -> {
            FloatBuffer buf = u.getFloatBuffer();
            buf.clear();
            for (Vector3f v : arr) buf.put(v.x).put(v.y).put(v.z);
            buf.flip();
        });

        register(Vector4f[].class, (u, arr) -> {
            FloatBuffer buf = u.getFloatBuffer();
            buf.clear();
            for (Vector4f v : arr) buf.put(v.x).put(v.y).put(v.z).put(v.w);
            buf.flip();
        });

        register(Matrix2f[].class, (u, arr) -> {
            FloatBuffer buf = u.getFloatBuffer();
            buf.clear();
            for (Matrix2f m : arr) {
                m.get(buf);
            }
            buf.flip();
        });

        register(Matrix3f[].class, (u, arr) -> {
            FloatBuffer buf = u.getFloatBuffer();
            buf.clear();
            for (Matrix3f m : arr) {
                m.get(buf);
            }
            buf.flip();
        });

        register(Matrix4f[].class, (u, arr) -> {
            FloatBuffer buf = u.getFloatBuffer();
            buf.clear();
            for (Matrix4f m : arr) {
                m.get(buf);
            }
            buf.flip();
        });
    }

    public static <T> McUniformWrapper<T> create(Identifier id, Uniform uniform, Class<T> clazz) {
        @SuppressWarnings("unchecked")
        UniformApplier<T> applier = (UniformApplier<T>) appliers.get(clazz);
        if (applier == null) {
            throw new IllegalArgumentException("No applier registered for type: " + clazz);
        }
        return new McUniformWrapper<>(id, uniform, applier);
    }

    public static <T> void register(Class<T> clazz, UniformApplier<T> applier) {
        appliers.put(clazz, applier);
    }

    public static Map<String, ShaderResource<?>> convertUniformMap(Map<String, Uniform> uniformMap) {
        Map<String, ShaderResource<?>> result = new HashMap<>();
        for (Map.Entry<String, Uniform> entry : uniformMap.entrySet()) {
            Uniform uniform = entry.getValue();
            Identifier id = Identifier.of(entry.getKey());

            ShaderResource<?> wrapper = guessUniformWrapper(id, uniform);
            if (wrapper != null) {
                result.put(entry.getKey(), wrapper);
            }
        }
        return result;
    }

    private static ShaderResource<?> guessUniformWrapper(Identifier id, Uniform uniform) {
        int type = uniform.getType();
        int count = uniform.getCount();

        int elementSize = switch (type) {
            case Uniform.UT_INT1, Uniform.UT_FLOAT1 -> 1;
            case Uniform.UT_INT2, Uniform.UT_FLOAT2 -> 2;
            case Uniform.UT_INT3, Uniform.UT_FLOAT3 -> 3;
            case Uniform.UT_INT4, Uniform.UT_FLOAT4 -> 4;
            case Uniform.UT_MAT2 -> 4;
            case Uniform.UT_MAT3 -> 9;
            case Uniform.UT_MAT4 -> 16;
            default -> 1;
        };

        boolean isArray = count > elementSize;

        if (!isArray) {
            return switch (type) {
                case Uniform.UT_INT1 -> create(id, uniform, Integer.class);
                case Uniform.UT_INT2 -> create(id, uniform, Vector2i.class);
                case Uniform.UT_INT3 -> create(id, uniform, Vector3i.class);
                case Uniform.UT_INT4 -> create(id, uniform, Vector4i.class);

                case Uniform.UT_FLOAT1 -> create(id, uniform, Float.class);
                case Uniform.UT_FLOAT2 -> create(id, uniform, Vector2f.class);
                case Uniform.UT_FLOAT3 -> create(id, uniform, Vector3f.class);
                case Uniform.UT_FLOAT4 -> create(id, uniform, Vector4f.class);

                case Uniform.UT_MAT2 -> create(id, uniform, Matrix2f.class);
                case Uniform.UT_MAT3 -> create(id, uniform, Matrix3f.class);
                case Uniform.UT_MAT4 -> create(id, uniform, Matrix4f.class);
                default -> null;
            };
        } else {
            return switch (type) {
                case Uniform.UT_INT1 -> create(id, uniform, int[].class);
                case Uniform.UT_INT2 -> create(id, uniform, Vector2i[].class);
                case Uniform.UT_INT3 -> create(id, uniform, Vector3i[].class);
                case Uniform.UT_INT4 -> create(id, uniform, Vector4i[].class);

                case Uniform.UT_FLOAT1 -> create(id, uniform, float[].class);
                case Uniform.UT_FLOAT2 -> create(id, uniform, Vector2f[].class);
                case Uniform.UT_FLOAT3 -> create(id, uniform, Vector3f[].class);
                case Uniform.UT_FLOAT4 -> create(id, uniform, Vector4f[].class);

                case Uniform.UT_MAT2 -> create(id, uniform, Matrix2f[].class);
                case Uniform.UT_MAT3 -> create(id, uniform, Matrix3f[].class);
                case Uniform.UT_MAT4 -> create(id, uniform, Matrix4f[].class);
                default -> null;
            };
        }
    }

    @FunctionalInterface
    public interface UniformApplier<T> {
        void apply(Uniform uniform, T value);
    }
}