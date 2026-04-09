package rogo.sketch.core.instance;

import rogo.sketch.core.util.KeyId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class StandardFunctionGraphics extends FunctionGraphics {
    private final Command[] commands;

    public StandardFunctionGraphics(KeyId keyId, KeyId stageId, Command[] commands) {
        super(keyId, stageId);
        this.commands = commands != null ? commands.clone() : new Command[0];
    }

    public List<Command> commands() {
        if (commands.length == 0) {
            return List.of();
        }
        List<Command> copied = new ArrayList<>(commands.length);
        Collections.addAll(copied, commands);
        return copied;
    }

    public interface Command {
    }

    public static final class ClearCommand implements Command {
        private final KeyId renderTargetId;
        private final List<Object> colorAttachments;
        private final boolean clearColor;
        private final boolean clearDepth;
        private final float[] clearColorValue;
        private final float clearDepthValue;
        private final boolean[] colorMask;
        private final boolean restorePreviousRenderTarget;

        public ClearCommand(
                KeyId renderTargetId,
                List<Object> colorAttachments,
                boolean clearColor,
                boolean clearDepth,
                float[] clearColorValue,
                float clearDepthValue,
                boolean[] colorMask,
                boolean restorePreviousRenderTarget) {
            this.renderTargetId = renderTargetId;
            this.colorAttachments = colorAttachments != null ? List.copyOf(colorAttachments) : List.of();
            this.clearColor = clearColor;
            this.clearDepth = clearDepth;
            this.clearColorValue = clearColorValue != null ? clearColorValue.clone() : new float[]{0f, 0f, 0f, 0f};
            this.clearDepthValue = clearDepthValue;
            this.colorMask = colorMask != null ? colorMask.clone() : null;
            this.restorePreviousRenderTarget = restorePreviousRenderTarget;
        }

        public KeyId renderTargetId() {
            return renderTargetId;
        }

        public List<Object> colorAttachments() {
            return colorAttachments;
        }

        public boolean clearColor() {
            return clearColor;
        }

        public boolean clearDepth() {
            return clearDepth;
        }

        public float[] clearColorValue() {
            return clearColorValue.clone();
        }

        public float clearDepthValue() {
            return clearDepthValue;
        }

        public boolean[] colorMask() {
            return colorMask != null ? colorMask.clone() : null;
        }

        public boolean restorePreviousRenderTarget() {
            return restorePreviousRenderTarget;
        }
    }

    public static final class GenMipmapCommand implements Command {
        private final KeyId textureId;

        public GenMipmapCommand(KeyId textureId) {
            this.textureId = textureId;
        }

        public KeyId textureId() {
            return textureId;
        }
    }
}

