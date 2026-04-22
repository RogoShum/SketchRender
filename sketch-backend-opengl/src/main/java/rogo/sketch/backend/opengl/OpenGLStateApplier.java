package rogo.sketch.backend.opengl;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import rogo.sketch.core.backend.BackendResourceRegistry;
import rogo.sketch.core.backend.BackendInstalledRenderTarget;
import rogo.sketch.core.backend.BackendStateApplier;
import rogo.sketch.core.driver.state.AttachmentBindingState;
import rogo.sketch.core.driver.state.DepthState;
import rogo.sketch.core.driver.state.DynamicRenderState;
import rogo.sketch.core.driver.state.PassBindingState;
import rogo.sketch.core.driver.state.PipelineRasterState;
import rogo.sketch.core.driver.state.ShaderBindingState;
import rogo.sketch.backend.opengl.driver.GraphicsAPI;
import rogo.sketch.core.driver.state.BlendFactor;
import rogo.sketch.core.driver.state.BlendOp;
import rogo.sketch.core.driver.state.CompareOp;
import rogo.sketch.core.driver.state.CullFaceMode;
import rogo.sketch.core.driver.state.FrontFaceMode;
import rogo.sketch.core.driver.state.LogicOp;
import rogo.sketch.core.driver.state.StencilOperation;
import rogo.sketch.core.driver.state.component.BlendState;
import rogo.sketch.core.driver.state.component.ColorMaskState;
import rogo.sketch.core.driver.state.component.CullState;
import rogo.sketch.core.driver.state.component.LogicOpState;
import rogo.sketch.core.driver.state.component.PolygonOffsetState;
import rogo.sketch.core.driver.state.component.RenderTargetState;
import rogo.sketch.core.driver.state.component.ScissorState;
import rogo.sketch.core.driver.state.component.ShaderState;
import rogo.sketch.core.driver.state.component.StencilState;
import rogo.sketch.core.driver.state.component.ViewportState;
import rogo.sketch.core.pipeline.PipelineConfig;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.resource.ResourceTypes;
import rogo.sketch.core.pipeline.module.diagnostic.SketchDiagnostics;
import rogo.sketch.core.resource.vision.AttachmentBackedRenderTarget;
import rogo.sketch.core.resource.vision.RenderTarget;
import rogo.sketch.core.shader.ShaderProgramHandle;
import rogo.sketch.core.shader.ShaderProgramResolver;
import rogo.sketch.core.util.KeyId;

import java.io.IOException;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

public final class OpenGLStateApplier implements BackendStateApplier {
    private final GraphicsAPI api;
    private final BackendResourceRegistry resourceRegistry;
    private final OpenGLStateAccess stateAccess;

    public OpenGLStateApplier(GraphicsAPI api, BackendResourceRegistry resourceRegistry, OpenGLStateAccess stateAccess) {
        this.api = api;
        this.resourceRegistry = resourceRegistry;
        this.stateAccess = stateAccess;
    }

    @Override
    public void applyPipelineRasterState(PipelineRasterState state, RenderContext context) {
        if (state == null) {
            return;
        }
        if (state.blendState() != null) {
            applyBlend(state.blendState());
        }
        if (state.depthState() != null) {
            applyDepth(state.depthState());
        }
        if (state.cullState() != null) {
            applyCull(state.cullState());
        }
        if (state.stencilState() != null) {
            applyStencil(state.stencilState());
        }
        if (state.polygonOffsetState() != null) {
            applyPolygonOffset(state.polygonOffsetState());
        }
        if (state.colorMaskState() != null) {
            stateAccess.colorMask(
                    state.colorMaskState().red(),
                    state.colorMaskState().green(),
                    state.colorMaskState().blue(),
                    state.colorMaskState().alpha());
        }
    }

