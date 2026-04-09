package rogo.sketch.core.data.builder;

import org.lwjgl.system.MemoryUtil;

import java.util.Arrays;

/**
 * Collects per-primitive sort metrics without mixing sort state into the
 * general-purpose vertex cursor implementation.
 */
final class PrimitiveSortOrder {
    private float[] centroidDistances;
    private Integer[] primitiveIndices;
    private int primitiveCount;

    PrimitiveSortOrder(int initialCapacity) {
        int capacity = Math.max(1, initialCapacity);
        this.centroidDistances = new float[capacity];
        this.primitiveIndices = new Integer[capacity];
    }

    void capturePrimitive(long bufferStart,
                          long stride,
                          long sortKeyOffset,
                          int startVertexIndex,
                          int verticesPerPrimitive,
                          float camX,
                          float camY,
                          float camZ) {
        if (primitiveCount >= centroidDistances.length) {
            grow();
        }

        float sumX = 0.0f;
        float sumY = 0.0f;
        float sumZ = 0.0f;
        for (int i = 0; i < verticesPerPrimitive; i++) {
            long vertexAddr = bufferStart + (long) (startVertexIndex + i) * stride;
            long posAddr = vertexAddr + sortKeyOffset;
            sumX += MemoryUtil.memGetFloat(posAddr);
            sumY += MemoryUtil.memGetFloat(posAddr + 4);
            sumZ += MemoryUtil.memGetFloat(posAddr + 8);
        }

        float centerX = sumX / verticesPerPrimitive;
        float centerY = sumY / verticesPerPrimitive;
        float centerZ = sumZ / verticesPerPrimitive;
        float dx = centerX - camX;
        float dy = centerY - camY;
        float dz = centerZ - camZ;

        centroidDistances[primitiveCount] = dx * dx + dy * dy + dz * dz;
        primitiveIndices[primitiveCount] = primitiveCount;
        primitiveCount++;
    }

    void sortBackToFront() {
        Arrays.sort(primitiveIndices, 0, primitiveCount, (left, right) ->
                Float.compare(centroidDistances[right], centroidDistances[left]));
    }

    int primitiveCount() {
        return primitiveCount;
    }

    int orderedPrimitiveIndex(int sortedIndex) {
        return primitiveIndices[sortedIndex];
    }

    void reset() {
        primitiveCount = 0;
    }

    private void grow() {
        int newCapacity = centroidDistances.length * 2;
        centroidDistances = Arrays.copyOf(centroidDistances, newCapacity);
        primitiveIndices = Arrays.copyOf(primitiveIndices, newCapacity);
    }
}

