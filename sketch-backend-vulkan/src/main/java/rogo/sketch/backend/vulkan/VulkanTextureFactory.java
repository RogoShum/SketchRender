package rogo.sketch.backend.vulkan;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkBufferCreateInfo;
import org.lwjgl.vulkan.VkBufferImageCopy;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkImageCreateInfo;
import org.lwjgl.vulkan.VkImageMemoryBarrier;
import org.lwjgl.vulkan.VkImageViewCreateInfo;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryRequirements;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties;
import org.lwjgl.vulkan.VkSamplerCreateInfo;
import rogo.sketch.core.resource.descriptor.ImageFormat;
import rogo.sketch.core.resource.descriptor.ResolvedImageResource;
import rogo.sketch.core.resource.descriptor.SamplerFilter;
import rogo.sketch.core.resource.descriptor.SamplerWrap;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.Objects;

import static org.lwjgl.vulkan.VK10.VK_ACCESS_SHADER_READ_BIT;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_TRANSFER_WRITE_BIT;
import static org.lwjgl.vulkan.VK10.VK_BORDER_COLOR_INT_OPAQUE_BLACK;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_TRANSFER_SRC_BIT;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_D32_SFLOAT;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_R32_SFLOAT;
import static org.lwjgl.vulkan.VK10.VK_FILTER_LINEAR;
import static org.lwjgl.vulkan.VK10.VK_FILTER_NEAREST;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_R8G8B8A8_UNORM;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_COLOR_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_DEPTH_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_GENERAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_UNDEFINED;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_TILING_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_TYPE_2D;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_SAMPLED_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_STORAGE_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_TRANSFER_DST_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_TRANSFER_SRC_BIT;
import static org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT;
import static org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;
import static org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_TRANSFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_QUEUE_FAMILY_IGNORED;
import static org.lwjgl.vulkan.VK10.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
import static org.lwjgl.vulkan.VK10.VK_SAMPLER_ADDRESS_MODE_MIRRORED_REPEAT;
import static org.lwjgl.vulkan.VK10.VK_SAMPLER_ADDRESS_MODE_REPEAT;
import static org.lwjgl.vulkan.VK10.VK_SAMPLER_MIPMAP_MODE_NEAREST;
import static org.lwjgl.vulkan.VK10.VK_SAMPLE_COUNT_1_BIT;
import static org.lwjgl.vulkan.VK10.VK_SHARING_MODE_EXCLUSIVE;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_VIEW_TYPE_2D;
import static org.lwjgl.vulkan.VK10.vkBindBufferMemory;
import static org.lwjgl.vulkan.VK10.vkAllocateMemory;
import static org.lwjgl.vulkan.VK10.vkBindImageMemory;
import static org.lwjgl.vulkan.VK10.vkCmdCopyBufferToImage;
import static org.lwjgl.vulkan.VK10.vkCmdPipelineBarrier;
import static org.lwjgl.vulkan.VK10.vkCreateBuffer;
import static org.lwjgl.vulkan.VK10.vkCreateImage;
import static org.lwjgl.vulkan.VK10.vkCreateImageView;
import static org.lwjgl.vulkan.VK10.vkCreateSampler;
import static org.lwjgl.vulkan.VK10.vkDestroyBuffer;
import static org.lwjgl.vulkan.VK10.vkFreeMemory;
import static org.lwjgl.vulkan.VK10.vkGetBufferMemoryRequirements;
import static org.lwjgl.vulkan.VK10.vkGetImageMemoryRequirements;
import static org.lwjgl.vulkan.VK10.vkGetPhysicalDeviceMemoryProperties;
import static org.lwjgl.vulkan.VK10.vkMapMemory;
import static org.lwjgl.vulkan.VK10.vkUnmapMemory;

final class VulkanTextureFactory {
    private VulkanTextureFactory() {
    }

