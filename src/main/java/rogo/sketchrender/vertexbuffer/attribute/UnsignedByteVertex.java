package rogo.sketchrender.vertexbuffer.attribute;

import com.mojang.blaze3d.vertex.VertexFormatElement;

public class UnsignedByteVertex extends GLVertex {

    public UnsignedByteVertex(int index, String name, int size) {
        super(index, name, size);
    }

    public static UnsignedByteVertex size1(int index, String name) {
        return new UnsignedByteVertex(index, name, 1);
    }

    public static UnsignedByteVertex size2(int index, String name) {
        return new UnsignedByteVertex(index, name, 2);
    }

    public static UnsignedByteVertex size3(int index, String name) {
        return new UnsignedByteVertex(index, name, 3);
    }

    public static UnsignedByteVertex size4(int index, String name) {
        return new UnsignedByteVertex(index, name, 4);
    }

    @Override
    public VertexFormatElement.Type elementType() {
        return VertexFormatElement.Type.UBYTE;
    }
}
