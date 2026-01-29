package rogo.sketch.core.data.builder;

import org.joml.*;

public interface VertexBuilder {
    VertexBuilder put(float x);

    VertexBuilder put(float x, float y);

    VertexBuilder put(float x, float y, float z);

    VertexBuilder put(float x, float y, float z, float w);

    VertexBuilder put(int x);

    VertexBuilder put(int x, int y);

    VertexBuilder put(int x, int y, int z);

    VertexBuilder put(int x, int y, int z, int w);

    VertexBuilder put(short x);

    VertexBuilder put(short x, short y);

    VertexBuilder put(short x, short y, short z);

    VertexBuilder put(short x, short y, short z, short w);

    VertexBuilder put(byte x);

    VertexBuilder put(byte x, byte y);

    VertexBuilder put(byte x, byte y, byte z);

    VertexBuilder put(byte x, byte y, byte z, byte w);

    VertexBuilder put(Vector2fc v);

    VertexBuilder put(Vector3fc v);

    VertexBuilder put(Vector4fc v);

    VertexBuilder put(Vector2ic v);

    VertexBuilder put(Vector3ic v);

    VertexBuilder put(Vector4ic v);

    VertexBuilder put(Matrix4fc m);

    VertexBuilder put(Matrix3fc m);

    VertexBuilder put(long val);

    VertexBuilder put(float[] data);

    VertexBuilder put(float[] data, int offset, int count);

    VertexBuilder put(int[] data);

    VertexBuilder put(int[] data, int offset, int count);

    VertexBuilder put(short[] data);

    VertexBuilder put(short[] data, int offset, int count);

    VertexBuilder put(byte[] data);

    VertexBuilder put(byte[] data, int offset, int count);
}