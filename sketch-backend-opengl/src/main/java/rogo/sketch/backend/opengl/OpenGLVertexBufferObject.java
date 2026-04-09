package rogo.sketch.backend.opengl;

import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL44;
import org.lwjgl.system.MemoryUtil;
import rogo.sketch.core.api.BufferResourceObject;
import rogo.sketch.backend.opengl.driver.GLRuntimeFlags;
import rogo.sketch.backend.opengl.internal.IGLBufferStrategy;
import rogo.sketch.backend.opengl.internal.OpenGLRuntimeSupport;
import rogo.sketch.core.resource.descriptor.BufferUpdatePolicy;

import java.nio.ByteBuffer;

/**
 * Represents a single OpenGL Vertex Buffer Object (VBO).
 * Managed as a component of an OpenGL geometry binding (VAO).
 * Uses GraphicsAPI buffer strategy for DSA/Legacy abstraction.
 * <p>
 * VBO operations (create, upload, map) require a GL context but NOT necessarily
 * the main thread -- they can run on the render worker thread if GL_WORKER_ENABLED.
 * VAO operations (bind to VAO, setup attributes) still require the main thread.
 */
public class OpenGLVertexBufferObject implements BufferResourceObject {
    private final int handle;
    private final BufferUpdatePolicy updatePolicy;
    private long size;
    private boolean disposed = false;
    private long mappedAddress = MemoryUtil.NULL;
    private boolean persistentEnabled = false;
    private long writeFence = 0L;

    private static IGLBufferStrategy getBufferStrategy() {
        return OpenGLRuntimeSupport.bufferStrategy();
    }

    public OpenGLVertexBufferObject(BufferUpdatePolicy updatePolicy) {
        OpenGLRuntimeSupport.assertRenderContext("OpenGLVertexBufferObject.<init>");
        this.updatePolicy = updatePolicy;
        this.handle = getBufferStrategy().createBuffer();
        this.size = 0;
    }

    public OpenGLVertexBufferObject(int size, BufferUpdatePolicy updatePolicy) {
        this(updatePolicy);
        ensureCapacity(size);
    }

    public void bind() {
        OpenGLRuntimeSupport.assertRenderContext("OpenGLVertexBufferObject.bind");
        OpenGLRuntimeSupport.vertexArrayStrategy().bindVertexBuffer(handle);
    }

    public void unbind() {
        OpenGLRuntimeSupport.assertRenderContext("OpenGLVertexBufferObject.unbind");
        OpenGLRuntimeSupport.vertexArrayStrategy().bindVertexBuffer(0);
    }

    public void upload(ByteBuffer data) {
        OpenGLRuntimeSupport.assertRenderContext("OpenGLVertexBufferObject.upload(ByteBuffer)");
        getBufferStrategy().bufferData(handle, data, toGLUsage(updatePolicy));
        this.size = data.limit();
    }

    public void upload(long ptr, long dataSize, long maxSize) {
        OpenGLRuntimeSupport.assertRenderContext("OpenGLVertexBufferObject.upload(ptr)");
        if (GLRuntimeFlags.VBO_UPLOAD_STRATEGY == GLRuntimeFlags.VBOUploadStrategy.PERSISTENT) {
            if (mappedAddress == MemoryUtil.NULL || size < maxSize) {
                mapPersistent(maxSize);
            }
            if (mappedAddress != MemoryUtil.NULL) {
                // Ensure previous GPU consumption of this mapped region has completed.
                if (writeFence != 0L) {
                    OpenGLRuntimeSupport.clientWaitSync(writeFence, 1_000_000L);
                    OpenGLRuntimeSupport.deleteFenceSync(writeFence);
                    writeFence = 0L;
                }

                MemoryUtil.memCopy(ptr, mappedAddress, dataSize);
                if (!GLRuntimeFlags.VBO_PERSISTENT_COHERENT) {
                    OpenGLRuntimeSupport.flushMappedBufferRange(handle, 0, dataSize);
                }
                // Memory barrier for client mapped buffer
                org.lwjgl.opengl.GL42.glMemoryBarrier(GL44.GL_CLIENT_MAPPED_BUFFER_BARRIER_BIT);
                writeFence = OpenGLRuntimeSupport.createFenceSync();
                this.size = Math.max(this.size, maxSize);
                return;
            }
        }
        getBufferStrategy().bufferData(handle, dataSize, ptr, toGLUsage(updatePolicy));
        this.size = maxSize;
    }

    public void uploadSubData(long offset, ByteBuffer data) {
        OpenGLRuntimeSupport.assertRenderContext("OpenGLVertexBufferObject.uploadSubData");
        getBufferStrategy().bufferSubData(handle, offset, data);
    }

    public void ensureCapacity(long requiredSize) {
        OpenGLRuntimeSupport.assertRenderContext("OpenGLVertexBufferObject.ensureCapacity");
        if (this.size < requiredSize) {
            getBufferStrategy().bufferData(handle, requiredSize, MemoryUtil.NULL, toGLUsage(updatePolicy));
            this.size = requiredSize;
        }
    }

    public long mapPersistent(long capacity) {
        OpenGLRuntimeSupport.assertRenderContext("OpenGLVertexBufferObject.mapPersistent");
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
        OpenGLRuntimeSupport.assertRenderContext("OpenGLVertexBufferObject.unmap");
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
        OpenGLRuntimeSupport.assertRenderContext("OpenGLVertexBufferObject.dispose");
        if (!disposed) {
            if (writeFence != 0L) {
                OpenGLRuntimeSupport.deleteFenceSync(writeFence);
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

    private static int toGLUsage(BufferUpdatePolicy updatePolicy) {
        if (updatePolicy == null) {
            return GL15.GL_STATIC_DRAW;
        }
        return switch (updatePolicy) {
            case IMMUTABLE -> GL15.GL_STATIC_DRAW;
            case DYNAMIC -> GL15.GL_DYNAMIC_DRAW;
            case STREAM -> GL15.GL_STREAM_DRAW;
        };
    }
}

