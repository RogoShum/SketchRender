package rogo.sketch.render;

import rogo.sketch.render.data.format.DataFormat;
import rogo.sketch.render.vertex.VertexResource;
import rogo.sketch.render.vertex.VertexResourceType;

/**
 * Complete render parameters including vertex resource creation info
 */
public record RenderParameter(
    DataFormat dataFormat,          // Vertex data format
    int primitiveType,              // OpenGL primitive type (GL_TRIANGLES, etc.)
    int usage,                      // Buffer usage hint (GL_STATIC_DRAW, etc.)
    boolean enableIndexBuffer,      // Whether to enable index buffer
    boolean enableSorting,          // Whether to enable vertex sorting
    int initialCapacity,            // Initial buffer capacity
    VertexResourceType resourceType // How this resource should be managed
) {
    
    /**
     * Create default dynamic render parameters
     */
    public static RenderParameter createDynamic(DataFormat dataFormat, int primitiveType) {
        return new RenderParameter(
            dataFormat,
            primitiveType,
            org.lwjgl.opengl.GL15.GL_DYNAMIC_DRAW,
            true,  // Enable index buffer by default
            false, // Disable sorting by default for dynamic
            1024,  // Default capacity
            VertexResourceType.SHARED_DYNAMIC // Default to shared batching
        );
    }
    
    /**
     * Create default static render parameters
     */
    public static RenderParameter createStatic(DataFormat dataFormat, int primitiveType) {
        return new RenderParameter(
            dataFormat,
            primitiveType,
            org.lwjgl.opengl.GL15.GL_STATIC_DRAW,
            true,  // Enable index buffer by default
            true,  // Enable sorting by default for static
            512,   // Default capacity
            VertexResourceType.SHARED_DYNAMIC // Default to shared batching
        );
    }
    
    /**
     * Create instanced render parameters
     */
    public static RenderParameter createInstanced(DataFormat staticFormat, int primitiveType) {
        return new RenderParameter(
            staticFormat,
            primitiveType,
            org.lwjgl.opengl.GL15.GL_STATIC_DRAW,
            true,  // Enable index buffer
            true,  // Enable sorting
            256,   // Default capacity for instanced
            VertexResourceType.INSTANCE_INSTANCED // Use instanced resource type
        );
    }
    
    /**
     * Create parameters for instance-owned static resources
     */
    public static RenderParameter createInstanceStatic(DataFormat dataFormat, int primitiveType) {
        return new RenderParameter(
            dataFormat,
            primitiveType,
            org.lwjgl.opengl.GL15.GL_STATIC_DRAW,
            true,  // Enable index buffer
            false, // No sorting needed for static
            256,   // Default capacity
            VertexResourceType.INSTANCE_STATIC
        );
    }
    
    /**
     * Create parameters for instance-owned dynamic resources
     */
    public static RenderParameter createInstanceDynamic(DataFormat dataFormat, int primitiveType) {
        return new RenderParameter(
            dataFormat,
            primitiveType,
            org.lwjgl.opengl.GL15.GL_DYNAMIC_DRAW,
            true,  // Enable index buffer
            true,  // Enable sorting for dynamic
            512,   // Default capacity
            VertexResourceType.INSTANCE_DYNAMIC
        );
    }
    
    /**
     * Create VertexResource from these parameters
     */
    public VertexResource createVertexResource() {
        return switch (usage) {
            case org.lwjgl.opengl.GL15.GL_STATIC_DRAW -> VertexResource.createStatic(dataFormat, primitiveType);
            case org.lwjgl.opengl.GL15.GL_DYNAMIC_DRAW -> VertexResource.createDynamic(dataFormat, primitiveType);
            default -> VertexResource.createDynamic(dataFormat, primitiveType);
        };
    }
} 