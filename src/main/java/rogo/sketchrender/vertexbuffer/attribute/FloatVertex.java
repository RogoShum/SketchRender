package rogo.sketchrender.vertexbuffer.attribute;

import com.mojang.blaze3d.vertex.VertexFormatElement;

public class FloatVertex extends GLVertex {

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
    public VertexFormatElement.Type elementType() {
        return VertexFormatElement.Type.FLOAT;
    }
}
