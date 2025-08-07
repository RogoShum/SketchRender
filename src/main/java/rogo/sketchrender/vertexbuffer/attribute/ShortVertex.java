package rogo.sketchrender.vertexbuffer.attribute;

import org.lwjgl.opengl.GL11;

public class ShortVertex extends Vertex {

    public ShortVertex(int index, String name, int size) {
        super(index, name, size);
    }

    public static ShortVertex size1(int index, String name) {
        return new ShortVertex(index, name, 1);
    }

    public static ShortVertex size2(int index, String name) {
        return new ShortVertex(index, name, 2);
    }

    public static ShortVertex size3(int index, String name) {
        return new ShortVertex(index, name, 3);
    }

    public static ShortVertex size4(int index, String name) {
        return new ShortVertex(index, name, 4);
    }

    @Override
    public int size() {
        return Short.BYTES;
    }

    @Override
    public int glType() {
        return GL11.GL_SHORT;
    }
}