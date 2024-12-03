package rogo.sketchrender.vertexbuffer.attribute;

import com.mojang.blaze3d.vertex.VertexFormatElement;

public class UnsignedIntegerVertex extends GLVertex {

    public UnsignedIntegerVertex(int index, String name, int size) {
        super(index, name, size);
    }

    public static UnsignedIntegerVertex size1(int index, String name) {
        return new UnsignedIntegerVertex(index, name, 1);
    }

    public static UnsignedIntegerVertex size2(int index, String name) {
        return new UnsignedIntegerVertex(index, name, 2);
    }

    public static UnsignedIntegerVertex size3(int index, String name) {
        return new UnsignedIntegerVertex(index, name, 3);
    }

    public static UnsignedIntegerVertex size4(int index, String name) {
        return new UnsignedIntegerVertex(index, name, 4);
    }

    @Override
    public VertexFormatElement.Type elementType() {
        return VertexFormatElement.Type.UINT;
    }
}
