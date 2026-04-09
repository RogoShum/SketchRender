package rogo.sketch.backend.opengl;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL42;
import org.lwjgl.opengl.GL43;
import org.lwjgl.opengl.GL40;
import rogo.sketch.core.data.type.ValueType;
import rogo.sketch.core.api.ShaderResource;
import rogo.sketch.backend.opengl.driver.GraphicsAPI;
import rogo.sketch.core.resource.ResourceTypes;
import rogo.sketch.core.shader.ProgramReflectionRegistry;
import rogo.sketch.core.shader.ProgramReflectionService;
import rogo.sketch.core.shader.uniform.UniformHookGroup;
import rogo.sketch.core.shader.uniform.UniformHookRegistry;
import rogo.sketch.core.util.KeyId;

import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;

public final class OpenGLProgramReflectionService implements ProgramReflectionService {
    private static volatile OpenGLProgramReflectionService instance;
    private final GraphicsAPI api;

    private OpenGLProgramReflectionService(GraphicsAPI api) {
        this.api = api;
    }

    public static void install(GraphicsAPI api) {
        OpenGLProgramReflectionService service = new OpenGLProgramReflectionService(api);
        instance = service;
        ProgramReflectionRegistry.register(service);
    }

    @Override
    public Map<String, ShaderResource<?>> collectUniforms(int program) {
        Map<String, ShaderResource<?>> uniforms = new HashMap<>();

        IntBuffer sizeBuffer = BufferUtils.createIntBuffer(1);
        IntBuffer typeBuffer = BufferUtils.createIntBuffer(1);
        int uniformCount = GL20.glGetProgrami(program, GL20.GL_ACTIVE_UNIFORMS);

        for (int i = 0; i < uniformCount; i++) {
            sizeBuffer.clear();
            typeBuffer.clear();

            String uniformName = GL20.glGetActiveUniform(program, i, sizeBuffer, typeBuffer);
            int location = GL20.glGetUniformLocation(program, uniformName);
            uniformName = uniformName.replaceFirst("\\[0]$", "");
            int glType = typeBuffer.get(0);
            int glSize = sizeBuffer.get(0);

            if (location >= 0) {
                ValueType dataType = inferUniformType(glType);
                if (dataType != null) {
                    uniforms.put(uniformName, new OpenGLShaderUniform<>(KeyId.of(uniformName), location, dataType, glSize, program));
                }
            }
        }

        return uniforms;
    }

    @Override
    public Map<KeyId, Map<KeyId, Integer>> discoverResourceBindings(int program) {
        Map<KeyId, Map<KeyId, Integer>> bindings = new HashMap<>();

        api.useProgram(program);
        discoverTextureBindings(program, bindings);
        if (GL.getCapabilities().OpenGL43) {
            discoverSsboBindings(program, bindings);
        }
        discoverUboBindings(program, bindings);
        if (GL.getCapabilities().OpenGL42) {
            discoverImageBindings(program, bindings);
        }
        api.useProgram(0);

        return bindings;
    }

    @Override
    public UniformHookGroup initializeHooks(int program, Map<String, ? extends rogo.sketch.core.api.ShaderResource<?>> uniforms) {
        UniformHookGroup hookGroup = new UniformHookGroup();
        UniformHookRegistry.getInstance().initializeHooksFromMap(program, uniforms, hookGroup);
        return hookGroup;
    }

    private static void discoverTextureBindings(int program, Map<KeyId, Map<KeyId, Integer>> bindings) {
        IntBuffer sizeBuffer = BufferUtils.createIntBuffer(1);
        IntBuffer typeBuffer = BufferUtils.createIntBuffer(1);
        int uniformCount = GL20.glGetProgrami(program, GL20.GL_ACTIVE_UNIFORMS);

        Map<KeyId, Integer> textureBindings = new HashMap<>();
        for (int i = 0; i < uniformCount; i++) {
            sizeBuffer.clear();
            typeBuffer.clear();

            String uniformName = GL20.glGetActiveUniform(program, i, sizeBuffer, typeBuffer);
            int location = GL20.glGetUniformLocation(program, uniformName);
            uniformName = uniformName.replaceFirst("\\[0]$", "");
            int glType = typeBuffer.get(0);

            if (location >= 0 && isSamplerType(glType)) {
                textureBindings.put(KeyId.of(uniformName), GL20.glGetUniformi(program, location));
            }
        }

        if (!textureBindings.isEmpty()) {
            bindings.put(ResourceTypes.TEXTURE, textureBindings);
        }
    }

