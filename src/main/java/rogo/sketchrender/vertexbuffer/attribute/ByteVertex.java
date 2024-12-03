package rogo.sketchrender.vertexbuffer.attribute;

import com.mojang.blaze3d.vertex.VertexFormatElement;

public class ByteVertex extends GLVertex {

    public ByteVertex(int index, String name, int size) {
        super(index, name, size);
    }

    public static ByteVertex size1(int index, String name) {
        return new ByteVertex(index, name, 1);
    }

    public static ByteVertex size2(int index, String name) {
        return new ByteVertex(index, name, 2);
    }

    public static ByteVertex size3(int index, String name) {
        return new ByteVertex(index, name, 3);
    }

    public static ByteVertex size4(int index, String name) {
        return new ByteVertex(index, name, 4);
    }

    @Override
    public VertexFormatElement.Type elementType() {
        return VertexFormatElement.Type.BYTE;
    }
}
