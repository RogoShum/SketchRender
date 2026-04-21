package rogo.sketch.backend.vulkan;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkBufferCreateInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryRequirements;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties;
import rogo.sketch.core.memory.TrackedTransientAllocation;
import rogo.sketch.core.resource.descriptor.BufferRole;
import rogo.sketch.core.resource.descriptor.BufferUpdatePolicy;
import rogo.sketch.core.resource.descriptor.ResolvedBufferResource;
import rogo.sketch.core.util.KeyId;
import rogo.sketch.module.culling.TerrainMeshCounterBuffer;

import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT;
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

final class VulkanCounterBufferResource
        implements TerrainMeshCounterBuffer, VulkanDescriptorBufferResource {
    private final KeyId resourceId;
    private final VkPhysicalDevice physicalDevice;
    private final VkDevice device;
    private BufferUpdatePolicy updatePolicy;
    private long strideBytes;
    private long counterCount;
    private long capacityBytes;
    private long buffer;
    private long memory;
    private long mappedAddress;
    private boolean disposed;

    VulkanCounterBufferResource(
            KeyId resourceId,
            VkPhysicalDevice physicalDevice,
            VkDevice device,
            ResolvedBufferResource descriptor) {
        this.resourceId = resourceId != null ? resourceId : KeyId.of("vk_counter_" + System.identityHashCode(this));
        this.physicalDevice = physicalDevice;
        this.device = device;
        this.updatePolicy = descriptor != null ? descriptor.updatePolicy() : BufferUpdatePolicy.DYNAMIC;
        this.strideBytes = Math.max(Integer.BYTES, descriptor != null ? descriptor.strideBytes() : Integer.BYTES);
        this.counterCount = Math.max(1L, descriptor != null ? descriptor.elementCount() : 1L);
        this.capacityBytes = Math.max(strideBytes, counterCount * strideBytes);
        allocate(capacityBytes);
    }

    ResolvedBufferResource descriptor() {
        return new ResolvedBufferResource(
                resourceId,
                BufferRole.ATOMIC_COUNTER,
                updatePolicy,
                counterCount,
                strideBytes,
                capacityBytes);
    }

    @Override
    public int handle() {
        return 0;
    }

    @Override
    public long counterCount() {
        return counterCount;
    }

    @Override
    public long strideBytes() {
        return strideBytes;
    }

    @Override
    public long getDataCount() {
        return counterCount;
    }

    @Override
    public long getCapacity() {
        return capacityBytes;
    }

    @Override
    public long getStride() {
        return strideBytes;
    }

    @Override
    public long getMemoryAddress() {
        return mappedAddress;
    }

    @Override
    public int getHandle() {
        return 0;
    }

    @Override
    public void resize(int count) {
        long normalizedCount = Math.max(1L, count);
        long requiredBytes = Math.max(strideBytes, normalizedCount * strideBytes);
        if (requiredBytes > capacityBytes) {
            resizeInternal(requiredBytes, true);
        }
        counterCount = normalizedCount;
    }

    @Override
    public void updateCount(int count) {
        checkDisposed();
        for (int i = 0; i < counterCount; i++) {
            MemoryUtil.memPutInt(mappedAddress + ((long) i * strideBytes), count);
        }
    }

    @Override
    public void bind() {
        // No direct bind target for Vulkan terrain mesh counters.
    }

    @Override
    public void bind(KeyId resourceType, int binding) {
        // Descriptor binding is managed by VulkanDescriptorArena.
    }

    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        destroyBuffer();
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }

    @Override
    public long descriptorBuffer() {
        return buffer;
    }

    @Override
    public long descriptorRange() {
        return capacityBytes;
    }

    private void resizeInternal(long newCapacityBytes, boolean copy) {
        checkDisposed();
        byte[] snapshot = null;
        if (copy && mappedAddress != MemoryUtil.NULL && capacityBytes > 0L) {
            int snapshotSize = Math.toIntExact(Math.min(capacityBytes, newCapacityBytes));
            snapshot = new byte[snapshotSize];
            MemoryUtil.memByteBuffer(mappedAddress, snapshotSize).get(snapshot);
        }
        destroyBuffer();
        allocate(Math.max(strideBytes, newCapacityBytes));
        if (snapshot != null && snapshot.length > 0) {
            try (TrackedTransientAllocation copyBuffer = TrackedTransientAllocation.allocate(
                    "vulkan-counter-buffer-resize",
                    snapshot.length)) {
                java.nio.ByteBuffer buffer = copyBuffer.buffer();
                buffer.put(snapshot).flip();
                MemoryUtil.memCopy(copyBuffer.address(), mappedAddress, snapshot.length);
            }
        }
    }

    private void allocate(long requestedCapacityBytes) {
        capacityBytes = requestedCapacityBytes;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferCreateInfo bufferInfo = VkBufferCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
                    .size(requestedCapacityBytes)
                    .usage(VK_BUFFER_USAGE_STORAGE_BUFFER_BIT)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE);
            java.nio.LongBuffer bufferPointer = stack.mallocLong(1);
            VulkanDeviceBootstrapper.checkVkResult(
                    vkCreateBuffer(device, bufferInfo, null, bufferPointer),
                    "vkCreateBuffer(counter-buffer)");
            buffer = bufferPointer.get(0);

            VkMemoryRequirements memoryRequirements = VkMemoryRequirements.calloc(stack);
            vkGetBufferMemoryRequirements(device, buffer, memoryRequirements);

            VkMemoryAllocateInfo allocateInfo = VkMemoryAllocateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                    .allocationSize(memoryRequirements.size())
                    .memoryTypeIndex(findMemoryType(
                            physicalDevice,
                            memoryRequirements.memoryTypeBits(),
                            VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT));
            java.nio.LongBuffer memoryPointer = stack.mallocLong(1);
            VulkanDeviceBootstrapper.checkVkResult(
                    vkAllocateMemory(device, allocateInfo, null, memoryPointer),
                    "vkAllocateMemory(counter-buffer)");
            memory = memoryPointer.get(0);

            VulkanDeviceBootstrapper.checkVkResult(
                    vkBindBufferMemory(device, buffer, memory, 0L),
                    "vkBindBufferMemory(counter-buffer)");

            PointerBuffer mappedPointer = stack.mallocPointer(1);
            VulkanDeviceBootstrapper.checkVkResult(
                    vkMapMemory(device, memory, 0L, requestedCapacityBytes, 0, mappedPointer),
                    "vkMapMemory(counter-buffer)");
            mappedAddress = mappedPointer.get(0);
        }
    }

    private void destroyBuffer() {
        if (mappedAddress != MemoryUtil.NULL && memory != VK_NULL_HANDLE) {
            vkUnmapMemory(device, memory);
            mappedAddress = MemoryUtil.NULL;
        }
        if (buffer != VK_NULL_HANDLE) {
            vkDestroyBuffer(device, buffer, null);
            buffer = VK_NULL_HANDLE;
        }
        if (memory != VK_NULL_HANDLE) {
            vkFreeMemory(device, memory, null);
            memory = VK_NULL_HANDLE;
        }
    }

    private void checkDisposed() {
        if (disposed) {
            throw new IllegalStateException("Counter buffer has been disposed: " + resourceId);
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
        throw new IllegalStateException("Failed to find compatible Vulkan memory type for counter buffer");
    }
}
