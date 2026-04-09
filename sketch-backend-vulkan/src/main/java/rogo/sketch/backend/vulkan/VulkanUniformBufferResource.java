package rogo.sketch.backend.vulkan;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkBufferCreateInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryRequirements;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties;
import rogo.sketch.core.api.ResourceObject;
import rogo.sketch.core.backend.BackendInstalledBuffer;
import rogo.sketch.core.backend.BackendUniformBuffer;
import rogo.sketch.core.resource.descriptor.BufferRole;
import rogo.sketch.core.resource.descriptor.BufferUpdatePolicy;
import rogo.sketch.core.resource.descriptor.ResolvedBufferResource;
import rogo.sketch.core.util.KeyId;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;
import static org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.VK_SHARING_MODE_EXCLUSIVE;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
import static org.lwjgl.vulkan.VK10.vkAllocateMemory;
import static org.lwjgl.vulkan.VK10.vkBindBufferMemory;
import static org.lwjgl.vulkan.VK10.vkCreateBuffer;
import static org.lwjgl.vulkan.VK10.vkDestroyBuffer;
import static org.lwjgl.vulkan.VK10.vkFreeMemory;
import static org.lwjgl.vulkan.VK10.vkGetBufferMemoryRequirements;
import static org.lwjgl.vulkan.VK10.vkGetPhysicalDeviceMemoryProperties;
import static org.lwjgl.vulkan.VK10.vkMapMemory;
import static org.lwjgl.vulkan.VK10.vkUnmapMemory;

public final class VulkanUniformBufferResource implements ResourceObject, BackendInstalledBuffer, BackendUniformBuffer {
    private final VkDevice device;
    private final long size;
    private final long buffer;
    private final long memory;
    private final long mappedAddress;
    private final ResolvedBufferResource descriptor;
    private boolean disposed;

    public VulkanUniformBufferResource(VkPhysicalDevice physicalDevice, VkDevice device, long size) {
        this(KeyId.of("vk_uniform_" + System.identityHashCode(device) + "_" + size), physicalDevice, device, size);
    }

    public VulkanUniformBufferResource(KeyId resourceId, VkPhysicalDevice physicalDevice, VkDevice device, long size) {
        if (physicalDevice == null) {
            throw new IllegalArgumentException("physicalDevice must not be null");
        }
        if (device == null) {
            throw new IllegalArgumentException("device must not be null");
        }
        if (size <= 0L) {
            throw new IllegalArgumentException("size must be > 0");
        }
        this.device = device;
        this.size = size;
        this.descriptor = new ResolvedBufferResource(
                resourceId != null ? resourceId : KeyId.of("vk_uniform_" + size),
                BufferRole.UNIFORM,
                BufferUpdatePolicy.DYNAMIC,
                Math.max(1L, size),
                1L,
                size);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferCreateInfo bufferInfo = VkBufferCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
                    .size(size)
                    .usage(VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE);
            LongBuffer bufferPointer = stack.mallocLong(1);
            VulkanDeviceBootstrapper.checkVkResult(
                    vkCreateBuffer(device, bufferInfo, null, bufferPointer),
                    "vkCreateBuffer(uniform-buffer)");
            this.buffer = bufferPointer.get(0);

            VkMemoryRequirements memoryRequirements = VkMemoryRequirements.calloc(stack);
            vkGetBufferMemoryRequirements(device, buffer, memoryRequirements);

            VkMemoryAllocateInfo allocInfo = VkMemoryAllocateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                    .allocationSize(memoryRequirements.size())
                    .memoryTypeIndex(findMemoryType(
                            physicalDevice,
                            memoryRequirements.memoryTypeBits(),
                            VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT));
            LongBuffer memoryPointer = stack.mallocLong(1);
            VulkanDeviceBootstrapper.checkVkResult(
                    vkAllocateMemory(device, allocInfo, null, memoryPointer),
                    "vkAllocateMemory(uniform-buffer)");
            this.memory = memoryPointer.get(0);

            VulkanDeviceBootstrapper.checkVkResult(
                    vkBindBufferMemory(device, buffer, memory, 0L),
                    "vkBindBufferMemory(uniform-buffer)");

            org.lwjgl.PointerBuffer mappedPointer = stack.mallocPointer(1);
            VulkanDeviceBootstrapper.checkVkResult(
                    vkMapMemory(device, memory, 0L, size, 0, mappedPointer),
                    "vkMapMemory(uniform-buffer)");
            this.mappedAddress = mappedPointer.get(0);
        }
    }

    public long buffer() {
        return buffer;
    }

    public long size() {
        return size;
    }

    @Override
    public ResolvedBufferResource descriptor() {
        return descriptor;
    }

    @Override
    public long sizeBytes() {
        return size;
    }

    public void update(byte[] bytes) {
        if (bytes == null) {
            update((ByteBuffer) null);
            return;
        }
        update(ByteBuffer.wrap(bytes));
    }

    public void update(ByteBuffer source) {
        if (disposed) {
            throw new IllegalStateException("Uniform buffer has been disposed");
        }
        ByteBuffer target = MemoryUtil.memByteBuffer(mappedAddress, Math.toIntExact(size));
        target.clear();
        if (source != null) {
            ByteBuffer copy = source.slice();
            if (copy.remaining() > target.remaining()) {
                throw new IllegalArgumentException("Uniform buffer update exceeds capacity " + size);
            }
            target.put(copy);
        }
        while (target.hasRemaining()) {
            target.put((byte) 0);
        }
    }

    @Override
    public void bind(KeyId resourceType, int binding) {
        // Vulkan descriptor writes happen through VulkanDescriptorArena; direct bind is not used.
    }

    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        if (mappedAddress != 0L) {
            vkUnmapMemory(device, memory);
        }
        if (buffer != VK_NULL_HANDLE) {
            vkDestroyBuffer(device, buffer, null);
        }
        if (memory != VK_NULL_HANDLE) {
            vkFreeMemory(device, memory, null);
        }
    }

    @Override
    public boolean isDisposed() {
        return disposed;
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
        throw new IllegalStateException("Failed to find compatible Vulkan memory type for uniform buffer");
    }
}

