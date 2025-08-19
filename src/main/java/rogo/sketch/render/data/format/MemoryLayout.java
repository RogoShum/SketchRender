package rogo.sketch.render.data.format;

import java.util.ArrayList;
import java.util.List;

/**
 * Multi-level memory layout calculation tool
 * Supports nested memory structure definitions with flexible offset and size calculations
 */
public class MemoryLayout {
    private final DataFormat baseFormat;
    private final List<Dimension> dimensions;
    private final long[] strides;  // Element stride for each dimension
    private final long baseElementSize;  // Base element size in bytes
    
    /**
     * Dimension definition
     */
    public static class Dimension {
        public final String name;
        public final long size;  // Size of this dimension
        
        public Dimension(String name, long size) {
            this.name = name;
            this.size = size;
        }
    }
    
    /**
     * Memory structure information
     */
    public static class StructureInfo {
        public final long byteOffset;        // Byte offset
        public final long elementOffset;     // Element offset  
        public final long dataSize;          // Data size in bytes
        public final long elementCount;      // Number of base elements contained
        public final String structureName;   // Structure name
        
        public StructureInfo(String structureName, long byteOffset, long elementOffset, 
                           long dataSize, long elementCount) {
            this.structureName = structureName;
            this.byteOffset = byteOffset;
            this.elementOffset = elementOffset;
            this.dataSize = dataSize;
            this.elementCount = elementCount;
        }
        
        @Override
        public String toString() {
            return String.format("%s{byteOffset=%d, elementOffset=%d, dataSize=%d, elementCount=%d}",
                    structureName, byteOffset, elementOffset, dataSize, elementCount);
        }
    }
    
    private MemoryLayout(DataFormat baseFormat, List<Dimension> dimensions) {
        this.baseFormat = baseFormat;
        this.dimensions = new ArrayList<>(dimensions);
        this.baseElementSize = baseFormat.getStride();
        
        // Calculate stride for each dimension
        this.strides = new long[dimensions.size()];
        calculateStrides();
    }
    
    /**
     * Calculate stride for each dimension (in base element units)
     */
    private void calculateStrides() {
        if (dimensions.isEmpty()) {
            return;
        }
        
        // Start calculating from innermost layer
        // Innermost layer stride is 1 (per base element)
        strides[dimensions.size() - 1] = 1;
        
        // Calculate stride for each layer from inner to outer
        for (int i = dimensions.size() - 2; i >= 0; i--) {
            strides[i] = strides[i + 1] * dimensions.get(i + 1).size;
        }
    }
    
    /**
     * Calculate byte offset
     */
    public long calculateByteOffset(long... indices) {
        return calculateElementOffset(indices) * baseElementSize;
    }
    
    /**
     * Calculate element offset (in base element units)
     */
    public long calculateElementOffset(long... indices) {
        if (indices.length > dimensions.size()) {
            throw new IllegalArgumentException("Too many indices provided");
        }
        
        long offset = 0;
        for (int i = 0; i < indices.length; i++) {
            offset += indices[i] * strides[i];
        }
        return offset;
    }
    
    /**
     * Get data size in bytes for specified dimension
     */
    public long getDataSize(String dimensionName) {
        int dimensionIndex = findDimensionIndex(dimensionName);
        if (dimensionIndex == -1) {
            throw new IllegalArgumentException("Dimension not found: " + dimensionName);
        }
        
        // Calculate total element count from this dimension to innermost layer
        long elementCount = 1;
        for (int i = dimensionIndex; i < dimensions.size(); i++) {
            elementCount *= dimensions.get(i).size;
        }
        
        return elementCount * baseElementSize;
    }
    
    /**
     * Get number of base elements contained in specified dimension
     */
    public long getElementCount(String dimensionName) {
        int dimensionIndex = findDimensionIndex(dimensionName);
        if (dimensionIndex == -1) {
            throw new IllegalArgumentException("Dimension not found: " + dimensionName);
        }
        
        // Calculate total element count from this dimension to innermost layer
        long elementCount = 1;
        for (int i = dimensionIndex; i < dimensions.size(); i++) {
            elementCount *= dimensions.get(i).size;
        }
        
        return elementCount;
    }
    
    /**
     * Get size of specified dimension at its level (number of direct child elements)
     */
    public long getDimensionSize(String dimensionName) {
        int dimensionIndex = findDimensionIndex(dimensionName);
        if (dimensionIndex == -1) {
            throw new IllegalArgumentException("Dimension not found: " + dimensionName);
        }
        
        return dimensions.get(dimensionIndex).size;
    }
    
    /**
     * Get structure information for specified position
     */
    public StructureInfo getStructureInfo(String dimensionName, long... indices) {
        int dimensionIndex = findDimensionIndex(dimensionName);
        if (dimensionIndex == -1) {
            throw new IllegalArgumentException("Dimension not found: " + dimensionName);
        }
        
        if (indices.length < dimensionIndex) {
            throw new IllegalArgumentException("Not enough indices for dimension: " + dimensionName);
        }
        
        // Calculate offset to this dimension
        long[] partialIndices = new long[dimensionIndex + 1];
        System.arraycopy(indices, 0, partialIndices, 0, Math.min(indices.length, dimensionIndex + 1));
        
        long elementOffset = calculateElementOffset(partialIndices);
        long byteOffset = elementOffset * baseElementSize;
        long dataSize = getDataSize(dimensionName);
        long elementCount = getElementCount(dimensionName);
        
        return new StructureInfo(dimensionName, byteOffset, elementOffset, dataSize, elementCount);
    }
    
    /**
     * Get base data format
     */
    public DataFormat getBaseFormat() {
        return baseFormat;
    }
    
    /**
     * Get base element size in bytes
     */
    public long getBaseElementSize() {
        return baseElementSize;
    }
    
    /**
     * Get all dimension information
     */
    public List<Dimension> getDimensions() {
        return new ArrayList<>(dimensions);
    }
    
    /**
     * Find dimension index
     */
    private int findDimensionIndex(String dimensionName) {
        for (int i = 0; i < dimensions.size(); i++) {
            if (dimensions.get(i).name.equals(dimensionName)) {
                return i;
            }
        }
        return -1;
    }
    
    /**
     * Create layout builder
     */
    public static Builder builder(DataFormat baseFormat) {
        return new Builder(baseFormat);
    }
    
    /**
     * Layout builder
     */
    public static class Builder {
        private final DataFormat baseFormat;
        private final List<Dimension> dimensions = new ArrayList<>();
        
        private Builder(DataFormat baseFormat) {
            this.baseFormat = baseFormat;
        }
        
        /**
         * Add dimension (from outer to inner order)
         */
        public Builder addDimension(String name, long size) {
            dimensions.add(new Dimension(name, size));
            return this;
        }
        
        /**
         * Build memory layout
         */
        public MemoryLayout build() {
            if (dimensions.isEmpty()) {
                throw new IllegalStateException("At least one dimension must be specified");
            }
            return new MemoryLayout(baseFormat, dimensions);
        }
    }
    
    /**
     * Debug information
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("MemoryLayout{\n");
        sb.append("  baseFormat: ").append(baseFormat.getClass().getSimpleName())
          .append(" (").append(baseElementSize).append(" bytes)\n");
        
        for (int i = 0; i < dimensions.size(); i++) {
            Dimension dim = dimensions.get(i);
            sb.append("  ").append(dim.name).append(": size=").append(dim.size)
              .append(", stride=").append(strides[i]).append("\n");
        }
        
        sb.append("}");
        return sb.toString();
    }
}