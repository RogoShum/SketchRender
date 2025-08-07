package rogo.sketchrender.vertexbuffer.attribute;

import org.lwjgl.opengl.GL11;

public class FloatVertex extends Vertex {

    public FloatVertex(int index, String name, int size) {
        super(index, name, size);
    }

    public static FloatVertex size1(int index, String name) {
        return new FloatVertex(index, name, 1);
    }

    public static FloatVertex size2(int index, String name) {
        return new FloatVertex(index, name, 2);
    }

    public static FloatVertex size3(int index, String name) {
        return new FloatVertex(index, name, 3);
    }

    public static FloatVertex size4(int index, String name) {
        return new FloatVertex(index, name, 4);
    }

    @Override
    public int size() {
        return Float.BYTES;
    }

    @Override
    public int glType() {
        return GL11.GL_FLOAT;
    }
}