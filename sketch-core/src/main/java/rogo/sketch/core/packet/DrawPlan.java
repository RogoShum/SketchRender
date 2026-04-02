package rogo.sketch.core.packet;

import rogo.sketch.core.data.PrimitiveType;
import rogo.sketch.core.data.vertex.VertexDataShard;

import java.util.List;

public record DrawPlan(
        DrawSubmission submission,
        PrimitiveType primitiveType,
        VertexDataShard indexedShard,
        int vertexCount,
        int firstVertex,
        int instanceCount,
        int baseInstance,
        int drawCount,
        long indirectOffset,
        int indirectStride,
        List<DirectDrawItem> directItems
) {
    public enum DrawSubmission {
        DIRECT_INDEXED_INSTANCED,
        DIRECT_NON_INDEXED_INSTANCED,
        DIRECT_BATCH,
        MULTI_DRAW_INDIRECT
    }

    public DrawPlan {
        directItems = directItems != null ? List.copyOf(directItems) : List.of();
    }

    public static DrawPlan directIndexed(PrimitiveType primitiveType, VertexDataShard indexedShard, int instanceCount, int baseInstance) {
        DirectDrawItem item = DirectDrawItem.indexed(indexedShard, instanceCount, baseInstance);
        return new DrawPlan(
                DrawSubmission.DIRECT_INDEXED_INSTANCED,
                primitiveType,
                indexedShard,
                0,
                0,
                instanceCount,
                baseInstance,
                0,
                0L,
                0,
                List.of(item));
    }

    public static DrawPlan directNonIndexed(PrimitiveType primitiveType, int vertexCount, int firstVertex, int instanceCount, int baseInstance) {
        DirectDrawItem item = DirectDrawItem.nonIndexed(vertexCount, firstVertex, instanceCount, baseInstance);
        return new DrawPlan(
                DrawSubmission.DIRECT_NON_INDEXED_INSTANCED,
                primitiveType,
                null,
                vertexCount,
                firstVertex,
                instanceCount,
                baseInstance,
                0,
                0L,
                0,
                List.of(item));
    }

    public static DrawPlan directBatch(PrimitiveType primitiveType, List<DirectDrawItem> directItems) {
        if (directItems == null || directItems.isEmpty()) {
            throw new IllegalArgumentException("directItems must not be empty");
        }
        DirectDrawItem first = directItems.get(0);
        return new DrawPlan(
                DrawSubmission.DIRECT_BATCH,
                primitiveType,
                first.indexedShard(),
                first.vertexCount(),
                first.firstVertex(),
                first.instanceCount(),
                first.baseInstance(),
                directItems.size(),
                0L,
                0,
                directItems);
    }

    public static DrawPlan multiDrawIndirect(
            PrimitiveType primitiveType,
            int drawCount,
            long indirectOffset,
            int indirectStride) {
        return new DrawPlan(
                DrawSubmission.MULTI_DRAW_INDIRECT,
                primitiveType,
                null,
                0,
                0,
                0,
                0,
                drawCount,
                indirectOffset,
                indirectStride,
                List.of());
    }

    public boolean isIndirect() {
        return submission == DrawSubmission.MULTI_DRAW_INDIRECT;
    }

    public boolean isIndexedDirect() {
        return submission == DrawSubmission.DIRECT_INDEXED_INSTANCED;
    }

    public boolean isDirectBatch() {
        return submission == DrawSubmission.DIRECT_BATCH;
    }

    public record DirectDrawItem(
            VertexDataShard indexedShard,
            int vertexCount,
            int firstVertex,
            int instanceCount,
            int baseInstance
    ) {
        public static DirectDrawItem indexed(VertexDataShard indexedShard, int instanceCount, int baseInstance) {
            return new DirectDrawItem(indexedShard, 0, 0, instanceCount, baseInstance);
        }

        public static DirectDrawItem nonIndexed(int vertexCount, int firstVertex, int instanceCount, int baseInstance) {
            return new DirectDrawItem(null, vertexCount, firstVertex, instanceCount, baseInstance);
        }

        public boolean indexed() {
            return indexedShard != null;
        }
    }
}
