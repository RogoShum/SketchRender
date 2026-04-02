package rogo.sketch.core.pipeline.flow.plan;

import rogo.sketch.core.command.prosessor.DrawRange;
import rogo.sketch.core.data.PrimitiveType;
import rogo.sketch.core.packet.DrawPlan;
import rogo.sketch.core.resource.buffer.IndirectCommandBuffer;

import java.util.List;

public final class DrawPlanCompiler {
    private DrawPlanCompiler() {
    }

    public static DrawPlan compileIndirect(PrimitiveType primitiveType, DrawRange range, IndirectCommandBuffer indirectBuffer) {
        if (primitiveType == null || range == null || indirectBuffer == null) {
            return null;
        }
        return DrawPlan.multiDrawIndirect(
                primitiveType,
                range.count(),
                (long) range.startCommandIndex() * indirectBuffer.getStride(),
                (int) indirectBuffer.getStride());
    }

    public static DrawPlan compileDirectIndexed(
            PrimitiveType primitiveType,
            rogo.sketch.core.data.vertex.VertexDataShard indexedShard,
            int instanceCount,
            int baseInstance) {
        if (primitiveType == null || indexedShard == null || instanceCount <= 0) {
            return null;
        }
        return DrawPlan.directIndexed(primitiveType, indexedShard, instanceCount, baseInstance);
    }

    public static DrawPlan compileDirectNonIndexed(
            PrimitiveType primitiveType,
            int vertexCount,
            int firstVertex,
            int instanceCount,
            int baseInstance) {
        if (primitiveType == null || vertexCount <= 0 || instanceCount <= 0) {
            return null;
        }
        return DrawPlan.directNonIndexed(primitiveType, vertexCount, firstVertex, instanceCount, baseInstance);
    }

    public static DrawPlan compileDirectBatch(
            PrimitiveType primitiveType,
            List<DrawPlan.DirectDrawItem> drawItems) {
        if (primitiveType == null || drawItems == null || drawItems.isEmpty()) {
            return null;
        }
        if (drawItems.size() == 1) {
            DrawPlan.DirectDrawItem item = drawItems.get(0);
            if (item.indexed()) {
                return compileDirectIndexed(primitiveType, item.indexedShard(), item.instanceCount(), item.baseInstance());
            }
            return compileDirectNonIndexed(
                    primitiveType,
                    item.vertexCount(),
                    item.firstVertex(),
                    item.instanceCount(),
                    item.baseInstance());
        }
        return DrawPlan.directBatch(primitiveType, drawItems);
    }
}
