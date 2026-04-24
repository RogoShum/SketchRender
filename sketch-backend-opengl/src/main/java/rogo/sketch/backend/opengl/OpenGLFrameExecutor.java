package rogo.sketch.backend.opengl;

import org.lwjgl.BufferUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL42;
import org.lwjgl.opengl.GL43;
import org.lwjgl.opengl.GL45;
import org.lwjgl.system.MemoryStack;
import rogo.sketch.core.backend.BackendPacketHandlerRegistry;
import rogo.sketch.core.backend.BackendInstalledRenderTarget;
import rogo.sketch.core.backend.BackendIndirectBuffer;
import rogo.sketch.core.backend.BackendInstalledTexture;
import rogo.sketch.core.backend.BackendFrameExecutor;
import rogo.sketch.core.backend.BackendResourceRegistry;
import rogo.sketch.core.backend.BackendStageScope;
import rogo.sketch.core.debug.RenderDocRuntime;
import rogo.sketch.backend.opengl.driver.GraphicsAPI;
import rogo.sketch.core.driver.state.component.ColorMaskState;
import rogo.sketch.backend.opengl.state.snapshot.GLStateSnapshot;
import rogo.sketch.core.driver.state.snapshot.SnapshotScope;
import rogo.sketch.core.packet.ClearPacket;
import rogo.sketch.core.packet.CopyTexturePacket;
import rogo.sketch.core.packet.DispatchPacket;
import rogo.sketch.core.packet.DrawPacket;
import rogo.sketch.core.packet.DrawPlan;
import rogo.sketch.core.packet.ExecutionKey;
import rogo.sketch.core.packet.GenerateMipmapPacket;
import rogo.sketch.core.packet.RenderPacket;
import rogo.sketch.core.packet.RenderPacketQueue;
import rogo.sketch.core.pipeline.GraphicsPipeline;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.RenderStateManager;
import rogo.sketch.core.pipeline.data.FrameDataDomain;
import rogo.sketch.core.pipeline.data.GeometryFrameData;
import rogo.sketch.core.graphics.ecs.GraphicsUniformSubject;
import rogo.sketch.core.pipeline.module.diagnostic.RenderTraceRecorder;
import rogo.sketch.core.resource.GraphicsResourceManager;
import rogo.sketch.core.resource.ResourceBinding;
import rogo.sketch.core.resource.ResourceTypes;
import rogo.sketch.core.resource.vision.RenderTarget;
import rogo.sketch.core.resource.vision.StandardRenderTarget;
import rogo.sketch.core.shader.ComputeDispatchSupport;
import rogo.sketch.core.shader.ShaderProgramHandle;
import rogo.sketch.core.shader.uniform.UniformCaptureTiming;
import rogo.sketch.core.shader.uniform.UniformHookGroup;
import rogo.sketch.core.shader.uniform.UniformValueSnapshot;
import rogo.sketch.core.shader.vertex.ActiveShaderVertexLayout;
import rogo.sketch.core.util.KeyId;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Formal OpenGL frame executor responsible for stage scope capture/restore and
 * packet execution.
 */
public final class OpenGLFrameExecutor implements BackendFrameExecutor {
    private final GraphicsAPI api;
    private final BackendResourceRegistry resourceRegistry;
    private final BackendPacketHandlerRegistry<OpenGLPacketHandler> packetHandlers = new BackendPacketHandlerRegistry<>();

    public OpenGLFrameExecutor(GraphicsAPI api, BackendResourceRegistry resourceRegistry) {
        this.api = api;
        this.resourceRegistry = resourceRegistry;
        registerBuiltInPacketHandlers();
    }

    public BackendPacketHandlerRegistry<OpenGLPacketHandler> packetHandlerRegistry() {
        return packetHandlers;
    }

    public GraphicsAPI graphicsApi() {
        return api;
    }

    public BackendResourceRegistry backendResourceRegistry() {
        return resourceRegistry;
    }

