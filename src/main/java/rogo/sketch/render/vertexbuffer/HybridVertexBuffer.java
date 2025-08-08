package rogo.sketch.render.vertexbuffer;

import com.mojang.blaze3d.platform.GlStateManager;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL33;
import org.lwjgl.system.MemoryUtil;
import rogo.sketch.render.data.format.DataElement;
import rogo.sketch.render.data.format.DataFormat;
import rogo.sketch.render.vertexbuffer.filler.ByteBufferFiller;
import rogo.sketch.render.vertexbuffer.filler.DataFiller;

import java.nio.ByteBuffer;

/**
 * Hybrid vertex buffer supporting both static and dynamic (instanced) vertex attributes
 * Based on the original component design but modernized with DataFormat system
 */
public class HybridVertexBuffer implements AutoCloseable {
    // Static buffer for main vertex data
    private final int staticVBO;
    private final DataFormat staticFormat;
    private ByteBuffer staticBuffer;
    private int staticVertexCount;
    
    // Dynamic buffer for instance data
    private final int dynamicVBO;
    private final DataFormat dynamicFormat;
    private ByteBuffer dynamicBuffer;
    private int instanceCount;
    private final int dynamicBufferInitialSize;
    
    // Vertex Array Object
    private final int vao;
    
    // Drawing parameters
    private final int primitiveMode;
    private final DrawMode drawMode;
    
    public enum DrawMode {
        NORMAL,      // Regular drawing without instancing
        INSTANCED    // Instanced drawing with dynamic attributes
    }

    public HybridVertexBuffer(DataFormat staticFormat, DataFormat dynamicFormat, 
                             int primitiveMode, DrawMode drawMode) {
        this.staticFormat = staticFormat;
        this.dynamicFormat = dynamicFormat;
        this.primitiveMode = primitiveMode;
        this.drawMode = drawMode;
        this.dynamicBufferInitialSize = 64; // Initial size for dynamic buffer
        
        // Create OpenGL objects
        this.vao = GL30.glGenVertexArrays();
        this.staticVBO = GL15.glGenBuffers();
        this.dynamicVBO = dynamicFormat != null ? GL15.glGenBuffers() : 0;
        
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
     * Fill static vertex data
     */
    public DataFiller beginStaticFill(int vertexCount) {
        if (staticFormat == null) {
            throw new IllegalStateException("No static format defined");
        }
        
        int bufferSize = vertexCount * staticFormat.getStride();
        if (staticBuffer != null) {
            MemoryUtil.memFree(staticBuffer);
        }
        staticBuffer = MemoryUtil.memAlloc(bufferSize);
        
        return ByteBufferFiller.wrap(staticFormat, staticBuffer);
    }

    /**
     * Finish static data filling and upload to GPU
     */
    public void endStaticFill(int vertexCount) {
        this.staticVertexCount = vertexCount;
        
        staticBuffer.position(0);
        staticBuffer.limit(vertexCount * staticFormat.getStride());
        
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, staticVBO);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, staticBuffer, GL15.GL_STATIC_DRAW);
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        
        staticBuffer.clear();
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
     * Draw the vertex buffer
     */
    public void draw() {
        if (staticVertexCount == 0) {
            return;
        }
        
        GL30.glBindVertexArray(vao);
        
        if (drawMode == DrawMode.INSTANCED && instanceCount > 0) {
            GL31.glDrawArraysInstanced(primitiveMode, 0, staticVertexCount, instanceCount);
        } else {
            GL15.glDrawArrays(primitiveMode, 0, staticVertexCount);
        }
        
        GL30.glBindVertexArray(0);
    }

    /**
     * Draw with custom parameters
     */
    public void draw(int mode, int first, int count) {
        GL30.glBindVertexArray(vao);
        GL15.glDrawArrays(mode, first, count);
        GL30.glBindVertexArray(0);
    }

    /**
     * Draw with instancing and custom parameters
     */
    public void drawInstanced(int mode, int first, int count, int instanceCount) {
        GL30.glBindVertexArray(vao);
        GL31.glDrawArraysInstanced(mode, first, count, instanceCount);
        GL30.glBindVertexArray(0);
    }

    // Getters
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

    public int getDynamicVBO() {
        return dynamicVBO;
    }

    public DrawMode getDrawMode() {
        return drawMode;
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

    @Override
    public void close() {
        if (staticBuffer != null) {
            MemoryUtil.memFree(staticBuffer);
            staticBuffer = null;
        }
        
        if (dynamicBuffer != null) {
            MemoryUtil.memFree(dynamicBuffer);
            dynamicBuffer = null;
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

    /**
     * Create a hybrid vertex buffer for normal (non-instanced) rendering
     */
    public static HybridVertexBuffer createStatic(DataFormat format, int primitiveMode) {
        return new HybridVertexBuffer(format, null, primitiveMode, DrawMode.NORMAL);
    }

    /**
     * Create a hybrid vertex buffer with both static and dynamic attributes for instanced rendering
     */
    public static HybridVertexBuffer createHybrid(DataFormat staticFormat, DataFormat dynamicFormat, int primitiveMode) {
        return new HybridVertexBuffer(staticFormat, dynamicFormat, primitiveMode, DrawMode.INSTANCED);
    }
}
