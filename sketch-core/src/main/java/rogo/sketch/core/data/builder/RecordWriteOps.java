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

interface RecordWriteOps {
    RecordWriteOps put(float x);

    RecordWriteOps put(float x, float y);

    RecordWriteOps put(float x, float y, float z);

    RecordWriteOps put(float x, float y, float z, float w);

    RecordWriteOps put(int x);

    RecordWriteOps put(int x, int y);

    RecordWriteOps put(int x, int y, int z);

    RecordWriteOps put(int x, int y, int z, int w);

    RecordWriteOps put(short x);

    RecordWriteOps put(short x, short y);

    RecordWriteOps put(short x, short y, short z);

    RecordWriteOps put(short x, short y, short z, short w);

    RecordWriteOps put(byte x);

    RecordWriteOps put(byte x, byte y);

    RecordWriteOps put(byte x, byte y, byte z);

    RecordWriteOps put(byte x, byte y, byte z, byte w);

    RecordWriteOps put(Vector2fc v);

    RecordWriteOps put(Vector3fc v);

    RecordWriteOps put(Vector4fc v);

    RecordWriteOps put(Vector2ic v);

    RecordWriteOps put(Vector3ic v);

    RecordWriteOps put(Vector4ic v);

    RecordWriteOps put(Matrix4fc m);

    RecordWriteOps put(Matrix3fc m);

    RecordWriteOps put(Quaternionfc q);

    RecordWriteOps put(long val);

    RecordWriteOps put(float[] data);

    RecordWriteOps put(float[] data, int offset, int count);

    RecordWriteOps put(int[] data);

    RecordWriteOps put(int[] data, int offset, int count);

    RecordWriteOps put(short[] data);

    RecordWriteOps put(short[] data, int offset, int count);

    RecordWriteOps put(byte[] data);

    RecordWriteOps put(byte[] data, int offset, int count);
}