    static VulkanTextureResource createUploadedSampledTexture(
            VulkanBackendRuntime runtime,
            ResolvedImageResource descriptor,
            ByteBuffer imageData) {
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(imageData, "imageData");
        int width = Math.max(1, descriptor.width());
        int height = Math.max(1, descriptor.height());
        int uploadSize = uploadSizeBytes(width, height, descriptor.format());
        ByteBuffer uploadData = uploadSlice(imageData, uploadSize);
        VulkanTextureResource resource = createTexture(
                runtime.physicalDevice(),
                runtime.device(),
                width,
                height,
                1,
                vulkanFormat(descriptor.format()),
                VK_IMAGE_ASPECT_COLOR_BIT,
                VK_IMAGE_USAGE_SAMPLED_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT,
                VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                true,
                filterMode(descriptor.magFilter()),
                filterMode(descriptor.minFilter()),
                addressMode(descriptor.wrapS()),
                addressMode(descriptor.wrapT()));
        StagingBuffer stagingBuffer = null;
        try {
            stagingBuffer = createStagingBuffer(runtime.physicalDevice(), runtime.device(), uploadData);
            StagingBuffer uploadBuffer = stagingBuffer;
            runtime.submitGraphicsCommandsAndWait(
                    "vkUploadTexture(" + descriptor.identifier() + ")",
                    commandBuffer -> {
                        transitionImageLayout(
                                commandBuffer,
                                resource.image(),
                                VK_IMAGE_LAYOUT_UNDEFINED,
                                VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                                resource.mipLevels());
                        copyBufferToImage(commandBuffer, uploadBuffer.buffer(), resource.image(), width, height);
                        transitionImageLayout(
                                commandBuffer,
                                resource.image(),
                                VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                                VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                                resource.mipLevels());
                    });
            resource.setCurrentImageLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
            return resource;
        } catch (RuntimeException e) {
            resource.dispose();
            throw e;
        } finally {
            if (stagingBuffer != null) {
                stagingBuffer.destroy(runtime.device());
            }
        }
    }

    static VulkanTextureResource createPlaceholderTexture(
            VkPhysicalDevice physicalDevice,
            VkDevice device,
            int width,
            int height) {
        return createPlaceholderTexture(physicalDevice, device, width, height, 1);
    }

    static VulkanTextureResource createPlaceholderTexture(
            VkPhysicalDevice physicalDevice,
            VkDevice device,
            int width,
            int height,
            int mipLevels) {
        return createTexture(
                physicalDevice,
                device,
                width,
                height,
                mipLevels,
                VK_FORMAT_R8G8B8A8_UNORM,
                VK_IMAGE_ASPECT_COLOR_BIT,
                VK_IMAGE_USAGE_SAMPLED_BIT
                        | VK_IMAGE_USAGE_TRANSFER_SRC_BIT
                        | VK_IMAGE_USAGE_TRANSFER_DST_BIT,
                VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                true);
    }

    static VulkanTextureResource createRenderTargetColorTexture(
            VkPhysicalDevice physicalDevice,
            VkDevice device,
            int width,
            int height,
            int mipLevels) {
        return createRenderTargetColorTexture(physicalDevice, device, width, height, mipLevels, VK_FORMAT_R8G8B8A8_UNORM);
    }

    static VulkanTextureResource createRenderTargetColorTexture(
            VkPhysicalDevice physicalDevice,
            VkDevice device,
            int width,
            int height,
            int mipLevels,
            int format) {
        return createTexture(
                physicalDevice,
                device,
                width,
                height,
                mipLevels,
                format,
                VK_IMAGE_ASPECT_COLOR_BIT,
                VK_IMAGE_USAGE_SAMPLED_BIT
                        | VK_IMAGE_USAGE_TRANSFER_SRC_BIT
                        | VK_IMAGE_USAGE_TRANSFER_DST_BIT
                        | VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT,
                VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                true);
    }

