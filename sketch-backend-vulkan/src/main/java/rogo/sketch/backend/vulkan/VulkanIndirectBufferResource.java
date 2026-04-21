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
import rogo.sketch.core.util.KeyId;
import rogo.sketch.module.culling.TerrainMeshIndirectBuffer;

import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT;
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

final class VulkanIndirectBufferResource
        implements TerrainMeshIndirectBuffer, VulkanDescriptorBufferResource {
    private final KeyId resourceId;
    private final VkPhysicalDevice physicalDevice;
    private final VkDevice device;
    private long buffer;
    private long memory;
    private long mappedAddress;
    private long capacityBytes;
    private long positionBytes;
    private int commandCount;
    private boolean disposed;

    VulkanIndirectBufferResource(KeyId resourceId, VkPhysicalDevice physicalDevice, VkDevice device, long commandCapacity) {
        this.resourceId = resourceId != null ? resourceId : KeyId.of("vk_indirect_" + System.identityHashCode(this));
        this.physicalDevice = physicalDevice;
        this.device = device;
        allocate(Math.max(1L, commandCapacity) * COMMAND_STRIDE_BYTES);
    }

    @Override
    public long strideBytes() {
        return COMMAND_STRIDE_BYTES;
    }

    @Override
    public int commandCount() {
        return commandCount;
    }

    @Override
    public long writePositionBytes() {
        return positionBytes;
    }

    @Override
    public long memoryAddress() {
        return mappedAddress;
    }

    @Override
    public void ensureCommandCapacity(int requiredCommandCount) {
        if (requiredCommandCount <= 0) {
            return;
        }
        long requiredBytes = (long) requiredCommandCount * COMMAND_STRIDE_BYTES;
        if (requiredBytes <= capacityBytes) {
            return;
        }
        resizeInternal(requiredBytes, true);
    }

    @Override
    public void uploadRange(long byteOffset, long byteCount) {
        // Host-visible coherent memory is written directly through mappedAddress.
    }

    @Override
    public void setCommandCount(int commandCount) {
        this.commandCount = Math.max(commandCount, 0);
    }

    @Override
    public void setWritePositionBytes(long byteCount) {
        this.positionBytes = Math.max(byteCount, 0L);
    }

    @Override
    public void clear() {
        positionBytes = 0L;
        commandCount = 0;
    }

    @Override
    public void bind() {
        // Vulkan indirect buffers are consumed via command recording, not direct bind calls.
    }

    @Override
    public void bind(KeyId resourceType, int binding) {
        // Descriptor binding is managed by VulkanDescriptorArena.
    }

    @Override
    public void unbind() {
        // No-op for Vulkan.
    }

    @Override
    public void upload() {
        // Host-visible coherent memory is written directly through mappedAddress.
    }

    @Override
    public void addDrawArraysCommand(int count, int instanceCount, int first, int baseInstance) {
        int index = ensureCommandCapacityForAppend();
        putArraysCommand(mappedAddress, index, count, instanceCount, first, baseInstance);
        positionBytes += COMMAND_STRIDE_BYTES;
        commandCount++;
    }

    @Override
    public void addDrawElementsCommand(int count, int instanceCount, int firstIndex, int baseVertex, int baseInstance) {
        int index = ensureCommandCapacityForAppend();
        putElementsCommand(mappedAddress, index, count, instanceCount, firstIndex, baseVertex, baseInstance);
        positionBytes += COMMAND_STRIDE_BYTES;
        commandCount++;
    }

    @Override
    public void resize(long commandCapacity) {
        ensureCommandCapacity(Math.toIntExact(Math.max(1L, commandCapacity)));
    }

    @Override
    public long getDataCount() {
        return Math.max(1L, capacityBytes / COMMAND_STRIDE_BYTES);
    }

    @Override
    public long getCapacity() {
        return capacityBytes;
    }

    @Override
    public long getStride() {
        return COMMAND_STRIDE_BYTES;
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

    private int ensureCommandCapacityForAppend() {
        if (positionBytes + COMMAND_STRIDE_BYTES <= capacityBytes) {
            return commandCount;
        }
        ensureCommandCapacity(commandCount + 1);
        return commandCount;
    }

    private void resizeInternal(long newCapacityBytes, boolean copy) {
        byte[] snapshot = null;
        if (copy && mappedAddress != MemoryUtil.NULL && positionBytes > 0L) {
            int snapshotSize = Math.toIntExact(Math.min(positionBytes, newCapacityBytes));
            snapshot = new byte[snapshotSize];
            MemoryUtil.memByteBuffer(mappedAddress, snapshotSize).get(snapshot);
        }
        destroyBuffer();
        allocate(newCapacityBytes);
        if (snapshot != null && snapshot.length > 0) {
            try (TrackedTransientAllocation copyBuffer = TrackedTransientAllocation.allocate(
                    "vulkan-indirect-buffer-resize",
                    snapshot.length)) {
                java.nio.ByteBuffer buffer = copyBuffer.buffer();
                buffer.put(snapshot).flip();
                MemoryUtil.memCopy(copyBuffer.address(), mappedAddress, snapshot.length);
            }
        }
        positionBytes = Math.min(positionBytes, capacityBytes);
    }

    private void allocate(long requestedCapacityBytes) {
        capacityBytes = Math.max(COMMAND_STRIDE_BYTES, requestedCapacityBytes);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferCreateInfo bufferInfo = VkBufferCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
                    .size(capacityBytes)
                    .usage(VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT | VK_BUFFER_USAGE_STORAGE_BUFFER_BIT)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE);
            java.nio.LongBuffer bufferPointer = stack.mallocLong(1);
            VulkanDeviceBootstrapper.checkVkResult(
                    vkCreateBuffer(device, bufferInfo, null, bufferPointer),
                    "vkCreateBuffer(indirect-buffer)");
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
                    "vkAllocateMemory(indirect-buffer)");
            memory = memoryPointer.get(0);

            VulkanDeviceBootstrapper.checkVkResult(
                    vkBindBufferMemory(device, buffer, memory, 0L),
                    "vkBindBufferMemory(indirect-buffer)");

            PointerBuffer mappedPointer = stack.mallocPointer(1);
            VulkanDeviceBootstrapper.checkVkResult(
                    vkMapMemory(device, memory, 0L, capacityBytes, 0, mappedPointer),
                    "vkMapMemory(indirect-buffer)");
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

    private static void putArraysCommand(long address, int index, int count, int instanceCount, int first, int baseInstance) {
        long offset = address + (long) index * COMMAND_STRIDE_BYTES;
        MemoryUtil.memPutInt(offset + 0, count);
        MemoryUtil.memPutInt(offset + 4, instanceCount);
        MemoryUtil.memPutInt(offset + 8, first);
        MemoryUtil.memPutInt(offset + 12, baseInstance);
    }

    private static void putElementsCommand(long address, int index, int count, int instanceCount, int firstIndex, int baseVertex, int baseInstance) {
        long offset = address + (long) index * COMMAND_STRIDE_BYTES;
        MemoryUtil.memPutInt(offset + 0, count);
        MemoryUtil.memPutInt(offset + 4, instanceCount);
        MemoryUtil.memPutInt(offset + 8, firstIndex);
        MemoryUtil.memPutInt(offset + 12, baseVertex);
        MemoryUtil.memPutInt(offset + 16, baseInstance);
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
        throw new IllegalStateException("Failed to find compatible Vulkan memory type for indirect buffer");
    }
}
