package rogo.sketchrender.vertexbuffer.attribute;

import org.lwjgl.opengl.GL11;

public class UShortVertex extends Vertex {

    public UShortVertex(int index, String name, int size) {
        super(index, name, size);
    }

    public static UShortVertex size1(int index, String name) {
        return new UShortVertex(index, name, 1);
    }

    public static UShortVertex size2(int index, String name) {
        return new UShortVertex(index, name, 2);
    }

    public static UShortVertex size3(int index, String name) {
        return new UShortVertex(index, name, 3);
    }

    public static UShortVertex size4(int index, String name) {
        return new UShortVertex(index, name, 4);
    }

    @Override
    public int size() {
        return Short.BYTES;
    }

    @Override
    public int glType() {
        return GL11.GL_UNSIGNED_SHORT;
    }
}