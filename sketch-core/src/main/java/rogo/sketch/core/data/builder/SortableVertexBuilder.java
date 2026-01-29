package rogo.sketch.core.data.builder;

import org.lwjgl.system.MemoryUtil;
import rogo.sketch.core.data.DataType;
import rogo.sketch.core.data.PrimitiveType;
import rogo.sketch.core.data.format.DataElement;
import rogo.sketch.core.data.format.DataFormat;

import java.util.Arrays;

public class SortableVertexBuilder extends VertexStreamBuilder {

    private final boolean isInstancedMode;
    private final long sortKeyOffset;
    private final PrimitiveType primitiveType;

    private float[] centroids;
    private Integer[] primitiveIndices;
    private int primitiveCount = 0;

    private final int verticesPerPrimitive;
    private int currentVertexInPrimitive = 0;

    private float camX, camY, camZ;

    public SortableVertexBuilder(long address, long capacity, DataFormat format, PrimitiveType primitiveType, boolean isInstanced) {
        super(address, capacity, format, primitiveType);
        this.primitiveType = primitiveType;
        this.isInstancedMode = isInstanced;

        DataElement sortKey = format.getSortKeyElement();
        if (sortKey == null) {
            throw new IllegalStateException("Format missing sort key");
        }
        if (sortKey.getDataType() != DataType.VEC3F && sortKey.getDataType() != DataType.FLOAT) {
            throw new UnsupportedOperationException("Sort key must be VEC3F or FLOAT");
        }
        this.sortKeyOffset = sortKey.getOffset();

        if (isInstanced) {
            this.verticesPerPrimitive = 1;
        } else {
            this.verticesPerPrimitive = primitiveType.getVerticesPerPrimitive();
        }

        int initialCap = (int) (capacity / format.getStride() / verticesPerPrimitive);
        this.centroids = new float[initialCap];
        this.primitiveIndices = new Integer[initialCap];
    }

    public void setCameraPosition(float x, float y, float z) {
        this.camX = x;
        this.camY = y;
        this.camZ = z;
    }

    @Override
    protected void endVertex() {
        super.endVertex();

        currentVertexInPrimitive++;
        if (currentVertexInPrimitive == verticesPerPrimitive) {
            capturePrimitiveCentroid();
            currentVertexInPrimitive = 0;
            primitiveCount++;
        }
    }

    private void capturePrimitiveCentroid() {
        if (primitiveCount >= centroids.length) {
            growArrays();
        }

        long stride = getFormat().getStride();
        long bufferStart = getBaseAddress();
        int startVertexIdx = getVertexCount() - verticesPerPrimitive;

        float sumX = 0, sumY = 0, sumZ = 0;

        for (int i = 0; i < verticesPerPrimitive; i++) {
            long vertexAddr = bufferStart + (long) (startVertexIdx + i) * stride;
            long posAddr = vertexAddr + sortKeyOffset;

            sumX += MemoryUtil.memGetFloat(posAddr);
            sumY += MemoryUtil.memGetFloat(posAddr + 4);
            sumZ += MemoryUtil.memGetFloat(posAddr + 8);
        }

        float cenX = sumX / verticesPerPrimitive;
        float cenY = sumY / verticesPerPrimitive;
        float cenZ = sumZ / verticesPerPrimitive;

        float dx = cenX - camX;
        float dy = cenY - camY;
        float dz = cenZ - camZ;

        centroids[primitiveCount] = dx * dx + dy * dy + dz * dz;
        primitiveIndices[primitiveCount] = primitiveCount;
    }

    private void growArrays() {
        int newCap = centroids.length * 2;
        centroids = Arrays.copyOf(centroids, newCap);
        primitiveIndices = Arrays.copyOf(primitiveIndices, newCap);
    }

    public void flushSortedIndices(UnsafeBatchBuilder targetIndexBuffer, int baseVertexGlobalIndex) {
        if (isInstancedMode) {
            throw new IllegalStateException("Cannot flush indices in Instanced Mode");
        }

        sortPrimitives();

        for (int i = 0; i < primitiveCount; i++) {
            int primIdx = primitiveIndices[i];
            int startVert = baseVertexGlobalIndex + primIdx * verticesPerPrimitive;

            if (primitiveType == PrimitiveType.QUADS) {
                targetIndexBuffer.put(startVert + 0);
                targetIndexBuffer.put(startVert + 1);
                targetIndexBuffer.put(startVert + 2);

                targetIndexBuffer.put(startVert + 2);
                targetIndexBuffer.put(startVert + 3);
                targetIndexBuffer.put(startVert + 0);
            } else if (primitiveType == PrimitiveType.TRIANGLES) {
                targetIndexBuffer.put(startVert + 0);
                targetIndexBuffer.put(startVert + 1);
                targetIndexBuffer.put(startVert + 2);
            } else {
                for (int v = 0; v < verticesPerPrimitive; v++) {
                    targetIndexBuffer.put(startVert + v);
                }
            }
        }
    }

    public void flushSortedData(UnsafeBatchBuilder targetVBOBuffer) {
        if (!isInstancedMode) {
            throw new IllegalStateException("Cannot flush data reordering in Geometry Mode");
        }

        sortPrimitives();

        long stride = getFormat().getStride();
        long baseAddr = getBaseAddress();
        int bytesPerPrimitive = (int) stride * verticesPerPrimitive;

        for (int i = 0; i < primitiveCount; i++) {
            int oldIndex = primitiveIndices[i];
            long srcAddr = baseAddr + (long) oldIndex * bytesPerPrimitive;
            targetVBOBuffer.putData(srcAddr, bytesPerPrimitive);
        }

        reset();
    }

    private void sortPrimitives() {
        Arrays.sort(primitiveIndices, 0, primitiveCount, (a, b) ->
                Float.compare(centroids[b], centroids[a])
        );
    }

    @Override
    public void reset() {
        super.reset();
        primitiveCount = 0;
        currentVertexInPrimitive = 0;
    }

    public DataFormat getFormat() {
        return super.format;
    }
}