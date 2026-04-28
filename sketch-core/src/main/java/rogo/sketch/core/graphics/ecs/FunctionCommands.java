package rogo.sketch.core.graphics.ecs;

import rogo.sketch.core.util.KeyId;

import java.util.List;

public final class FunctionCommands {
    private FunctionCommands() {
    }

    public sealed interface Command permits ClearCommand, CopyTextureCommand, GenMipmapCommand {
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
        public ClearCommand {
            colorAttachments = colorAttachments != null ? List.copyOf(colorAttachments) : List.of();
            clearColorValue = clearColorValue != null ? clearColorValue.clone() : new float[]{0f, 0f, 0f, 0f};
            colorMask = colorMask != null ? colorMask.clone() : null;
        }
    }

    public record GenMipmapCommand(KeyId textureId) implements Command {
    }

    public record CopyTextureCommand(
            KeyId sourceTextureId,
            KeyId destinationTextureId,
            int width,
            int height,
            boolean depthCopy
    ) implements Command {
    }
}
