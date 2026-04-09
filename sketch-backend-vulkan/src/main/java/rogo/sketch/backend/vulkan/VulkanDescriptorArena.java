package rogo.sketch.backend.vulkan;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDescriptorBufferInfo;
import org.lwjgl.vulkan.VkDescriptorImageInfo;
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo;
import org.lwjgl.vulkan.VkDescriptorPoolSize;
import org.lwjgl.vulkan.VkDescriptorSetAllocateInfo;
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding;
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkWriteDescriptorSet;
import rogo.sketch.core.packet.ResourceBindingPlan;
import rogo.sketch.core.packet.ResourceSetKey;
import rogo.sketch.core.pipeline.kernel.FrameExecutionPlan;
import rogo.sketch.core.pipeline.module.diagnostic.SketchDiagnostics;
import rogo.sketch.core.resource.ResourceTypes;
import rogo.sketch.core.util.KeyId;

import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_POOL_CREATE_FREE_DESCRIPTOR_SET_BIT;
import static org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
import static org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;
import static org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER;
import static org.lwjgl.vulkan.VK10.VK_ERROR_FRAGMENTED_POOL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_BIND_POINT_COMPUTE;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_BIND_POINT_GRAPHICS;
import static org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_ALL;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET;
import static org.lwjgl.vulkan.VK10.vkAllocateDescriptorSets;
import static org.lwjgl.vulkan.VK10.vkCmdBindDescriptorSets;
import static org.lwjgl.vulkan.VK10.vkCreateDescriptorPool;
import static org.lwjgl.vulkan.VK10.vkCreateDescriptorSetLayout;
import static org.lwjgl.vulkan.VK10.vkDestroyDescriptorPool;
import static org.lwjgl.vulkan.VK10.vkDestroyDescriptorSetLayout;
import static org.lwjgl.vulkan.VK10.vkFreeDescriptorSets;
import static org.lwjgl.vulkan.VK10.vkUpdateDescriptorSets;

final class VulkanDescriptorArena {
    private static final String DIAG_MODULE = "vulkan-descriptor-arena";
    private static final int INITIAL_MAX_DESCRIPTOR_SETS = 512;
    private static final int INITIAL_MAX_TEXTURE_DESCRIPTORS = 512;
    private static final int INITIAL_MAX_UNIFORM_BUFFER_DESCRIPTORS = 512;
    private static final int INITIAL_MAX_STORAGE_BUFFER_DESCRIPTORS = 512;
    private static final int VK_ERROR_OUT_OF_POOL_MEMORY = -1000069000;

    private final VkDevice device;
    private final VulkanResourceResolver resourceResolver;
    private final Map<KeyId, DescriptorLayout> layouts = new ConcurrentHashMap<>();
    private final Map<ResourceSetKey, InstalledResourceSet> installedSets = new ConcurrentHashMap<>();
    private final Set<String> warnedUnsupportedTypes = ConcurrentHashMap.newKeySet();
    private final Set<String> warnedMissingBindings = ConcurrentHashMap.newKeySet();
    private final Set<String> warnedPoolGrowth = ConcurrentHashMap.newKeySet();
    private final List<DescriptorPoolArena> descriptorPools = new ArrayList<>();
    private final long emptySetLayout;

    VulkanDescriptorArena(VkDevice device, VulkanResourceResolver resourceResolver) {
        this.device = device;
        this.resourceResolver = resourceResolver;
        this.emptySetLayout = createDescriptorSetLayout(List.of());
        this.descriptorPools.add(createDescriptorPoolArena(
                INITIAL_MAX_DESCRIPTOR_SETS,
                INITIAL_MAX_TEXTURE_DESCRIPTORS,
                INITIAL_MAX_UNIFORM_BUFFER_DESCRIPTORS,
                INITIAL_MAX_STORAGE_BUFFER_DESCRIPTORS));
    }

    void install(List<FrameExecutionPlan.ResourceUploadPlan> resourceUploadPlans) {
        if (resourceUploadPlans == null || resourceUploadPlans.isEmpty()) {
            return;
        }
        for (FrameExecutionPlan.ResourceUploadPlan resourceUploadPlan : resourceUploadPlans) {
            if (resourceUploadPlan == null) {
                continue;
            }
            DescriptorLayout descriptorLayout = ensureLayout(resourceUploadPlan.bindingPlan());
            InstalledResourceSet installed = installResourceSet(resourceUploadPlan, descriptorLayout);
            if (installed != null) {
                installedSets.put(resourceUploadPlan.resourceSetKey(), installed);
            }
        }
    }

