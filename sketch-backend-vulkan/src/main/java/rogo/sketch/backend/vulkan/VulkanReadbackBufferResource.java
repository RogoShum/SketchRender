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
import rogo.sketch.core.backend.BackendReadbackBuffer;
import rogo.sketch.core.memory.TrackedTransientAllocation;
import rogo.sketch.core.util.KeyId;
import rogo.sketch.module.culling.TerrainMeshReadbackBuffer;

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

final class VulkanReadbackBufferResource
        implements BackendReadbackBuffer, TerrainMeshReadbackBuffer, VulkanDescriptorBufferResource {
    private final KeyId resourceId;
    private final VkPhysicalDevice physicalDevice;
    private final VkDevice device;
    private final long strideBytes;
    private long dataCount;
    private long capacityBytes;
    private long buffer;
    private long memory;
    private long mappedAddress;
    private boolean disposed;

    VulkanReadbackBufferResource(
            KeyId resourceId,
            VkPhysicalDevice physicalDevice,
            VkDevice device,
            int initialElementCapacity,
            long strideBytes) {
        this.resourceId = resourceId != null ? resourceId : KeyId.of("vk_readback_" + System.identityHashCode(this));
        this.physicalDevice = physicalDevice;
        this.device = device;
        this.strideBytes = Math.max(1L, strideBytes);
        this.dataCount = Math.max(1L, initialElementCapacity);
        this.capacityBytes = Math.max(this.strideBytes, this.dataCount * this.strideBytes);
        allocate(capacityBytes);
    }

    @Override
    public void ensureCapacity(int requiredCount, boolean force) {
        long requiredBytes = Math.max(strideBytes, (long) Math.max(1, requiredCount) * strideBytes);
        if (!force && requiredBytes <= capacityBytes) {
            dataCount = Math.max(dataCount, Math.max(1, requiredCount));
            return;
        }
        resize(requiredBytes);
        dataCount = Math.max(1L, requiredCount);
    }

    @Override
    public int getInt(long index) {
        checkDisposed();
        if (mappedAddress == MemoryUtil.NULL) {
            throw new IllegalStateException("Readback buffer is not mapped: " + resourceId);
        }
        return MemoryUtil.memGetInt(mappedAddress + (index * Integer.BYTES));
    }

    @Override
    public int getUnsignedByte(long index) {
        checkDisposed();
        if (mappedAddress == MemoryUtil.NULL) {
            throw new IllegalStateException("Readback buffer is not mapped: " + resourceId);
        }
        return Byte.toUnsignedInt(MemoryUtil.memGetByte(mappedAddress + index));
    }

    @Override
    public byte getByte(long index) {
        checkDisposed();
        if (mappedAddress == MemoryUtil.NULL) {
            throw new IllegalStateException("Readback buffer is not mapped: " + resourceId);
        }
        return MemoryUtil.memGetByte(mappedAddress + index);
    }

    @Override
    public long getDataCount() {
        return dataCount;
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
    public void bind(KeyId resourceType, int binding) {
        // Descriptor binding is managed by Vulkan descriptor writes, not direct bind calls.
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

    private void resize(long newCapacityBytes) {
        byte[] snapshot = null;
        if (mappedAddress != MemoryUtil.NULL && capacityBytes > 0L) {
            int snapshotSize = Math.toIntExact(Math.min(capacityBytes, newCapacityBytes));
            snapshot = new byte[snapshotSize];
            MemoryUtil.memByteBuffer(mappedAddress, snapshotSize).get(snapshot);
        }
        destroyBuffer();
        capacityBytes = Math.max(strideBytes, newCapacityBytes);
        allocate(capacityBytes);
        if (snapshot != null && snapshot.length > 0) {
            try (TrackedTransientAllocation copy = TrackedTransientAllocation.allocate(
                    "vulkan-readback-buffer-resize",
                    snapshot.length)) {
                java.nio.ByteBuffer buffer = copy.buffer();
                buffer.put(snapshot).flip();
                MemoryUtil.memCopy(copy.address(), mappedAddress, snapshot.length);
            }
        }
    }

    private void allocate(long requestedCapacityBytes) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferCreateInfo bufferInfo = VkBufferCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
                    .size(requestedCapacityBytes)
                    .usage(VK_BUFFER_USAGE_STORAGE_BUFFER_BIT)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE);
            java.nio.LongBuffer bufferPointer = stack.mallocLong(1);
            VulkanDeviceBootstrapper.checkVkResult(
                    vkCreateBuffer(device, bufferInfo, null, bufferPointer),
                    "vkCreateBuffer(readback-buffer)");
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
                    "vkAllocateMemory(readback-buffer)");
            memory = memoryPointer.get(0);

            VulkanDeviceBootstrapper.checkVkResult(
                    vkBindBufferMemory(device, buffer, memory, 0L),
                    "vkBindBufferMemory(readback-buffer)");

            PointerBuffer mappedPointer = stack.mallocPointer(1);
            VulkanDeviceBootstrapper.checkVkResult(
                    vkMapMemory(device, memory, 0L, requestedCapacityBytes, 0, mappedPointer),
                    "vkMapMemory(readback-buffer)");
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
            throw new IllegalStateException("Readback buffer has been disposed: " + resourceId);
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
        throw new IllegalStateException("Failed to find compatible Vulkan memory type for readback buffer");
    }
}