    private static void discoverSsboBindings(int program, Map<KeyId, Map<KeyId, Integer>> bindings) {
        int numSsbo = GL43.glGetProgramInterfacei(program, GL43.GL_SHADER_STORAGE_BLOCK, GL43.GL_ACTIVE_RESOURCES);
        if (numSsbo <= 0) {
            return;
        }

        Map<KeyId, Integer> ssboBindings = new HashMap<>();
        IntBuffer props = BufferUtils.createIntBuffer(1).put(0, GL43.GL_BUFFER_BINDING);
        IntBuffer params = BufferUtils.createIntBuffer(1);

        for (int i = 0; i < numSsbo; i++) {
            String blockName = GL43.glGetProgramResourceName(program, GL43.GL_SHADER_STORAGE_BLOCK, i, 256);
            params.clear();
            GL43.glGetProgramResourceiv(program, GL43.GL_SHADER_STORAGE_BLOCK, i, props, null, params);
            ssboBindings.put(KeyId.of(blockName), params.get(0));
        }

        bindings.put(ResourceTypes.STORAGE_BUFFER, ssboBindings);
    }

    private static void discoverUboBindings(int program, Map<KeyId, Map<KeyId, Integer>> bindings) {
        int numUbo = GL20.glGetProgrami(program, GL31.GL_ACTIVE_UNIFORM_BLOCKS);
        if (numUbo <= 0) {
            return;
        }

        Map<KeyId, Integer> uboBindings = new HashMap<>();
        for (int i = 0; i < numUbo; i++) {
            String blockName = GL31.glGetActiveUniformBlockName(program, i);
            IntBuffer bindingBuffer = BufferUtils.createIntBuffer(1);
            GL31.glGetActiveUniformBlockiv(program, i, GL31.GL_UNIFORM_BLOCK_BINDING, bindingBuffer);
            uboBindings.put(KeyId.of(blockName), bindingBuffer.get(0));
        }

        bindings.put(ResourceTypes.UNIFORM_BUFFER, uboBindings);
    }

    private static void discoverImageBindings(int program, Map<KeyId, Map<KeyId, Integer>> bindings) {
        IntBuffer sizeBuffer = BufferUtils.createIntBuffer(1);
        IntBuffer typeBuffer = BufferUtils.createIntBuffer(1);
        int uniformCount = GL20.glGetProgrami(program, GL20.GL_ACTIVE_UNIFORMS);
        Map<KeyId, Integer> imageBindings = new HashMap<>();

        for (int i = 0; i < uniformCount; i++) {
            sizeBuffer.clear();
            typeBuffer.clear();

            String uniformName = GL20.glGetActiveUniform(program, i, sizeBuffer, typeBuffer);
            int location = GL20.glGetUniformLocation(program, uniformName);
            uniformName = uniformName.replaceFirst("\\[0]$", "");
            int glType = typeBuffer.get(0);

            if (location >= 0 && isImageType(glType)) {
                imageBindings.put(KeyId.of(uniformName), GL20.glGetUniformi(program, location));
            }
        }

        if (!imageBindings.isEmpty()) {
            bindings.put(ResourceTypes.IMAGE, imageBindings);
        }
    }

    private static boolean isSamplerType(int type) {
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

    private static boolean isImageType(int type) {
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

    private static ValueType inferUniformType(int glType) {
        return switch (glType) {
            case GL20.GL_FLOAT -> ValueType.FLOAT;
            case GL20.GL_FLOAT_VEC2 -> ValueType.VEC2F;
            case GL20.GL_FLOAT_VEC3 -> ValueType.VEC3F;
            case GL20.GL_FLOAT_VEC4 -> ValueType.VEC4F;
            case GL20.GL_INT -> ValueType.INT;
            case GL20.GL_INT_VEC2 -> ValueType.VEC2I;
            case GL20.GL_INT_VEC3 -> ValueType.VEC3I;
            case GL20.GL_INT_VEC4 -> ValueType.VEC4I;
            case GL30.GL_UNSIGNED_INT -> ValueType.UINT;
            case GL30.GL_UNSIGNED_INT_VEC2 -> ValueType.VEC2UI;
            case GL30.GL_UNSIGNED_INT_VEC3 -> ValueType.VEC3UI;
            case GL30.GL_UNSIGNED_INT_VEC4 -> ValueType.VEC4UI;
            case GL20.GL_FLOAT_MAT2 -> ValueType.MAT2;
            case GL20.GL_FLOAT_MAT3 -> ValueType.MAT3;
            case GL20.GL_FLOAT_MAT4 -> ValueType.MAT4;
            default -> null;
        };
    }
}

