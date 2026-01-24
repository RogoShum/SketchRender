package rogo.sketch.render.data.builder;

import rogo.sketch.render.data.DataType;
import rogo.sketch.render.data.PrimitiveType;
import rogo.sketch.render.data.builder.processor.VertexSorterProcessor;
import rogo.sketch.render.data.format.DataElement;
import rogo.sketch.render.data.format.DataFormat;

import java.util.ArrayList;
import java.util.List;

/**
 * A modernization of the vertex filling pipeline.
 * Acts as a lightweight wrapper around a DataBufferWriter to provide vertex-level semantics,
 * sorting, and format-aware data submission.
 */
public class VertexDataBuilder {
    
    // Core dependencies
    private final DataBufferWriter writer;
    private final DataFormat format; // Used for structure knowledge (offsets, padding), NOT for buffer creation
    private final PrimitiveType primitiveType;
    private boolean markedAsInstanced;
    
    // State
    private int vertexCount = 0;
    private int elementIndex = 0;
    private DataElement currentElement;
    
    // Processors chain
    private final List<VertexProcessor> processors = new ArrayList<>();
    
    // Components (Lazy loaded)
    private MemoryBufferWriter sortStagingBuffer; // Staging buffer for CPU sorting if needed
    
    /**
     * Creates a builder wrapping an existing writer.
     * @param writer The target writer (Memory or Address).
     * @param format The data format describing the vertex layout.
     * @param primitiveType The primitive topology.
     */
    public VertexDataBuilder(DataBufferWriter writer, DataFormat format, PrimitiveType primitiveType) {
        this.writer = writer;
        this.format = format;
        this.primitiveType = primitiveType;
        if (format.getElementCount() > 0) {
            this.currentElement = format.getElement(0);
        }
    }

    /**
     * Creates a builder with a default growable memory writer (Heap ByteBuffer).
     */
    public VertexDataBuilder(DataFormat format, PrimitiveType primitiveType) {
        this(new MemoryBufferWriter(4096), format, primitiveType);
    }

    // ===== Configuration =====

    public VertexDataBuilder enableSorting(VertexSorting strategy) {
        if (!primitiveType.supportsSorting()) {
            // Sorting not supported for this primitive type
            return this;
        }
        
        // Ensure staging buffer exists
        if (this.sortStagingBuffer == null) {
            this.sortStagingBuffer = new MemoryBufferWriter(4096);
        }
        
        // Add sorting processor
        // Note: The Sorter Processor needs to know about staging buffer.
        // In this design, if sorting is enabled, we WRITE to staging buffer, then Processor flushes to target.
        processors.add(new VertexSorterProcessor(strategy, primitiveType));
        
        return this;
    }
    
    public VertexDataBuilder addProcessor(VertexProcessor processor) {
        processors.add(processor);
        return this;
    }

    // ===== Vertex Submission =====

    /**
     * Signals the start of a vertex. 
     */
    public VertexDataBuilder vertex() {
        for (VertexProcessor p : processors) p.onStartVertex(this, vertexCount);
        elementIndex = 0;
        if (format.getElementCount() > 0) {
            currentElement = format.getElement(0);
        }
        return this;
    }

    /**
     * Signals the end of a vertex.
     * Advances the vertex count.
     */
    public VertexDataBuilder endVertex() {
        for (VertexProcessor p : processors) p.onEndVertex(this, vertexCount);
        vertexCount++;
        elementIndex = 0;
        if (format.getElementCount() > 0) {
            currentElement = format.getElement(0);
        }

        return this;
    }
    
    private void nextElement() {
        elementIndex++;
        if (elementIndex < format.getElementCount()) {
            currentElement = format.getElement(elementIndex);
        } else {
            currentElement = null; // Overflow or end of vertex
        }
    }

    // ===== Data Putting (Delegates to Writer) =====
    
    private DataBufferWriter activeWriter() {
        // If we have a staging buffer (e.g. for sorting), we write there.
        // Otherwise write directly to target.
        // But VertexSorterProcessor logic implies it takes data FROM staging and puts to TARGET.
        // So while building, we must write to staging.
        return sortStagingBuffer != null ? sortStagingBuffer : writer;
    }

    // Raw Put methods (Manual control)
    public VertexDataBuilder putFloat(float f) { activeWriter().putFloat(f); return this; }
    public VertexDataBuilder putInt(int i) { activeWriter().putInt(i); return this; }
    public VertexDataBuilder putUInt(int i) { activeWriter().putUInt(i); return this; }
    public VertexDataBuilder putByte(byte b) { activeWriter().putByte(b); return this; }
    public VertexDataBuilder putUByte(byte b) { activeWriter().putUByte(b); return this; }
    public VertexDataBuilder putShort(short s) { activeWriter().putShort(s); return this; }
    public VertexDataBuilder putUShort(short s) { activeWriter().putUShort(s); return this; }
    public VertexDataBuilder putDouble(double d) { activeWriter().putDouble(d); return this; }
    
    public VertexDataBuilder putVec2(float x, float y) { activeWriter().putVec2(x, y); return this; }
    public VertexDataBuilder putVec3(float x, float y, float z) { activeWriter().putVec3(x, y, z); return this; }
    public VertexDataBuilder putVec4(float x, float y, float z, float w) { activeWriter().putVec4(x, y, z, w); return this; }
    