    @Override
    public <C extends RenderContext> BackendStageScope beginExecutionScope(
            GraphicsPipeline<C> pipeline,
            RenderPacketQueue<C> queue,
            List<KeyId> stageIds,
            SnapshotScope snapshotScope,
            C context) {
        if (snapshotScope == null || snapshotScope.isEmpty()) {
            return BackendStageScope.NO_OP;
        }
        GLStateSnapshot snapshot = api.snapshot(snapshotScope);
        return () -> snapshot.restore(api);
    }

    @Override
    public <C extends RenderContext> void executePacketGroup(
            GraphicsPipeline<C> pipeline,
            ExecutionKey stateKey,
            List<RenderPacket> packets,
            RenderStateManager manager,
            C context) {
        if (packets == null || packets.isEmpty()) {
            return;
        }
        if (stateKey.renderParameter() != null && stateKey.renderParameter().isInvalid()) {
            return;
        }

        manager.accept(stateKey, context);
        ShaderProgramHandle shaderProvider = context.shaderProgramHandle();
        if (shaderProvider != null) {
            shaderProvider.uniformHooks().updateUniforms(context, UniformCaptureTiming.PER_DRAW_DEFERRED);
        }

        for (RenderPacket packet : packets) {
            executePacket(pipeline, packet, context);
        }
    }

    @Override
    public <C extends RenderContext> void executeImmediate(
            GraphicsPipeline<C> pipeline,
            RenderPacket packet,
            RenderStateManager manager,
            C context) {
        if (packet == null) {
            return;
        }
        if (packet.stateKey().renderParameter() != null && packet.stateKey().renderParameter().isInvalid()) {
            return;
        }

        manager.accept(packet.stateKey(), context);
        ShaderProgramHandle shaderProvider = context.shaderProgramHandle();
        if (shaderProvider != null) {
            shaderProvider.uniformHooks().updateUniforms(context, UniformCaptureTiming.PER_DRAW_DEFERRED);
        }
        executePacket(pipeline, packet, context);
    }

    private <C extends RenderContext> void executePacket(GraphicsPipeline<C> pipeline, RenderPacket packet, C context) {
        boolean renderDocEnabled = RenderDocRuntime.enabled();
        if (renderDocEnabled) {
            pushDebugGroup(debugLabel(packet));
        }
        try {
            applyUniformSnapshot(packet.uniformGroups().frameUniforms().snapshot(), context);
            applyUniformSnapshot(packet.uniformGroups().passUniforms().snapshot(), context);
            applyUniformSnapshot(resolveUniformSnapshot(packet), context);
            if (packet.bindingPlan() != packet.stateKey().bindingPlan()) {
                applyResourceBinding(packet.bindingPlan(), context);
            }
            OpenGLPacketHandler handler = packetHandlers.handlerFor(packet);
            if (handler == null) {
                throw new IllegalArgumentException(
                        "No OpenGL packet handler registered for " + packet.packetType().id()
                                + " (" + packet.getClass().getName() + ")");
            }
            handler.execute(pipeline, packet, context, this);

            completePacket(packet, context);
        } finally {
            if (renderDocEnabled) {
                popDebugGroup();
            }
        }
    }

    private void registerBuiltInPacketHandlers() {
        packetHandlers.register(rogo.sketch.core.packet.RenderPacketType.DRAW, (pipeline, packet, context, executor) ->
                executor.executeDrawPacket(pipeline, (DrawPacket) packet, context));
        packetHandlers.register(rogo.sketch.core.packet.RenderPacketType.DISPATCH, (pipeline, packet, context, executor) ->
                executor.executeDispatchPacket((DispatchPacket) packet, context));
        packetHandlers.register(rogo.sketch.core.packet.RenderPacketType.CLEAR, (pipeline, packet, context, executor) ->
                executor.executeClearPacket((ClearPacket) packet, context));
        packetHandlers.register(rogo.sketch.core.packet.RenderPacketType.COPY_TEXTURE, (pipeline, packet, context, executor) ->
                executor.executeCopyTexturePacket((CopyTexturePacket) packet));
        packetHandlers.register(rogo.sketch.core.packet.RenderPacketType.GENERATE_MIPMAP, (pipeline, packet, context, executor) ->
                executor.executeGenerateMipmapPacket((GenerateMipmapPacket) packet));
    }

