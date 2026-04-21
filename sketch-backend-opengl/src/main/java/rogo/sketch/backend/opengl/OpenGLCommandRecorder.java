package rogo.sketch.backend.opengl;

import org.lwjgl.opengl.GL42;
import org.lwjgl.opengl.GL43;
import rogo.sketch.core.backend.BackendCounterBuffer;
import rogo.sketch.core.backend.BackendInstalledBuffer;
import rogo.sketch.core.backend.BackendStorageBuffer;
import rogo.sketch.core.backend.CommandRecorder;

final class OpenGLCommandRecorder implements CommandRecorder {
    @Override
    public void uploadBuffer(BackendStorageBuffer target, long sourceAddress, long byteCount) {
        CommandRecorder.super.uploadBuffer(target, sourceAddress, byteCount);
    }

    @Override
    public void clearCounter(BackendCounterBuffer counterBuffer, int value) {
        if (counterBuffer instanceof OpenGLCounterStorageBuffer openGLCounterBuffer && value == 0) {
            openGLCounterBuffer.updateCount(0);
        } else if (counterBuffer instanceof OpenGLCounterBuffer openGLCounterBuffer && value == 0) {
            openGLCounterBuffer.updateCount(0);
        }
    }

    @Override
    public void clearBuffer(BackendInstalledBuffer buffer, long offsetBytes, long byteCount, int clearValue) {
        if (!(buffer instanceof OpenGLStorageBuffer storageBuffer) || clearValue != 0) {
            return;
        }
        long capacity = storageBuffer.capacityBytes();
        if (offsetBytes != 0L || byteCount <= 0L || byteCount < capacity) {
            return;
        }
        org.lwjgl.system.MemoryUtil.memSet(storageBuffer.memoryAddress(), 0, capacity);
        storageBuffer.upload();
    }

    @Override
    public void bufferBarrier() {
        GL42.glMemoryBarrier(
                GL43.GL_SHADER_STORAGE_BARRIER_BIT
                        | GL43.GL_ATOMIC_COUNTER_BARRIER_BIT
                        | GL43.GL_COMMAND_BARRIER_BIT);
    }

    @Override
    public void imageBarrier() {
        GL42.glMemoryBarrier(
                GL43.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT
                        | GL43.GL_TEXTURE_FETCH_BARRIER_BIT
                        | GL43.GL_FRAMEBUFFER_BARRIER_BIT);
    }

    @Override
    public void dispatch(int groupCountX, int groupCountY, int groupCountZ) {
        GL43.glDispatchCompute(groupCountX, groupCountY, groupCountZ);
    }
}