    static VulkanTextureResource createDepthTexture(
            VkPhysicalDevice physicalDevice,
            VkDevice device,
            int width,
            int height) {
        return createTexture(
                physicalDevice,
                device,
                width,
                height,
                1,
                VK_FORMAT_D32_SFLOAT,
                VK_IMAGE_ASPECT_DEPTH_BIT,
                VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT,
                VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL,
                false);
    }

    static VulkanTextureResource createSampledDepthTexture(
            VkPhysicalDevice physicalDevice,
            VkDevice device,
            int width,
            int height) {
        return createTexture(
                physicalDevice,
                device,
                width,
                height,
                1,
                VK_FORMAT_D32_SFLOAT,
                VK_IMAGE_ASPECT_DEPTH_BIT,
                VK_IMAGE_USAGE_TRANSFER_DST_BIT
                        | VK_IMAGE_USAGE_TRANSFER_SRC_BIT
                        | VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT
                        | VK_IMAGE_USAGE_SAMPLED_BIT,
                VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL,
                true);
    }

    static VulkanTextureResource createSampledStorageTexture(
            VkPhysicalDevice physicalDevice,
            VkDevice device,
            int width,
            int height) {
        return createSampledStorageTexture(physicalDevice, device, width, height, 1, VK_FORMAT_R32_SFLOAT);
    }

    static VulkanTextureResource createSampledFloatTexture(
            VkPhysicalDevice physicalDevice,
            VkDevice device,
            int width,
            int height) {
        return createTexture(
                physicalDevice,
                device,
                width,
                height,
                1,
                VK_FORMAT_R32_SFLOAT,
                VK_IMAGE_ASPECT_COLOR_BIT,
                VK_IMAGE_USAGE_SAMPLED_BIT
                        | VK_IMAGE_USAGE_TRANSFER_SRC_BIT
                        | VK_IMAGE_USAGE_TRANSFER_DST_BIT,
                VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                true);
    }

    static VulkanTextureResource createSampledStorageTexture(
            VkPhysicalDevice physicalDevice,
            VkDevice device,
            int width,
            int height,
            int mipLevels,
            int format) {
        VulkanTextureResource resource = createTexture(
                physicalDevice,
                device,
                width,
                height,
                mipLevels,
                format,
                VK_IMAGE_ASPECT_COLOR_BIT,
                VK_IMAGE_USAGE_SAMPLED_BIT
                        | VK_IMAGE_USAGE_STORAGE_BIT
                        | VK_IMAGE_USAGE_TRANSFER_SRC_BIT
                        | VK_IMAGE_USAGE_TRANSFER_DST_BIT,
                VK_IMAGE_LAYOUT_GENERAL,
                true);
        resource.setCurrentImageLayout(VK_IMAGE_LAYOUT_GENERAL);
        return resource;
    }

    private static VulkanTextureResource createTexture(
            VkPhysicalDevice physicalDevice,
            VkDevice device,
            int width,
            int height,
            int mipLevels,
            int format,
            int aspectMask,
            int usageFlags,
            int descriptorImageLayout,
            boolean createSampler) {
        return createTexture(
                physicalDevice,
                device,
                width,
                height,
                mipLevels,
                format,
                aspectMask,
                usageFlags,
                descriptorImageLayout,
                createSampler,
                VK_FILTER_NEAREST,
                VK_FILTER_NEAREST,
                VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE,
                VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE);
    }

