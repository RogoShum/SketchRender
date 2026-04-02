package rogo.sketch.backend.vulkan;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkShaderModuleCreateInfo;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;

import static org.lwjgl.util.shaderc.Shaderc.shaderc_compile_into_spv;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_compilation_status_success;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_compiler_initialize;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_compiler_release;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_compile_options_initialize;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_compile_options_release;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_glsl_fragment_shader;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_glsl_vertex_shader;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_result_get_bytes;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_result_get_compilation_status;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_result_get_error_message;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_result_release;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.vkCreateShaderModule;

final class VulkanShaderCompiler {
    private VulkanShaderCompiler() {
    }

    static long createVertexShaderModule(VkDevice device, String source, String name) {
        return createShaderModule(device, source, shaderc_glsl_vertex_shader, name);
    }

    static long createFragmentShaderModule(VkDevice device, String source, String name) {
        return createShaderModule(device, source, shaderc_glsl_fragment_shader, name);
    }

    private static long createShaderModule(VkDevice device, String source, int shaderKind, String name) {
        long compiler = shaderc_compiler_initialize();
        long options = shaderc_compile_options_initialize();
        if (compiler == 0L || options == 0L) {
            if (options != 0L) {
                shaderc_compile_options_release(options);
            }
            if (compiler != 0L) {
                shaderc_compiler_release(compiler);
            }
            throw new IllegalStateException("Failed to initialize shaderc compiler");
        }

        long result = 0L;
        try {
            result = shaderc_compile_into_spv(compiler, source, shaderKind, name, "main", options);
            if (result == 0L) {
                throw new IllegalStateException("shaderc returned null result for " + name);
            }
            int status = shaderc_result_get_compilation_status(result);
            if (status != shaderc_compilation_status_success) {
                throw new IllegalStateException("Failed to compile " + name + ": " + shaderc_result_get_error_message(result));
            }

            ByteBuffer spirv = shaderc_result_get_bytes(result);
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkShaderModuleCreateInfo createInfo = VkShaderModuleCreateInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO)
                        .pCode(spirv);
                LongBuffer shaderModulePointer = stack.mallocLong(1);
                VulkanDeviceBootstrapper.checkVkResult(
                        vkCreateShaderModule(device, createInfo, null, shaderModulePointer),
                        "vkCreateShaderModule(" + name + ")");
                return shaderModulePointer.get(0);
            }
        } finally {
            if (result != 0L) {
                shaderc_result_release(result);
            }
            shaderc_compile_options_release(options);
            shaderc_compiler_release(compiler);
        }
    }
}
