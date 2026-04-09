package rogo.sketch.core.data.builder;

import org.lwjgl.system.MemoryUtil;
import rogo.sketch.core.data.PrimitiveType;
import rogo.sketch.core.data.layout.StructLayout;

/**
 * Structured cursor specialized for sequential vertex/instance records.
 */
public class VertexWriteCursor extends StructuredWriteCursor {
    protected final PrimitiveType primitiveType;
    protected int vertexCount = 0;

    public VertexWriteCursor(StructLayout format, PrimitiveType primitiveType) {
        this(2097152L, format, primitiveType);
    }

    public VertexWriteCursor(long capacity, StructLayout format, PrimitiveType primitiveType) {
        this(MemoryUtil.nmemAlloc(capacity), capacity, format, primitiveType, false);
    }

    public VertexWriteCursor(long address, long capacity, StructLayout format, PrimitiveType primitiveType) {
        this(address, capacity, format, primitiveType, true);
    }

    protected VertexWriteCursor(long address, long capacity, StructLayout format, PrimitiveType primitiveType, boolean externalMemory) {
        super(address, capacity, format, externalMemory);
        this.primitiveType = primitiveType;
    }

    public PrimitiveType primitiveType() {
        return primitiveType;
    }

    public int getVertexCount() {
        return vertexCount;
    }

    @Override
    protected void endRecord() {
        endVertex();
    }

    protected void endVertex() {
        vertexCount++;
        elementIndex = 0;

        recordStartAddr = baseAddress + (long) vertexCount * stride;
        currentAddr = recordStartAddr;
        if (currentAddr + stride > limitAddr) {
            ensureCapacity(stride);
            recordStartAddr = baseAddress + (long) vertexCount * stride;
            currentAddr = recordStartAddr;
        }
    }

    @Override
    public void reset() {
        super.reset();
        this.vertexCount = 0;
    }
}

