package rogo.sketch.render.pipeline;

import org.joml.Matrix4f;
import rogo.sketch.api.graphics.Graphics;
import rogo.sketch.api.model.PreparedMesh;
import rogo.sketch.render.data.format.VertexBufferKey;

import javax.annotation.Nullable;

/**
 * A lightweight, immutable representation of an object to be rendered.
 * Contains all necessary data for batching and sorting, but no GPU offsets.
 */
public record RenderObject(
        Graphics instance,
        @Nullable PreparedMesh mesh,
        VertexBufferKey key,
        Matrix4f transform,
        long sortKey) {
    public boolean hasMesh() {
        return mesh != null;
    }

    public boolean isInstanced() {
        return key.hasInstancing();
    }
}
