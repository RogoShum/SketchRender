package rogo.sketchrender.render.minecraft.uniform;

import com.mojang.blaze3d.shaders.Uniform;
import org.joml.*;
import rogo.sketchrender.api.ShaderUniform;
import rogo.sketchrender.util.Identifier;

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

    public static Map<String, ShaderUniform<?>> convertUniformMap(Map<String, Uniform> uniformMap) {
        Map<String, ShaderUniform<?>> result = new HashMap<>();
        for (Map.Entry<String, Uniform> entry : uniformMap.entrySet()) {
            Uniform uniform = entry.getValue();
            Identifier id = Identifier.of(entry.getKey());

            ShaderUniform<?> wrapper = guessUniformWrapper(id, uniform);
            if (wrapper != null) {
                result.put(entry.getKey(), wrapper);
            }
        }
        return result;
    }

    private static ShaderUniform<?> guessUniformWrapper(Identifier id, Uniform uniform) {
        int type = uniform.getType();
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
    }

    @FunctionalInterface
    public interface UniformApplier<T> {
        void apply(Uniform uniform, T value);
    }
}