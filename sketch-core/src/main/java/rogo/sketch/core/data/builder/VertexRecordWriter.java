package rogo.sketch.core.data.builder;

import org.joml.Matrix3fc;
import org.joml.Matrix4fc;
import org.joml.Quaternionfc;
import org.joml.Vector2fc;
import org.joml.Vector2ic;
import org.joml.Vector3fc;
import org.joml.Vector3ic;
import org.joml.Vector4fc;
import org.joml.Vector4ic;
import rogo.sketch.core.data.PrimitiveType;
import rogo.sketch.core.data.layout.StructLayout;

/**
 * Public sequential record writer used by dynamic mesh and per-instance authoring.
 */
public class VertexRecordWriter extends StructuredRecordWriter {
    public VertexRecordWriter(StructLayout format, PrimitiveType primitiveType) {
        super(format, primitiveType);
    }

    public VertexRecordWriter(long capacity, StructLayout format, PrimitiveType primitiveType) {
        super(capacity, format, primitiveType);
    }

    public VertexRecordWriter(long address, long capacity, StructLayout format, PrimitiveType primitiveType) {
        super(address, capacity, format, primitiveType);
    }

    public VertexRecordWriter put(float x) {
        super.put(x);
        return this;
    }

    public VertexRecordWriter put(float x, float y) {
        super.put(x, y);
        return this;
    }

    public VertexRecordWriter put(float x, float y, float z) {
        super.put(x, y, z);
        return this;
    }

    public VertexRecordWriter put(float x, float y, float z, float w) {
        super.put(x, y, z, w);
        return this;
    }

    public VertexRecordWriter put(int x) {
        super.put(x);
        return this;
    }

    public VertexRecordWriter put(int x, int y) {
        super.put(x, y);
        return this;
    }

    public VertexRecordWriter put(int x, int y, int z) {
        super.put(x, y, z);
        return this;
    }

    public VertexRecordWriter put(int x, int y, int z, int w) {
        super.put(x, y, z, w);
        return this;
    }

    public VertexRecordWriter put(short x) {
        super.put(x);
        return this;
    }

    public VertexRecordWriter put(short x, short y) {
        super.put(x, y);
        return this;
    }

    public VertexRecordWriter put(short x, short y, short z) {
        super.put(x, y, z);
        return this;
    }

    public VertexRecordWriter put(short x, short y, short z, short w) {
        super.put(x, y, z, w);
        return this;
    }

    public VertexRecordWriter put(byte x) {
        super.put(x);
        return this;
    }

    public VertexRecordWriter put(byte x, byte y) {
        super.put(x, y);
        return this;
    }

    public VertexRecordWriter put(byte x, byte y, byte z) {
        super.put(x, y, z);
        return this;
    }

    public VertexRecordWriter put(byte x, byte y, byte z, byte w) {
        super.put(x, y, z, w);
        return this;
    }

    public VertexRecordWriter put(long value) {
        super.put(value);
        return this;
    }

    public VertexRecordWriter put(Vector2fc value) {
        super.put(value);
        return this;
    }

    public VertexRecordWriter put(Vector3fc value) {
        super.put(value);
        return this;
    }

    public VertexRecordWriter put(Vector4fc value) {
        super.put(value);
        return this;
    }

    public VertexRecordWriter put(Vector2ic value) {
        super.put(value);
        return this;
    }

    public VertexRecordWriter put(Vector3ic value) {
        super.put(value);
        return this;
    }

    public VertexRecordWriter put(Vector4ic value) {
        super.put(value);
        return this;
    }

    public VertexRecordWriter put(Matrix4fc value) {
        super.put(value);
        return this;
    }

    public VertexRecordWriter put(Matrix3fc value) {
        super.put(value);
        return this;
    }

    public VertexRecordWriter put(Quaternionfc value) {
        super.put(value);
        return this;
    }

    public VertexRecordWriter put(float[] data) {
        super.put(data);
        return this;
    }

    public VertexRecordWriter put(float[] data, int offset, int count) {
        super.put(data, offset, count);
        return this;
    }

    public VertexRecordWriter put(int[] data) {
        super.put(data);
        return this;
    }

    public VertexRecordWriter put(int[] data, int offset, int count) {
        super.put(data, offset, count);
        return this;
    }

    public VertexRecordWriter put(short[] data) {
        super.put(data);
        return this;
    }

    public VertexRecordWriter put(short[] data, int offset, int count) {
        super.put(data, offset, count);
        return this;
    }

    public VertexRecordWriter put(byte[] data) {
        super.put(data);
        return this;
    }

    public VertexRecordWriter put(byte[] data, int offset, int count) {
        super.put(data, offset, count);
        return this;
    }

    @Override
    public VertexRecordWriter snapshotCopy() {
        long writtenBytes = Math.max(getWriteOffset(), 1L);
        VertexRecordWriter copy = new VertexRecordWriter(writtenBytes, format, primitiveType);
        if (getWriteOffset() > 0L) {
            copy.putData(getBaseAddress(), (int) getWriteOffset());
        }
        copy.vertexCount = this.vertexCount;
        copy.elementIndex = this.elementIndex;
        copy.recordStartAddr = copy.getBaseAddress() + (long) copy.vertexCount * stride;
        return copy;
    }

}