    long layoutHandle(KeyId resourceLayoutKey) {
        if (resourceLayoutKey == null) {
            return emptySetLayout;
        }
        DescriptorLayout layout = layouts.get(resourceLayoutKey);
        return layout != null ? layout.handle() : emptySetLayout;
    }

    void bindResources(VkCommandBuffer commandBuffer, long pipelineLayout, ResourceSetKey resourceSetKey) {
        bindResources(commandBuffer, pipelineLayout, resourceSetKey, VK_PIPELINE_BIND_POINT_GRAPHICS);
    }

    void bindResources(VkCommandBuffer commandBuffer, long pipelineLayout, ResourceSetKey resourceSetKey, int pipelineBindPoint) {
        if (commandBuffer == null || pipelineLayout == 0L || resourceSetKey == null || resourceSetKey.isEmpty()) {
            return;
        }
        InstalledResourceSet installedResourceSet = installedSets.get(resourceSetKey);
        if (installedResourceSet == null || !installedResourceSet.complete() || installedResourceSet.descriptorSet() == VK_NULL_HANDLE) {
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            vkCmdBindDescriptorSets(
                    commandBuffer,
                    pipelineBindPoint == VK_PIPELINE_BIND_POINT_COMPUTE ? VK_PIPELINE_BIND_POINT_COMPUTE : VK_PIPELINE_BIND_POINT_GRAPHICS,
                    pipelineLayout,
                    0,
                    stack.longs(installedResourceSet.descriptorSet()),
                    null);
        }
    }

    void destroy() {
        for (InstalledResourceSet installedSet : installedSets.values()) {
            freeInstalledSet(installedSet);
        }
        for (DescriptorLayout layout : layouts.values()) {
            if (layout.handle() != emptySetLayout) {
                vkDestroyDescriptorSetLayout(device, layout.handle(), null);
            }
        }
        layouts.clear();
        installedSets.clear();
        warnedUnsupportedTypes.clear();
        warnedMissingBindings.clear();
        warnedPoolGrowth.clear();
        vkDestroyDescriptorSetLayout(device, emptySetLayout, null);
        for (DescriptorPoolArena pool : descriptorPools) {
            vkDestroyDescriptorPool(device, pool.handle(), null);
        }
        descriptorPools.clear();
    }

    private DescriptorLayout ensureLayout(ResourceBindingPlan bindingPlan) {
        ResourceBindingPlan resolvedPlan = bindingPlan != null ? bindingPlan : ResourceBindingPlan.empty();
        if (resolvedPlan.isEmpty()) {
            return new DescriptorLayout(resolvedPlan.layoutKey(), emptySetLayout, List.of());
        }
        return layouts.computeIfAbsent(resolvedPlan.layoutKey(), ignored -> createLayout(resolvedPlan));
    }

    private DescriptorLayout createLayout(ResourceBindingPlan bindingPlan) {
        List<DescriptorBinding> bindings = new ArrayList<>();
        int nextBinding = 0;
        List<ResourceBindingPlan.BindingEntry> sortedEntries = new ArrayList<>(bindingPlan.entries());
        sortedEntries.sort(Comparator
                .comparing((ResourceBindingPlan.BindingEntry entry) -> entry.bindingName().toString())
                .thenComparing(entry -> entry.resourceType().toString()));
        for (ResourceBindingPlan.BindingEntry entry : sortedEntries) {
            Integer descriptorType = descriptorType(entry.resourceType());
            if (descriptorType == null) {
                warnUnsupported(entry, bindingPlan.layoutKey());
                continue;
            }
            bindings.add(new DescriptorBinding(
                    nextBinding++,
                    descriptorType,
                    entry.bindingName(),
                    entry.resourceType()));
        }
        long handle = bindings.isEmpty() ? emptySetLayout : createDescriptorSetLayout(bindings);
        return new DescriptorLayout(bindingPlan.layoutKey(), handle, List.copyOf(bindings));
    }

