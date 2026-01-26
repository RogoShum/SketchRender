package rogo.sketch.core.instance;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import rogo.sketch.core.driver.GraphicsAPI;
import rogo.sketch.core.driver.GraphicsDriver;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.resource.GraphicsResourceManager;
import rogo.sketch.core.resource.RenderTarget;
import rogo.sketch.core.resource.ResourceTypes;
import rogo.sketch.core.resource.StandardRenderTarget;
import rogo.sketch.core.util.KeyId;

import java.nio.IntBuffer;
import java.util.List;
import java.util.Map;

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

    @Override
    public boolean tickable() {
        return false;
    }

    public interface Command {
        void execute(RenderContext context);
    }

    public static class ClearCommand implements Command {
        private final KeyId renderTargetId;
        private final boolean color, depth;
        private final float[] clearColor;
        private final float clearDepth;

        public ClearCommand(KeyId renderTargetId, boolean color, boolean depth, float[] clearColor, float clearDepth) {
            this.renderTargetId = renderTargetId;
            this.color = color;
            this.depth = depth;
            this.clearColor = clearColor;
            this.clearDepth = clearDepth;
        }

        @Override
        public void execute(RenderContext context) {
            GraphicsResourceManager.getInstance()
                    .getResource(rogo.sketch.core.resource.ResourceTypes.RENDER_TARGET, renderTargetId)
                    .ifPresent(rt -> {
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
                        RenderTarget.unbind();
                    });
        }
    }

    public static class DrawBuffersCommand implements Command {
        private final KeyId renderTargetId;
        private final java.util.Map<KeyId, Boolean> colorComponents;

        public DrawBuffersCommand(KeyId renderTargetId, java.util.Map<KeyId, Boolean> colorComponents) {
            this.renderTargetId = renderTargetId;
            this.colorComponents = colorComponents;
        }

        @Override
        public void execute(RenderContext context) {
            GraphicsResourceManager.getInstance()
                    .getResource(ResourceTypes.RENDER_TARGET, renderTargetId)
                    .ifPresent(rt -> {
                        if (rt instanceof StandardRenderTarget srt) {
                            srt.bind();
                            List<KeyId> attachments = srt.getColorAttachmentIds();
                            List<Integer> activeBuffers = new java.util.ArrayList<>();
                            for (int i = 0; i < attachments.size(); i++) {
                                KeyId attachmentId = attachments.get(i);
                                if (colorComponents.getOrDefault(attachmentId, false)) {
                                    activeBuffers.add(GL30.GL_COLOR_ATTACHMENT0 + i);
                                }
                            }

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
                            RenderTarget.unbind();
                        }
                    });
        }
    }

    public static class GenMipmapCommand implements Command {
        private final KeyId renderTargetId;
        private final Map<KeyId, Boolean> colorComponents;

        public GenMipmapCommand(KeyId renderTargetId, Map<KeyId, Boolean> colorComponents) {
            this.renderTargetId = renderTargetId;
            this.colorComponents = colorComponents;
        }

        @Override
        public void execute(RenderContext context) {
            GraphicsResourceManager.getInstance()
                    .getResource(ResourceTypes.RENDER_TARGET, renderTargetId)
                    .ifPresent(rt -> {
                        if (rt instanceof StandardRenderTarget srt) {
                            List<KeyId> attachments = srt.getColorAttachmentIds();
                            for (int i = 0; i < attachments.size(); i++) {
                                KeyId attachmentId = attachments.get(i);
                                if (colorComponents.getOrDefault(attachmentId, false)) {
                                    GraphicsResourceManager.getInstance()
                                            .getResource(ResourceTypes.TEXTURE, attachmentId)
                                            .ifPresent(tex -> {
                                                GraphicsDriver.getCurrentAPI().bindTexture(GL11.GL_TEXTURE_2D, tex.getHandle());
                                                GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
                                                GraphicsDriver.getCurrentAPI().bindTexture(GL11.GL_TEXTURE_2D, 0);
                                            });
                                }
                            }
                        }
                    });
        }
    }
}