    @Override
    public void applyDynamicRenderState(DynamicRenderState state, RenderContext context) {
        if (state == null) {
            return;
        }
        if (state.scissorState() != null) {
            applyScissor(state.scissorState());
        }
        if (state.viewportState() != null) {
            applyViewport(state.viewportState(), context);
        }
        if (state.logicOpState() != null) {
            applyLogicOp(state.logicOpState());
        }
    }

    @Override
    public void applyPassBindingState(PassBindingState state, RenderContext context) {
        if (state == null) {
            return;
        }
        if (state.renderTargetState() != null) {
            applyRenderTarget(state.renderTargetState());
        }
        if (state.shaderState() != null) {
            applyShader(state.shaderState(), context);
        }
    }

    @Override
    public void applyAttachmentBindingState(AttachmentBindingState state, RenderContext context) {
        if (state == null || state.renderTargetState() == null) {
            return;
        }
        applyRenderTarget(state.renderTargetState());
    }

    @Override
    public void applyShaderBindingState(ShaderBindingState state, RenderContext context) {
        if (state == null || state.shaderState() == null) {
            return;
        }
        applyShader(state.shaderState(), context);
    }

    private void applyBlend(BlendState state) {
        if (state.enabled()) {
            stateAccess.enableBlend();
            stateAccess.blendFuncSeparate(
                    mapBlendFactor(state.colorSrcFactor()),
                    mapBlendFactor(state.colorDstFactor()),
                    mapBlendFactor(state.alphaSrcFactor()),
                    mapBlendFactor(state.alphaDstFactor()));
            stateAccess.blendEquationSeparate(
                    mapBlendOp(state.colorOp()),
                    mapBlendOp(state.alphaOp()));
        } else {
            stateAccess.disableBlend();
        }
    }

    private void applyDepth(DepthState state) {
        if (state.testEnabled()) {
            stateAccess.enableDepthTest();
            stateAccess.depthFunc(mapCompareOp(state.compareOp()));
        } else {
            stateAccess.disableDepthTest();
        }
        stateAccess.depthMask(state.writeEnabled());
    }

    private void applyCull(CullState state) {
        if (state.enabled()) {
            stateAccess.enableCullFace();
            stateAccess.cullFace(mapCullFace(state.face()));
            stateAccess.frontFace(mapFrontFace(state.frontFace()));
        } else {
            stateAccess.disableCullFace();
        }
    }

    private void applyLogicOp(LogicOpState state) {
        if (state.enabled()) {
            stateAccess.enableLogicOp();
            stateAccess.logicOp(mapLogicOp(state.opcode()));
        } else {
            stateAccess.disableLogicOp();
        }
    }

    private void applyPolygonOffset(PolygonOffsetState state) {
        if (state.enabled()) {
            stateAccess.enablePolygonOffset();
            stateAccess.polygonOffset(state.factor(), state.units());
        } else {
            stateAccess.disablePolygonOffset();
        }
    }

    private void applyRenderTarget(RenderTargetState state) {
        KeyId renderTargetId = state.renderTargetId();
        BackendInstalledRenderTarget installedRenderTarget = resourceRegistry.resolveRenderTarget(renderTargetId);
        if (installedRenderTarget != null && !installedRenderTarget.isDisposed()) {
            installedRenderTarget.bind();
        } else if (PipelineConfig.DEFAULT_RENDER_TARGET_ID.equals(renderTargetId)) {
            stateAccess.bindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        }

        RenderTarget logicalTarget = resourceRegistry.resolveLogicalResource(ResourceTypes.RENDER_TARGET, renderTargetId) instanceof RenderTarget target
                ? target
                : null;
        applyDrawBuffers(logicalTarget, state.drawBuffers());
    }