    private static VulkanTextureResource createTexture(
            VkPhysicalDevice physicalDevice,
            VkDevice device,
            int width,
            int height,
            int mipLevels,
            int format,
            int aspectMask,
            int usageFlags,
            int descriptorImageLayout,
            boolean createSampler,
            int magFilter,
            int minFilter,
            int addressModeU,
            int addressModeV) {
        int safeWidth = Math.max(1, width);
        int safeHeight = Math.max(1, height);
        int safeMipLevels = Math.max(1, mipLevels);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageCreateInfo imageInfo = VkImageCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO)
                    .imageType(VK_IMAGE_TYPE_2D)
                    .format(format)
                    .mipLevels(safeMipLevels)
                    .arrayLayers(1)
                    .samples(VK_SAMPLE_COUNT_1_BIT)
                    .tiling(VK_IMAGE_TILING_OPTIMAL)
                    .usage(usageFlags)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
                    .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);
            imageInfo.extent()
                    .width(safeWidth)
                    .height(safeHeight)
                    .depth(1);

            LongBuffer imagePointer = stack.mallocLong(1);
            VulkanDeviceBootstrapper.checkVkResult(
                    vkCreateImage(device, imageInfo, null, imagePointer),
                    "vkCreateImage(placeholder-texture)");
            long image = imagePointer.get(0);

            VkMemoryRequirements memoryRequirements = VkMemoryRequirements.calloc(stack);
            vkGetImageMemoryRequirements(device, image, memoryRequirements);

            VkMemoryAllocateInfo allocateInfo = VkMemoryAllocateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                    .allocationSize(memoryRequirements.size())
                    .memoryTypeIndex(findMemoryType(
                            physicalDevice,
                            memoryRequirements.memoryTypeBits(),
                            VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT));
            LongBuffer memoryPointer = stack.mallocLong(1);
            VulkanDeviceBootstrapper.checkVkResult(
                    vkAllocateMemory(device, allocateInfo, null, memoryPointer),
                    "vkAllocateMemory(placeholder-texture)");
            long memory = memoryPointer.get(0);

            VulkanDeviceBootstrapper.checkVkResult(
                    vkBindImageMemory(device, image, memory, 0L),
                    "vkBindImageMemory(placeholder-texture)");

            VkImageViewCreateInfo viewInfo = VkImageViewCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
                    .image(image)
                    .viewType(VK_IMAGE_VIEW_TYPE_2D)
                    .format(format);
            viewInfo.subresourceRange()
                    .aspectMask(aspectMask)
                    .baseMipLevel(0)
                    .levelCount(safeMipLevels)
                    .baseArrayLayer(0)
                    .layerCount(1);

            LongBuffer imageViewPointer = stack.mallocLong(1);
            VulkanDeviceBootstrapper.checkVkResult(
                    vkCreateImageView(device, viewInfo, null, imageViewPointer),
                    "vkCreateImageView(placeholder-texture)");
            long imageView = imageViewPointer.get(0);

            long sampler = 0L;
            if (createSampler) {
                VkSamplerCreateInfo samplerInfo = VkSamplerCreateInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO)
                        .magFilter(magFilter)
                        .minFilter(minFilter)
                        .addressModeU(addressModeU)
                        .addressModeV(addressModeV)
                        .addressModeW(addressModeV)
                        .anisotropyEnable(false)
                        .maxAnisotropy(1.0f)
                        .borderColor(VK_BORDER_COLOR_INT_OPAQUE_BLACK)
                        .unnormalizedCoordinates(false)
                        .compareEnable(false)
                        .mipmapMode(VK_SAMPLER_MIPMAP_MODE_NEAREST)
                        .minLod(0.0f)
                        .maxLod(Math.max(0, safeMipLevels - 1))
                        .mipLodBias(0.0f);

                LongBuffer samplerPointer = stack.mallocLong(1);
                VulkanDeviceBootstrapper.checkVkResult(
                        vkCreateSampler(device, samplerInfo, null, samplerPointer),
                        "vkCreateSampler(placeholder-texture)");
                sampler = samplerPointer.get(0);
            }

            return new VulkanTextureResource(
                    device,
                    image,
                    memory,
                    imageView,
                    sampler,
                    safeWidth,
                    safeHeight,
                    safeMipLevels,
                    descriptorImageLayout,
                    VK_IMAGE_LAYOUT_UNDEFINED,
                    format,
                    aspectMask,
                    usageFlags,
                    true);
        }
    }

    private static StagingBuffer createStagingBuffer(
            VkPhysicalDevice physicalDevice,
            VkDevice device,
            ByteBuffer uploadData) {
        int uploadSize = uploadData.remaining();
        if (uploadSize <= 0) {
            throw new IllegalArgumentException("Texture upload data must not be empty");
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferCreateInfo bufferInfo = VkBufferCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
                    .size(uploadSize)
                    .usage(VK_BUFFER_USAGE_TRANSFER_SRC_BIT)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE);
            LongBuffer bufferPointer = stack.mallocLong(1);
            VulkanDeviceBootstrapper.checkVkResult(
                    vkCreateBuffer(device, bufferInfo, null, bufferPointer),
                    "vkCreateBuffer(texture-staging)");
            long buffer = bufferPointer.get(0);
            long memory = 0L;
            try {
                VkMemoryRequirements memoryRequirements = VkMemoryRequirements.calloc(stack);
                vkGetBufferMemoryRequirements(device, buffer, memoryRequirements);
                VkMemoryAllocateInfo allocateInfo = VkMemoryAllocateInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                        .allocationSize(memoryRequirements.size())
                        .memoryTypeIndex(findMemoryType(
                                physicalDevice,
                                memoryRequirements.memoryTypeBits(),
                                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT));
                LongBuffer memoryPointer = stack.mallocLong(1);
                VulkanDeviceBootstrapper.checkVkResult(
                        vkAllocateMemory(device, allocateInfo, null, memoryPointer),
                        "vkAllocateMemory(texture-staging)");
                memory = memoryPointer.get(0);

                VulkanDeviceBootstrapper.checkVkResult(
                        vkBindBufferMemory(device, buffer, memory, 0L),
                        "vkBindBufferMemory(texture-staging)");
                PointerBuffer mappedPointer = stack.mallocPointer(1);
                VulkanDeviceBootstrapper.checkVkResult(
                        vkMapMemory(device, memory, 0L, uploadSize, 0, mappedPointer),
                        "vkMapMemory(texture-staging)");
                try {
                    ByteBuffer mapped = MemoryUtil.memByteBuffer(mappedPointer.get(0), uploadSize);
                    mapped.put(uploadData.duplicate());
                } finally {
                    vkUnmapMemory(device, memory);
                }
                return new StagingBuffer(buffer, memory);
            } catch (RuntimeException e) {
                vkDestroyBuffer(device, buffer, null);
                if (memory != 0L) {
                    vkFreeMemory(device, memory, null);
                }
                throw e;
            }
        }
    }

    private static void copyBufferToImage(
            org.lwjgl.vulkan.VkCommandBuffer commandBuffer,
            long buffer,
            long image,
            int width,
            int height) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferImageCopy.Buffer copyRegion = VkBufferImageCopy.calloc(1, stack);
            copyRegion.get(0)
                    .bufferOffset(0L)
                    .bufferRowLength(0)
                    .bufferImageHeight(0);
            copyRegion.get(0).imageSubresource()
                    .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .mipLevel(0)
                    .baseArrayLayer(0)
                    .layerCount(1);
            copyRegion.get(0).imageOffset()
                    .x(0)
                    .y(0)
                    .z(0);
            copyRegion.get(0).imageExtent()
                    .width(width)
                    .height(height)
                    .depth(1);
            vkCmdCopyBufferToImage(
                    commandBuffer,
                    buffer,
                    image,
                    VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                    copyRegion);
        }
    }

    private static void transitionImageLayout(
            org.lwjgl.vulkan.VkCommandBuffer commandBuffer,
            long image,
            int oldLayout,
            int newLayout,
            int mipLevels) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.calloc(1, stack);
            barrier.get(0)
                    .sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                    .oldLayout(oldLayout)
                    .newLayout(newLayout)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .image(image)
                    .srcAccessMask(accessMaskForLayout(oldLayout))
                    .dstAccessMask(accessMaskForLayout(newLayout));
            barrier.get(0).subresourceRange()
                    .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .baseMipLevel(0)
                    .levelCount(Math.max(1, mipLevels))
                    .baseArrayLayer(0)
                    .layerCount(1);
            vkCmdPipelineBarrier(
                    commandBuffer,
                    stageMaskForLayout(oldLayout),
                    stageMaskForLayout(newLayout),
                    0,
                    null,
                    null,
                    barrier);
        }
    }

    private static int accessMaskForLayout(int layout) {
        if (layout == VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL) {
            return VK_ACCESS_TRANSFER_WRITE_BIT;
        }
        if (layout == VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL) {
            return VK_ACCESS_SHADER_READ_BIT;
        }
        return 0;
    }

    private static int stageMaskForLayout(int layout) {
        if (layout == VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL) {
            return VK_PIPELINE_STAGE_TRANSFER_BIT;
        }
        if (layout == VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL) {
            return VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
        }
        return VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
    }

    private static ByteBuffer uploadSlice(ByteBuffer imageData, int expectedBytes) {
        ByteBuffer source = imageData.duplicate();
        if (source.remaining() < expectedBytes && source.capacity() >= expectedBytes) {
            source.clear();
        }
        if (source.remaining() < expectedBytes) {
            throw new IllegalArgumentException(
                    "RGBA8 texture upload expected " + expectedBytes + " bytes but only "
                            + source.remaining() + " bytes are available");
        }
        source.limit(source.position() + expectedBytes);
        return source.slice();
    }

    private static int uploadSizeBytes(int width, int height, ImageFormat format) {
        if (format != ImageFormat.RGBA8_UNORM) {
            throw new IllegalArgumentException("Vulkan texture upload v1 only supports RGBA8_UNORM, got " + format);
        }
        long bytes = (long) Math.max(1, width) * Math.max(1, height) * 4L;
        if (bytes > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Texture upload is too large: " + bytes + " bytes");
        }
        return (int) bytes;
    }

    private static int vulkanFormat(ImageFormat format) {
        if (format == ImageFormat.RGBA8_UNORM) {
            return VK_FORMAT_R8G8B8A8_UNORM;
        }
        throw new IllegalArgumentException("Unsupported Vulkan texture upload format: " + format);
    }

    private static int filterMode(SamplerFilter filter) {
        return switch (filter) {
            case LINEAR, LINEAR_MIPMAP_NEAREST, LINEAR_MIPMAP_LINEAR -> VK_FILTER_LINEAR;
            default -> VK_FILTER_NEAREST;
        };
    }

    private static int addressMode(SamplerWrap wrap) {
        return switch (wrap) {
            case REPEAT -> VK_SAMPLER_ADDRESS_MODE_REPEAT;
            case MIRRORED_REPEAT -> VK_SAMPLER_ADDRESS_MODE_MIRRORED_REPEAT;
            default -> VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
        };
    }

    private static int findMemoryType(VkPhysicalDevice physicalDevice, int typeFilter, int properties) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPhysicalDeviceMemoryProperties memoryProperties = VkPhysicalDeviceMemoryProperties.calloc(stack);
            vkGetPhysicalDeviceMemoryProperties(physicalDevice, memoryProperties);
            for (int i = 0; i < memoryProperties.memoryTypeCount(); i++) {
                boolean typeSupported = (typeFilter & (1 << i)) != 0;
                boolean propertiesMatch = (memoryProperties.memoryTypes(i).propertyFlags() & properties) == properties;
                if (typeSupported && propertiesMatch) {
                    return i;
                }
            }
        }
        throw new IllegalStateException("Failed to find compatible Vulkan memory type for placeholder texture");
    }

    private record StagingBuffer(long buffer, long memory) {
        private void destroy(VkDevice device) {
            vkDestroyBuffer(device, buffer, null);
            vkFreeMemory(device, memory, null);
        }
    }
}