    private void applyUniformSnapshot(UniformValueSnapshot snapshot, RenderContext context) {
        if (snapshot == null || snapshot.isEmpty()) {
            return;
        }
        ShaderProgramHandle shaderProvider = context.shaderProgramHandle();
        if (shaderProvider == null) {
            return;
        }
        UniformHookGroup uniformHookGroup = shaderProvider.uniformHooks();
        snapshot.applyTo(uniformHookGroup);
    }

    private void applyResourceBinding(rogo.sketch.core.packet.ResourceBindingPlan bindingPlan, RenderContext context) {
        if (bindingPlan == null) {
            return;
        }
        ResourceBinding binding = bindingPlan.binding();
        if (binding != null) {
            binding.bind(context);
        }
    }

    private UniformValueSnapshot resolveUniformSnapshot(RenderPacket packet) {
        if (packet instanceof DrawPacket drawPacket) {
            return drawPacket.uniformSnapshot();
        }
        if (packet instanceof DispatchPacket dispatchPacket) {
            return dispatchPacket.uniformSnapshot();
        }
        if (packet instanceof ClearPacket clearPacket) {
            return clearPacket.uniformSnapshot();
        }
        if (packet instanceof CopyTexturePacket copyTexturePacket) {
            return copyTexturePacket.uniformSnapshot();
        }
        if (packet instanceof GenerateMipmapPacket generateMipmapPacket) {
            return generateMipmapPacket.uniformSnapshot();
        }
        return UniformValueSnapshot.empty();
    }

    private void executeDrawPacket(GraphicsPipeline<? extends RenderContext> pipeline, DrawPacket packet, RenderContext context) {
        GeometryFrameData geometryFrameData = pipeline
                .getPipelineDataStore(packet.pipelineType(), FrameDataDomain.SYNC_READ)
                .get(GeometryFrameData.KEY);
        if (geometryFrameData == null) {
            traceBackendDrop(pipeline, packet, "backend_missing_geometry_frame_data(handle=" + packet.geometryHandle() + ")");
            return;
        }
        GeometryFrameData.GeometryBinding geometryBinding = geometryFrameData.resolve(packet.geometryHandle());
        if (geometryBinding == null) {
            traceBackendDrop(pipeline, packet, "backend_missing_geometry_binding(handle=" + packet.geometryHandle() + ")");
            return;
        }
        if (!(geometryBinding.geometryBinding() instanceof OpenGLGeometryBinding openGLGeometryBinding)) {
            traceBackendDrop(pipeline, packet, "backend_missing_opengl_geometry_binding(handle=" + packet.geometryHandle() + ")");
            return;
        }
        if (packet.drawPlan() == null) {
            traceBackendDrop(pipeline, packet, "backend_missing_draw_plan");
            return;
        }

        BackendIndirectBuffer indirectBuffer = geometryBinding.indirectBuffer() instanceof BackendIndirectBuffer resolvedIndirectBuffer
                ? resolvedIndirectBuffer
                : null;
        DrawPlan drawPlan = packet.drawPlan();
        if (drawPlan.submission() != DrawPlan.DrawSubmission.MULTI_DRAW_INDIRECT && drawPlan.directItems().isEmpty()) {
            traceBackendDrop(pipeline, packet, "backend_empty_direct_items");
            return;
        }

        traceBackendExecuted(pipeline, packet);
        ActiveShaderVertexLayout activeLayout = context.shaderProgramHandle() != null
                && context.shaderProgramHandle().interfaceSpec() != null
                ? context.shaderProgramHandle().interfaceSpec().activeVertexLayout()
                : ActiveShaderVertexLayout.empty();
        OpenGLVertexInputLayout vertexInputLayout = openGLGeometryBinding.resolveVertexInputLayout(activeLayout);
        vertexInputLayout.bind();
        try {
            if (openGLGeometryBinding.hasIndices() && openGLGeometryBinding.getIndexBuffer().isDirty()) {
                openGLGeometryBinding.getIndexBuffer().upload();
            }

            if (drawPlan.submission() == DrawPlan.DrawSubmission.MULTI_DRAW_INDIRECT) {
                if (indirectBuffer == null || drawPlan.drawCount() <= 0) {
                    traceBackendDrop(pipeline, packet, "backend_invalid_indirect_plan");
                    return;
                }

                indirectBuffer.bind();
                try {
                    if (drawPlan.indexed()) {
                        GL43.glMultiDrawElementsIndirect(
                                OpenGLPrimitiveMappings.toGlType(drawPlan.primitiveType()),
                                OpenGLIndexTypeMappings.toGlType(openGLGeometryBinding.getIndexBuffer().currentIndexType()),
                                drawPlan.indirectOffset(),
                                drawPlan.drawCount(),
                                drawPlan.indirectStride());
                    } else {
                        GL43.glMultiDrawArraysIndirect(
                                OpenGLPrimitiveMappings.toGlType(drawPlan.primitiveType()),
                                drawPlan.indirectOffset(),
                                drawPlan.drawCount(),
                                drawPlan.indirectStride());
                    }
                } finally {
                    indirectBuffer.unbind();
                }
                return;
            }

            if (drawPlan.directItems().isEmpty()) {
                return;
            }

            executeDirectBatchOptimized(openGLGeometryBinding, drawPlan);
        } finally {
            vertexInputLayout.unbind();
        }
    }

