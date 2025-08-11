package rogo.sketch.render.vertex;

import rogo.sketch.render.DrawCommand;

public record VertexResourcePair(VertexResource resource, DrawCommand drawCommand) {
    public static VertexResourcePair of(VertexResource resource, DrawCommand drawCommand) {
        return new VertexResourcePair(resource, drawCommand);
    }
}