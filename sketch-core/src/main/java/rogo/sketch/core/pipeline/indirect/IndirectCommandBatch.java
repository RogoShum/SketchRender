package rogo.sketch.core.pipeline.indirect;

import org.lwjgl.system.MemoryUtil;
import rogo.sketch.core.backend.BackendIndirectBuffer;
import rogo.sketch.core.packet.DrawPlan;

import java.util.ArrayList;
import java.util.List;

/**
 * CPU-built indirect command batch for one logical stream.
 */
public final class IndirectCommandBatch {
    private final List<Command> commands;

    private IndirectCommandBatch(List<Command> commands) {
        this.commands = commands != null ? List.copyOf(commands) : List.of();
    }

    public static IndirectCommandBatch from(List<DrawPlan.DirectDrawItem> drawItems) {
        if (drawItems == null || drawItems.isEmpty()) {
            return new IndirectCommandBatch(List.of());
        }
        List<Command> commands = new ArrayList<>(drawItems.size());
        for (DrawPlan.DirectDrawItem drawItem : drawItems) {
            if (drawItem == null) {
                continue;
            }
            if (drawItem.indexed() && drawItem.indexedSlice() != null) {
                commands.add(new Command(
                        true,
                        drawItem.indexedSlice().indexCount(),
                        drawItem.instanceCount(),
                        (int) (drawItem.indexedSlice().firstIndexByteOffset() / Integer.BYTES),
                        drawItem.indexedSlice().baseVertex(),
                        drawItem.baseInstance()));
            } else {
                commands.add(new Command(
                        false,
                        drawItem.vertexCount(),
                        drawItem.instanceCount(),
                        drawItem.firstVertex(),
                        0,
                        drawItem.baseInstance()));
            }
        }
        return new IndirectCommandBatch(commands);
    }

    public boolean isEmpty() {
        return commands.isEmpty();
    }

    public int commandCount() {
        return commands.size();
    }

    public boolean indexed() {
        return !commands.isEmpty() && commands.get(0).indexed();
    }

    public void writeTo(BackendIndirectBuffer buffer, PersistentIndirectSlice slice) {
        if (buffer == null || slice == null || commands.isEmpty() || buffer.memoryAddress() == 0L) {
            return;
        }
        long stride = buffer.strideBytes();
        long baseAddress = buffer.memoryAddress() + (long) slice.startCommandIndex() * stride;
        for (int i = 0; i < commands.size(); i++) {
            long offset = baseAddress + i * stride;
            Command command = commands.get(i);
            MemoryUtil.memPutInt(offset, command.count());
            MemoryUtil.memPutInt(offset + 4L, command.instanceCount());
            MemoryUtil.memPutInt(offset + 8L, command.firstOrFirstIndex());
            if (command.indexed()) {
                MemoryUtil.memPutInt(offset + 12L, command.baseVertex());
                MemoryUtil.memPutInt(offset + 16L, command.baseInstance());
            } else {
                MemoryUtil.memPutInt(offset + 12L, command.baseInstance());
            }
        }
    }

    private record Command(
            boolean indexed,
            int count,
            int instanceCount,
            int firstOrFirstIndex,
            int baseVertex,
            int baseInstance
    ) {
    }
}
