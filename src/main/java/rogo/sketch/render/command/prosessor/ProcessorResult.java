package rogo.sketch.render.command.prosessor;

import rogo.sketch.render.pipeline.RenderBatch;
import rogo.sketch.render.resource.buffer.VertexResource;

import java.util.Map;

public record ProcessorResult(VertexResource resource, Map<RenderBatch<?>, DrawRange> ranges) {
}