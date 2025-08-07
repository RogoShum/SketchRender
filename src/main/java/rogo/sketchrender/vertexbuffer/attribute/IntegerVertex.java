package rogo.sketchrender.vertexbuffer.attribute;

import org.lwjgl.opengl.GL11;

public class IntegerVertex extends Vertex {

    public IntegerVertex(int index, String name, int size) {
        super(index, name, size);
    }

    public static IntegerVertex size1(int index, String name) {
        return new IntegerVertex(index, name, 1);
    }

    public static IntegerVertex size2(int index, String name) {
        return new IntegerVertex(index, name, 2);
    }

    public static IntegerVertex size3(int index, String name) {
        return new IntegerVertex(index, name, 3);
    }

    public static IntegerVertex size4(int index, String name) {
        return new IntegerVertex(index, name, 4);
    }

    @Override
    public int size() {
        return Integer.BYTES;
    }

    @Override
    public int glType() {
        return GL11.GL_INT;
    }
}