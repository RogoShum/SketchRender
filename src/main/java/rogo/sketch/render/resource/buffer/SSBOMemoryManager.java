package rogo.sketch.render.resource.buffer;

import rogo.sketch.render.data.format.DataFormat;
import rogo.sketch.render.data.format.Std430DataFormat;
import rogo.sketch.render.vertexbuffer.filler.SSBOFiller;

import java.util.HashMap;
import java.util.Map;

/**
 * Advanced SSBO memory manager for complex multi-dimensional buffer layouts
 * Supports hierarchical memory blocks with Region -> Pass -> Section structure
 */
public class SSBOMemoryManager {
    private final ShaderStorageBuffer ssbo;
    private final DataFormat dataFormat;
    private final MemoryLayout layout;
    private final Map<String, MemoryBlock> namedBlocks = new HashMap<>();
    
    public SSBOMemoryManager(DataFormat format, MemoryLayout layout) {
        this.dataFormat = format;
        this.layout = layout;
        
        // Create SSBO with calculated total capacity
        long totalCapacity = layout.calculateTotalSize(format.getStride());
        this.ssbo = new ShaderStorageBuffer(totalCapacity / format.getStride(), format.getStride(), 
                                          org.lwjgl.opengl.GL15.GL_DYNAMIC_DRAW);
    }
    
    /**
     * Get a memory block for specific coordinates
     */
    public MemoryBlock getBlock(int... coordinates) {
        if (coordinates.length != layout.getDimensions()) {
            throw new IllegalArgumentException("Coordinate dimensions mismatch. Expected: " + 
                                             layout.getDimensions() + ", got: " + coordinates.length);
        }
        
        long offset = layout.calculateOffset(coordinates);
        long size = layout.getBlockSize(coordinates.length - 1);
        
        return new MemoryBlock(offset, size, coordinates.clone());
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
     * Create a filler for specific memory block
     */
    public SSBOFiller createFillerForBlock(MemoryBlock block) {
        // Create a virtual SSBO view for this memory block
        SSBOView view = new SSBOView(ssbo, block.offset, block.size);
        return new SSBOFiller(dataFormat, view);
    }
    
    /**
     * Create a filler for specific coordinates
     */
    public SSBOFiller createFiller(int... coordinates) {
        MemoryBlock block = getBlock(coordinates);
        return createFillerForBlock(block);
    }
    
    /**
     * Create a filler for named block
     */
    public SSBOFiller createFiller(String blockName) {
        MemoryBlock block = getNamedBlock(blockName);
        if (block == null) {
            throw new IllegalArgumentException("Named block not found: " + blockName);
        }
        return createFillerForBlock(block);
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
    public void bindToShaderSlot(int bindingPoint) {
        ssbo.bindShaderSlot(bindingPoint);
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
    
    /**
     * Memory layout definition for multi-dimensional SSBO structures
     */
    public static class MemoryLayout {
        private final long[] dimensionSizes;
        private final long[] strides;
        
        public MemoryLayout(long... dimensionSizes) {
            this.dimensionSizes = dimensionSizes.clone();
            this.strides = calculateStrides();
        }
        
        private long[] calculateStrides() {
            long[] strides = new long[dimensionSizes.length];
            strides[strides.length - 1] = 1; // Innermost dimension has stride 1
            
            for (int i = strides.length - 2; i >= 0; i--) {
                strides[i] = strides[i + 1] * dimensionSizes[i + 1];
            }
            
            return strides;
        }
        
        public long calculateOffset(int... coordinates) {
            if (coordinates.length > dimensionSizes.length) {
                throw new IllegalArgumentException("Too many coordinates");
            }
            
            long offset = 0;
            for (int i = 0; i < coordinates.length; i++) {
                if (coordinates[i] >= dimensionSizes[i]) {
                    throw new IndexOutOfBoundsException("Coordinate " + i + " out of bounds: " + 
                                                      coordinates[i] + " >= " + dimensionSizes[i]);
                }
                offset += coordinates[i] * strides[i];
            }
            
            return offset;
        }
        
        public long getBlockSize(int dimensionLevel) {
            if (dimensionLevel >= dimensionSizes.length) {
                return 1;
            }
            
            long blockSize = 1;
            for (int i = dimensionLevel + 1; i < dimensionSizes.length; i++) {
                blockSize *= dimensionSizes[i];
            }
            
            return blockSize;
        }
        
        public long calculateTotalSize(long elementStride) {
            long totalElements = 1;
            for (long size : dimensionSizes) {
                totalElements *= size;
            }
            return totalElements * elementStride;
        }
        
        public int getDimensions() {
            return dimensionSizes.length;
        }
        
        public long getDimensionSize(int dimension) {
            return dimensionSizes[dimension];
        }
    }
    
    /**
     * Virtual view of SSBO for specific memory range
     */
    private static class SSBOView extends ShaderStorageBuffer {
        private final ShaderStorageBuffer parent;
        private final long viewOffset;
        private final long viewSize;
        
        public SSBOView(ShaderStorageBuffer parent, long offset, long size) {
            super(parent); // Copy constructor
            this.parent = parent;
            this.viewOffset = offset;
            this.viewSize = size;
            
            // Override memory address to point to offset location
            setBufferPointer(parent.getMemoryAddress() + offset);
            setCapacity(size);
        }
        
        @Override
        public void upload() {
            // Upload only this view's range
            parent.upload(viewOffset / parent.getStride(), (int) viewSize);
        }
        
        @Override
        public void upload(long index) {
            // Translate index to parent's address space
            parent.upload((viewOffset / parent.getStride()) + index);
        }
    }
    
    /**
     * Factory methods for common SSBO layouts
     */
    public static class Factory {
        
        /**
         * Create manager for Region-Pass-Section layout (like Minecraft chunk rendering)
         */
        public static SSBOMemoryManager createRegionPassSectionLayout(
                DataFormat sectionFormat, int maxRegions, int passCount, int sectionsPerRegion) {
            
            MemoryLayout layout = new MemoryLayout(maxRegions, passCount, sectionsPerRegion);
            return new SSBOMemoryManager(sectionFormat, layout);
        }
        
        /**
         * Create manager for simple 2D grid layout
         */
        public static SSBOMemoryManager create2DGrid(DataFormat elementFormat, int width, int height) {
            MemoryLayout layout = new MemoryLayout(height, width);
            return new SSBOMemoryManager(elementFormat, layout);
        }
        
        /**
         * Create manager for 3D volume layout
         */
        public static SSBOMemoryManager create3DVolume(DataFormat elementFormat, int depth, int height, int width) {
            MemoryLayout layout = new MemoryLayout(depth, height, width);
            return new SSBOMemoryManager(elementFormat, layout);
        }
        
        /**
         * Create manager with std430-compliant format
         */
        public static SSBOMemoryManager createWithStd430Format(
                Std430DataFormat format, long... dimensionSizes) {
            
            MemoryLayout layout = new MemoryLayout(dimensionSizes);
            return new SSBOMemoryManager(format, layout);
        }
    }
}