    private InstalledResourceSet installResourceSet(
            FrameExecutionPlan.ResourceUploadPlan resourceUploadPlan,
            DescriptorLayout descriptorLayout) {
        if (resourceUploadPlan == null || resourceUploadPlan.resourceSetKey() == null) {
            return null;
        }
        InstalledResourceSet existing = installedSets.get(resourceUploadPlan.resourceSetKey());
        if (descriptorLayout.handle() == emptySetLayout || descriptorLayout.bindings().isEmpty()) {
            freeInstalledSet(existing);
            return new InstalledResourceSet(resourceUploadPlan, descriptorLayout, VK_NULL_HANDLE, VK_NULL_HANDLE, 0L, true);
        }

        DescriptorWritePlan writePlan = buildWritePlan(resourceUploadPlan, descriptorLayout);
        if (existing != null
                && existing.descriptorLayout().handle() == descriptorLayout.handle()
                && existing.contentSignature() == writePlan.contentSignature()) {
            return existing;
        }

        if (existing != null && existing.descriptorLayout().handle() == descriptorLayout.handle()) {
            boolean complete = writeDescriptorSet(existing.descriptorSet(), writePlan);
            return new InstalledResourceSet(
                    resourceUploadPlan,
                    descriptorLayout,
                    existing.descriptorSet(),
                    existing.descriptorPool(),
                    writePlan.contentSignature(),
                    complete);
        }

        DescriptorSetAllocation allocation = allocateDescriptorSet(descriptorLayout.handle());
        boolean complete = writeDescriptorSet(allocation.descriptorSet(), writePlan);
        freeInstalledSet(existing);
        return new InstalledResourceSet(
                resourceUploadPlan,
                descriptorLayout,
                allocation.descriptorSet(),
                allocation.descriptorPool(),
                writePlan.contentSignature(),
                complete);
    }

    private Integer descriptorType(KeyId resourceType) {
        KeyId normalized = ResourceTypes.normalize(resourceType);
        if (ResourceTypes.TEXTURE.equals(normalized) || ResourceTypes.IMAGE.equals(normalized)) {
            return VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
        }
        if (ResourceTypes.UNIFORM_BUFFER.equals(normalized)) {
            return VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER;
        }
        if (ResourceTypes.STORAGE_BUFFER.equals(normalized)) {
            return VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;
        }
        return null;
    }

    private void warnUnsupported(ResourceBindingPlan.BindingEntry entry, KeyId layoutKey) {
        String warnKey = layoutKey + ":" + entry.resourceType();
        if (!warnedUnsupportedTypes.add(warnKey)) {
            return;
        }
        SketchDiagnostics.get().warn(
                DIAG_MODULE,
                "Unsupported Vulkan descriptor resource type " + entry.resourceType()
                        + " for layout=" + layoutKey
                        + " binding=" + entry.bindingName());
    }

    private void warnMissingBinding(ResourceBindingPlan.BindingEntry entry, KeyId resourceSetKey, String reason) {
        String warnKey = resourceSetKey + ":" + entry.bindingName() + ":" + entry.resourceId() + ":" + reason;
        if (!warnedMissingBindings.add(warnKey)) {
            return;
        }
        SketchDiagnostics.get().warn(
                DIAG_MODULE,
                "Unable to install Vulkan descriptor for set=" + resourceSetKey
                        + " binding=" + entry.bindingName()
                        + " resourceId=" + entry.resourceId()
                        + " resourceType=" + entry.resourceType()
                        + " reason=" + reason);
    }

    private DescriptorPoolArena createDescriptorPoolArena(
            int maxSets,
            int textureDescriptors,
            int uniformBufferDescriptors,
            int storageBufferDescriptors) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorPoolSize.Buffer poolSizes = VkDescriptorPoolSize.calloc(3, stack);
            poolSizes.get(0)
                    .type(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(textureDescriptors);
            poolSizes.get(1)
                    .type(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                    .descriptorCount(uniformBufferDescriptors);
            poolSizes.get(2)
                    .type(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                    .descriptorCount(storageBufferDescriptors);

            VkDescriptorPoolCreateInfo createInfo = VkDescriptorPoolCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO)
                    .flags(VK_DESCRIPTOR_POOL_CREATE_FREE_DESCRIPTOR_SET_BIT)
                    .maxSets(maxSets)
                    .pPoolSizes(poolSizes);
            LongBuffer descriptorPoolPointer = stack.mallocLong(1);
            VulkanDeviceBootstrapper.checkVkResult(
                    vkCreateDescriptorPool(device, createInfo, null, descriptorPoolPointer),
                    "vkCreateDescriptorPool");
            return new DescriptorPoolArena(
                    descriptorPoolPointer.get(0),
                    maxSets,
                    textureDescriptors,
                    uniformBufferDescriptors,
                    storageBufferDescriptors);
        }
    }

