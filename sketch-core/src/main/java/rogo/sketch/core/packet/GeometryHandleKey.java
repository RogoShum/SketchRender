package rogo.sketch.core.packet;

import rogo.sketch.core.data.format.VertexBufferKey;

import java.util.Objects;

public record GeometryHandleKey(VertexBufferKey vertexBufferKey) {
    public GeometryHandleKey {
        Objects.requireNonNull(vertexBufferKey, "vertexBufferKey");
    }

    public static GeometryHandleKey from(VertexBufferKey key) {
        return new GeometryHandleKey(key);
    }
}
