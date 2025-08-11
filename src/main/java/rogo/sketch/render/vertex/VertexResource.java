package rogo.sketch.render.vertex;

import com.mojang.blaze3d.platform.GlStateManager;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;
import org.lwjgl.system.MemoryUtil;
import rogo.sketch.api.BufferResourceObject;
import rogo.sketch.render.data.format.DataElement;
import rogo.sketch.render.data.format.DataFormat;
import rogo.sketch.render.data.filler.ByteBufferFiller;
import rogo.sketch.render.data.filler.DataFiller;
import rogo.sketch.render.data.filler.VertexFiller;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;

public class VertexResource implements BufferResourceObject, AutoCloseable {

    // Static buffer for main vertex data
    private final int staticVBO;
    private final DataFormat staticFormat;
    private ByteBuffer staticBuffer;
    private int staticVertexCount;

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

    private VertexFiller currentFiller;

    public VertexResource(DataFormat staticFormat, DataFormat dynamicFormat, DrawMode drawMode, int primitiveType, int staticUsage) {
        this.staticFormat = staticFormat;
        this.dynamicFormat = dynamicFormat;
        this.drawMode = drawMode;
        this.dynamicBufferInitialSize = 64;
        this.primitiveType = primitiveType;
        this.staticUsage = staticUsage;

        // Create OpenGL objects
        this.vao = GL30.glGenVertexArrays();
        this.staticVBO = GL15.glGenBuffers();
        this.dynamicVBO = dynamicFormat != null ? GL15.glGenBuffers() : 0;

        // Index buffer is enabled by default
        this.indexBuffer = new IndexBufferResource();

        // Initialize dynamic buffer if needed
        if (dynamicFormat != null) {
            initializeDynamicBuffer();
        }

        setupVertexArray();
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
        if (staticFormat == null) {
            throw new IllegalStateException("No static format defined");
        }

        if (currentFiller != null) {
            currentFiller.dispose();
        }

        currentFiller = new VertexFiller(staticFormat);

        // Index buffer is always enabled by default
        currentFiller.enableIndexBuffer();

        return currentFiller;
    }

    /**
     * Finish filling and upload data to GPU
     */
    //TODO ?要改成重复利用的buffer
    public void endFill() {
        if (currentFiller == null) {
            throw new IllegalStateException("No active filler. Call beginFill() first.");
        }

        VertexFiller.VertexFillerResult result = currentFiller.finish();

        // Upload vertex data
        this.staticVertexCount = result.getVertexCount();

        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, staticVBO);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, result.getVertexBuffer(), this.staticUsage);
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);

        // Upload index data
        if (result.hasIndices()) {
            indexBuffer.upload(result.getIndexType());
        }

        // Clean up
        result.dispose();
        currentFiller.dispose();
        currentFiller = null;
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

    @Override
    public int getHandle() {
        return vao;
    }

    @Override
    public void dispose() {
        if (currentFiller != null) {
            currentFiller.dispose();
            currentFiller = null;
        }

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