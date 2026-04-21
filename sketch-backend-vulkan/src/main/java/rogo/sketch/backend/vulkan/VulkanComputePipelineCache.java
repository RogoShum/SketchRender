package rogo.sketch.backend.vulkan;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkComputePipelineCreateInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo;
import rogo.sketch.core.packet.ComputePipelineKey;
import rogo.sketch.core.util.KeyId;

import java.nio.LongBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_BIND_POINT_COMPUTE;
import static org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_COMPUTE_BIT;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_COMPUTE_PIPELINE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.vkCreateComputePipelines;
import static org.lwjgl.vulkan.VK10.vkDestroyPipeline;

final class VulkanComputePipelineCache {
    private final VkDevice device;
    private final VulkanPipelineLayoutCache layoutCache;
    private final VulkanShaderVariantCache shaderVariantCache;
    private final Map<PipelineVariantKey, PipelineVariant> pipelines = new ConcurrentHashMap<>();

    VulkanComputePipelineCache(VkDevice device, VulkanPipelineLayoutCache layoutCache) {
        this.device = device;
        this.layoutCache = layoutCache;
        this.shaderVariantCache = new VulkanShaderVariantCache(device);
    }

    long pipelineFor(ComputePipelineKey stateKey, KeyId resourceLayoutKey, long descriptorSetLayout) {
        PipelineVariantKey key = new PipelineVariantKey(
                stateKey,
                resourceLayoutKey != null ? resourceLayoutKey : KeyId.of("sketch:empty_resource_layout"));
        PipelineVariant variant = pipelines.computeIfAbsent(key, ignored -> createPipelineVariant(key, descriptorSetLayout));
        return variant != null ? variant.pipeline() : VK_NULL_HANDLE;
    }

    long pipelineLayout(KeyId resourceLayoutKey, long descriptorSetLayout) {
        return layoutCache.layoutFor(resourceLayoutKey, descriptorSetLayout);
    }

    void destroy() {
        for (PipelineVariant variant : pipelines.values()) {
            if (variant != null && variant.pipeline() != VK_NULL_HANDLE) {
                vkDestroyPipeline(device, variant.pipeline(), null);
            }
        }
        pipelines.clear();
        shaderVariantCache.destroy();
    }

    private PipelineVariant createPipelineVariant(PipelineVariantKey key, long descriptorSetLayout) {
        if (key == null || key.stateKey() == null) {
            return PipelineVariant.invalid();
        }

        VulkanShaderVariantCache.ComputeVariantModules computeVariant = shaderVariantCache.resolveComputeVariant(
                key.stateKey().shaderId(),
                key.stateKey().shaderVariantKey(),
                key.resourceLayoutKey());
        if (computeVariant == null || !computeVariant.isValid()) {
            return PipelineVariant.invalid();
        }

        long pipelineLayout = layoutCache.layoutFor(key.resourceLayoutKey(), descriptorSetLayout);
        long pipeline = createComputePipeline(
                device,
                pipelineLayout,
                computeVariant.computeShaderModule(),
                computeVariant.spec().templateId(),
                key.resourceLayoutKey());
        return new PipelineVariant(pipeline, pipelineLayout);
    }

    private static long createComputePipeline(
            VkDevice device,
            long pipelineLayout,
            long computeShaderModule,
            KeyId shaderTemplateId,
            KeyId resourceLayoutKey) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPipelineShaderStageCreateInfo shaderStage = VkPipelineShaderStageCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                    .stage(VK_SHADER_STAGE_COMPUTE_BIT)
                    .module(computeShaderModule)
                    .pName(stack.UTF8("main"));

            VkComputePipelineCreateInfo.Buffer createInfo = VkComputePipelineCreateInfo.calloc(1, stack);
            createInfo.get(0)
                    .sType(VK_STRUCTURE_TYPE_COMPUTE_PIPELINE_CREATE_INFO)
                    .stage(shaderStage)
                    .layout(pipelineLayout)
                    .basePipelineHandle(VK_NULL_HANDLE)
                    .basePipelineIndex(-1);

            LongBuffer pipelinePointer = stack.mallocLong(1);
            try {
                VulkanDeviceBootstrapper.checkVkResult(
                        vkCreateComputePipelines(device, VK_NULL_HANDLE, createInfo, null, pipelinePointer),
                        "vkCreateComputePipelines(shader=" + shaderTemplateId + ", layout=" + resourceLayoutKey + ")");
            } catch (RuntimeException ex) {
                throw new IllegalStateException(
                        "Failed to create Vulkan compute pipeline for shader=" + shaderTemplateId
                                + ", resourceLayout=" + resourceLayoutKey,
                        ex);
            }
            return pipelinePointer.get(0);
        }
    }

    private record PipelineVariantKey(ComputePipelineKey stateKey, KeyId resourceLayoutKey) {
    }

    private record PipelineVariant(long pipeline, long pipelineLayout) {
        static PipelineVariant invalid() {
            return new PipelineVariant(VK_NULL_HANDLE, VK_NULL_HANDLE);
        }
    }
}

