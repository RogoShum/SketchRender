package rogo.sketch.core.resource.buffer;

import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL44;
import org.lwjgl.system.MemoryUtil;
import rogo.sketch.core.api.BufferResourceObject;
import rogo.sketch.core.data.Usage;
import rogo.sketch.core.driver.GLRuntimeFlags;
import rogo.sketch.core.driver.GraphicsAPI;
import rogo.sketch.core.driver.GraphicsDriver;
import rogo.sketch.core.driver.internal.IGLBufferStrategy;

import java.nio.ByteBuffer;

/**
 * Represents a single OpenGL Vertex Buffer Object (VBO).
 * Managed as a component of a VertexResource (VAO).
 * Uses GraphicsAPI buffer strategy for DSA/Legacy abstraction.
 * <p>
 * VBO operations (create, upload, map) require a GL context but NOT necessarily
 * the main thread -- they can run on the render worker thread if GL_WORKER_ENABLED.
 * VAO operations (bind to VAO, setup attributes) still require the main thread.
 */
public class VertexBufferObject implements BufferResourceObject {
    private final int handle;
    private final Usage usage;
    private long size;
    private boolean disposed = false;
    private long mappedAddress = MemoryUtil.NULL;
    private boolean persistentEnabled = false;
    private long writeFence = 0L;

    private static GraphicsAPI api() {
        return GraphicsDriver.getCurrentAPI();
    }

    private static IGLBufferStrategy getBufferStrategy() {
        return api().getBufferStrategy();
    }

    public VertexBufferObject(Usage usage) {
        api().assertGLContext("VertexBufferObject.<init>");
        this.usage = usage;
        this.handle = getBufferStrategy().createBuffer();
        this.size = 0;
    }

    public VertexBufferObject(int size, Usage usage) {
        this(usage);
        ensureCapacity(size);
    }

    public void bind() {
        api().assertGLContext("VertexBufferObject.bind");
        api().bindVertexBuffer(handle);
    }

    public void unbind() {
        api().assertGLContext("VertexBufferObject.unbind");
        api().bindVertexBuffer(0);
    }

    public void upload(ByteBuffer data) {
        api().assertGLContext("VertexBufferObject.upload(ByteBuffer)");
        getBufferStrategy().bufferData(handle, data, usage.getGLConstant());
        this.size = data.limit();
    }

    public void upload(long ptr, long dataSize, long maxSize) {
        api().assertGLContext("VertexBufferObject.upload(ptr)");
        if (GLRuntimeFlags.VBO_UPLOAD_STRATEGY == GLRuntimeFlags.VBOUploadStrategy.PERSISTENT) {
            if (mappedAddress == MemoryUtil.NULL || size < maxSize) {
                mapPersistent(maxSize);
            }
            if (mappedAddress != MemoryUtil.NULL) {
                // Ensure previous GPU consumption of this mapped region has completed.
                if (writeFence != 0L) {
                    api().clientWaitSync(writeFence, 1_000_000L);
                    api().deleteFenceSync(writeFence);
                    writeFence = 0L;
                }

                MemoryUtil.memCopy(ptr, mappedAddress, dataSize);
                if (!GLRuntimeFlags.VBO_PERSISTENT_COHERENT) {
                    api().flushMappedBufferRange(handle, 0, dataSize);
                }
                // Memory barrier for client mapped buffer
                api().memoryBarrier(GL44.GL_CLIENT_MAPPED_BUFFER_BARRIER_BIT);
                writeFence = api().createFenceSync();
                this.size = Math.max(this.size, maxSize);
                return;
            }
        }
        getBufferStrategy().bufferData(handle, dataSize, ptr, usage.getGLConstant());
        this.size = maxSize;
    }

    public void uploadSubData(long offset, ByteBuffer data) {
        api().assertGLContext("VertexBufferObject.uploadSubData");
        getBufferStrategy().bufferSubData(handle, offset, data);
    }

    public void ensureCapacity(long requiredSize) {
        api().assertGLContext("VertexBufferObject.ensureCapacity");
        if (this.size < requiredSize) {
            getBufferStrategy().bufferData(handle, requiredSize, MemoryUtil.NULL, usage.getGLConstant());
            this.size = requiredSize;
        }
    }

    public long mapPersistent(long capacity) {
        api().assertGLContext("VertexBufferObject.mapPersistent");
        int storageFlags = GL44.GL_MAP_WRITE_BIT | GL44.GL_MAP_PERSISTENT_BIT
                | (GLRuntimeFlags.VBO_PERSISTENT_COHERENT ? GL44.GL_MAP_COHERENT_BIT : 0);
        int mapFlags = GL44.GL_MAP_WRITE_BIT | GL44.GL_MAP_PERSISTENT_BIT
                | (GLRuntimeFlags.VBO_PERSISTENT_COHERENT ? GL44.GL_MAP_COHERENT_BIT : GL44.GL_MAP_FLUSH_EXPLICIT_BIT);

        getBufferStrategy().bufferStorage(handle, capacity, MemoryUtil.NULL, storageFlags);
        ByteBuffer mapped = getBufferStrategy().mapBufferRange(handle, GL15.GL_ARRAY_BUFFER, 0, capacity, mapFlags);
        if (mapped != null) {
            mappedAddress = MemoryUtil.memAddress(mapped);
            persistentEnabled = true;
            this.size = capacity;
        } else {
            mappedAddress = MemoryUtil.NULL;
        }
        return mappedAddress;
    }

    public void unmap() {
        api().assertGLContext("VertexBufferObject.unmap");
        if (mappedAddress != MemoryUtil.NULL) {
            getBufferStrategy().unmapBuffer(handle, GL15.GL_ARRAY_BUFFER);
            mappedAddress = MemoryUtil.NULL;
            persistentEnabled = false;
        }
    }

    public long getMappedAddress() {
        return mappedAddress;
    }

    @Override
    public int getHandle() {
        return handle;
    }

    @Override
    public void dispose() {
        api().assertGLContext("VertexBufferObject.dispose");
        if (!disposed) {
            if (writeFence != 0L) {
                api().deleteFenceSync(writeFence);
                writeFence = 0L;
            }
            if (mappedAddress != MemoryUtil.NULL) {
                try {
                    unmap();
                } catch (Exception ignored) {
                }
            }
            getBufferStrategy().deleteBuffer(handle);
            disposed = true;
        }
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }
}