    private void executeDirectBatchOptimized(OpenGLGeometryBinding geometryBinding, DrawPlan drawPlan) {
        List<DrawPlan.DirectDrawItem> directItems = drawPlan.directItems();
        int index = 0;
        while (index < directItems.size()) {
            DrawPlan.DirectDrawItem item = directItems.get(index);
            if (canUseNativeMultiDraw(item)) {
                int segmentEnd = index + 1;
                while (segmentEnd < directItems.size()
                        && canBatchWithNativeMultiDraw(item, directItems.get(segmentEnd))) {
                    segmentEnd++;
                }
                if (segmentEnd - index > 1) {
                    executeNativeMultiDraw(geometryBinding, drawPlan, directItems.subList(index, segmentEnd));
                    index = segmentEnd;
                    continue;
                }
            }
            executeDirectItem(geometryBinding, drawPlan, item);
            index++;
        }
    }

    private boolean canUseNativeMultiDraw(DrawPlan.DirectDrawItem item) {
        if (item == null || item.instanceCount() != 1 || item.baseInstance() != 0) {
            return false;
        }
        if (item.indexed()) {
            return item.indexedSlice() != null && item.indexedSlice().indexCount() > 0;
        }
        return item.vertexCount() > 0;
    }

    private boolean canBatchWithNativeMultiDraw(
            DrawPlan.DirectDrawItem reference,
            DrawPlan.DirectDrawItem candidate) {
        return reference != null
                && candidate != null
                && reference.indexed() == candidate.indexed()
                && canUseNativeMultiDraw(candidate);
    }

