package rogo.sketch.core.pipeline.flow.v2;

import rogo.sketch.core.util.KeyId;

import java.util.List;

public final class FunctionCommands {
    private FunctionCommands() {
    }

    public sealed interface Command permits ClearCommand, GenMipmapCommand {
    }

    public record ClearCommand(
            KeyId renderTargetId,
            List<Object> colorAttachments,
            boolean clearColor,
            boolean clearDepth,
            float[] clearColorValue,
            float clearDepthValue,
            boolean[] colorMask,
            boolean restorePreviousRenderTarget
    ) implements Command {
    }

    public record GenMipmapCommand(KeyId textureId) implements Command {
    }
}
