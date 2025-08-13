package rogo.sketch.render.data.format;

import rogo.sketch.render.data.DataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * OpenGL std430 layout compliant data format
 * Used for SSBO (Shader Storage Buffer Objects) data alignment
 */
public class Std430DataFormat extends DataFormat {
    
    public Std430DataFormat(String name, DataElement... elements) {
        this(name, Arrays.asList(elements));
    }

    public Std430DataFormat(String name, List<DataElement> elements) {
        super(name, recalculateWithStd430(name, elements));
    }
    
    private static List<DataElement> recalculateWithStd430(String name, List<DataElement> elements) {
        List<DataElement> result = new ArrayList<>(elements);
        calculateStd430Offsets(result);
        return result;
    }

    private static void calculateStd430Offsets(List<DataElement> elements) {
        int currentOffset = 0;
        
        for (DataElement element : elements) {
            // Get std430 alignment requirement
            int alignment = getStd430Alignment(element.getDataType());
            
            // Apply std430 alignment
            int misalignment = currentOffset % alignment;
            if (misalignment != 0) {
                currentOffset += alignment - misalignment;
            }
            
            element.setOffset(currentOffset);
            currentOffset += element.getDataType().getStride();
        }
    }
    
    /**
     * Get std430 alignment requirements for each data type
     */
    private static int getStd430Alignment(DataType type) {
        return switch (type) {
            case FLOAT, INT, UINT -> 4;                              // scalar: 4-byte aligned
            case VEC2, VEC2I, VEC2UI -> 8;                          // vec2: 8-byte aligned
            case VEC3, VEC4, VEC3I, VEC4I, VEC3UI, VEC4UI -> 16;    // vec3/4: 16-byte aligned
            case MAT2 -> 8;                                         // mat2: 8-byte aligned (column-major)
            case MAT3, MAT4 -> 16;                                  // mat3/4: 16-byte aligned (column-major)
            case BYTE, UBYTE -> 1;                                  // byte: 1-byte aligned
            case SHORT, USHORT -> 2;                                // short: 2-byte aligned
            case DOUBLE -> 8;                                       // double: 8-byte aligned
            default -> 4;                                           // default: 4-byte aligned
        };
    }
    
    /**
     * Create a builder for std430 data format
     */
    public static Std430Builder std430Builder(String name) {
        return new Std430Builder(name);
    }
    
    public static class Std430Builder {
        private final String name;
        private final List<DataElement> elements = new ArrayList<>();
        
        public Std430Builder(String name) {
            this.name = name;
        }
        
        public Std430Builder element(String name, DataType type) {
            elements.add(new DataElement(name, type, elements.size()));
            return this;
        }
        
        // Convenience methods for common types
        public Std430Builder floatElement(String name) {
            return element(name, DataType.FLOAT);
        }
        
        public Std430Builder intElement(String name) {
            return element(name, DataType.INT);
        }
        
        public Std430Builder vec2Element(String name) {
            return element(name, DataType.VEC2);
        }
        
        public Std430Builder vec3Element(String name) {
            return element(name, DataType.VEC3);
        }
        
        public Std430Builder vec4Element(String name) {
            return element(name, DataType.VEC4);
        }
        
        public Std430Builder mat4Element(String name) {
            return element(name, DataType.MAT4);
        }
        
        public Std430DataFormat build() {
            return new Std430DataFormat(name, elements);
        }
    }
    
    /**
     * Validate if this format matches std430 layout expectations
     */
    public boolean validateStd430Layout() {
        List<DataElement> elements = getElements();
        int expectedOffset = 0;
        
        for (DataElement element : elements) {
            int alignment = getStd430Alignment(element.getDataType());
            
            // Calculate expected offset with std430 alignment
            int misalignment = expectedOffset % alignment;
            if (misalignment != 0) {
                expectedOffset += alignment - misalignment;
            }
            
            if (element.getOffset() != expectedOffset) {
                return false;
            }
            
            expectedOffset += element.getDataType().getStride();
        }
        
        return true;
    }
}
