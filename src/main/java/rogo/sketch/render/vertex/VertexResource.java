package rogo.sketch.render.vertex;

import com.mojang.blaze3d.platform.GlStateManager;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;
import org.lwjgl.system.MemoryUtil;
import rogo.sketch.api.BufferResourceObject;
import rogo.sketch.render.data.filler.ByteBufferFiller;
import rogo.sketch.render.data.filler.DataFiller;
import rogo.sketch.render.data.filler.VertexFiller;
import rogo.sketch.render.data.format.DataElement;
import rogo.sketch.render.data.format.DataFormat;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;

public class VertexResource implements BufferResourceObject, AutoCloseable {
    // Static buffer for main vertex data
    private final int staticVBO;
    private final DataFormat staticFormat;
    private ByteBuffer staticBuffer;
    private int staticVertexCount;
    private final int staticBufferInitialSize;

    // Dynamic buffer for instance data (optional)
    private final int dynamicVBO;
    private final DataFormat dynamicFormat;
    private ByteBuffer dynamicBuffer;
    private int instanceCount;
    private final int dynamicBufferInitialSize;
    private final IndexBufferResource indexBuffer;

    private final int vao;
    private final DrawMode drawMode;
    private final int primitiveType;
    private final int staticUsage;

    // Reusable fillers for performance
    private VertexFiller reusableVertexFiller;
    private ByteBufferFiller reusableByteBufferFiller;
    private boolean useReusableFillers;
    private boolean disposed = false;

    public VertexResource(DataFormat staticFormat, DataFormat dynamicFormat, DrawMode drawMode, int primitiveType, int staticUsage) {
        this.staticFormat = staticFormat;
        this.dynamicFormat = dynamicFormat;
        this.drawMode = drawMode;
        this.dynamicBufferInitialSize = 64;
        this.staticBufferInitialSize = 1024; // Initial capacity for ~1024 vertices
        this.primitiveType = primitiveType;
        this.staticUsage = staticUsage;
        this.useReusableFillers = true; // Enable by default for better performance

        // Create OpenGL objects
        this.vao = GL30.glGenVertexArrays();
        this.staticVBO = GL15.glGenBuffers();
        this.dynamicVBO = dynamicFormat != null ? GL15.glGenBuffers() : 0;

        // Index buffer is enabled by default
        this.indexBuffer = new IndexBufferResource();

        // Initialize static buffer if needed
        if (staticFormat != null) {
            initializeStaticBuffer();
        }

        // Initialize dynamic buffer if needed
        if (dynamicFormat != null) {
            initializeDynamicBuffer();
        }

        setupVertexArray();
    }

    private void initializeStaticBuffer() {
        int bufferSize = staticBufferInitialSize * staticFormat.getStride();
        staticBuffer = MemoryUtil.memAlloc(bufferSize);
    }

