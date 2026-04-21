package rogo.sketch.core.backend;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.packet.RenderPacket;
import rogo.sketch.core.pipeline.GraphicsPipeline;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.util.KeyId;

import java.util.List;

public record AsyncUploadRequest<C extends RenderContext>(
        GraphicsPipeline<C> pipeline,
        C renderContext,
        List<RenderPacket> packets,
        @Nullable KeyId resourceId,
        long epoch,
        @Nullable Runnable completionCallback
) {
}
