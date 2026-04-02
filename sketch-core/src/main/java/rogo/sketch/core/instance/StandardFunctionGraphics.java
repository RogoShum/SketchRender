package rogo.sketch.core.instance;

import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.util.KeyId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class StandardFunctionGraphics extends FunctionGraphics {
    private final Command[] commands;

    public StandardFunctionGraphics(KeyId keyId, Command[] commands) {
        super(keyId);
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

    @Override
    public void execute(RenderContext context) {
        // Standard function graphics no longer execute GL directly in core.
        // FunctionFlowStrategy converts the commands to explicit render packets.
    }

    public sealed interface Command permits ClearCommand, DrawBuffersCommand, GenMipmapCommand, BindRenderTargetCommand {
    }

    public static final class ClearCommand implements Command {
        private final KeyId renderTargetId;
        private final boolean clearColor;
        private final boolean clearDepth;
        private final float[] clearColorValue;
        private final float clearDepthValue;
        private final boolean[] colorMask;

        public ClearCommand(
                KeyId renderTargetId,
                boolean clearColor,
                boolean clearDepth,
                float[] clearColorValue,
                float clearDepthValue,
                boolean[] colorMask) {
            this.renderTargetId = renderTargetId;
            this.clearColor = clearColor;
            this.clearDepth = clearDepth;
            this.clearColorValue = clearColorValue != null ? clearColorValue.clone() : new float[]{0f, 0f, 0f, 0f};
            this.clearDepthValue = clearDepthValue;
            this.colorMask = colorMask != null ? colorMask.clone() : null;
        }

        public KeyId renderTargetId() {
            return renderTargetId;
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
    }

    public static final class DrawBuffersCommand implements Command {
        private final KeyId renderTargetId;
        private final List<Object> colorComponents;

        public DrawBuffersCommand(KeyId renderTargetId, List<Object> colorComponents) {
            this.renderTargetId = renderTargetId;
            this.colorComponents = colorComponents != null ? List.copyOf(colorComponents) : null;
        }

        public KeyId renderTargetId() {
            return renderTargetId;
        }

        public List<Object> colorComponents() {
            return colorComponents;
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

    public static final class BindRenderTargetCommand implements Command {
        private final KeyId renderTargetId;

        public BindRenderTargetCommand(KeyId renderTargetId) {
            this.renderTargetId = renderTargetId;
        }

        public KeyId renderTargetId() {
            return renderTargetId;
        }
    }
}