    private void applyDrawBuffers(RenderTarget renderTarget, List<Object> drawBuffers) {
        List<Integer> activeBuffers = new ArrayList<>();
        boolean shouldApply = false;

        if (renderTarget instanceof AttachmentBackedRenderTarget attachmentBacked) {
            shouldApply = true;
            List<KeyId> attachments = attachmentBacked.getColorAttachmentIds();
            if (drawBuffers == null || drawBuffers.isEmpty()) {
                for (int i = 0; i < attachments.size(); i++) {
                    activeBuffers.add(GL30.GL_COLOR_ATTACHMENT0 + i);
                }
            } else {
                for (Object component : drawBuffers) {
                    if (component instanceof KeyId keyId) {
                        int index = attachments.indexOf(keyId);
                        if (index >= 0) {
                            activeBuffers.add(GL30.GL_COLOR_ATTACHMENT0 + index);
                        }
                    } else if (component instanceof Integer value) {
                        activeBuffers.add(GL30.GL_COLOR_ATTACHMENT0 + value);
                    }
                }
            }
        } else if (drawBuffers != null && !drawBuffers.isEmpty()) {
            shouldApply = true;
            for (Object component : drawBuffers) {
                if (component instanceof Integer value) {
                    activeBuffers.add(value);
                }
            }
        }

        if (!shouldApply) {
            return;
        }

        if (activeBuffers.isEmpty()) {
            GL11.glDrawBuffer(GL11.GL_NONE);
            return;
        }

        IntBuffer buffer = BufferUtils.createIntBuffer(activeBuffers.size());
        for (int activeBuffer : activeBuffers) {
            buffer.put(activeBuffer);
        }
        buffer.flip();
        GL20.glDrawBuffers(buffer);
    }

    private void applyScissor(ScissorState state) {
        if (state.enabled()) {
            stateAccess.enableScissor(state.x(), state.y(), state.width(), state.height());
        } else {
            stateAccess.disableScissor();
        }
    }

    private void applyShader(ShaderState state, RenderContext context) {
        try {
            if (state == null || state.getShaderId() == null || "empty".equals(state.getShaderId().toString())) {
                return;
            }
            ShaderProgramHandle programHandle = ShaderProgramResolver.resolveProgramHandle(state);
            if (programHandle == null) {
                SketchDiagnostics.get().warn(
                        "shader-state",
                        "No shader program handle resolved for " + state.getShaderId() + " / " + state.getVariantKey());
                return;
            }

            api.getShaderStrategy().useProgram(programHandle.programHandle());
            if (context != null) {
                context.setShaderProgramHandle(programHandle);
            }
        } catch (IOException e) {
            SketchDiagnostics.get().error(
                    "shader-state",
                    "Failed to resolve shader program handle for " + state.getShaderId() + " / " + state.getVariantKey(),
                    e);
        }
    }

    private void applyStencil(StencilState state) {
        if (state.enabled()) {
            stateAccess.enableStencil();
            stateAccess.stencilFunc(mapCompareOp(state.func()), state.ref(), state.mask());
            stateAccess.stencilOp(
                    mapStencilOp(state.fail()),
                    mapStencilOp(state.zfail()),
                    mapStencilOp(state.zpass()));
        } else {
            stateAccess.disableStencil();
        }
    }

    private void applyViewport(ViewportState state, RenderContext context) {
        if (state.auto()) {
            int width = context != null ? context.windowWidth() : 0;
            int height = context != null ? context.windowHeight() : 0;
            stateAccess.viewport(0, 0, width, height);
        } else {
            stateAccess.viewport(state.x(), state.y(), state.width(), state.height());
        }
    }

    private static int mapBlendFactor(BlendFactor factor) {
        if (factor == null) {
            return GL11.GL_ONE;
        }
        return switch (factor) {
            case ZERO -> GL11.GL_ZERO;
            case ONE -> GL11.GL_ONE;
            case SRC_COLOR -> GL11.GL_SRC_COLOR;
            case ONE_MINUS_SRC_COLOR -> GL11.GL_ONE_MINUS_SRC_COLOR;
            case DST_COLOR -> GL11.GL_DST_COLOR;
            case ONE_MINUS_DST_COLOR -> GL11.GL_ONE_MINUS_DST_COLOR;
            case SRC_ALPHA -> GL11.GL_SRC_ALPHA;
            case ONE_MINUS_SRC_ALPHA -> GL11.GL_ONE_MINUS_SRC_ALPHA;
            case DST_ALPHA -> GL11.GL_DST_ALPHA;
            case ONE_MINUS_DST_ALPHA -> GL11.GL_ONE_MINUS_DST_ALPHA;
            case SRC_ALPHA_SATURATE -> GL11.GL_SRC_ALPHA_SATURATE;
        };
    }

