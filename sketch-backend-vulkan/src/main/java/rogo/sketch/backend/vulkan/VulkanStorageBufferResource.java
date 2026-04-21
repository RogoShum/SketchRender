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
import rogo.sketch.core.backend.BackendStorageBuffer;
import rogo.sketch.core.memory.MemoryDomain;
import rogo.sketch.core.memory.MemoryLease;
import rogo.sketch.core.memory.TrackedTransientAllocation;
import rogo.sketch.core.memory.UnifiedMemoryFabric;
import rogo.sketch.core.resource.descriptor.BufferRole;
import rogo.sketch.core.resource.descriptor.BufferUpdatePolicy;
import rogo.sketch.core.resource.descriptor.ResolvedBufferResource;
import rogo.sketch.core.util.KeyId;

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

final class VulkanStorageBufferResource implements BackendStorageBuffer, VulkanDescriptorBufferResource {
    private final VkPhysicalDevice physicalDevice;
    private final VkDevice device;
    private final KeyId resourceId;
    private BufferUpdatePolicy updatePolicy;
    private long strideBytes;
    private long elementCount;
    private long capacityBytes;
    private long buffer;
    private long memory;
    private long mappedAddress;
    private long position;
    private final MemoryLease mappedLease;
    private boolean disposed;

    VulkanStorageBufferResource(
            KeyId resourceId,
            VkPhysicalDevice physicalDevice,
            VkDevice device,
            ResolvedBufferResource descriptor,
            java.nio.ByteBuffer initialData) {
        this.physicalDevice = physicalDevice;
        this.device = device;
        this.resourceId = resourceId != null ? resourceId : descriptor.identifier();
        this.updatePolicy = descriptor.updatePolicy();
        this.strideBytes = Math.max(1L, descriptor.strideBytes());
        this.elementCount = Math.max(1L, descriptor.elementCount());
        this.capacityBytes = Math.max(strideBytes, descriptor.capacityBytes());
        this.mappedLease = UnifiedMemoryFabric.get()
                .openLease(MemoryDomain.GPU_PERSISTENT_MAPPED, "vk-storage-buffer/" + this.resourceId)
                .bindSuppliers(this::trackedReservedBytes, this::trackedLiveBytes);
        allocate(capacityBytes);
        if (initialData != null) {
            try (TrackedTransientAllocation copy = TrackedTransientAllocation.allocate(
                    "vk-storage-buffer-init/" + this.resourceId,
                    initialData.remaining())) {
                copy.buffer().put(initialData.slice()).flip();
                upload(copy.address(), copy.buffer().remaining());
            }
        }
    }

    @Override
    public ResolvedBufferResource descriptor() {
        return new ResolvedBufferResource(
                resourceId,
                BufferRole.STORAGE,
                updatePolicy,
                elementCount,
                strideBytes,
                capacityBytes);
    }

    @Override
    public long dataCount() {
        return elementCount;
    }

    @Override
    public long capacityBytes() {
        return capacityBytes;
    }

    @Override
    public long strideBytes() {
        return strideBytes;
    }

    @Override
    public long memoryAddress() {
        return mappedAddress;
    }

    @Override
    public long position() {
        return position;
    }

    @Override
    public void position(long newPosition) {
        this.position = Math.max(0L, Math.min(newPosition, capacityBytes));
    }

    @Override
    public void upload() {
        // Host-visible coherent memory is written directly through mappedAddress.
    }

    @Override
    public void upload(long sourceAddress, long byteCount) {
        checkDisposed();
        if (sourceAddress == MemoryUtil.NULL || byteCount <= 0L) {
            return;
        }
        if (byteCount > capacityBytes) {
            throw new IllegalArgumentException("Storage buffer upload exceeds capacity " + capacityBytes);
        }
        MemoryUtil.memCopy(sourceAddress, mappedAddress, byteCount);
        position = Math.max(position, byteCount);
    }

    @Override
    public void upload(long elementIndex) {
        upload(elementIndex, Math.toIntExact(strideBytes));
    }

    @Override
    public void upload(long elementIndex, int byteCount) {
        checkDisposed();
        long byteOffset = elementIndex * strideBytes;
        if (byteOffset < 0L || byteOffset + byteCount > capacityBytes) {
            throw new IllegalArgumentException("Storage buffer element upload is out of bounds");
        }
        position = Math.max(position, byteOffset + byteCount);
    }

