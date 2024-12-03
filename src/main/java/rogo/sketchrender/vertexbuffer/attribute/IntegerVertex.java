package rogo.sketchrender.vertexbuffer.attribute;

import com.mojang.blaze3d.vertex.VertexFormatElement;

public class IntegerVertex extends GLVertex {

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
    public VertexFormatElement.Type elementType() {
        return VertexFormatElement.Type.INT;
    }
}
