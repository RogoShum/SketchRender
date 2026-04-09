package rogo.sketch.backend.opengl;

import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL31;
import org.lwjgl.system.MemoryUtil;
import rogo.sketch.core.backend.BackendUniformBuffer;
import rogo.sketch.backend.opengl.driver.GraphicsAPI;
import rogo.sketch.backend.opengl.internal.IGLBufferStrategy;
import rogo.sketch.core.resource.descriptor.ResolvedBufferResource;
import rogo.sketch.core.util.KeyId;

import java.nio.ByteBuffer;

final class OpenGLUniformBufferResource implements BackendUniformBuffer {
    private final GraphicsAPI api;
    private final int id;
    private final ResolvedBufferResource descriptor;
    private final long sizeBytes;
    private boolean disposed;

    OpenGLUniformBufferResource(GraphicsAPI api, ResolvedBufferResource descriptor, ByteBuffer initialData) {
        this.api = api;
        this.descriptor = descriptor;
        this.sizeBytes = Math.max(16L, descriptor.capacityBytes());
        IGLBufferStrategy strategy = api.getBufferStrategy();
        this.id = strategy.createBuffer();
        strategy.bufferData(id, sizeBytes, MemoryUtil.NULL, GL15.GL_DYNAMIC_DRAW);
        if (initialData != null) {
            update(initialData);
        }
    }

    @Override
    public ResolvedBufferResource descriptor() {
        return descriptor;
    }

    @Override
    public long sizeBytes() {
        return sizeBytes;
    }

    @Override
    public void update(ByteBuffer source) {
        if (disposed) {
            throw new IllegalStateException("Uniform buffer has been disposed");
        }
        ByteBuffer writeData;
        if (source == null) {
            writeData = MemoryUtil.memCalloc(Math.toIntExact(sizeBytes));
        } else {
            ByteBuffer copy = source.slice();
            if (copy.remaining() > sizeBytes) {
                throw new IllegalArgumentException("Uniform buffer update exceeds capacity " + sizeBytes);
            }
            writeData = MemoryUtil.memCalloc(Math.toIntExact(sizeBytes));
            writeData.put(copy);
            writeData.position(0);
            writeData.limit(Math.toIntExact(sizeBytes));
        }
        try {
            api.getBufferStrategy().bufferSubData(id, 0, writeData);
        } finally {
            MemoryUtil.memFree(writeData);
        }
    }

    @Override
    public void bind(KeyId resourceType, int binding) {
        if (disposed) {
            throw new IllegalStateException("Uniform buffer has been disposed");
        }
        api.getBufferStrategy().bindBufferBase(GL31.GL_UNIFORM_BUFFER, binding, id);
    }

    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        api.getBufferStrategy().deleteBuffer(id);
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }
}

