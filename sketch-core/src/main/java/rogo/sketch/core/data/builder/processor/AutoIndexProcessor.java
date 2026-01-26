package rogo.sketch.core.data.builder.processor;

import rogo.sketch.core.data.PrimitiveType;
import rogo.sketch.core.data.builder.VertexDataBuilder;
import rogo.sketch.core.data.builder.VertexProcessor;
import rogo.sketch.core.resource.buffer.IndexBufferResource;

/**
 * Automatically generates indices for primitives.
 * Useful for converting QUADS to TRIANGLES or just generating standard sequential indices.
 */
public class AutoIndexProcessor implements VertexProcessor {
    
    private final IndexBufferResource indexBuffer;
    private final PrimitiveType primitiveType;
    private int baseVertex = 0;
    
    public AutoIndexProcessor(IndexBufferResource indexBuffer, PrimitiveType primitiveType) {
        this.indexBuffer = indexBuffer;
        this.primitiveType = primitiveType;
    }

    @Override
    public void onStartBuild(VertexDataBuilder builder) {
        baseVertex = builder.getVertexCount(); // Or 0 if builder resets?
        // Usually index generation is relative to the draw call start.
        // If we are appending to a large buffer, we need the global offset.
    }

    @Override
    public void onFinish(VertexDataBuilder builder) {
        // Calculate how many vertices were added
        int endVertex = builder.getVertexCount();
        int addedVertices = endVertex - baseVertex;
        
        if (addedVertices == 0) return;
        
        // Generate indices
        int[] indices = primitiveType.generateIndices(addedVertices);
        
        // Add offset (baseVertex) and upload
        // Note: primitiveType.generateIndices returns 0-based relative indices.
        for (int i : indices) {
            indexBuffer.addIndex(baseVertex + i);
        }
        
        // Upload immediately or wait? 
        // IndexBufferResource accumulates in a List usually.
        indexBuffer.upload(); // Assuming this appends/updates.
        
        baseVertex = endVertex;
    }
}