    private static int mapBlendOp(BlendOp op) {
        if (op == null) {
            return GL14.GL_FUNC_ADD;
        }
        return switch (op) {
            case ADD -> GL14.GL_FUNC_ADD;
            case SUBTRACT -> GL14.GL_FUNC_SUBTRACT;
            case REVERSE_SUBTRACT -> GL14.GL_FUNC_REVERSE_SUBTRACT;
            case MIN -> GL14.GL_MIN;
            case MAX -> GL14.GL_MAX;
        };
    }

    private static int mapCompareOp(CompareOp compareOp) {
        if (compareOp == null) {
            return GL11.GL_LESS;
        }
        return switch (compareOp) {
            case NEVER -> GL11.GL_NEVER;
            case LESS -> GL11.GL_LESS;
            case EQUAL -> GL11.GL_EQUAL;
            case LEQUAL -> GL11.GL_LEQUAL;
            case GREATER -> GL11.GL_GREATER;
            case NOTEQUAL -> GL11.GL_NOTEQUAL;
            case GEQUAL -> GL11.GL_GEQUAL;
            case ALWAYS -> GL11.GL_ALWAYS;
        };
    }

    private static int mapCullFace(CullFaceMode cullFaceMode) {
        if (cullFaceMode == null) {
            return GL11.GL_BACK;
        }
        return switch (cullFaceMode) {
            case FRONT -> GL11.GL_FRONT;
            case BACK -> GL11.GL_BACK;
            case FRONT_AND_BACK -> GL11.GL_FRONT_AND_BACK;
        };
    }

    private static int mapFrontFace(FrontFaceMode frontFaceMode) {
        if (frontFaceMode == null) {
            return GL11.GL_CCW;
        }
        return switch (frontFaceMode) {
            case CW -> GL11.GL_CW;
            case CCW -> GL11.GL_CCW;
        };
    }

    private static int mapLogicOp(LogicOp logicOp) {
        if (logicOp == null) {
            return GL11.GL_COPY;
        }
        return switch (logicOp) {
            case CLEAR -> GL11.GL_CLEAR;
            case SET -> GL11.GL_SET;
            case COPY -> GL11.GL_COPY;
            case COPY_INVERTED -> GL11.GL_COPY_INVERTED;
            case NOOP -> GL11.GL_NOOP;
            case INVERT -> GL11.GL_INVERT;
            case AND -> GL11.GL_AND;
            case NAND -> GL11.GL_NAND;
            case OR -> GL11.GL_OR;
            case NOR -> GL11.GL_NOR;
            case XOR -> GL11.GL_XOR;
            case EQUIV -> GL11.GL_EQUIV;
            case AND_REVERSE -> GL11.GL_AND_REVERSE;
            case AND_INVERTED -> GL11.GL_AND_INVERTED;
            case OR_REVERSE -> GL11.GL_OR_REVERSE;
            case OR_INVERTED -> GL11.GL_OR_INVERTED;
        };
    }

    private static int mapStencilOp(StencilOperation stencilOperation) {
        if (stencilOperation == null) {
            return GL11.GL_KEEP;
        }
        return switch (stencilOperation) {
            case KEEP -> GL11.GL_KEEP;
            case ZERO -> GL11.GL_ZERO;
            case REPLACE -> GL11.GL_REPLACE;
            case INCR -> GL11.GL_INCR;
            case DECR -> GL11.GL_DECR;
            case INVERT -> GL11.GL_INVERT;
        };
    }
}


