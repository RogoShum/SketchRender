package rogo.sketch.backend.opengl;

import org.lwjgl.opengl.GL15;
import org.lwjgl.system.MemoryUtil;
import rogo.sketch.module.culling.TerrainMeshReadbackBuffer;

/**
 * Non-persistent fallback for shader-storage readback buffers when the current
 * desktop GL context does not expose persistent mapped buffer storage.
 */
public final class OpenGLReadbackStorageBuffer extends OpenGLStorageBuffer implements TerrainMeshReadbackBuffer {
    public OpenGLReadbackStorageBuffer(long dataCount, long stride) {
        super(dataCount, stride, GL15.GL_DYNAMIC_DRAW);
    }

    @Override
    public void ensureCapacity(int requiredCount, boolean force) {
        ensureCapacity(requiredCount, true, force);
    }

    @Override
    public int getInt(long index) {
        long address = memoryAddress() + index * Math.max(1L, strideBytes());
        return MemoryUtil.memGetInt(address);
    }

    @Override
    public int getUnsignedByte(long index) {
        long address = memoryAddress() + index * Math.max(1L, strideBytes());
        return Byte.toUnsignedInt(MemoryUtil.memGetByte(address));
    }

    @Override
    public byte getByte(long index) {
        long address = memoryAddress() + index * Math.max(1L, strideBytes());
        return MemoryUtil.memGetByte(address);
    }
}
