package rogo.sketch.render.data.sorting;

import org.joml.Vector3f;
import rogo.sketch.render.data.PrimitiveType;
import rogo.sketch.render.data.builder.VertexSorting;
import rogo.sketch.render.data.format.DataFormat;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for sorting primitives for transparent rendering
 * Handles primitive center calculation and sorting logic
 */
public class PrimitiveSorter {

    /**
     * Data structure for primitive sorting
     */
    public static class PrimitiveSortData {
        public final int primitiveIndex;
        public final Vector3f center;
        public final float distance;

        public PrimitiveSortData(int primitiveIndex, Vector3f center, float distance) {
            this.primitiveIndex = primitiveIndex;
            this.center = center;
            this.distance = distance;
        }
    }

    /**
     * Calculate sorted primitive order for transparency rendering
     *
     * @param vertexData    Combined vertex data buffer
     * @param format        Vertex data format
     * @param primitiveType Type of primitives
     * @param vertexCount   Total number of vertices
     * @param sorting       Sorting algorithm to use
     * @return Array of primitive indices in sorted order (back to front)
     */
    public static int[] calculateSortedOrder(ByteBuffer vertexData, DataFormat format,
                                             PrimitiveType primitiveType, int vertexCount,
                                             VertexSorting sorting) {
        if (!primitiveType.supportsSorting() || sorting == null || vertexData == null) {
            return getNaturalOrder(vertexCount, primitiveType);
        }

        List<PrimitiveSortData> sortData = calculatePrimitiveSortData(
                vertexData, format, primitiveType, vertexCount, sorting
        );

        if (sortData.isEmpty()) {
            return getNaturalOrder(vertexCount, primitiveType);
        }

        // Sort primitives by distance (back to front for transparency)
        sortData.sort((a, b) -> Float.compare(b.distance, a.distance));

        return sortData.stream()
                .mapToInt(data -> data.primitiveIndex)
                .toArray();
    }

    /**
     * Calculate primitive centers and distances for sorting
     */
    private static List<PrimitiveSortData> calculatePrimitiveSortData(ByteBuffer vertexData,
                                                                      DataFormat format,
                                                                      PrimitiveType primitiveType,
                                                                      int vertexCount,
                                                                      VertexSorting sorting) {
        List<PrimitiveSortData> sortData = new ArrayList<>();

        int verticesPerPrimitive = primitiveType.getVerticesPerPrimitive();
        int primitiveCount = vertexCount / verticesPerPrimitive;

        // Find position element in format
        int positionElementIndex = findPositionElement(format);
        if (positionElementIndex == -1) {
            return sortData; // No position data found
        }

        int positionOffset = format.getElements().get(positionElementIndex).getOffset();
        int vertexStride = format.getStride();

        // Calculate center and distance for each primitive
        for (int primitiveIndex = 0; primitiveIndex < primitiveCount; primitiveIndex++) {
            Vector3f center = calculatePrimitiveCenter(
                    vertexData, primitiveIndex, verticesPerPrimitive,
                    positionOffset, vertexStride
            );

            float distance = sorting.calculateDistance(center);
            sortData.add(new PrimitiveSortData(primitiveIndex, center, distance));
        }

        return sortData;
    }

    /**
     * Calculate the center point of a primitive
     */
    private static Vector3f calculatePrimitiveCenter(ByteBuffer vertexData,
                                                     int primitiveIndex,
                                                     int verticesPerPrimitive,
                                                     int positionOffset,
                                                     int vertexStride) {
        Vector3f center = new Vector3f(0, 0, 0);
        int baseVertexIndex = primitiveIndex * verticesPerPrimitive;

        vertexData.rewind();

        for (int v = 0; v < verticesPerPrimitive; v++) {
            int vertexIndex = baseVertexIndex + v;
            int vertexStart = vertexIndex * vertexStride + positionOffset;

            if (vertexStart + 12 <= vertexData.capacity()) { // 3 floats = 12 bytes
                float x = vertexData.getFloat(vertexStart);
                float y = vertexData.getFloat(vertexStart + 4);
                float z = vertexData.getFloat(vertexStart + 8);
                center.add(x, y, z);
            }
        }

        // Average the positions to get center
        center.div(verticesPerPrimitive);
        return center;
    }

    /**
     * Find position element in data format
     */
    private static int findPositionElement(DataFormat format) {
        for (int i = 0; i < format.getElementCount(); i++) {
            String name = format.getElements().get(i).getName().toLowerCase();
            if (name.equals("pos") || name.equals("position")) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Generate natural order (no sorting)
     */
    private static int[] getNaturalOrder(int vertexCount, PrimitiveType primitiveType) {
        int primitiveCount = vertexCount / primitiveType.getVerticesPerPrimitive();
        int[] order = new int[primitiveCount];
        for (int i = 0; i < primitiveCount; i++) {
            order[i] = i;
        }
        return order;
    }

    /**
     * Check if a primitive type can be sorted
     */
    public static boolean canSort(PrimitiveType primitiveType) {
        return primitiveType.supportsSorting() && !primitiveType.isConnected();
    }

    /**
     * Get sorting capabilities for different primitive types
     */
    public static String getSortingCapabilities() {
        StringBuilder sb = new StringBuilder();
        sb.append("Primitive Sorting Capabilities:\n");

        for (PrimitiveType type : PrimitiveType.values()) {
            sb.append(String.format("%-15s: ", type.name()));

            if (type.supportsSorting()) {
                sb.append("✓ Sortable");
            } else {
                sb.append("✗ Not sortable");
                if (type.isConnected()) {
                    sb.append(" (connected primitives)");
                }
            }
            sb.append("\n");
        }

        return sb.toString();
    }
}
