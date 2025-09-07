package rogo.sketch.render.data.filler;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import rogo.sketch.render.data.DataType;
import rogo.sketch.render.data.PrimitiveType;
import rogo.sketch.render.data.format.DataElement;
import rogo.sketch.render.data.format.DataFormat;

import java.nio.ByteBuffer;

/**
 * Example demonstrating the usage of the refactored data filler system
 */
public class FillerUsageExample {

    public static void main(String[] args) {
        // Define a vertex format with position, normal, UV, and color
        DataFormat vertexFormat = new DataFormat("StandardVertex",
            new DataElement("position", DataType.VEC3, 0),
            new DataElement("normal", DataType.VEC3, 1),
            new DataElement("uv", DataType.VEC2, 2),
            new DataElement("color", DataType.VEC4, 3)
        );

        // Example 1: Basic DataFiller usage with ByteBuffer
        demonstrateBasicDataFiller(vertexFormat);

        // Example 2: VertexFiller with semantic methods
        demonstrateVertexFiller(vertexFormat);

        // Example 3: SSBOFiller for GPU operations
        demonstrateSSBOFiller(vertexFormat);

        // Example 4: Memory filler for high performance
        demonstrateMemoryFiller(vertexFormat);

        // Example 5: GLSL data types usage
        demonstrateGLSLDataTypes();
    }

    private static void demonstrateBasicDataFiller(DataFormat format) {
        System.out.println("=== Basic DataFiller with ByteBuffer ===");
        
        ByteBufferFiller filler = ByteBufferFiller.create(format, 4);
        
        // Write vertex data using GLSL-style methods
        filler.putVec3(1.0f, 0.0f, 0.0f)    // position
              .putVec3(0.0f, 1.0f, 0.0f)    // normal
              .putVec2(0.0f, 0.0f)          // uv
              .putVec4(1.0f, 0.0f, 0.0f, 1.0f); // color (red)
        
        filler.putVec3(0.0f, 1.0f, 0.0f)    // position
              .putVec3(0.0f, 1.0f, 0.0f)    // normal
              .putVec2(1.0f, 0.0f)          // uv
              .putVec4(0.0f, 1.0f, 0.0f, 1.0f); // color (green)
        
        // Random access support
        if (filler.supportsRandomAccess()) {
            filler.putFloatAt(0, 2.0f); // Modify first vertex X position
        }
        
        filler.finish();
        ByteBuffer vertexData = filler.getBuffer();
        
        System.out.println("Generated " + vertexData.remaining() + " bytes of vertex data");
    }

    private static void demonstrateVertexFiller(DataFormat format) {
        System.out.println("\n=== VertexFiller with semantic methods ===");
        
        VertexFiller filler = new VertexFiller(format, PrimitiveType.TRIANGLES, 100);
        
        // Use semantic methods for clarity
        filler.position(0.0f, 0.0f, 0.0f)
              .normal(0.0f, 0.0f, 1.0f)
              .uv(0.0f, 0.0f)
              .color(1.0f, 1.0f, 1.0f, 1.0f)
              .nextVertex()
              
              .position(1.0f, 0.0f, 0.0f)
              .normal(0.0f, 0.0f, 1.0f)
              .uv(1.0f, 0.0f)
              .color(1.0f, 0.0f, 0.0f, 1.0f)
              .nextVertex()
              
              .position(0.5f, 1.0f, 0.0f)
              .normal(0.0f, 0.0f, 1.0f)
              .uv(0.5f, 1.0f)
              .color(0.0f, 1.0f, 0.0f, 1.0f);
        
        // Enable sorting for transparency
        filler.enableSorting();
        filler.sortByDistance(0.0f, 0.0f, 5.0f);
        
        // Random access update
        filler.positionAt(0, 0.1f, 0.1f, 0.1f);
        
        filler.finish();
        
        System.out.println("Generated triangle with " + filler.getVertexCount() + " vertices");
        System.out.println("Sorting enabled: " + filler.isSortingEnabled());
        System.out.println("Primitive type: " + filler.getPrimitiveType());
        
        // Clean up
        filler.dispose();
    }

