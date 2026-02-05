package rogo.sketch.core.instance;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import rogo.sketch.core.api.GpuObject;
import rogo.sketch.core.driver.GraphicsDriver;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.resource.GraphicsResourceManager;
import rogo.sketch.core.resource.RenderTarget;
import rogo.sketch.core.resource.ResourceTypes;
import rogo.sketch.core.resource.StandardRenderTarget;
import rogo.sketch.core.state.gl.ColorMaskState;
import rogo.sketch.core.util.KeyId;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

public class StandardFunctionGraphics extends FunctionGraphics {
    private final Command[] commands;

    public StandardFunctionGraphics(KeyId keyId, Command[] commands) {
        super(keyId);
        this.commands = commands;
    }

    @Override
    public void execute(RenderContext context) {
        for (Command command : commands) {
            command.execute(context);
        }
    }

    public interface Command {
        void execute(RenderContext context);
    }

    public static class ClearCommand implements Command {
        private final KeyId renderTargetId;
        private final boolean color, depth;
        private final float[] clearColor;
        private final float clearDepth;
        private final ColorMaskState colorMaskState;

        public ClearCommand(KeyId renderTargetId, boolean color, boolean depth, float[] clearColor, float clearDepth,
                ColorMaskState colorMaskState) {
            this.renderTargetId = renderTargetId;
            this.color = color;
            this.depth = depth;
            this.clearColor = clearColor;
            this.clearDepth = clearDepth;
            this.colorMaskState = colorMaskState;
        }

        @Override
        public void execute(RenderContext context) {
            GraphicsResourceManager.getInstance()
                    .getReference(ResourceTypes.RENDER_TARGET, renderTargetId)
                    .ifPresent(rt -> {
                        if (colorMaskState != null && context.renderStateManager() != null) {
                            context.renderStateManager().changeState(colorMaskState, context);
                        }
                        RenderTarget renderTarget = (RenderTarget) rt;
                        renderTarget.bind();
                        int mask = 0;
                        if (color) {
                            mask |= GL11.GL_COLOR_BUFFER_BIT;
                            GL11.glClearColor(clearColor[0], clearColor[1], clearColor[2], clearColor[3]);
                        }
                        if (depth) {
                            mask |= GL11.GL_DEPTH_BUFFER_BIT;
                            GL11.glClearDepth(clearDepth);
                        }
                        GL11.glClear(mask);
                    });
        }
    }

    public static class DrawBuffersCommand implements Command {
        private final KeyId renderTargetId;
        // 存储 KeyId 或 Integer 的混合列表
        private final List<Object> colorComponents;

        public DrawBuffersCommand(KeyId renderTargetId, List<Object> colorComponents) {
            this.renderTargetId = renderTargetId;
            this.colorComponents = colorComponents;
        }

        @Override
        public void execute(RenderContext context) {
            GraphicsResourceManager.getInstance()
                    .getReference(ResourceTypes.RENDER_TARGET, renderTargetId)
                    .ifPresent(res -> {
                        if (res instanceof RenderTarget rt) {
                            rt.bind();

                            List<Integer> activeBuffers = new ArrayList<>();
                            boolean shouldApply = false;

                            if (rt instanceof StandardRenderTarget srt) {
                                shouldApply = true;

                                if (colorComponents == null) {
                                    List<KeyId> attachments = srt.getColorAttachmentIds();
                                    for (int i = 0; i < attachments.size(); i++) {
                                        activeBuffers.add(GL30.GL_COLOR_ATTACHMENT0 + i);
                                    }
                                } else {
                                    List<KeyId> attachments = srt.getColorAttachmentIds();
                                    for (Object comp : colorComponents) {
                                        if (comp instanceof KeyId keyId) {
                                            int index = attachments.indexOf(keyId);
                                            if (index >= 0) {
                                                activeBuffers.add(GL30.GL_COLOR_ATTACHMENT0 + index);
                                            }
                                        } else if (comp instanceof Integer val) {
                                            activeBuffers.add(val);
                                        }
                                    }
                                }
                            } else {
                                if (colorComponents != null) {
                                    shouldApply = true;
                                    for (Object comp : colorComponents) {
                                        if (comp instanceof Integer val) {
                                            activeBuffers.add(val);
                                        }
                                    }
                                }
                            }

                            if (shouldApply) {
                                if (activeBuffers.isEmpty()) {
                                    GL11.glDrawBuffer(GL11.GL_NONE);
                                } else {
                                    IntBuffer buffer = BufferUtils.createIntBuffer(activeBuffers.size());
                                    for (int activeBuffer : activeBuffers) {
                                        buffer.put(activeBuffer);
                                    }
                                    buffer.flip();
                                    GL20.glDrawBuffers(buffer);
                                }
                            }
                        }
                    });
        }
    }

    public static class GenMipmapCommand implements Command {
        private final KeyId textureId;

        public GenMipmapCommand(KeyId textureId) {
            this.textureId = textureId;
        }

        @Override
        public void execute(RenderContext context) {
            GraphicsResourceManager.getInstance()
                    .getReference(ResourceTypes.TEXTURE, textureId)
                    .ifPresent(tex -> {
                        if (tex instanceof GpuObject gpuObject) {
                            GraphicsDriver.getCurrentAPI().bindTexture(GL11.GL_TEXTURE_2D, gpuObject.getHandle());
                            GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
                            GraphicsDriver.getCurrentAPI().bindTexture(GL11.GL_TEXTURE_2D, 0);
                        }
                    });
        }
    }

    public static class BindRenderTargetCommand implements Command {
        private final KeyId renderTargetId;

        public BindRenderTargetCommand(KeyId renderTargetId) {
            this.renderTargetId = renderTargetId;
        }

        @Override
        public void execute(RenderContext context) {
            GraphicsResourceManager.getInstance()
                    .getReference(ResourceTypes.RENDER_TARGET, renderTargetId)
                    .ifPresent(res -> {
                        if (res instanceof RenderTarget rt) {
                            rt.bind();
                        }
                    });
        }
    }
}