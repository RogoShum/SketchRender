package rogo.sketch.backend.vulkan;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkImageCreateInfo;
import org.lwjgl.vulkan.VkImageViewCreateInfo;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryRequirements;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties;
import org.lwjgl.vulkan.VkSamplerCreateInfo;

import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK10.VK_BORDER_COLOR_INT_OPAQUE_BLACK;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_D32_SFLOAT;
import static org.lwjgl.vulkan.VK10.VK_FILTER_NEAREST;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_R8G8B8A8_UNORM;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_COLOR_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_DEPTH_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_UNDEFINED;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_TILING_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_TYPE_2D;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_SAMPLED_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_TRANSFER_DST_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_TRANSFER_SRC_BIT;
import static org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT;
import static org.lwjgl.vulkan.VK10.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
import static org.lwjgl.vulkan.VK10.VK_SAMPLER_MIPMAP_MODE_NEAREST;
import static org.lwjgl.vulkan.VK10.VK_SAMPLE_COUNT_1_BIT;
import static org.lwjgl.vulkan.VK10.VK_SHARING_MODE_EXCLUSIVE;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_VIEW_TYPE_2D;
import static org.lwjgl.vulkan.VK10.vkAllocateMemory;
import static org.lwjgl.vulkan.VK10.vkBindImageMemory;
import static org.lwjgl.vulkan.VK10.vkCreateImage;
import static org.lwjgl.vulkan.VK10.vkCreateImageView;
import static org.lwjgl.vulkan.VK10.vkCreateSampler;
import static org.lwjgl.vulkan.VK10.vkGetImageMemoryRequirements;
import static org.lwjgl.vulkan.VK10.vkGetPhysicalDeviceMemoryProperties;

final class VulkanTextureFactory {
    private VulkanTextureFactory() {
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
                        .magFilter(VK_FILTER_NEAREST)
                        .minFilter(VK_FILTER_NEAREST)
                        .addressModeU(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                        .addressModeV(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                        .addressModeW(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
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
}

