package rogo.sketchrender.vertexbuffer.attribute;

import org.lwjgl.opengl.GL11;

public class UByteVertex extends Vertex {

    public UByteVertex(int index, String name, int size) {
        super(index, name, size);
    }

    public static UByteVertex size1(int index, String name) {
        return new UByteVertex(index, name, 1);
    }

    public static UByteVertex size2(int index, String name) {
        return new UByteVertex(index, name, 2);
    }

    public static UByteVertex size3(int index, String name) {
        return new UByteVertex(index, name, 3);
    }

    public static UByteVertex size4(int index, String name) {
        return new UByteVertex(index, name, 4);
    }

    @Override
    public int size() {
        return Byte.BYTES;
    }

    @Override
    public int glType() {
        return GL11.GL_UNSIGNED_BYTE;
    }
}