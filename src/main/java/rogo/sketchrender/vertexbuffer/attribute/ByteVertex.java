package rogo.sketchrender.vertexbuffer.attribute;

import org.lwjgl.opengl.GL11;

public class ByteVertex extends Vertex {

    public ByteVertex(int index, String name, int size) {
        super(index, name, size);
    }

    public static ByteVertex size1(int index, String name) {
        return new ByteVertex(index, name, 1);
    }

    public static ByteVertex size2(int index, String name) {
        return new ByteVertex(index, name, 2);
    }

    public static ByteVertex size3(int index, String name) {
        return new ByteVertex(index, name, 3);
    }

    public static ByteVertex size4(int index, String name) {
        return new ByteVertex(index, name, 4);
    }

    @Override
    public int size() {
        return Byte.BYTES;
    }

    @Override
    public int glType() {
        return GL11.GL_BYTE;
    }
}