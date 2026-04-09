package rogo.sketch.backend.opengl;

import rogo.sketch.core.packet.RenderPacket;
import rogo.sketch.core.pipeline.GraphicsPipeline;
import rogo.sketch.core.pipeline.RenderContext;

@FunctionalInterface
public interface OpenGLPacketHandler {
    void execute(
            GraphicsPipeline<? extends RenderContext> pipeline,
            RenderPacket packet,
            RenderContext context,
            OpenGLFrameExecutor executor);
}