    public VertexDataBuilder putColor(float r, float g, float b, float a) { activeWriter().putVec4(r, g, b, a); return this; }
    public VertexDataBuilder putColorbyte(int r, int g, int b, int a) { activeWriter().putVec4ub(r, g, b, a); return this; }

    // Smart Put methods (Format-aware)
    
    /**
     * Put a float value, automatically converting to the current element's type.
     * Advances to the next element.
     */
    public VertexDataBuilder put(float f) {
        if (currentElement == null) return putFloat(f); // Fallback to raw float
        
        DataType type = currentElement.getDataType();
        boolean norm = currentElement.isNormalized();
        
        switch (type) {
            case FLOAT -> activeWriter().putFloat(f);
            case BYTE -> activeWriter().putByte(norm ? (byte)(f * 127.0f) : (byte)f);
            case UBYTE -> activeWriter().putUByte(norm ? (byte)(f * 255.0f) : (byte)f);
            case SHORT -> activeWriter().putShort(norm ? (short)(f * 32767.0f) : (short)f);
            case USHORT -> activeWriter().putUShort(norm ? (short)(f * 65535.0f) : (short)f);
            case INT -> activeWriter().putInt((int)f);
            default -> activeWriter().putFloat(f); // Fallback
        }
        
        nextElement();
        return this;
    }
    
    /**
     * Put 2 floats, converting to current element type (e.g. Vec2 -> Vec2S).
     */
    public VertexDataBuilder put(float x, float y) {
        if (currentElement == null) return putVec2(x, y);
        
        DataType type = currentElement.getDataType();
        boolean norm = currentElement.isNormalized();
        
        switch (type) {
            case VEC2F -> activeWriter().putVec2(x, y);
            case VEC2S -> {
                short sx = norm ? (short)(x * 32767.0f) : (short)x;
                short sy = norm ? (short)(y * 32767.0f) : (short)y;
                activeWriter().putShort(sx);
                activeWriter().putShort(sy);
            }
            case VEC2B -> {
                byte bx = norm ? (byte)(x * 127.0f) : (byte)x;
                byte by = norm ? (byte)(y * 127.0f) : (byte)y;
                activeWriter().putByte(bx);
                activeWriter().putByte(by);
            }
            // Add other cases as needed
            default -> {
                // If scalar types are expected sequentially?
                // For simplicity, we assume element is vector.
                activeWriter().putVec2(x, y);
            }
        }
        nextElement();
        return this;
    }
    
    /**
     * Put 3 floats (Position/Normal/Color), converting to current element type.
     */
    public VertexDataBuilder put(float x, float y, float z) {
        if (currentElement == null) return putVec3(x, y, z);
        
        DataType type = currentElement.getDataType();
        boolean norm = currentElement.isNormalized();
        
        switch (type) {
            case VEC3F -> activeWriter().putVec3(x, y, z);
            case VEC3S -> {
                short sx = norm ? (short)(x * 32767.0f) : (short)x;
                short sy = norm ? (short)(y * 32767.0f) : (short)y;
                short sz = norm ? (short)(z * 32767.0f) : (short)z;
                activeWriter().putShort(sx); activeWriter().putShort(sy); activeWriter().putShort(sz);
            }
            case VEC3B -> {
                byte bx = norm ? (byte)(x * 127.0f) : (byte)x;
                byte by = norm ? (byte)(y * 127.0f) : (byte)y;
                byte bz = norm ? (byte)(z * 127.0f) : (byte)z;
                activeWriter().putByte(bx); activeWriter().putByte(by); activeWriter().putByte(bz);
            }
            default -> activeWriter().putVec3(x, y, z);
        }
        nextElement();
        return this;
    }

    // ===== Flush / Finish =====

    /**
     * Flushes data to the underlying writer.
     * If sorting is enabled, this triggers the sort-and-write operation via processors.
     */
    public void finish() {
        // Notify start of finish phase? Or just let processors handle it.
        // SorterProcessor needs to flush Staging -> Target.
        
        boolean processed = false;
        for (VertexProcessor p : processors) {
            if (p instanceof VertexSorterProcessor sorter && sortStagingBuffer != null) {
                // Sorter handles the flush from Staging to Target
                sorter.processAndFlush(sortStagingBuffer, writer, format.getStride(), format);
                processed = true;
            } else {
                p.onFinish(this);
            }
        }

        vertexCount = 0;
    }
    
    public void reset() {
        vertexCount = 0;
        if (sortStagingBuffer != null) {
            sortStagingBuffer.getBuffer().clear();
        }
    }

    // ===== Accessors =====
    
    public int getVertexCount() {
        return vertexCount;
    }
    
    public PrimitiveType getPrimitiveType() {
        return primitiveType;
    }
    
    public DataBufferWriter getWriter() {
        return activeWriter();
    }
    
    public DataFormat getFormat() {
        return format;
    }

    public boolean isMarkedAsInstanced() {
        return markedAsInstanced;
    }

    public VertexDataBuilder setMarkedAsInstanced(boolean markedAsInstanced) {
        this.markedAsInstanced = markedAsInstanced;
        return this;
    }
}