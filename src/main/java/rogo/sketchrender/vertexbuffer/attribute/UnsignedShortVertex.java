package rogo.sketchrender.vertexbuffer.attribute;

import com.mojang.blaze3d.vertex.VertexFormatElement;

public class UnsignedShortVertex extends GLVertex {

    public UnsignedShortVertex(int index, String name, int size) {
        super(index, name, size);
    }

    public static UnsignedShortVertex size1(int index, String name) {
        return new UnsignedShortVertex(index, name, 1);
    }

    public static UnsignedShortVertex size2(int index, String name) {
        return new UnsignedShortVertex(index, name, 2);
    }

    public static UnsignedShortVertex size3(int index, String name) {
        return new UnsignedShortVertex(index, name, 3);
    }

    public static UnsignedShortVertex size4(int index, String name) {
        return new UnsignedShortVertex(index, name, 4);
    }

    @Override
    public VertexFormatElement.Type elementType() {
        return VertexFormatElement.Type.USHORT;
    }
}