    private long createDescriptorSetLayout(List<DescriptorBinding> bindings) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorSetLayoutCreateInfo createInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO);
            if (!bindings.isEmpty()) {
                VkDescriptorSetLayoutBinding.Buffer bindingBuffer =
                        VkDescriptorSetLayoutBinding.calloc(bindings.size(), stack);
                for (int i = 0; i < bindings.size(); i++) {
                    DescriptorBinding binding = bindings.get(i);
                    bindingBuffer.get(i)
                            .binding(binding.binding())
                            .descriptorType(binding.descriptorType())
                            .descriptorCount(1)
                            .stageFlags(VK_SHADER_STAGE_ALL);
                }
                createInfo.pBindings(bindingBuffer);
            }
            LongBuffer layoutPointer = stack.mallocLong(1);
            VulkanDeviceBootstrapper.checkVkResult(
                    vkCreateDescriptorSetLayout(device, createInfo, null, layoutPointer),
                    "vkCreateDescriptorSetLayout");
            return layoutPointer.get(0);
        }
    }

    private DescriptorSetAllocation allocateDescriptorSet(long descriptorSetLayout) {
        for (int i = descriptorPools.size() - 1; i >= 0; i--) {
            DescriptorPoolArena pool = descriptorPools.get(i);
            DescriptorSetAllocation allocation = tryAllocateDescriptorSet(pool, descriptorSetLayout);
            if (allocation != null) {
                return allocation;
            }
        }
        DescriptorPoolArena grownPool = growDescriptorPoolArena();
        DescriptorSetAllocation allocation = tryAllocateDescriptorSet(grownPool, descriptorSetLayout);
        if (allocation == null) {
            throw new IllegalStateException("Unable to allocate Vulkan descriptor set even after growing descriptor pool arena");
        }
        return allocation;
    }

    private DescriptorPoolArena growDescriptorPoolArena() {
        int factor = 1 << descriptorPools.size();
        DescriptorPoolArena newPool = createDescriptorPoolArena(
                INITIAL_MAX_DESCRIPTOR_SETS * factor,
                INITIAL_MAX_TEXTURE_DESCRIPTORS * factor,
                INITIAL_MAX_UNIFORM_BUFFER_DESCRIPTORS * factor,
                INITIAL_MAX_STORAGE_BUFFER_DESCRIPTORS * factor);
        descriptorPools.add(newPool);
        String growthKey = newPool.maxSets() + ":" + newPool.textureDescriptors() + ":" + newPool.uniformBufferDescriptors()
                + ":" + newPool.storageBufferDescriptors();
        if (warnedPoolGrowth.add(growthKey)) {
            SketchDiagnostics.get().warn(
                    DIAG_MODULE,
                    "Vulkan descriptor pool arena expanded to maxSets=" + newPool.maxSets()
                            + ", textures=" + newPool.textureDescriptors()
                            + ", uniformBuffers=" + newPool.uniformBufferDescriptors()
                            + ", storageBuffers=" + newPool.storageBufferDescriptors());
        }
        return newPool;
    }

    private DescriptorSetAllocation tryAllocateDescriptorSet(DescriptorPoolArena descriptorPool, long descriptorSetLayout) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorSetAllocateInfo allocateInfo = VkDescriptorSetAllocateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO)
                    .descriptorPool(descriptorPool.handle())
                    .pSetLayouts(stack.longs(descriptorSetLayout));
            LongBuffer descriptorSetPointer = stack.mallocLong(1);
            int result = vkAllocateDescriptorSets(device, allocateInfo, descriptorSetPointer);
            if (result == VK_SUCCESS) {
                return new DescriptorSetAllocation(descriptorSetPointer.get(0), descriptorPool.handle());
            }
            if (isPoolExhausted(result)) {
                return null;
            }
            VulkanDeviceBootstrapper.checkVkResult(result, "vkAllocateDescriptorSets");
            return null;
        }
    }

    private boolean isPoolExhausted(int result) {
        return result == VK_ERROR_OUT_OF_POOL_MEMORY
                || result == VK_ERROR_FRAGMENTED_POOL;
    }

    private DescriptorWritePlan buildWritePlan(
            FrameExecutionPlan.ResourceUploadPlan resourceUploadPlan,
            DescriptorLayout descriptorLayout) {
        Map<KeyId, ResourceBindingPlan.BindingEntry> entriesByBindingName = new HashMap<>();
        for (ResourceBindingPlan.BindingEntry entry : resourceUploadPlan.bindingPlan().entries()) {
            entriesByBindingName.put(entry.bindingName(), entry);
        }

        List<ResolvedDescriptorBinding> resolvedBindings = new ArrayList<>(descriptorLayout.bindings().size());
        long contentSignature = descriptorLayout.handle();
        boolean complete = true;
        for (DescriptorBinding descriptorBinding : descriptorLayout.bindings()) {
            ResourceBindingPlan.BindingEntry entry = entriesByBindingName.get(descriptorBinding.bindingName());
            if (entry == null) {
                warnMissingBinding(
                        new ResourceBindingPlan.BindingEntry(
                                descriptorBinding.resourceType(),
                                descriptorBinding.bindingName(),
                                KeyId.of("sketch:missing_resource")),
                        resourceUploadPlan.resourceSetKey().resourceLayoutKey(),
                        "missing_binding_plan_entry");
                complete = false;
                continue;
            }

            VulkanTextureResource textureResource = null;
            VulkanUniformBufferResource uniformBufferResource = null;
            VulkanStorageBufferResource storageBufferResource = null;
            if (descriptorBinding.descriptorType() == VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER) {
                textureResource = resourceResolver.resolveTextureResource(entry.resourceId());
                if (textureResource == null || textureResource.isDisposed()) {
                    warnMissingBinding(entry, resourceUploadPlan.resourceSetKey().resourceLayoutKey(), "missing_vulkan_texture");
                    complete = false;
                    continue;
                }
                contentSignature = mixSignature(contentSignature, descriptorBinding.binding());
                contentSignature = mixSignature(contentSignature, descriptorBinding.descriptorType());
                contentSignature = mixSignature(contentSignature, entry.resourceId().hashCode());
                contentSignature = mixSignature(contentSignature, textureResource.imageView());
                contentSignature = mixSignature(contentSignature, textureResource.sampler());
            } else if (descriptorBinding.descriptorType() == VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER) {
                uniformBufferResource = resourceResolver.resolveUniformBufferResource(entry.resourceId());
                if (uniformBufferResource == null || uniformBufferResource.isDisposed()) {
                    warnMissingBinding(entry, resourceUploadPlan.resourceSetKey().resourceLayoutKey(), "missing_vulkan_uniform_buffer");
                    complete = false;
                    continue;
                }
                contentSignature = mixSignature(contentSignature, descriptorBinding.binding());
                contentSignature = mixSignature(contentSignature, descriptorBinding.descriptorType());
                contentSignature = mixSignature(contentSignature, entry.resourceId().hashCode());
                contentSignature = mixSignature(contentSignature, uniformBufferResource.buffer());
                contentSignature = mixSignature(contentSignature, uniformBufferResource.size());
            } else if (descriptorBinding.descriptorType() == VK_DESCRIPTOR_TYPE_STORAGE_BUFFER) {
                storageBufferResource = resourceResolver.resolveStorageBufferResource(entry.resourceId());
                if (storageBufferResource == null || storageBufferResource.isDisposed()) {
                    warnMissingBinding(entry, resourceUploadPlan.resourceSetKey().resourceLayoutKey(), "missing_vulkan_storage_buffer");
                    complete = false;
                    continue;
                }
                contentSignature = mixSignature(contentSignature, descriptorBinding.binding());
                contentSignature = mixSignature(contentSignature, descriptorBinding.descriptorType());
                contentSignature = mixSignature(contentSignature, entry.resourceId().hashCode());
                contentSignature = mixSignature(contentSignature, storageBufferResource.buffer());
                contentSignature = mixSignature(contentSignature, storageBufferResource.capacityBytes());
            } else {
                warnUnsupported(entry, descriptorLayout.resourceLayoutKey());
                complete = false;
                continue;
            }
            resolvedBindings.add(new ResolvedDescriptorBinding(
                    descriptorBinding,
                    entry,
                    textureResource,
                    uniformBufferResource,
                    storageBufferResource));
        }
        return new DescriptorWritePlan(List.copyOf(resolvedBindings), contentSignature, complete && !resolvedBindings.isEmpty());
    }

    private long mixSignature(long seed, long value) {
        return seed * 31L + value;
    }

    private boolean writeDescriptorSet(
            long descriptorSet,
            DescriptorWritePlan writePlan) {
        if (descriptorSet == VK_NULL_HANDLE || writePlan == null || writePlan.resolvedBindings().isEmpty()) {
            return false;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(writePlan.resolvedBindings().size(), stack);
            int writeCount = 0;

            for (ResolvedDescriptorBinding resolvedBinding : writePlan.resolvedBindings()) {
                DescriptorBinding descriptorBinding = resolvedBinding.descriptorBinding();

                VkWriteDescriptorSet write = writes.get(writeCount)
                        .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                        .dstSet(descriptorSet)
                        .dstBinding(descriptorBinding.binding())
                        .dstArrayElement(0)
                        .descriptorType(descriptorBinding.descriptorType())
                        .descriptorCount(1);

                if (descriptorBinding.descriptorType() == VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER) {
                    VulkanTextureResource textureResource = resolvedBinding.textureResource();
                    VkDescriptorImageInfo.Buffer imageInfo = VkDescriptorImageInfo.calloc(1, stack);
                    imageInfo.get(0)
                            .imageLayout(textureResource.imageLayout() != 0
                                    ? textureResource.imageLayout()
                                    : VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                            .imageView(textureResource.imageView())
                            .sampler(textureResource.sampler());
                    write.pImageInfo(imageInfo);
                } else if (descriptorBinding.descriptorType() == VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER) {
                    VulkanUniformBufferResource uniformBufferResource = resolvedBinding.uniformBufferResource();
                    VkDescriptorBufferInfo.Buffer bufferInfo = VkDescriptorBufferInfo.calloc(1, stack);
                    bufferInfo.get(0)
                            .buffer(uniformBufferResource.buffer())
                            .offset(0L)
                            .range(uniformBufferResource.size());
                    write.pBufferInfo(bufferInfo);
                } else if (descriptorBinding.descriptorType() == VK_DESCRIPTOR_TYPE_STORAGE_BUFFER) {
                    VulkanStorageBufferResource storageBufferResource = resolvedBinding.storageBufferResource();
                    VkDescriptorBufferInfo.Buffer bufferInfo = VkDescriptorBufferInfo.calloc(1, stack);
                    bufferInfo.get(0)
                            .buffer(storageBufferResource.buffer())
                            .offset(0L)
                            .range(storageBufferResource.capacityBytes());
                    write.pBufferInfo(bufferInfo);
                } else {
                    continue;
                }
                writeCount++;
            }

            if (writeCount <= 0) {
                return false;
            }
            writes.limit(writeCount);
            vkUpdateDescriptorSets(device, writes, null);
            return writePlan.complete();
        }
    }

    private void freeInstalledSet(InstalledResourceSet installedSet) {
        if (installedSet == null || installedSet.descriptorSet() == VK_NULL_HANDLE || installedSet.descriptorPool() == VK_NULL_HANDLE) {
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            vkFreeDescriptorSets(device, installedSet.descriptorPool(), stack.longs(installedSet.descriptorSet()));
        }
    }

    private record DescriptorBinding(int binding, int descriptorType, KeyId bindingName, KeyId resourceType) {
    }

    private record DescriptorLayout(KeyId resourceLayoutKey, long handle, List<DescriptorBinding> bindings) {
    }

    private record DescriptorSetAllocation(long descriptorSet, long descriptorPool) {
    }

    private record DescriptorPoolArena(
            long handle,
            int maxSets,
            int textureDescriptors,
            int uniformBufferDescriptors,
            int storageBufferDescriptors
    ) {
    }

    private record ResolvedDescriptorBinding(
            DescriptorBinding descriptorBinding,
            ResourceBindingPlan.BindingEntry entry,
            VulkanTextureResource textureResource,
            VulkanUniformBufferResource uniformBufferResource,
            VulkanStorageBufferResource storageBufferResource
    ) {
    }

    private record DescriptorWritePlan(
            List<ResolvedDescriptorBinding> resolvedBindings,
            long contentSignature,
            boolean complete
    ) {
    }

    private record InstalledResourceSet(
            FrameExecutionPlan.ResourceUploadPlan uploadPlan,
            DescriptorLayout descriptorLayout,
            long descriptorSet,
            long descriptorPool,
            long contentSignature,
            boolean complete
    ) {
    }
}

