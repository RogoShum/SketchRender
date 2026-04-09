package rogo.sketch.core.data.builder;

import org.lwjgl.system.MemoryUtil;
import rogo.sketch.core.data.layout.FieldSpec;
import rogo.sketch.core.data.layout.StructLayout;
import rogo.sketch.core.data.type.ValueType;

/**
 * Format-aware record writer.
 * <p>
 * This layer understands layout, padding, alignment, and per-element type
 * expectations. It does not own high-level vertex sorting or backend upload.
 */
public abstract class StructuredWriteCursor extends NativeWriteBuffer {
    protected final StructLayout format;
    protected final int stride;
    protected final int elementCount;

    protected final ValueType[] elementTypes;
    protected final boolean[] elementNormalized;
    protected final int[] elementOffsets;
    protected final int[] elementComponentCounts;
    protected final int[] elementByteSizes;

    protected int elementIndex = 0;
    protected long recordStartAddr;

    protected StructuredWriteCursor(long capacity, StructLayout format) {
        this(MemoryUtil.nmemAlloc(capacity), capacity, format, false);
    }

    protected StructuredWriteCursor(long address, long capacity, StructLayout format, boolean externalMemory) {
        super(address, capacity, externalMemory);
        this.format = format;
        this.stride = format.getStride();
        this.elementCount = format.getElementCount();
        this.recordStartAddr = address;

        this.elementTypes = new ValueType[elementCount];
        this.elementNormalized = new boolean[elementCount];
        this.elementOffsets = new int[elementCount];
        this.elementComponentCounts = new int[elementCount];
        this.elementByteSizes = new int[elementCount];

        for (int i = 0; i < elementCount; i++) {
            FieldSpec field = format.getField(i);
            elementTypes[i] = field.getValueType();
            elementNormalized[i] = field.isNormalized();
            elementOffsets[i] = field.getOffset();
            elementComponentCounts[i] = field.getComponentCount();
            elementByteSizes[i] = field.getStride();
        }
    }

    public StructLayout getFormat() {
        return format;
    }

    public int getStride() {
        return stride;
    }

    public StructuredWriteCursor padding() {
        if (elementIndex >= elementCount) {
            return this;
        }

        long targetAddr = recordStartAddr + elementOffsets[elementIndex];
        long diff = targetAddr - currentAddr;
        if (diff > 0) {
            org.lwjgl.system.MemoryUtil.memSet(currentAddr, 0, diff);
            currentAddr = targetAddr;
            return this;
        }
        if (diff < 0) {
            throw new IllegalStateException("Memory overlap detected while advancing structured write cursor");
        }
        return this;
    }

    public void validateComplete() {
        if (elementIndex != 0) {
            throw new IllegalStateException(
                    "Incomplete record: filled " + elementIndex + "/" + elementCount + " elements");
        }
    }

    protected void checkMatch(int inputComponents) {
        if (elementIndex >= elementCount) {
            throw new IllegalStateException("Structured record overflow");
        }
        int expected = elementComponentCounts[elementIndex];
        if (expected != inputComponents) {
            throw new IllegalArgumentException(
                    "Component count mismatch at index " + elementIndex
                            + " (" + elementTypes[elementIndex] + "), expected "
                            + expected + " but got " + inputComponents);
        }
    }

    protected void checkMatch(ValueType expectedType) {
        if (elementIndex >= elementCount) {
            throw new IllegalStateException("Structured record overflow");
        }
        ValueType actual = elementTypes[elementIndex];
        if (actual != expectedType) {
            throw new IllegalArgumentException(
                    "Type mismatch at index " + elementIndex + ", expected " + expectedType + " but got " + actual);
        }
    }

    protected final void advance() {
        long elementStart = recordStartAddr + elementOffsets[elementIndex];
        long actualWritten = currentAddr - elementStart;
        int expectedSize = elementByteSizes[elementIndex];
        if (actualWritten != expectedSize) {
            throw new IllegalStateException(
                    "Data mismatch at element " + elementIndex
                            + " ('" + format.getField(elementIndex).getName() + "'), expected "
                            + expectedSize + " bytes but wrote " + actualWritten + " bytes");
        }

        elementIndex++;
        while (elementIndex < elementCount && format.getField(elementIndex).isPadding()) {
            padding();
            elementIndex++;
        }
        if (elementIndex >= elementCount) {
            endRecord();
        }
    }

    protected abstract void endRecord();

    @Override
    public void reset() {
        super.reset();
        this.elementIndex = 0;
        this.recordStartAddr = this.baseAddress;
    }
}

