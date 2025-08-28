package rogo.sketch.render.vertex;

import rogo.sketch.render.DrawCommand;
import rogo.sketch.render.resource.buffer.VertexResource;

public record VertexResourcePair(VertexResource resource, DrawCommand drawCommand) {
    public static VertexResourcePair of(VertexResource resource, DrawCommand drawCommand) {
        return new VertexResourcePair(resource, drawCommand);
    }
}