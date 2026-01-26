package rogo.sketch.core.resource.buffer;

import rogo.sketch.core.data.format.DataFormat;
import rogo.sketch.core.data.format.MemoryLayout;
import rogo.sketch.core.resource.ResourceTypes;

import java.util.HashMap;
import java.util.Map;

/**
 * Advanced SSBO memory manager for complex multi-dimensional buffer layouts
 * Supports hierarchical memory blocks with Region -> Pass -> Section structure
 */
public class SSBOMemoryManager {
    private final ShaderStorageBuffer ssbo;
    private final DataFormat dataFormat;
    // Use the MemoryLayout from data.format package
    private final MemoryLayout layout;
    private final Map<String, MemoryBlock> namedBlocks = new HashMap<>();

    public SSBOMemoryManager(DataFormat format, MemoryLayout layout) {
        this.dataFormat = format;
        this.layout = layout;

        // Calculate total capacity using MemoryLayout
        // MemoryLayout.getDataSize returns bytes for the dimension. 
        // We need total bytes for root dimension.
        // Assuming first dimension is root.
        long totalCapacity = 0;
        if (!layout.getDimensions().isEmpty()) {
            totalCapacity = layout.getDataSize(layout.getDimensions().get(0).name);
        } else {
            totalCapacity = format.getStride();
        }

        long elementCount = totalCapacity / format.getStride();
        this.ssbo = new ShaderStorageBuffer(elementCount, format.getStride(),
                org.lwjgl.opengl.GL15.GL_DYNAMIC_DRAW);
    }

    /**
     * Get a memory block for specific coordinates
     */
    public MemoryBlock getBlock(int... coordinates) {
        // We need to map coordinates to byte offset using MemoryLayout
        long byteOffset = layout.calculateByteOffset(convertToLongArray(coordinates));

        // Calculate size of the block at this level
        // MemoryLayout stores dimensions.
        // If we provide N coordinates, we are pointing to an element at depth N-1.
        // The "size" of this block depends on remaining dimensions.

        // We can find the dimension name for the current level?
        // Or calculate manually?
        // MemoryLayout.getDataSize(dimName) gives size of ONE unit of that dimension.
        // If coordinates point to a specific "Pass", we want size of that Pass.

        // Let's assume user provides coordinates up to a certain depth.
        // We need to know which dimension that corresponds to.
        if (coordinates.length > layout.getDimensions().size()) {
            throw new IllegalArgumentException("Too many coordinates");
        }

        // If full coordinates provided, size is 1 element stride.
        // If partial, size is stride of the dimension AFTER the last coordinate.
        // Wait, MemoryLayout.strides logic:
        // stride[i] = size of one element at dimension i.
        // If we provide index for dimension 0..k, we have selected a block at level k.
        // Its size is stride[k] * baseElementSize? No.

        // MemoryLayout.calculateByteOffset returns start of the element.
        // We need the size of the block we selected.
        // If we selected a Region (dim 0), we want size of Region.
        // Size of Region = stride[0] * baseSize (as per my update to MemoryLayout.getDataSize).
        // So size = layout.getStride(dimensionName).

        String dimName = layout.getDimensions().get(coordinates.length - 1).name;
        long size = layout.getDataSize(dimName); // Returns size of ONE unit of this dimension

        return new MemoryBlock(byteOffset, size, coordinates.clone());
    }

    private long[] convertToLongArray(int[] ints) {
        long[] longs = new long[ints.length];
        for (int i = 0; i < ints.length; i++) longs[i] = ints[i];
        return longs;
    }

    /**
     * Get a named memory block for easier access
     */
    public MemoryBlock getNamedBlock(String name) {
        return namedBlocks.get(name);
    }

    /**
     * Register a named memory block
     */
    public SSBOMemoryManager registerBlock(String name, int... coordinates) {
        MemoryBlock block = getBlock(coordinates);
        namedBlocks.put(name, block);
        return this;
    }


    /**
     * Upload specific memory block to GPU
     */
    public void uploadBlock(MemoryBlock block) {
        ssbo.upload(block.offset / ssbo.getStride(), (int) block.size);
    }

    /**
     * Upload all data to GPU
     */
    public void uploadAll() {
        ssbo.upload();
    }

    /**
     * Bind SSBO to shader slot
     */
    public void bind(int bindingPoint) {
        ssbo.bind(ResourceTypes.SHADER_STORAGE_BUFFER, bindingPoint);
    }

    /**
     * Get the underlying SSBO
     */
    public ShaderStorageBuffer getSSBO() {
        return ssbo;
    }

    /**
     * Get memory layout
     */
    public MemoryLayout getLayout() {
        return layout;
    }

    /**
     * Get data format
     */
    public DataFormat getDataFormat() {
        return dataFormat;
    }

    /**
     * Dispose resources
     */
    public void dispose() {
        ssbo.dispose();
        namedBlocks.clear();
    }

    /**
     * Memory block representation
     */
    public static class MemoryBlock {
        public final long offset;
        public final long size;
        public final int[] coordinates;

        MemoryBlock(long offset, long size, int[] coordinates) {
            this.offset = offset;
            this.size = size;
            this.coordinates = coordinates;
        }

        @Override
        public String toString() {
            return String.format("MemoryBlock{offset=%d, size=%d, coords=%s}",
                    offset, size, java.util.Arrays.toString(coordinates));
        }
    }
}