    private void executeNativeMultiDraw(
            OpenGLGeometryBinding geometryBinding,
            DrawPlan drawPlan,
            List<DrawPlan.DirectDrawItem> items) {
        if (items == null || items.size() <= 1) {
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            if (!items.get(0).indexed()) {
                java.nio.IntBuffer firsts = stack.mallocInt(items.size());
                java.nio.IntBuffer counts = stack.mallocInt(items.size());
                for (DrawPlan.DirectDrawItem item : items) {
                    firsts.put(item.firstVertex());
                    counts.put(item.vertexCount());
                }
                firsts.flip();
                counts.flip();
                GL14.glMultiDrawArrays(OpenGLPrimitiveMappings.toGlType(drawPlan.primitiveType()), firsts, counts);
                return;
            }

            java.nio.IntBuffer counts = stack.mallocInt(items.size());
            PointerBuffer indices = stack.mallocPointer(items.size());
            java.nio.IntBuffer baseVertices = stack.mallocInt(items.size());
            for (DrawPlan.DirectDrawItem item : items) {
                counts.put(item.indexedSlice().indexCount());
                indices.put(item.indexedSlice().firstIndexByteOffset());
                baseVertices.put(item.indexedSlice().baseVertex());
            }
            counts.flip();
            indices.flip();
            baseVertices.flip();
            GL32.glMultiDrawElementsBaseVertex(
                    OpenGLPrimitiveMappings.toGlType(drawPlan.primitiveType()),
                    counts,
                    OpenGLIndexTypeMappings.toGlType(geometryBinding.getIndexBuffer().currentIndexType()),
                    indices,
                    baseVertices);
        }
    }

    private void executeDirectItem(OpenGLGeometryBinding geometryBinding, DrawPlan drawPlan, DrawPlan.DirectDrawItem item) {
        if (item == null) {
            return;
        }
        if (!item.indexed()) {
            if (item.instanceCount() <= 0 || item.vertexCount() <= 0) {
                return;
            }
            if (item.instanceCount() == 1 && item.baseInstance() == 0) {
                GL11.glDrawArrays(
                        OpenGLPrimitiveMappings.toGlType(drawPlan.primitiveType()),
                        item.firstVertex(),
                        item.vertexCount());
                return;
            }
            if (item.baseInstance() == 0) {
                GL31.glDrawArraysInstanced(
                        OpenGLPrimitiveMappings.toGlType(drawPlan.primitiveType()),
                        item.firstVertex(),
                        item.vertexCount(),
                        item.instanceCount());
                return;
            }
            GL42.glDrawArraysInstancedBaseInstance(
                    OpenGLPrimitiveMappings.toGlType(drawPlan.primitiveType()),
                    item.firstVertex(),
                    item.vertexCount(),
                    item.instanceCount(),
                    item.baseInstance());
            return;
        }

        if (item.indexedSlice() == null || item.instanceCount() <= 0) {
            return;
        }
        int indexCount = item.indexedSlice().indexCount();
        int indexType = OpenGLIndexTypeMappings.toGlType(geometryBinding.getIndexBuffer().currentIndexType());
        long indexOffset = item.indexedSlice().firstIndexByteOffset();
        int vertexOffset = item.indexedSlice().baseVertex();
        if (indexCount <= 0) {
            return;
        }

        if (item.instanceCount() == 1 && item.baseInstance() == 0) {
            if (vertexOffset == 0) {
                GL11.glDrawElements(
                        OpenGLPrimitiveMappings.toGlType(drawPlan.primitiveType()),
                        indexCount,
                        indexType,
                        indexOffset);
                return;
            }
            GL32.glDrawElementsBaseVertex(
                    OpenGLPrimitiveMappings.toGlType(drawPlan.primitiveType()),
                    indexCount,
                    indexType,
                    indexOffset,
                    vertexOffset);
            return;
        }

        if (item.baseInstance() == 0) {
            if (vertexOffset == 0) {
                GL31.glDrawElementsInstanced(
                        OpenGLPrimitiveMappings.toGlType(drawPlan.primitiveType()),
                        indexCount,
                        indexType,
                        indexOffset,
                        item.instanceCount());
                return;
            }
            GL32.glDrawElementsInstancedBaseVertex(
                    OpenGLPrimitiveMappings.toGlType(drawPlan.primitiveType()),
                    indexCount,
                    indexType,
                    indexOffset,
                    item.instanceCount(),
                    vertexOffset);
            return;
        }

        GL45.glDrawElementsInstancedBaseVertexBaseInstance(
                OpenGLPrimitiveMappings.toGlType(drawPlan.primitiveType()),
                indexCount,
                indexType,
                indexOffset,
                item.instanceCount(),
                vertexOffset,
                item.baseInstance());
    }