    private void initializeDynamicBuffer() {
        dynamicBuffer = MemoryUtil.memAlloc(dynamicBufferInitialSize);

        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, dynamicVBO);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, dynamicBuffer.capacity(), GL15.GL_DYNAMIC_DRAW);
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
    }

    private void setupVertexArray() {
        GL30.glBindVertexArray(vao);

        // Setup static attributes
        if (staticFormat != null) {
            GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, staticVBO);
            setupAttributes(staticFormat, false);
        }

        // Setup dynamic (instance) attributes
        if (dynamicFormat != null) {
            GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, dynamicVBO);
            setupAttributes(dynamicFormat, true);
        }

        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL30.glBindVertexArray(0);
    }

    private void setupAttributes(DataFormat format, boolean isInstance) {
        for (DataElement element : format.getElements()) {
            GL20.glVertexAttribPointer(
                    element.getIndex(),
                    element.getComponentCount(),
                    element.getGLType(),
                    element.isNormalized(),
                    format.getStride(),
                    element.getOffset()
            );
            GL20.glEnableVertexAttribArray(element.getIndex());

            // Set divisor for instanced attributes
            if (isInstance) {
                GL33.glVertexAttribDivisor(element.getIndex(), 1);
            }
        }
    }

    /**
     * Start filling static vertex data with automatic vertex counting
     */
    public VertexFiller beginFill() {
        if (useReusableFillers) {
            return beginFillReuse();
        } else {
            return beginFillNew();
        }
    }

    /**
     * Start filling using reusable buffers for better performance
     */
    public VertexFiller beginFillReuse() {
        if (staticFormat == null) {
            throw new IllegalStateException("No static format defined");
        }

        if (reusableVertexFiller == null) {
            // Create reusable filler with our static buffer
            reusableVertexFiller = new VertexFiller(staticFormat, staticBufferInitialSize);
            reusableVertexFiller.enableIndexBuffer();
        } else {
            // Clear existing data for reuse
            reusableVertexFiller.clear();
            reusableVertexFiller.enableIndexBuffer();
        }

        return reusableVertexFiller;
    }

    /**
     * Start filling with new buffers (legacy behavior)
     */
    public VertexFiller beginFillNew() {
        if (staticFormat == null) {
            throw new IllegalStateException("No static format defined");
        }

        VertexFiller newFiller = new VertexFiller(staticFormat);
        newFiller.enableIndexBuffer();
        return newFiller;
    }

    /**
     * Start filling using a ByteBuffer approach for direct control
     */
    public ByteBufferFiller beginFillDirect() {
        if (staticFormat == null) {
            throw new IllegalStateException("No static format defined");
        }

        ensureStaticCapacity(staticBufferInitialSize);

        if (reusableByteBufferFiller == null) {
            reusableByteBufferFiller = ByteBufferFiller.wrap(staticFormat, staticBuffer);
        } else {
            reusableByteBufferFiller.reset();
        }

        return reusableByteBufferFiller;
    }

    /**
     * Ensure static buffer has enough capacity
     */
    private void ensureStaticCapacity(int requiredVertexCount) {
        int requiredBytes = requiredVertexCount * staticFormat.getStride();
        
        if (staticBuffer == null || staticBuffer.capacity() < requiredBytes) {
            // Need to expand buffer
            int newCapacity = Math.max(
                staticBuffer != null ? staticBuffer.capacity() * 2 : requiredBytes,
                requiredBytes
            );

            ByteBuffer newBuffer = MemoryUtil.memAlloc(newCapacity);
            
            if (staticBuffer != null) {
                MemoryUtil.memFree(staticBuffer);
            }
            
            staticBuffer = newBuffer;
            staticBuffer.clear();
        }
    }

    /**
     * Finish filling and upload data to GPU
     */
    public void endFill() {
        endFill(reusableVertexFiller);
    }

    /**
     * Finish filling with a specific filler and upload data to GPU
     */
    public void endFill(VertexFiller filler) {
        if (filler == null) {
            throw new IllegalStateException("No active filler provided");
        }

        VertexFiller.VertexFillerResult result = filler.finish();

        // Upload vertex data
        this.staticVertexCount = result.getVertexCount();

        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, staticVBO);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, result.getVertexBuffer(), this.staticUsage);
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);

        // Upload index data
        if (result.hasIndices()) {
            indexBuffer.upload(result.getIndexType());
        }

        // Clean up only if not using reusable filler
        result.dispose();
        if (filler != reusableVertexFiller) {
            filler.dispose();
        }
    }

    /**
     * Finish filling with ByteBufferFiller and upload data to GPU
     */
    public void endFillDirect(ByteBufferFiller filler, int vertexCount) {
        if (filler == null) {
            throw new IllegalStateException("No filler provided");
        }

        this.staticVertexCount = vertexCount;
        
        ByteBuffer buffer = filler.prepareForReading();
        
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, staticVBO);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buffer, this.staticUsage);
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        
        // Reset buffer for next use
        buffer.clear();
    }

    /**
     * Add instance data (for dynamic/instanced attributes)
     */
    public DataFiller addInstance() {
        if (dynamicFormat == null) {
            throw new IllegalStateException("No dynamic format defined");
        }

        ensureDynamicCapacity();

        // Create a slice of the dynamic buffer for this instance
        int instanceSize = dynamicFormat.getStride();
        int currentPos = dynamicBuffer.position();

        ByteBuffer instanceBuffer = dynamicBuffer.slice();
        instanceBuffer.limit(instanceSize);

        // Move the main buffer position forward
        dynamicBuffer.position(currentPos + instanceSize);

        return ByteBufferFiller.wrap(dynamicFormat, instanceBuffer);
    }

    private void ensureDynamicCapacity() {
        int requiredSpace = dynamicFormat.getStride();

        if (dynamicBuffer.remaining() < requiredSpace) {
            // Need to expand buffer
            int newCapacity = Math.max(
                    dynamicBuffer.capacity() * 2,
                    dynamicBuffer.capacity() + requiredSpace
            );

            ByteBuffer newBuffer = MemoryUtil.memAlloc(newCapacity);

            // Copy existing data
            dynamicBuffer.flip();
            newBuffer.put(dynamicBuffer);

            MemoryUtil.memFree(dynamicBuffer);
            dynamicBuffer = newBuffer;

            // Update GPU buffer
            GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, dynamicVBO);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, newBuffer.capacity(), GL15.GL_DYNAMIC_DRAW);
            GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        }
    }

    /**
     * Finish adding instances and upload dynamic data to GPU
     */
    public void endInstanceFill() {
        instanceCount = dynamicBuffer.position() / dynamicFormat.getStride();

        dynamicBuffer.flip();
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, dynamicVBO);
        GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0, dynamicBuffer);
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);

        dynamicBuffer.clear();
    }

    /**
     * Clear all instance data
     */
    public void clearInstances() {
        if (dynamicBuffer != null) {
            dynamicBuffer.clear();
        }
        instanceCount = 0;
    }

    /**
     * Bind the VAO
     */
    @Override
    public void bind() {
        GL30.glBindVertexArray(vao);

        // Bind index buffer if it has data
        if (indexBuffer != null && indexBuffer.getIndexCount() > 0) {
            indexBuffer.bind();
        }
    }

    /**
     * Unbind the VAO
     */
    @Override
    public void unbind() {
        GL30.glBindVertexArray(0);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
    }

    public DataFormat getStaticFormat() {
        return staticFormat;
    }

    public DataFormat getDynamicFormat() {
        return dynamicFormat;
    }

    public int getStaticVertexCount() {
        return staticVertexCount;
    }

    public int getInstanceCount() {
        return instanceCount;
    }

    public int getVAO() {
        return vao;
    }

    public int getStaticVBO() {
        return staticVBO;
    }

    @Nullable
    public int getDynamicVBO() {
        return dynamicVBO;
    }

    public DrawMode getDrawMode() {
        return drawMode;
    }

    @Nullable
    public IndexBufferResource getIndexBuffer() {
        return indexBuffer;
    }

    /**
     * Check if this resource has index data
     */
    public boolean hasIndices() {
        return indexBuffer != null && indexBuffer.getIndexCount() > 0;
    }

    /**
     * Check if this resource has instance data
     */
    public boolean hasInstances() {
        return dynamicFormat != null && instanceCount > 0;
    }

    /**
     * Check if static format is compatible with another format
     */
    public boolean isStaticCompatibleWith(DataFormat otherFormat) {
        return staticFormat != null && staticFormat.isCompatibleWith(otherFormat);
    }

    /**
     * Check if dynamic format is compatible with another format
     */
    public boolean isDynamicCompatibleWith(DataFormat otherFormat) {
        return dynamicFormat != null && dynamicFormat.isCompatibleWith(otherFormat);
    }

    public int getPrimitiveType() {
        return primitiveType;
    }

    /**
     * Enable or disable buffer reuse optimization
     */
    public void setBufferReuseEnabled(boolean enabled) {
        this.useReusableFillers = enabled;
    }

    /**
     * Check if buffer reuse is enabled
     */
    public boolean isBufferReuseEnabled() {
        return useReusableFillers;
    }

    /**
     * Get the current static buffer capacity in vertices
     */
    public int getStaticBufferCapacity() {
        if (staticBuffer == null || staticFormat == null) {
            return 0;
        }
        return staticBuffer.capacity() / staticFormat.getStride();
    }

    /**
     * Pre-allocate static buffer for a specific number of vertices
     */
    public void preAllocateStaticBuffer(int vertexCount) {
        if (staticFormat != null) {
            ensureStaticCapacity(vertexCount);
        }
    }

    @Override
    public int getHandle() {
        return vao;
    }

    @Override
    public void dispose() {
        if (staticBuffer != null) {
            MemoryUtil.memFree(staticBuffer);
            staticBuffer = null;
        }

        if (dynamicBuffer != null) {
            MemoryUtil.memFree(dynamicBuffer);
            dynamicBuffer = null;
        }

        if (indexBuffer != null) {
            indexBuffer.dispose();
        }

        if (staticVBO > 0) {
            GL15.glDeleteBuffers(staticVBO);
        }

        if (dynamicVBO > 0) {
            GL15.glDeleteBuffers(dynamicVBO);
        }

        if (vao > 0) {
            GL30.glDeleteVertexArrays(vao);
        }

        disposed = true;
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }

    @Override
    public void close() {
        dispose();
    }

    /**
     * Create a never changed static vertex resource
     */
    public static VertexResource createStatic(DataFormat format, int primitiveType) {
        return new VertexResource(format, null, DrawMode.NORMAL, primitiveType, GL15.GL_STATIC_DRAW);
    }

    /**
     * Create an often changed dynamic vertex resource
     */
    public static VertexResource createDynamic(DataFormat format, int primitiveType) {
        return new VertexResource(format, null, DrawMode.NORMAL, primitiveType, GL15.GL_DYNAMIC_DRAW);
    }

    /**
     * Create a vertex resource with both static and dynamic attributes for instanced rendering
     */
    public static VertexResource createInstanced(DataFormat staticFormat, DataFormat dynamicFormat, int primitiveType) {
        return new VertexResource(staticFormat, dynamicFormat, DrawMode.INSTANCED, primitiveType, GL15.GL_STATIC_DRAW);
    }
}