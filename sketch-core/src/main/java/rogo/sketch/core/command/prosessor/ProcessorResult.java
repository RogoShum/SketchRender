package rogo.sketch.core.command.prosessor;

import rogo.sketch.core.pipeline.flow.RenderBatch;
import rogo.sketch.core.resource.buffer.VertexResource;

import java.util.Map;

public record ProcessorResult(VertexResource resource, Map<RenderBatch<?>, DrawRange> ranges) {
}