    private void traceBackendExecuted(GraphicsPipeline<? extends RenderContext> pipeline, DrawPacket packet) {
        RenderTraceRecorder renderTraceRecorder = pipeline.renderTraceRecorder();
        if (renderTraceRecorder == null || packet.completionSubjects() == null) {
            return;
        }
        for (GraphicsUniformSubject subject : packet.completionSubjects()) {
            if (subject != null) {
                renderTraceRecorder.recordBackendExecuted(packet.stageId(), subject);
            }
        }
    }

    private void traceBackendDrop(GraphicsPipeline<? extends RenderContext> pipeline, DrawPacket packet, String reason) {
        RenderTraceRecorder renderTraceRecorder = pipeline.renderTraceRecorder();
        if (renderTraceRecorder == null || packet.completionSubjects() == null) {
            return;
        }
        for (GraphicsUniformSubject subject : packet.completionSubjects()) {
            if (subject != null) {
                renderTraceRecorder.recordDrop(packet.stageId(), subject, reason);
            }
        }
    }

    private void executeDispatchPacket(DispatchPacket packet, RenderContext context) {
        rogo.sketch.core.api.graphics.ComputeDispatchContext dispatchContext = ComputeDispatchSupport.createContext(context);
        if (packet.dispatchCommand() != null) {
            packet.dispatchCommand().dispatch(dispatchContext);
        } else if (packet.computeInfo() != null) {
            packet.computeInfo().dispatch(dispatchContext);
        } else {
            api.dispatchCompute(packet.workGroupsX(), packet.workGroupsY(), packet.workGroupsZ());
        }
    }

    private void executeClearPacket(ClearPacket packet, RenderContext context) {
        int previousFramebuffer = packet.restorePreviousRenderTarget()
                ? GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)
                : -1;

