package rogo.sketch.api.graphics;

import rogo.sketch.render.pipeline.RenderContext;
import rogo.sketch.render.shader.ComputeShader;

import java.util.function.BiConsumer;

public interface DispatchProvider {
    BiConsumer<RenderContext, ComputeShader> getDispatchCommand();
}