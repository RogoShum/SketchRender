package rogo.sketch.core.shader;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import rogo.sketch.core.api.ShaderResource;
import rogo.sketch.core.data.DataType;
import rogo.sketch.core.driver.GraphicsDriver;
import rogo.sketch.core.resource.ResourceTypes;
import rogo.sketch.core.shader.uniform.ShaderUniform;
import rogo.sketch.core.shader.uniform.UniformHookGroup;
import rogo.sketch.core.shader.uniform.UniformHookRegistry;
import rogo.sketch.core.util.KeyId;

import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for shader resource discovery and binding management.
 * Extracts uniform collection, resource binding discovery, and uniform hook
 * initialization from the Shader base class for a cleaner separation of concerns.
 */
public final class ShaderResourceHelper {

    private ShaderResourceHelper() {
        // Utility class, no instantiation
    }

    /**
     * Container for unified uniform discovery results.
     */
    public record UniformDiscoveryResult(
            Map<String, ShaderResource<?>> uniforms,
            Map<KeyId, Integer> textureBindings,
            Map<KeyId, Integer> imageBindings
    ) {
    }

    /**
     * Collect uniforms from a shader program.
     *
     * @param program The OpenGL program ID
     * @return Map of uniform name to ShaderUniform
     */
    public static Map<String, ShaderUniform<?>> collectUniforms(int program) {
        Map<String, ShaderUniform<?>> uniforms = new HashMap<>();

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
                DataType dataType = inferUniformType(glType);
                if (dataType != null) {
                    KeyId uniformId = KeyId.of(uniformName);
                    ShaderUniform<?> shaderUniform = new ShaderUniform<>(uniformId, location, dataType, glSize, program);
                    uniforms.put(uniformName, shaderUniform);
                }
            }
        }

        return uniforms;
    }

    /**
     * Discover all resource bindings (textures, SSBOs, UBOs, images) from a shader program.
     *
     * @param program The OpenGL program ID
     * @return Map of resource type to (name, binding) map
     */
    public static Map<KeyId, Map<KeyId, Integer>> discoverResourceBindings(int program) {
        Map<KeyId, Map<KeyId, Integer>> bindings = new HashMap<>();

        GraphicsDriver.getCurrentAPI().useProgram(program);
        // Discover texture bindings
        discoverTextureBindings(program, bindings);

        // Discover SSBO bindings (requires OpenGL 4.3+)
        if (GL.getCapabilities().OpenGL43) {
            discoverSSBOBindings(program, bindings);
        }

        // Discover UBO bindings
        discoverUBOBindings(program, bindings);

        // Discover image bindings (requires OpenGL 4.2+)
        if (GL.getCapabilities().OpenGL42) {
            discoverImageBindings(program, bindings);
        }
        GraphicsDriver.getCurrentAPI().useProgram(0);

        return bindings;
    }

    /**
     * Initialize uniform hooks for a shader program.
     *
     * @param program  The OpenGL program ID
     * @param uniforms The collected uniforms
     * @return The initialized UniformHookGroup
     */
    public static UniformHookGroup initializeHooks(int program, Map<String, ? extends ShaderResource<?>> uniforms) {
        UniformHookGroup hookGroup = new UniformHookGroup();
        UniformHookRegistry.getInstance().initializeHooksFromMap(program, uniforms, hookGroup);
        return hookGroup;
    }

    private static void discoverTextureBindings(int program, Map<KeyId, Map<KeyId, Integer>> bindings) {
        IntBuffer sizeBuffer = BufferUtils.createIntBuffer(1);
        IntBuffer typeBuffer = BufferUtils.createIntBuffer(1);

        int uniformCount = GL20.glGetProgrami(program, GL20.GL_ACTIVE_UNIFORMS);
        int nextTextureUnit = 0;

        Map<KeyId, Integer> textureBindings = new HashMap<>();

        for (int i = 0; i < uniformCount; i++) {
            sizeBuffer.clear();
            typeBuffer.clear();

            String uniformName = GL20.glGetActiveUniform(program, i, sizeBuffer, typeBuffer);
            int location = GL20.glGetUniformLocation(program, uniformName);
            uniformName = uniformName.replaceFirst("\\[0]$", "");
            int glType = typeBuffer.get(0);

            if (location >= 0 && isSamplerType(glType)) {
                int unit = nextTextureUnit++;
                GraphicsDriver.getCurrentAPI().uniform1i(program, location, unit);
                textureBindings.put(KeyId.of(uniformName), unit);
                System.out.println("Discovered Texture: " + uniformName + " -> unit " + unit);
            }
        }

        if (!textureBindings.isEmpty()) {
            bindings.put(ResourceTypes.TEXTURE, textureBindings);
        }
    }

    private static void discoverSSBOBindings(int program, Map<KeyId, Map<KeyId, Integer>> bindings) {
        int numSSBOs = GL43.glGetProgramInterfacei(program, GL43.GL_SHADER_STORAGE_BLOCK, GL43.GL_ACTIVE_RESOURCES);

        if (numSSBOs > 0) {
            Map<KeyId, Integer> ssboBindings = new HashMap<>();

            IntBuffer props = BufferUtils.createIntBuffer(1).put(0, GL43.GL_BUFFER_BINDING);
            IntBuffer params = BufferUtils.createIntBuffer(1);

            for (int i = 0; i < numSSBOs; i++) {
                String blockName = GL43.glGetProgramResourceName(program, GL43.GL_SHADER_STORAGE_BLOCK, i, 256);

                params.clear();
                GL43.glGetProgramResourceiv(program, GL43.GL_SHADER_STORAGE_BLOCK, i, props, null, params);

                int binding = params.get(0);
                ssboBindings.put(KeyId.of(blockName), binding);
                System.out.println("Discovered SSBO: " + blockName + " -> binding " + binding);
            }

            bindings.put(ResourceTypes.SHADER_STORAGE_BUFFER, ssboBindings);
        }
    }

    private static void discoverUBOBindings(int program, Map<KeyId, Map<KeyId, Integer>> bindings) {
        try {
            int numUBOs = GL20.glGetProgrami(program, GL31.GL_ACTIVE_UNIFORM_BLOCKS);

            if (numUBOs > 0) {
                Map<KeyId, Integer> uboBindings = new HashMap<>();

                for (int i = 0; i < numUBOs; i++) {
                    String blockName = GL31.glGetActiveUniformBlockName(program, i);

                    IntBuffer bindingBuffer = BufferUtils.createIntBuffer(1);
                    GL31.glGetActiveUniformBlockiv(program, i, GL31.GL_UNIFORM_BLOCK_BINDING, bindingBuffer);
                    int binding = bindingBuffer.get(0);

                    uboBindings.put(KeyId.of(blockName), binding);
                    System.out.println("Discovered UBO: " + blockName + " -> binding " + binding);
                }

                bindings.put(ResourceTypes.UNIFORM_BLOCK, uboBindings);
            }
        } catch (Exception e) {
            System.err.println("Error discovering UBO bindings: " + e.getMessage());
        }
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
                int unit = GL20.glGetUniformi(program, location);
                imageBindings.put(KeyId.of(uniformName), unit);
                System.out.println("Discovered Image: " + uniformName + " -> unit " + unit);
            }
        }

        if (!imageBindings.isEmpty()) {
            bindings.put(ResourceTypes.IMAGE_BUFFER, imageBindings);
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

    private static DataType inferUniformType(int glType) {
        return switch (glType) {
            case GL20.GL_FLOAT -> DataType.FLOAT;
            case GL20.GL_FLOAT_VEC2 -> DataType.VEC2F;
            case GL20.GL_FLOAT_VEC3 -> DataType.VEC3F;
            case GL20.GL_FLOAT_VEC4 -> DataType.VEC4F;
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
}