    private static void demonstrateSSBOFiller(DataFormat format) {
        System.out.println("\n=== SSBOFiller for GPU operations ===");
        
        SSBOFiller filler = SSBOFiller.create(format, 1000);
        
        // Switch to memory access for performance
        filler.useMemoryAccess();
        
        // Fill some data
        for (int i = 0; i < 100; i++) {
            float x = (float) Math.cos(i * 0.1f);
            float y = (float) Math.sin(i * 0.1f);
            
            filler.putVec3(x, y, 0.0f)              // position
                  .putVec3(0.0f, 0.0f, 1.0f)        // normal
                  .putVec2(i / 100.0f, 0.0f)        // uv
                  .putVec4(x * 0.5f + 0.5f, y * 0.5f + 0.5f, 0.5f, 1.0f); // color
        }
        
        // Random access updates
        filler.positionAt(50, 0.0f, 0.0f, 1.0f);
        filler.colorAt(25, 12, 1.0f, 0.0f, 0.0f, 1.0f); // element offset 12 for color
        
        // Upload to GPU and bind
        filler.finish(); // This automatically uploads
        filler.bind(0);  // Bind to binding point 0
        
        System.out.println("SSBO created with capacity: " + filler.getVertexCapacity() + " vertices");
        System.out.println("Memory address: 0x" + Long.toHexString(filler.getMemoryAddress()));
        System.out.println("Random access supported: " + filler.supportsRandomAccess());
    }

    private static void demonstrateMemoryFiller(DataFormat format) {
        System.out.println("\n=== MemoryFiller for high performance ===");
        
        // Allocate memory for 500 vertices
        MemoryFiller filler = MemoryFiller.allocate(format, 500);
        
        // Fill data efficiently
        for (int i = 0; i < 500; i++) {
            float angle = i * 0.01f;
            filler.putVec3((float) Math.cos(angle), (float) Math.sin(angle), 0.0f)
                  .putVec3(0.0f, 0.0f, 1.0f)
                  .putVec2(angle / (2.0f * (float) Math.PI), 0.0f)
                  .putVec4(1.0f, 1.0f, 1.0f, 1.0f);
        }
        
        filler.finish();
        
        System.out.println("Memory filler capacity: " + filler.getCapacity() + " bytes");
        System.out.println("Base address: 0x" + Long.toHexString(filler.getBaseAddress()));
        
        // Clean up
        filler.dispose();
    }

    private static void demonstrateGLSLDataTypes() {
        System.out.println("\n=== GLSL Data Types Demonstration ===");
        
        // Create a format for shader uniforms
        DataFormat uniformFormat = new DataFormat("ShaderUniforms",
            new DataElement("modelMatrix", DataType.MAT4, 0),
            new DataElement("lightPositions", DataType.VEC3, 1), // Array element
            new DataElement("materialColor", DataType.VEC4, 2),
            new DataElement("flags", DataType.IVEC4, 3),
            new DataElement("enabled", DataType.BVEC4, 4)
        );
        
        ByteBufferFiller filler = ByteBufferFiller.create(uniformFormat, 1);
        
        // Matrix data
        Matrix4f modelMatrix = new Matrix4f().identity().scale(2.0f);
        filler.putMat4(modelMatrix);
        
        // Vector data
        filler.putVec3(10.0f, 5.0f, 0.0f); // Light position
        filler.putVec4(0.8f, 0.2f, 0.1f, 1.0f); // Material color
        
        // Integer vectors (GLSL: ivec4)
        filler.putIVec4(1, 2, 3, 4);
        
        // Boolean vectors (GLSL: bvec4)
        filler.putBVec4(true, false, true, false);
        
        // Unsigned integer types
        filler = ByteBufferFiller.create(new DataFormat("UnsignedData",
            new DataElement("indices", DataType.UVEC4, 0)
        ), 1);
        
        filler.putUVec4(100, 200, 300, 400);
        
        // Byte vectors for compressed data
        filler = ByteBufferFiller.create(new DataFormat("CompressedData",
            new DataElement("compressedNormal", DataType.VEC3B, 0),
            new DataElement("packedColor", DataType.VEC4UB, 1)
        ), 1);
        
        filler.putVec3b((byte) 127, (byte) 0, (byte) -127);  // Signed bytes
        filler.putVec4ub(255, 128, 64, 255);                // Unsigned bytes
        
        filler.finish();
        
        System.out.println("GLSL data types demonstration completed");
        System.out.println("Supports all common GLSL types: float, int, uint, bool vectors and matrices");
    }
}
