package rogo.sketch.backend.vulkan;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkBufferCreateInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryRequirements;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties;
import rogo.sketch.core.api.model.SharedGeometrySourceSnapshot;
import rogo.sketch.core.packet.GeometryHandleKey;
import rogo.sketch.core.pipeline.data.GeometryFrameData;
import rogo.sketch.core.pipeline.kernel.FrameExecutionPlan;
import rogo.sketch.core.pipeline.module.diagnostic.SketchDiagnostics;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_INDEX_BUFFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT;
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
    private static final String DIAG_MODULE = "vulkan-geometry-arena";
    private static final long DEFAULT_VERTEX_CAPACITY = 16L * 1024L * 1024L;
    private static final long DEFAULT_INDEX_CAPACITY = 8L * 1024L * 1024L;
    private static final long DEFAULT_INDIRECT_CAPACITY = 2L * 1024L * 1024L;
    private static final int DEFAULT_ALIGNMENT = 16;

    private final VkPhysicalDevice physicalDevice;
    private final VkDevice device;
    private final BufferArena sharedVertexArena;
    private final BufferArena sharedIndexArena;
    private final BufferArena vertexArena;
    private final BufferArena indexArena;
    private final BufferArena indirectArena;
    private final Map<GeometryHandleKey, GeometrySlice> slices = new ConcurrentHashMap<>();
    private final Map<Long, SharedSourceSlice> sharedSourceRegistry = new ConcurrentHashMap<>();

    VulkanGeometryArena(VkPhysicalDevice physicalDevice, VkDevice device) {
        this.physicalDevice = physicalDevice;
        this.device = device;
        this.sharedVertexArena = new BufferArena(VK_BUFFER_USAGE_VERTEX_BUFFER_BIT, DEFAULT_VERTEX_CAPACITY);
        this.sharedIndexArena = new BufferArena(VK_BUFFER_USAGE_INDEX_BUFFER_BIT, DEFAULT_INDEX_CAPACITY);
        this.vertexArena = new BufferArena(VK_BUFFER_USAGE_VERTEX_BUFFER_BIT, DEFAULT_VERTEX_CAPACITY);
        this.indexArena = new BufferArena(VK_BUFFER_USAGE_INDEX_BUFFER_BIT, DEFAULT_INDEX_CAPACITY);
        this.indirectArena = new BufferArena(VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT, DEFAULT_INDIRECT_CAPACITY);
    }

    void install(List<FrameExecutionPlan.GeometryUploadPlan> geometryUploadPlans, long frameEpoch, int maxFramesInFlight) {
        if (geometryUploadPlans == null || geometryUploadPlans.isEmpty()) {
            return;
        }
        for (FrameExecutionPlan.GeometryUploadPlan geometryUploadPlan : geometryUploadPlans) {
            if (geometryUploadPlan == null || geometryUploadPlan.geometryHandle() == null) {
                continue;
            }
            GeometrySlice slice = install(geometryUploadPlan, frameEpoch, maxFramesInFlight);
            if (slice != null) {
                slices.put(geometryUploadPlan.geometryHandle(), slice);
            }
        }
    }

    GeometrySlice registerInterleavedColorGeometry(GeometryHandleKey key, float[] vertexData, int vertexCount) {
        if (key == null || vertexData == null || vertexData.length == 0 || vertexCount <= 0) {
            return null;
        }
        ByteBuffer buffer = ByteBuffer.allocateDirect(vertexData.length * Float.BYTES);
        buffer.asFloatBuffer().put(vertexData);
        BufferArena.Allocation allocation = vertexArena.upload(buffer, -1L, Integer.MAX_VALUE);
        GeometrySlice slice = new GeometrySlice(
                new VertexBindingSlice[]{
                        new VertexBindingSlice(0, allocation.buffer(), allocation.offset(), 5 * Float.BYTES, vertexCount, false)
                },
                null,
                null,
                GeometryFrameData.SourceKind.BACKEND_NATIVE,
                vertexCount,
                0);
        slices.put(key, slice);
        return slice;
    }

    GeometrySlice resolve(GeometryHandleKey key) {
        return key == null ? null : slices.get(key);
    }

    void destroy() {
        slices.clear();
        sharedSourceRegistry.clear();
        sharedVertexArena.destroy();
        sharedIndexArena.destroy();
        vertexArena.destroy();
        indexArena.destroy();
        indirectArena.destroy();
    }

    private GeometrySlice install(
            FrameExecutionPlan.GeometryUploadPlan geometryUploadPlan,
            long frameEpoch,
            int maxFramesInFlight) {
        SharedSourceSlice sharedSourceSlice = null;
        if (geometryUploadPlan.optionalSharedSourceSnapshot() != null) {
            sharedSourceSlice = ensureSharedSourceSlice(geometryUploadPlan.optionalSharedSourceSnapshot());
        } else if (geometryUploadPlan.sharedSourceRef() > 0L) {
            sharedSourceSlice = sharedSourceRegistry.get(geometryUploadPlan.sharedSourceRef());
        }

        List<VertexBindingSlice> bindingSlices = new ArrayList<>();
        for (FrameExecutionPlan.VertexUploadSnapshot vertexUpload : geometryUploadPlan.dynamicVertexUploads()) {
            if (vertexUpload == null || vertexUpload.data().length == 0) {
                continue;
            }
            BufferArena.Allocation allocation = vertexArena.upload(vertexUpload.data(), frameEpoch, maxFramesInFlight);
            bindingSlices.add(new VertexBindingSlice(
                    vertexUpload.bindingPoint(),
                    allocation.buffer(),
                    allocation.offset(),
                    vertexUpload.stride(),
                    vertexUpload.vertexCount(),
                vertexUpload.instanced()));
        }
        if (sharedSourceSlice != null) {
            bindingSlices.addAll(List.of(sharedSourceSlice.vertexBindings()));
        }
        bindingSlices.sort(java.util.Comparator.comparingInt(VertexBindingSlice::binding));

        IndexSlice indexSlice = null;
        if (geometryUploadPlan.optionalIndexUpload() != null && geometryUploadPlan.optionalIndexUpload().data().length > 0) {
            BufferArena.Allocation allocation = indexArena.upload(
                    geometryUploadPlan.optionalIndexUpload().data(),
                    frameEpoch,
                    maxFramesInFlight);
            indexSlice = new IndexSlice(
                    allocation.buffer(),
                    allocation.offset(),
                    geometryUploadPlan.optionalIndexUpload().indexCount());
        }
        if (indexSlice == null && sharedSourceSlice != null) {
            indexSlice = sharedSourceSlice.indexSlice();
        }

        IndirectSlice indirectSlice = null;
        if (geometryUploadPlan.optionalIndirectUpload() != null && geometryUploadPlan.optionalIndirectUpload().data().length > 0) {
            BufferArena.Allocation allocation = indirectArena.upload(
                    geometryUploadPlan.optionalIndirectUpload().data(),
                    frameEpoch,
                    maxFramesInFlight);
            indirectSlice = new IndirectSlice(
                    allocation.buffer(),
                    allocation.offset(),
                    geometryUploadPlan.optionalIndirectUpload().drawCount(),
                    geometryUploadPlan.optionalIndirectUpload().stride());
        }

        if (geometryUploadPlan.sharedSourceRef() > 0L && sharedSourceSlice == null) {
            SketchDiagnostics.get().warn(
                    DIAG_MODULE,
                    "Missing backend-native shared source slice for handle=" + geometryUploadPlan.geometryHandle()
                            + " sourceRef=" + geometryUploadPlan.sharedSourceRef()
                            + "; shared/static source geometry should be installed before packet execution");
        }
        if (bindingSlices.isEmpty() && indexSlice == null && indirectSlice == null) {
            return resolve(geometryUploadPlan.geometryHandle());
        }

        return new GeometrySlice(
                bindingSlices.toArray(VertexBindingSlice[]::new),
                indexSlice,
                indirectSlice,
                sharedSourceSlice != null
                        ? GeometryFrameData.SourceKind.SHARED_SOURCE
                        : GeometryFrameData.SourceKind.DYNAMIC_STAGING,
                geometryUploadPlan.vertexCount(),
                geometryUploadPlan.indexCount());
    }

    private SharedSourceSlice ensureSharedSourceSlice(SharedGeometrySourceSnapshot sharedGeometrySourceSnapshot) {
        if (sharedGeometrySourceSnapshot == null || sharedGeometrySourceSnapshot.sharedSourceRef() <= 0L) {
            return null;
        }
        return sharedSourceRegistry.computeIfAbsent(
                sharedGeometrySourceSnapshot.sharedSourceRef(),
                ignored -> installSharedSource(sharedGeometrySourceSnapshot));
    }

    private SharedSourceSlice installSharedSource(SharedGeometrySourceSnapshot sharedGeometrySourceSnapshot) {
        if (sharedGeometrySourceSnapshot == null || !sharedGeometrySourceSnapshot.hasVertexData()) {
            return null;
        }
        BufferArena.Allocation vertexAllocation = sharedVertexArena.upload(
                sharedGeometrySourceSnapshot.vertexData(),
                -1L,
                Integer.MAX_VALUE);
        VertexBindingSlice[] vertexBindings = new VertexBindingSlice[]{
                new VertexBindingSlice(
                        0,
                        vertexAllocation.buffer(),
                        vertexAllocation.offset(),
                        sharedGeometrySourceSnapshot.stride(),
                        sharedGeometrySourceSnapshot.vertexCount(),
                        false)
        };

        IndexSlice indexSlice = null;
        if (sharedGeometrySourceSnapshot.hasIndexData()) {
            BufferArena.Allocation indexAllocation = sharedIndexArena.upload(
                    sharedGeometrySourceSnapshot.indexData(),
                    -1L,
                    Integer.MAX_VALUE);
            indexSlice = new IndexSlice(
                    indexAllocation.buffer(),
                    indexAllocation.offset(),
                    sharedGeometrySourceSnapshot.indexCount());
        }

        return new SharedSourceSlice(
                vertexBindings,
                indexSlice,
                sharedGeometrySourceSnapshot.vertexCount(),
                sharedGeometrySourceSnapshot.indexCount());
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

    private final class BufferArena {
        private final int usageFlags;
        private final long capacity;
        private final long buffer;
        private final long memory;
        private final long mappedAddress;
        private final Deque<AllocationRange> liveRanges = new ArrayDeque<>();
        private long writeHead = 0L;

        private BufferArena(int usageFlags, long capacity) {
            this.usageFlags = usageFlags;
            this.capacity = capacity;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkBufferCreateInfo bufferInfo = VkBufferCreateInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
                        .size(capacity)
                        .usage(usageFlags)
                        .sharingMode(VK_SHARING_MODE_EXCLUSIVE);
                LongBuffer bufferPointer = stack.mallocLong(1);
                VulkanDeviceBootstrapper.checkVkResult(
                        vkCreateBuffer(device, bufferInfo, null, bufferPointer),
                        "vkCreateBuffer(arena)");
                this.buffer = bufferPointer.get(0);

                VkMemoryRequirements memoryRequirements = VkMemoryRequirements.calloc(stack);
                vkGetBufferMemoryRequirements(device, buffer, memoryRequirements);

                VkMemoryAllocateInfo allocInfo = VkMemoryAllocateInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                        .allocationSize(memoryRequirements.size())
                        .memoryTypeIndex(findMemoryType(
                                memoryRequirements.memoryTypeBits(),
                                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT));
                LongBuffer memoryPointer = stack.mallocLong(1);
                VulkanDeviceBootstrapper.checkVkResult(
                        vkAllocateMemory(device, allocInfo, null, memoryPointer),
                        "vkAllocateMemory(arena)");
                this.memory = memoryPointer.get(0);

                VulkanDeviceBootstrapper.checkVkResult(
                        vkBindBufferMemory(device, buffer, memory, 0L),
                        "vkBindBufferMemory(arena)");

                org.lwjgl.PointerBuffer mappedPointer = stack.mallocPointer(1);
                VulkanDeviceBootstrapper.checkVkResult(
                        vkMapMemory(device, memory, 0L, capacity, 0, mappedPointer),
                        "vkMapMemory(arena)");
                this.mappedAddress = mappedPointer.get(0);
            }
        }

        private Allocation upload(byte[] data, long frameEpoch, int maxFramesInFlight) {
            ByteBuffer buffer = ByteBuffer.wrap(data);
            return upload(buffer, frameEpoch, maxFramesInFlight);
        }

        private synchronized Allocation upload(ByteBuffer data, long frameEpoch, int maxFramesInFlight) {
            int size = data.remaining();
            long offset = reserve(size, frameEpoch, maxFramesInFlight);
            ByteBuffer target = MemoryUtil.memByteBuffer(mappedAddress + offset, size);
            target.put(data);
            return new Allocation(buffer, offset, size);
        }

        private long reserve(int size, long frameEpoch, int maxFramesInFlight) {
            if (size <= 0) {
                return 0L;
            }
            if (size > capacity) {
                throw new IllegalStateException("Requested upload size " + size + " exceeds Vulkan arena capacity " + capacity);
            }

            reclaim(frameEpoch, maxFramesInFlight);
            long alignedHead = align(writeHead, DEFAULT_ALIGNMENT);
            if (alignedHead + size > capacity) {
                alignedHead = 0L;
            }
            if (overlapsLiveRange(alignedHead, size)) {
                reclaim(frameEpoch + Math.max(maxFramesInFlight, 1), maxFramesInFlight);
            }
            if (overlapsLiveRange(alignedHead, size)) {
                throw new IllegalStateException("Vulkan arena is exhausted for usage=" + usageFlags + " size=" + size);
            }
            writeHead = alignedHead + size;
            liveRanges.addLast(new AllocationRange(alignedHead, size, frameEpoch));
            return alignedHead;
        }

        private void reclaim(long frameEpoch, int maxFramesInFlight) {
            while (!liveRanges.isEmpty()) {
                AllocationRange head = liveRanges.peekFirst();
                if (frameEpoch < 0L || frameEpoch - head.frameEpoch() < maxFramesInFlight) {
                    return;
                }
                liveRanges.removeFirst();
            }
        }

        private boolean overlapsLiveRange(long offset, int size) {
            long end = offset + size;
            for (AllocationRange range : liveRanges) {
                long rangeEnd = range.offset() + range.size();
                if (end > range.offset() && offset < rangeEnd) {
                    return true;
                }
            }
            return false;
        }

        private long align(long value, int alignment) {
            long mask = alignment - 1L;
            return (value + mask) & ~mask;
        }

        private void destroy() {
            if (mappedAddress != 0L) {
                vkUnmapMemory(device, memory);
            }
            if (buffer != VK_NULL_HANDLE) {
                vkDestroyBuffer(device, buffer, null);
            }
            if (memory != VK_NULL_HANDLE) {
                vkFreeMemory(device, memory, null);
            }
            liveRanges.clear();
        }

        private record Allocation(long buffer, long offset, int size) {
        }
    }

    private record AllocationRange(long offset, int size, long frameEpoch) {
    }

    record GeometrySlice(
            VertexBindingSlice[] vertexBindings,
            IndexSlice indexSlice,
            IndirectSlice indirectSlice,
            GeometryFrameData.SourceKind sourceKind,
            int vertexCount,
            int indexCount
    ) {
        GeometrySlice {
            vertexBindings = vertexBindings != null ? vertexBindings.clone() : new VertexBindingSlice[0];
            Arrays.sort(vertexBindings, java.util.Comparator.comparingInt(VertexBindingSlice::binding));
        }
    }

    record VertexBindingSlice(
            int binding,
            long buffer,
            long offset,
            int stride,
            int vertexCount,
            boolean instanced
    ) {
    }

    record IndexSlice(long buffer, long offset, int indexCount) {
    }

    record IndirectSlice(long buffer, long offset, int drawCount, int stride) {
    }

    private record SharedSourceSlice(
            VertexBindingSlice[] vertexBindings,
            IndexSlice indexSlice,
            int vertexCount,
            int indexCount
    ) {
    }
}

