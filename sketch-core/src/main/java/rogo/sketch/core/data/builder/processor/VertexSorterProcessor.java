package rogo.sketch.core.data.builder.processor;

import rogo.sketch.core.data.PrimitiveType;
import rogo.sketch.core.data.format.DataFormat;
import rogo.sketch.core.data.sorting.PrimitiveSorter;
import rogo.sketch.core.data.builder.VertexDataBuilder;
import rogo.sketch.core.data.builder.VertexProcessor;
import rogo.sketch.core.data.builder.VertexSorting;
import rogo.sketch.core.data.builder.DataBufferWriter;
import rogo.sketch.core.data.builder.MemoryBufferWriter;

import java.nio.ByteBuffer;

/**
 * Processor that sorts vertices/primitives on CPU before flushing to the target writer.
 * Requires the builder to use a staging buffer.
 */
public class VertexSorterProcessor implements VertexProcessor {
    
    private final VertexSorting sortingStrategy;
    private final PrimitiveType primitiveType;
    
    public VertexSorterProcessor(VertexSorting sortingStrategy, PrimitiveType primitiveType) {
        this.sortingStrategy = sortingStrategy;
        this.primitiveType = primitiveType;
    }

    @Override
    public void onFinish(VertexDataBuilder builder) {
        if (sortingStrategy == VertexSorting.DISTANCE_TO_ORIGIN && !primitiveType.supportsSorting()) {
            return;
        }
        
        // We need access to the staging buffer.
        // VertexDataBuilder should expose its staging writer if sorting is active.
        // Or better, this processor performs the flush logic.
        
        // This processor assumes it has control over how data moves from Staging -> Target.
        // The VertexDataBuilder calls onFinish() BEFORE it might normally flush?
        // Actually, VertexDataBuilder.finish() delegates to flushSorted() in current code.
        // We want to move flushSorted() logic HERE.
        
        // For this to work, VertexDataBuilder needs to expose its Staging Buffer and Target Writer.
    }
    
    /**
     * Executes the sort and flush operation.
     * @param stagingBuffer The buffer containing raw unsorted vertices.
     * @param targetWriter The writer to output sorted vertices to.
     * @param stride Vertex stride in bytes.
     * @param format The data format (for finding position).
     */
    public void processAndFlush(MemoryBufferWriter stagingBuffer, DataBufferWriter targetWriter, int stride, DataFormat format) {
        ByteBuffer rawData = stagingBuffer.getBuffer();
        rawData.flip();
        
        int totalBytes = rawData.remaining();
        int vertexCount = totalBytes / stride;
        
        if (vertexCount == 0) {
            rawData.clear();
            return;
        }

        // Reuse PrimitiveSorter logic
        int[] sortedPrimitiveIndices = PrimitiveSorter.calculateSortedOrder(
            rawData, format, primitiveType, vertexCount, sortingStrategy
        );
        
        int verticesPerPrimitive = primitiveType.getVerticesPerPrimitive();
        
        // Write to Target in Order
        for (int primIdx : sortedPrimitiveIndices) {
            int baseVertex = primIdx * verticesPerPrimitive;
            int byteOffset = baseVertex * stride;
            int byteLength = verticesPerPrimitive * stride;
            
            rawData.position(byteOffset);
            rawData.limit(byteOffset + byteLength);
            
            targetWriter.put(rawData);
            
            rawData.limit(rawData.capacity()); // Restore limit
        }

        rawData.clear();
    }
}

