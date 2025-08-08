package rogo.sketch.render.vertexbuffer;

import com.google.common.collect.Lists;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.nio.Buffer;
import java.util.List;

public abstract class BufferBuilder<T extends Buffer> {
    protected final List<Number> list = Lists.newArrayList();
    protected int size;

    public void put(Matrix4f value) {
        put(value.m00());
        put(value.m01());
        put(value.m02());
        put(value.m03());
        put(value.m10());
        put(value.m11());
        put(value.m12());
        put(value.m13());
        put(value.m20());
        put(value.m21());
        put(value.m22());
        put(value.m23());
        put(value.m30());
        put(value.m31());
        put(value.m32());
        put(value.m33());
    }

    public void put(BufferBuilder<T> src) {
        list.addAll(src.list);
    }

    public void put(Vector2f vector2f) {
        put(vector2f.x);
        put(vector2f.y);
    }

    public void put(Vector3f vector3f) {
        put(vector3f.x);
        put(vector3f.y);
        put(vector3f.z);
    }

    public void put(float[] value) {
        for (float v : value) {
            put(v);
        }
    }

    public void put(Number value) {
        list.add(value);
        size += getSizeInBytes(value);
    }

    public static int getSizeInBytes(Number number) {
        if (number instanceof Byte) {
            return Byte.BYTES; // 1 byte
        } else if (number instanceof Short) {
            return Short.BYTES; // 2 bytes
        } else if (number instanceof Integer) {
            return Integer.BYTES; // 4 bytes
        } else if (number instanceof Long) {
            return Long.BYTES; // 8 bytes
        } else if (number instanceof Float) {
            return Float.BYTES; // 4 bytes
        } else if (number instanceof Double) {
            return Double.BYTES; // 8 bytes
        } else {
            throw new IllegalArgumentException("Unsupported number type: " + number.getClass());
        }
    }

    protected abstract T _buildBuffer();

    public T buildBuffer() {
        T buffer = _buildBuffer();
        list.clear();
        size = 0;
        return buffer;
    }

    public T buildFlipBuffer() {
        T buffer = buildBuffer();
        buffer.flip();
        return buffer;
    }

    public int size() {
        return size;
    }
}