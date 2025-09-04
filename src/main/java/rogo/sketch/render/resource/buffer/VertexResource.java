package rogo.sketch.render.resource.buffer;

import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;
import org.lwjgl.system.MemoryUtil;
import rogo.sketch.api.BufferResourceObject;
import rogo.sketch.render.data.PrimitiveType;
import rogo.sketch.render.data.Usage;
import rogo.sketch.render.data.filler.ByteBufferFiller;
import rogo.sketch.render.data.filler.DataFiller;
import rogo.sketch.render.data.filler.VertexFiller;
import rogo.sketch.render.data.format.DataElement;
import rogo.sketch.render.data.format.DataFormat;
import rogo.sketch.render.vertex.DrawMode;

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
    private final PrimitiveType primitiveType;
    private final Usage staticUsage;

    private boolean disposed = false;

    public VertexResource(DataFormat staticFormat, DataFormat dynamicFormat, DrawMode drawMode, PrimitiveType primitiveType, Usage staticUsage) {
        this.staticFormat = staticFormat;
        this.dynamicFormat = dynamicFormat;
        this.drawMode = drawMode;
        this.dynamicBufferInitialSize = 64;
        this.staticBufferInitialSize = 1024; // Initial capacity for ~1024 vertices
        this.primitiveType = primitiveType;
        this.staticUsage = staticUsage;

        // Create OpenGL objects
        this.vao = GL30.glGenVertexArrays();
        this.staticVBO = GL15.glGenBuffers();
        this.dynamicVBO = dynamicFormat != null ? GL15.glGenBuffers() : 0;

        // Always create index buffer - it will be used based on primitive type
        if (primitiveType.requiresIndexBuffer()) {
            this.indexBuffer = new IndexBufferResource();
        } else {
            this.indexBuffer = null;
        }

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

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, dynamicVBO);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, dynamicBuffer.capacity(), GL15.GL_DYNAMIC_DRAW);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
    }

    private void setupVertexArray() {
        GL30.glBindVertexArray(vao);

        if (indexBuffer != null) {
            indexBuffer.bind();
        }

        // Setup static attributes
        if (staticFormat != null) {
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, staticVBO);
            setupAttributes(staticFormat, false);
        }

        // Setup dynamic (instance) attributes
        if (dynamicFormat != null) {
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, dynamicVBO);
            setupAttributes(dynamicFormat, true);
        }

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL30.glBindVertexArray(0);
    }

    private void setupAttributes(DataFormat format, boolean isInstance) {
        for (DataElement element : format.getElements()) {
            GL20.glEnableVertexAttribArray(element.getIndex());
            GL20.glVertexAttribPointer(
                    element.getIndex(),
                    element.getComponentCount(),
                    element.getGLType(),
                    element.isNormalized(),
                    format.getStride(),
                    element.getOffset()
            );

            // Set divisor for instanced attributes
            if (isInstance) {
                GL33.glVertexAttribDivisor(element.getIndex(), 1);
            }
        }
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
     * Upload vertex data from a VertexFiller to GPU
     * This is the main method for uploading filled vertex data
     */
    public void uploadFromVertexFiller(VertexFiller filler) {
        if (filler == null) {
            throw new IllegalArgumentException("VertexFiller cannot be null");
        }

        filler.end();

        // Get vertex data
        ByteBuffer vertexData = filler.getVertexData();
        if (vertexData != null) {
            uploadStaticData(vertexData, filler.getVertexCount());
        }

        // Generate and upload index data if needed
        if (filler.isUsingIndexBuffer() && indexBuffer != null) {
            generateIndices(filler);
        }
    }

    /**
     * Upload static vertex data directly
     */
    public void uploadStaticData(ByteBuffer vertexData, int vertexCount) {
        if (vertexData == null) {
            return;
        }

        this.staticVertexCount = vertexCount;

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, staticVBO);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vertexData, this.staticUsage.getGLConstant());
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
    }

    /**
     * Generate indices based on primitive type and upload to index buffer
     * Applies sorting if enabled in the VertexFiller
     */
    private void generateIndices(VertexFiller filler) {
        if (!filler.getPrimitiveType().equals(this.primitiveType)) {
            throw new IllegalStateException("primitiveType does not match");
        }

        int[] indices = primitiveType.generateIndices(filler.getVertexCount());

        // Clear existing indices
        indexBuffer.clear();

        // Add indices in natural order
        for (int index : indices) {
            indexBuffer.addIndex(index);
        }

        // Apply sorting if enabled (IndexBufferResource handles the sorting)
        if (filler.isSortingEnabled() && filler.getSorting() != null && this.primitiveType.supportsSorting()) {
            ByteBuffer vertexData = filler.getVertexData();
            indexBuffer.applySorting(
                    vertexData,
                    filler.getFormat(),
                    primitiveType,
                    filler.getVertexCount(),
                    filler.getSorting()
            );
        }
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
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, dynamicVBO);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, newBuffer.capacity(), GL15.GL_DYNAMIC_DRAW);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        }
    }

    /**
     * Finish adding instances and upload dynamic data to GPU
     */
    public void endInstanceFill() {
        instanceCount = dynamicBuffer.position() / dynamicFormat.getStride();

        dynamicBuffer.flip();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, dynamicVBO);
        GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0, dynamicBuffer);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);

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
     * Upload static vertex data from a VertexFiller
     */
    public void uploadStaticFromVertexFiller(VertexFiller filler) {
        if (filler == null) {
            throw new IllegalArgumentException("VertexFiller cannot be null");
        }

        filler.end();

        // Get vertex data
        ByteBuffer vertexData = filler.getVertexData();
        if (vertexData != null) {
            uploadStaticData(vertexData, filler.getVertexCount());
        }

        // Generate and upload index data if needed
        if (filler.isUsingIndexBuffer() && indexBuffer != null) {
            generateIndices(filler);
        }
    }
    
    /**
     * Upload dynamic vertex data from a VertexFiller (for instance data)
     */
    public void uploadDynamicFromVertexFiller(VertexFiller filler) {
        if (filler == null || dynamicFormat == null) {
            return;
        }

        filler.end();

        // Get vertex data
        ByteBuffer vertexData = filler.getVertexData();
        if (vertexData != null) {
            uploadDynamicData(vertexData, filler.getVertexCount());
        }
    }
    
    /**
     * Upload dynamic vertex data directly
     */
    public void uploadDynamicData(ByteBuffer vertexData, int instanceCount) {
        if (vertexData == null || dynamicFormat == null) {
            return;
        }

        this.instanceCount = instanceCount;

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, dynamicVBO);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vertexData, GL15.GL_DYNAMIC_DRAW);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
    }

    /**
     * Bind the VAO
     */
    @Override
    public void bind() {
        GL30.glBindVertexArray(vao);
    }

    /**
     * Unbind the VAO
     */
    @Override
    public void unbind() {
        GL30.glBindVertexArray(0);
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

    public int getDynamicVertexCount() {
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

    public PrimitiveType getPrimitiveType() {
        return primitiveType;
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
    public static VertexResource createStatic(DataFormat format, PrimitiveType primitiveType) {
        return new VertexResource(format, null, DrawMode.NORMAL, primitiveType, Usage.STATIC_DRAW);
    }

    /**
     * Create an often changed dynamic vertex resource
     */
    public static VertexResource createDynamic(DataFormat format, PrimitiveType primitiveType) {
        return new VertexResource(format, null, DrawMode.NORMAL, primitiveType, Usage.DYNAMIC_DRAW);
    }

    /**
     * Create a vertex resource with both static and dynamic attributes for instanced rendering
     */
    public static VertexResource createInstanced(DataFormat staticFormat, DataFormat dynamicFormat, PrimitiveType primitiveType) {
        return new VertexResource(staticFormat, dynamicFormat, DrawMode.INSTANCED, primitiveType, Usage.STATIC_DRAW);
    }
}