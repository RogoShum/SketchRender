package rogo.sketchrender.vertexbuffer.attribute;

import com.mojang.blaze3d.vertex.VertexFormatElement;

public class ShortVertex extends GLVertex {

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
    public VertexFormatElement.Type elementType() {
        return VertexFormatElement.Type.SHORT;
    }
}
