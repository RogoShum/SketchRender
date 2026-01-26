package rogo.sketch.core.api.graphics;

import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.shader.ComputeShader;

import java.util.function.BiConsumer;

public interface DispatchProvider {
    BiConsumer<RenderContext, ComputeShader> getDispatchCommand();
}