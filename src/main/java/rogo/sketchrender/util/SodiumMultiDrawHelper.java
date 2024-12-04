package rogo.sketchrender.util;

import me.jellysquid.mods.sodium.client.gl.device.MultiDrawBatch;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.Pointer;

public class SodiumMultiDrawHelper {
    private final MultiDrawBatch batch;

    public SodiumMultiDrawHelper(MultiDrawBatch batch) {
        this.batch = batch;
    }

    public int getDrawCount() {
        return batch.size();
    }

    public int getElementCount(int index) {
        if (index < 0 || index >= batch.size()) {
            throw new IndexOutOfBoundsException("Invalid index: " + index);
        }
        return MemoryUtil.memGetInt(batch.pElementCount + (long) index * 4L);
    }

    public long getFirstIndex(int index) {
        if (index < 0 || index >= batch.size()) {
            throw new IndexOutOfBoundsException("Invalid index: " + index);
        }
        return MemoryUtil.memGetLong(batch.pElementPointer + (long) index * Pointer.POINTER_SIZE);
    }

    public int getBaseVertex(int index) {
        if (index < 0 || index >= batch.size()) {
            throw new IndexOutOfBoundsException("Invalid index: " + index);
        }
        return MemoryUtil.memGetInt(batch.pBaseVertex + (long) index * 4L);
    }

    public int getInstanceCount(int index) {
        return 1;
    }

    public int getBaseInstance(int index) {
        return 0;
    }

    public DrawElementsIndirectCommand getDrawCommand(int index) {
        if (index < 0 || index >= batch.size()) {
            throw new IndexOutOfBoundsException("Invalid index: " + index);
        }

        DrawElementsIndirectCommand command = new DrawElementsIndirectCommand();
        command.count = getElementCount(index);
        command.firstIndex = (int) getFirstIndex(index);
        command.baseVertex = getBaseVertex(index);
        command.instanceCount = getInstanceCount(index);
        command.baseInstance = getBaseInstance(index);

        return command;
    }
}

