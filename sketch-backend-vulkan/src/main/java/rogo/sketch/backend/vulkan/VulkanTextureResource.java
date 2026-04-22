package rogo.sketch.backend.vulkan;

import org.lwjgl.vulkan.VkDevice;
import rogo.sketch.core.api.ResourceObject;
import rogo.sketch.core.backend.BackendInstalledTexture;
import rogo.sketch.core.memory.ImageMemoryEstimator;
import rogo.sketch.core.memory.MemoryDomain;
import rogo.sketch.core.memory.MemoryLease;
import rogo.sketch.core.memory.UnifiedMemoryFabric;
import rogo.sketch.core.resource.descriptor.ImageFormat;

import static org.lwjgl.vulkan.VK10.VK_FORMAT_R8G8B8A8_UNORM;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_COLOR_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_UNDEFINED;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_SAMPLED_BIT;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.vkDestroyImage;
import static org.lwjgl.vulkan.VK10.vkDestroyImageView;
import static org.lwjgl.vulkan.VK10.vkDestroySampler;
import static org.lwjgl.vulkan.VK10.vkFreeMemory;

public final class VulkanTextureResource implements ResourceObject, BackendInstalledTexture {
    private final VkDevice device;
    private final long image;
    private final long memory;
    private final long imageView;
    private final long sampler;
    private final int width;
    private final int height;
    private final int mipLevels;
    private final int descriptorImageLayout;
    private final int format;
    private final int aspectMask;
    private final int usageFlags;
    private final boolean ownsHandles;
    private final MemoryLease textureLease;
    private int currentImageLayout;
    private boolean disposed;

    public VulkanTextureResource(
            VkDevice device,
            long image,
            long memory,
            long imageView,
            long sampler,
            int width,
            int height,
            int mipLevels,
            int descriptorImageLayout,
            int currentImageLayout,
            int format,
            int aspectMask,
            int usageFlags,
            boolean ownsHandles) {
        this.device = device;
        this.image = image;
        this.memory = memory;
        this.imageView = imageView;
        this.sampler = sampler;
        this.width = width;
        this.height = height;
        this.mipLevels = Math.max(1, mipLevels);
        this.descriptorImageLayout = descriptorImageLayout != 0
                ? descriptorImageLayout
                : VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
        this.currentImageLayout = currentImageLayout != 0
                ? currentImageLayout
                : VK_IMAGE_LAYOUT_UNDEFINED;
        this.format = format != 0 ? format : VK_FORMAT_R8G8B8A8_UNORM;
        this.aspectMask = aspectMask != 0 ? aspectMask : VK_IMAGE_ASPECT_COLOR_BIT;
        this.usageFlags = usageFlags != 0 ? usageFlags : VK_IMAGE_USAGE_SAMPLED_BIT;
        this.ownsHandles = ownsHandles;
        this.textureLease = ownsHandles
                ? UnifiedMemoryFabric.get()
                .openLease(MemoryDomain.GPU_TEXTURE, "vk-texture/" + Long.toUnsignedString(image))
                .bindSuppliers(this::trackedReservedBytes, this::trackedLiveBytes)
                : null;
    }

    public static VulkanTextureResource borrowed(
            VkDevice device,
            long image,
            long imageView,
            long sampler,
            int width,
            int height) {
        return new VulkanTextureResource(
                device,
                image,
                VK_NULL_HANDLE,
                imageView,
                sampler,
                width,
                height,
                1,
                VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                VK_FORMAT_R8G8B8A8_UNORM,
                VK_IMAGE_ASPECT_COLOR_BIT,
                VK_IMAGE_USAGE_SAMPLED_BIT,
                false);
    }

    public long image() {
        return image;
    }

    public long imageView() {
        return imageView;
    }

    public long sampler() {
        return sampler;
    }

    public long memory() {
        return memory;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int mipLevels() {
        return mipLevels;
    }

    public int imageLayout() {
        return descriptorImageLayout;
    }

    public int currentImageLayout() {
        return currentImageLayout;
    }

    public void setCurrentImageLayout(int currentImageLayout) {
        this.currentImageLayout = currentImageLayout != 0
                ? currentImageLayout
                : VK_IMAGE_LAYOUT_UNDEFINED;
    }

    public int format() {
        return format;
    }

    public int aspectMask() {
        return aspectMask;
    }

    public int usageFlags() {
        return usageFlags;
    }

    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        if (textureLease != null) {
            textureLease.close();
        }
        if (!ownsHandles || device == null) {
            return;
        }
        if (sampler != VK_NULL_HANDLE) {
            vkDestroySampler(device, sampler, null);
        }
        if (imageView != VK_NULL_HANDLE) {
            vkDestroyImageView(device, imageView, null);
        }
        if (image != VK_NULL_HANDLE) {
            vkDestroyImage(device, image, null);
        }
        if (memory != VK_NULL_HANDLE) {
            vkFreeMemory(device, memory, null);
        }
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }

    private long trackedReservedBytes() {
        return disposed ? 0L : trackedLiveBytes();
    }

    private long trackedLiveBytes() {
        if (disposed || !ownsHandles) {
            return 0L;
        }
        return ImageMemoryEstimator.estimateBytes(width, height, mipLevels, toImageFormat(format));
    }

    private static ImageFormat toImageFormat(int vulkanFormat) {
        return switch (vulkanFormat) {
            case org.lwjgl.vulkan.VK10.VK_FORMAT_R16_SFLOAT -> ImageFormat.R16_FLOAT;
            case org.lwjgl.vulkan.VK10.VK_FORMAT_R32_SFLOAT -> ImageFormat.R32_FLOAT;
            case org.lwjgl.vulkan.VK10.VK_FORMAT_R16G16_SFLOAT -> ImageFormat.RG16_FLOAT;
            case org.lwjgl.vulkan.VK10.VK_FORMAT_R16G16B16A16_SFLOAT -> ImageFormat.RGBA16_FLOAT;
            case org.lwjgl.vulkan.VK10.VK_FORMAT_R32G32_SFLOAT -> ImageFormat.RG32_FLOAT;
            case org.lwjgl.vulkan.VK10.VK_FORMAT_R32G32B32A32_SFLOAT -> ImageFormat.RGBA32_FLOAT;
            case org.lwjgl.vulkan.VK10.VK_FORMAT_D16_UNORM -> ImageFormat.D16_UNORM;
            case org.lwjgl.vulkan.VK10.VK_FORMAT_D32_SFLOAT -> ImageFormat.D32_FLOAT;
            case org.lwjgl.vulkan.VK10.VK_FORMAT_D24_UNORM_S8_UINT -> ImageFormat.D24_UNORM_S8_UINT;
            default -> ImageFormat.RGBA8_UNORM;
        };
    }
}