        resolveLogicalRenderTarget(packet.renderTargetId()).ifPresent(renderTarget -> {
            if (packet.clearColor()) {
                boolean[] colorMask = packet.colorMask() != null && packet.colorMask().length >= 4
                        ? packet.colorMask()
                        : new boolean[]{true, true, true, true};
                if (context.renderStateManager() != null) {
                    context.renderStateManager().changeState(new ColorMaskState(
                            colorMask[0],
                            colorMask[1],
                            colorMask[2],
                            colorMask[3]), context);
                } else {
                    api.colorMask(colorMask[0], colorMask[1], colorMask[2], colorMask[3]);
                }
            }
            if (packet.clearDepth()) {
                api.depthMask(true);
            }

            try {
                BackendInstalledRenderTarget installedRenderTarget = resolveInstalledRenderTarget(packet.renderTargetId(), renderTarget);
                if (installedRenderTarget == null) {
                    return;
                }
                installedRenderTarget.bind();
                applyClearAttachments(packet, renderTarget);

                int mask = 0;
                if (packet.clearColor()) {
                    float[] color = packet.colorValue();
                    api.clearColor(color[0], color[1], color[2], color[3]);
                    mask |= GL11.GL_COLOR_BUFFER_BIT;
                }
                if (packet.clearDepth()) {
                    api.clearDepth(packet.depthValue());
                    mask |= GL11.GL_DEPTH_BUFFER_BIT;
                }
                if (mask != 0) {
                    api.clear(mask);
                }
            } finally {
                if (packet.restorePreviousRenderTarget() && previousFramebuffer >= 0) {
                    api.bindFrameBuffer(previousFramebuffer);
                }
            }
        });
    }

    private void executeGenerateMipmapPacket(GenerateMipmapPacket packet) {
        BackendInstalledTexture installedTexture = resourceRegistry.resolveTexture(packet.textureId());
        if (installedTexture instanceof OpenGLTextureHandleResource openGLTextureHandleResource) {
            api.bindTexture(GL11.GL_TEXTURE_2D, openGLTextureHandleResource.textureHandle());
            api.generateMipmap(GL11.GL_TEXTURE_2D);
            api.bindTexture(GL11.GL_TEXTURE_2D, 0);
        }
    }

    private void executeCopyTexturePacket(CopyTexturePacket packet) {
        if (packet == null || packet.sourceTextureId() == null || packet.destinationTextureId() == null) {
            return;
        }
        BackendInstalledTexture source = resourceRegistry.resolveTexture(packet.sourceTextureId());
        BackendInstalledTexture destination = resourceRegistry.resolveTexture(packet.destinationTextureId());
        if (!(source instanceof OpenGLTextureHandleResource sourceHandle)
                || !(destination instanceof OpenGLTextureHandleResource destinationHandle)) {
            return;
        }

        int width = packet.width();
        int height = packet.height();
        if (width <= 0 || height <= 0) {
            int sourceWidth = source instanceof rogo.sketch.core.resource.vision.Texture sourceTexture
                    ? sourceTexture.getCurrentWidth()
                    : 0;
            int sourceHeight = source instanceof rogo.sketch.core.resource.vision.Texture sourceTexture
                    ? sourceTexture.getCurrentHeight()
                    : 0;
            int destinationWidth = destination instanceof rogo.sketch.core.resource.vision.Texture destinationTexture
                    ? destinationTexture.getCurrentWidth()
                    : 0;
            int destinationHeight = destination instanceof rogo.sketch.core.resource.vision.Texture destinationTexture
                    ? destinationTexture.getCurrentHeight()
                    : 0;
            width = width > 0 ? width : Math.min(sourceWidth, destinationWidth);
            height = height > 0 ? height : Math.min(sourceHeight, destinationHeight);
        }
        if (width <= 0 || height <= 0) {
            return;
        }

        boolean canUseCopyImage = !packet.depthCopy()
                && (GL.getCapabilities().OpenGL43 || GL.getCapabilities().GL_ARB_copy_image);
        if (canUseCopyImage) {
            GL43.glCopyImageSubData(
                    sourceHandle.textureHandle(),
                    GL11.GL_TEXTURE_2D,
                    0,
                    0,
                    0,
                    0,
                    destinationHandle.textureHandle(),
                    GL11.GL_TEXTURE_2D,
                    0,
                    0,
                    0,
                    0,
                    width,
                    height,
                    1);
        } else {
            copyTextureWithFramebufferBlit(
                    sourceHandle.textureHandle(),
                    destinationHandle.textureHandle(),
                    width,
                    height,
                    packet.depthCopy());
        }
        GL42.glMemoryBarrier(
                GL42.GL_TEXTURE_FETCH_BARRIER_BIT
                        | GL42.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT
                        | GL42.GL_TEXTURE_UPDATE_BARRIER_BIT);
    }

    private void copyTextureWithFramebufferBlit(
            int sourceTextureHandle,
            int destinationTextureHandle,
            int width,
            int height,
            boolean depthCopy) {
        int readFramebuffer = api.createFramebuffer();
        int drawFramebuffer = api.createFramebuffer();
        int attachment = depthCopy ? GL30.GL_DEPTH_ATTACHMENT : GL30.GL_COLOR_ATTACHMENT0;
        int mask = depthCopy ? GL11.GL_DEPTH_BUFFER_BIT : GL11.GL_COLOR_BUFFER_BIT;
        try {
            api.bindFrameBuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
            api.framebufferTexture2D(readFramebuffer, attachment, GL11.GL_TEXTURE_2D, sourceTextureHandle, 0);
            api.bindFrameBuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
            api.framebufferTexture2D(drawFramebuffer, attachment, GL11.GL_TEXTURE_2D, destinationTextureHandle, 0);
            GL30.glBlitFramebuffer(
                    0,
                    0,
                    width,
                    height,
                    0,
                    0,
                    width,
                    height,
                    mask,
                    GL11.GL_NEAREST);
        } finally {
            api.bindFrameBuffer(GL30.GL_READ_FRAMEBUFFER, 0);
            api.bindFrameBuffer(GL30.GL_DRAW_FRAMEBUFFER, 0);
            api.deleteFramebuffer(readFramebuffer);
            api.deleteFramebuffer(drawFramebuffer);
        }
    }

    private Optional<RenderTarget> resolveLogicalRenderTarget(KeyId renderTargetId) {
        RenderTarget renderTarget = resourceRegistry.resolveLogicalResource(ResourceTypes.RENDER_TARGET, renderTargetId) instanceof RenderTarget target
                ? target
                : null;
        return Optional.ofNullable(renderTarget);
    }

    private BackendInstalledRenderTarget resolveInstalledRenderTarget(KeyId renderTargetId, RenderTarget logicalTarget) {
        return resourceRegistry.resolveRenderTarget(renderTargetId);
    }

    private void applyClearAttachments(ClearPacket packet, RenderTarget renderTarget) {
        List<Integer> activeBuffers = resolveDrawBuffers(packet.colorAttachments(), renderTarget);
        if (activeBuffers == null) {
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

    private List<Integer> resolveDrawBuffers(List<Object> colorAttachments, RenderTarget renderTarget) {
        if (renderTarget instanceof StandardRenderTarget standardRenderTarget) {
            List<Integer> activeBuffers = new ArrayList<>();
            List<KeyId> attachments = standardRenderTarget.getColorAttachmentIds();
            if (colorAttachments == null || colorAttachments.isEmpty()) {
                for (int i = 0; i < attachments.size(); i++) {
                    activeBuffers.add(GL30.GL_COLOR_ATTACHMENT0 + i);
                }
                return activeBuffers;
            }
            for (Object component : colorAttachments) {
                if (component instanceof KeyId keyId) {
                    int index = attachments.indexOf(keyId);
                    if (index >= 0) {
                        activeBuffers.add(GL30.GL_COLOR_ATTACHMENT0 + index);
                    }
                } else if (component instanceof Integer value) {
                    activeBuffers.add(GL30.GL_COLOR_ATTACHMENT0 + value);
                }
            }
            return activeBuffers;
        }
        if (colorAttachments == null || colorAttachments.isEmpty()) {
            return null;
        }

        List<Integer> activeBuffers = new ArrayList<>();
        for (Object component : colorAttachments) {
            if (component instanceof Integer value) {
                activeBuffers.add(value);
            }
        }
        return activeBuffers;
    }

    private void completePacket(RenderPacket packet, RenderContext context) {
        context.set(RenderContext.RENDERED, true);
    }

    private String debugLabel(RenderPacket packet) {
        if (packet == null) {
            return "sketch:packet:null";
        }
        StringBuilder builder = new StringBuilder("sketch:");
        builder.append(packet.getClass().getSimpleName());
        if (packet.stageId() != null) {
            builder.append(" stage=").append(packet.stageId());
        }
        if (packet instanceof DrawPacket drawPacket
                && drawPacket.completionSubjects() != null
                && !drawPacket.completionSubjects().isEmpty()) {
            GraphicsUniformSubject subject = drawPacket.completionSubjects().get(0);
            if (subject != null && subject.identifier() != null) {
                builder.append(" graphics=").append(subject.identifier());
            }
        }
        return builder.toString();
    }

    private void pushDebugGroup(String label) {
        if (!RenderDocRuntime.enabled() || label == null || label.isBlank() || GL.getCapabilities() == null) {
            return;
        }
        if (GL.getCapabilities().OpenGL43) {
            GL43.glPushDebugGroup(GL43.GL_DEBUG_SOURCE_APPLICATION, 0, label);
        }
    }

    private void popDebugGroup() {
        if (!RenderDocRuntime.enabled() || GL.getCapabilities() == null) {
            return;
        }
        if (GL.getCapabilities().OpenGL43) {
            GL43.glPopDebugGroup();
        }
    }
}

