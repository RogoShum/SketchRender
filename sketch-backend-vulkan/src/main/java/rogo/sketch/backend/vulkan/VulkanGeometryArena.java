package rogo.sketch.backend.vulkan;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkBufferCreateInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryRequirements;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties;
import rogo.sketch.core.packet.GeometryHandleKey;

import java.nio.LongBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT;
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

final class VulkanGeometryArena {
    private static final int INTERLEAVED_COLOR_STRIDE = 5 * Float.BYTES;

    private final VkPhysicalDevice physicalDevice;
    private final VkDevice device;
    private final Map<GeometryHandleKey, GeometrySlice> slices = new ConcurrentHashMap<>();

    VulkanGeometryArena(VkPhysicalDevice physicalDevice, VkDevice device) {
        this.physicalDevice = physicalDevice;
        this.device = device;
    }

    GeometrySlice registerInterleavedColorGeometry(GeometryHandleKey key, float[] vertexData, int vertexCount) {
        if (key == null || vertexData == null || vertexData.length == 0 || vertexCount <= 0) {
            return null;
        }

        GeometrySlice previous = slices.remove(key);
        if (previous != null) {
            destroySlice(previous);
        }

        GeometrySlice slice = createHostVisibleVertexSlice(vertexData, vertexCount);
        slices.put(key, slice);
        return slice;
    }

    GeometrySlice resolve(GeometryHandleKey key) {
        return key == null ? null : slices.get(key);
    }

    void destroy() {
        for (GeometrySlice slice : slices.values()) {
            destroySlice(slice);
        }
        slices.clear();
    }

    private GeometrySlice createHostVisibleVertexSlice(float[] vertexData, int vertexCount) {
        long bufferSize = (long) vertexData.length * Float.BYTES;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferCreateInfo bufferInfo = VkBufferCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
                    .size(bufferSize)
                    .usage(VK_BUFFER_USAGE_VERTEX_BUFFER_BIT)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE);
            LongBuffer bufferPointer = stack.mallocLong(1);
            VulkanDeviceBootstrapper.checkVkResult(
                    vkCreateBuffer(device, bufferInfo, null, bufferPointer),
                    "vkCreateBuffer(vertex)");

            VkMemoryRequirements memoryRequirements = VkMemoryRequirements.calloc(stack);
            vkGetBufferMemoryRequirements(device, bufferPointer.get(0), memoryRequirements);

            VkMemoryAllocateInfo allocInfo = VkMemoryAllocateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                    .allocationSize(memoryRequirements.size())
                    .memoryTypeIndex(findMemoryType(
                            memoryRequirements.memoryTypeBits(),
                            VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT));
            LongBuffer memoryPointer = stack.mallocLong(1);
            VulkanDeviceBootstrapper.checkVkResult(
                    vkAllocateMemory(device, allocInfo, null, memoryPointer),
                    "vkAllocateMemory(vertex)");

            VulkanDeviceBootstrapper.checkVkResult(
                    vkBindBufferMemory(device, bufferPointer.get(0), memoryPointer.get(0), 0L),
                    "vkBindBufferMemory(vertex)");

            org.lwjgl.PointerBuffer mappedPointer = stack.mallocPointer(1);
            VulkanDeviceBootstrapper.checkVkResult(
                    vkMapMemory(device, memoryPointer.get(0), 0L, bufferSize, 0, mappedPointer),
                    "vkMapMemory(vertex)");
            java.nio.ByteBuffer target = MemoryUtil.memByteBuffer(mappedPointer.get(0), (int) bufferSize);
            target.asFloatBuffer().put(vertexData);
            vkUnmapMemory(device, memoryPointer.get(0));

            return new GeometrySlice(
                    bufferPointer.get(0),
                    memoryPointer.get(0),
                    vertexCount,
                    INTERLEAVED_COLOR_STRIDE,
                    VK_NULL_HANDLE,
                    VK_NULL_HANDLE,
                    0,
                    VK_NULL_HANDLE);
        }
    }

    private int findMemoryType(int typeFilter, int properties) {
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
        throw new IllegalStateException("Failed to find compatible Vulkan memory type");
    }

    private void destroySlice(GeometrySlice slice) {
        if (slice == null) {
            return;
        }
        if (slice.vertexBuffer() != VK_NULL_HANDLE) {
            vkDestroyBuffer(device, slice.vertexBuffer(), null);
        }
        if (slice.vertexMemory() != VK_NULL_HANDLE) {
            vkFreeMemory(device, slice.vertexMemory(), null);
        }
        if (slice.indexBuffer() != VK_NULL_HANDLE) {
            vkDestroyBuffer(device, slice.indexBuffer(), null);
        }
        if (slice.indexMemory() != VK_NULL_HANDLE) {
            vkFreeMemory(device, slice.indexMemory(), null);
        }
    }

    record GeometrySlice(
            long vertexBuffer,
            long vertexMemory,
            int vertexCount,
            int vertexStride,
            long indexBuffer,
            long indexMemory,
            int indexCount,
            long indirectBuffer
    ) {
    }
}
