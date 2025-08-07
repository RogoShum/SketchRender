package rogo.sketchrender.vertexbuffer.attribute;

import org.lwjgl.opengl.GL11;

public class UIntegerVertex extends Vertex {

    public UIntegerVertex(int index, String name, int size) {
        super(index, name, size);
    }

    public static UIntegerVertex size1(int index, String name) {
        return new UIntegerVertex(index, name, 1);
    }

    public static UIntegerVertex size2(int index, String name) {
        return new UIntegerVertex(index, name, 2);
    }

    public static UIntegerVertex size3(int index, String name) {
        return new UIntegerVertex(index, name, 3);
    }

    public static UIntegerVertex size4(int index, String name) {
        return new UIntegerVertex(index, name, 4);
    }

    @Override
    public int size() {
        return Integer.BYTES;
    }

    @Override
    public int glType() {
        return GL11.GL_UNSIGNED_INT;
    }
}