    @Override
    public void resetUpload(BufferUpdatePolicy updatePolicy) {
        this.updatePolicy = updatePolicy != null ? updatePolicy : BufferUpdatePolicy.DYNAMIC;
    }

    @Override
    public void ensureCapacity(int requiredCount, boolean copy) {
        ensureCapacity(requiredCount, copy, false);
    }

    @Override
    public void ensureCapacity(int requiredCount, boolean copy, boolean force) {
        long requiredBytes = Math.max(strideBytes, (long) requiredCount * strideBytes);
        if (!force && requiredBytes <= capacityBytes) {
            return;
        }
        resizeInternal(requiredBytes, copy);
        elementCount = Math.max(1L, requiredCount);
    }

    @Override
    public void setBufferPointer(long bufferPointer) {
        throw new UnsupportedOperationException("Vulkan storage buffers own their mapped memory");
    }

    @Override
    public void setCapacity(long capacityBytes) {
        resizeInternal(Math.max(strideBytes, capacityBytes), true);
        elementCount = Math.max(1L, this.capacityBytes / Math.max(1L, strideBytes));
    }

    @Override
    public long resize(long newCapacity) {
        resizeInternal(Math.max(strideBytes, newCapacity), true);
        elementCount = Math.max(1L, capacityBytes / Math.max(1L, strideBytes));
        return mappedAddress;
    }

    @Override
    public void bind(KeyId resourceType, int binding) {
        // Vulkan descriptor writes happen through VulkanDescriptorArena.
    }

    long buffer() {
        return buffer;
    }

    @Override
    public long descriptorBuffer() {
        return buffer;
    }

    @Override
    public long descriptorRange() {
        return capacityBytes;
    }

    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        mappedLease.close();
        destroyBuffer();
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }

    private void resizeInternal(long newCapacityBytes, boolean copy) {
        checkDisposed();
        if (newCapacityBytes <= 0L) {
            throw new IllegalArgumentException("newCapacityBytes must be > 0");
        }
        long previousAddress = mappedAddress;
        long previousCapacity = capacityBytes;
        byte[] snapshot = null;
        if (copy && previousAddress != MemoryUtil.NULL && previousCapacity > 0L) {
            snapshot = new byte[Math.toIntExact(Math.min(previousCapacity, newCapacityBytes))];
            java.nio.ByteBuffer previousBytes = MemoryUtil.memByteBuffer(previousAddress, snapshot.length);
            previousBytes.get(snapshot);
        }
        destroyBuffer();
        allocate(newCapacityBytes);
        if (snapshot != null && snapshot.length > 0) {
            try (TrackedTransientAllocation copyBuffer = TrackedTransientAllocation.allocate(
                    "vk-storage-buffer-resize/" + resourceId,
                    snapshot.length)) {
                copyBuffer.buffer().put(snapshot).flip();
                MemoryUtil.memCopy(copyBuffer.address(), mappedAddress, snapshot.length);
            }
        }
        capacityBytes = newCapacityBytes;
        position = Math.min(position, capacityBytes);
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
                    "vkCreateBuffer(storage-buffer)");
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
                    "vkAllocateMemory(storage-buffer)");
            memory = memoryPointer.get(0);

            VulkanDeviceBootstrapper.checkVkResult(
                    vkBindBufferMemory(device, buffer, memory, 0L),
                    "vkBindBufferMemory(storage-buffer)");

            PointerBuffer mappedPointer = stack.mallocPointer(1);
            VulkanDeviceBootstrapper.checkVkResult(
                    vkMapMemory(device, memory, 0L, requestedCapacityBytes, 0, mappedPointer),
                    "vkMapMemory(storage-buffer)");
            mappedAddress = mappedPointer.get(0);
        }
    }

    private void destroyBuffer() {
        if (mappedAddress != 0L && memory != VK_NULL_HANDLE) {
            vkUnmapMemory(device, memory);
            mappedAddress = 0L;
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
            throw new IllegalStateException("Storage buffer has been disposed");
        }
    }

    private long trackedReservedBytes() {
        return disposed ? 0L : capacityBytes;
    }

    private long trackedLiveBytes() {
        return disposed ? 0L : Math.min(capacityBytes, Math.max(position, 0L));
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
        throw new IllegalStateException("Failed to find compatible Vulkan memory type for storage buffer");
    }
}

