package rogo.sketch.backend.opengl;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL42;
import org.lwjgl.opengl.GL43;
import org.lwjgl.opengl.GL45;
import rogo.sketch.core.api.GpuObject;
import rogo.sketch.core.api.ShaderProvider;
import rogo.sketch.core.api.graphics.Graphics;
import rogo.sketch.core.backend.BackendCapabilities;
import rogo.sketch.core.backend.BackendFrameExecutor;
import rogo.sketch.core.backend.BackendKind;
import rogo.sketch.core.backend.BackendRuntime;
import rogo.sketch.core.backend.BackendStageScope;
import rogo.sketch.core.backend.BackendWorkerLane;
import rogo.sketch.core.driver.GLRuntimeFlags;
import rogo.sketch.core.driver.GraphicsAPI;
import rogo.sketch.core.driver.state.gl.ColorMaskState;
import rogo.sketch.core.driver.state.snapshot.GLStateSnapshot;
import rogo.sketch.core.driver.state.snapshot.SnapshotScope;
import rogo.sketch.core.packet.BindRenderTargetPacket;
import rogo.sketch.core.packet.ClearPacket;
import rogo.sketch.core.packet.DispatchPacket;
import rogo.sketch.core.packet.DrawBuffersPacket;
import rogo.sketch.core.packet.DrawPacket;
import rogo.sketch.core.packet.DrawPlan;
import rogo.sketch.core.packet.GenerateMipmapPacket;
import rogo.sketch.core.packet.PipelineStateKey;
import rogo.sketch.core.packet.RenderPacket;
import rogo.sketch.core.packet.RenderPacketQueue;
import rogo.sketch.core.pipeline.GraphicsPipeline;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.RenderStateManager;
import rogo.sketch.core.pipeline.data.FrameDataDomain;
import rogo.sketch.core.pipeline.data.GeometryFrameData;
import rogo.sketch.core.pipeline.module.diagnostic.RenderTraceRecorder;
import rogo.sketch.core.resource.GraphicsResourceManager;
import rogo.sketch.core.resource.ResourceBinding;
import rogo.sketch.core.resource.ResourceTypes;
import rogo.sketch.core.resource.buffer.IndirectCommandBuffer;
import rogo.sketch.core.resource.buffer.VertexResource;
import rogo.sketch.core.resource.vision.RenderTarget;
import rogo.sketch.core.resource.vision.StandardRenderTarget;
import rogo.sketch.core.shader.ComputeShader;
import rogo.sketch.core.shader.uniform.UniformHookGroup;
import rogo.sketch.core.shader.uniform.UniformValueSnapshot;
import rogo.sketch.core.util.KeyId;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class LegacyGraphicsBackendRuntime implements BackendRuntime {
    private final BackendKind kind;
    private final GraphicsAPI api;
    private final long mainWindowHandle;
    private final BackendCapabilities capabilities;
    private final BackendFrameExecutor frameExecutor = new LegacyFrameExecutor();

    public LegacyGraphicsBackendRuntime(BackendKind kind, GraphicsAPI api, long mainWindowHandle) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.api = Objects.requireNonNull(api, "api");
        this.mainWindowHandle = mainWindowHandle;
        this.capabilities = new BackendCapabilities(
                GLRuntimeFlags.GL_WORKER_ENABLED,
                GLRuntimeFlags.allowUploadWorker(),
                GLRuntimeFlags.allowComputeWorker());
    }

    @Override
    public String backendName() {
        return api.getAPIName();
    }

    @Override
    public BackendKind kind() {
        return kind;
    }

    @Override
    public BackendCapabilities capabilities() {
        return capabilities;
    }

    @Override
    public BackendFrameExecutor frameExecutor() {
        return frameExecutor;
    }

    @Override
    public GraphicsAPI legacyGraphicsApi() {
        return api;
    }

    @Override
    public void registerMainThread() {
        api.registerMainThread();
    }

    @Override
    public boolean isMainThread() {
        return api.isMainThread();
    }

    @Override
    public void assertMainThread(String caller) {
        api.assertMainThread(caller);
    }

    @Override
    public void assertRenderContext(String caller) {
        api.assertGLContext(caller);
    }

    @Override
    public void initializeWorkerLane(BackendWorkerLane lane) {
        if (!capabilities.workerLanesSupported()) {
            return;
        }
        switch (lane) {
            case RENDER_ASYNC -> api.initRenderWorkerContext(mainWindowHandle);
            case TICK_ASYNC -> api.initTickWorkerContext(mainWindowHandle);
        }
    }

    @Override
    public void destroyWorkerLane(BackendWorkerLane lane) {
        if (!capabilities.workerLanesSupported()) {
            return;
        }
        switch (lane) {
            case RENDER_ASYNC -> api.destroyRenderWorkerContext();
            case TICK_ASYNC -> api.destroyTickWorkerContext();
        }
    }

    @Override
    public void onWorkerLaneStart(BackendWorkerLane lane) {
        switch (lane) {
            case RENDER_ASYNC -> api.onRenderWorkerThreadStart();
            case TICK_ASYNC -> api.onTickWorkerThreadStart();
        }
    }

    @Override
    public void onWorkerLaneEnd(BackendWorkerLane lane) {
        switch (lane) {
            case RENDER_ASYNC -> api.onRenderWorkerThreadEnd();
            case TICK_ASYNC -> api.onTickWorkerThreadEnd();
        }
    }

    private final class LegacyFrameExecutor implements BackendFrameExecutor {
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
            return () -> api.restore(snapshot);
        }

        @Override
        public <C extends RenderContext> void executePacketGroup(
                GraphicsPipeline<C> pipeline,
                PipelineStateKey stateKey,
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
            ShaderProvider shaderProvider = context.shaderProvider();
            if (shaderProvider != null) {
                shaderProvider.getUniformHookGroup().updateUniforms(context);
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
            ShaderProvider shaderProvider = context.shaderProvider();
            if (shaderProvider != null) {
                shaderProvider.getUniformHookGroup().updateUniforms(context);
            }
            executePacket(pipeline, packet, context);
        }

        private <C extends RenderContext> void executePacket(GraphicsPipeline<C> pipeline, RenderPacket packet, C context) {
            applyUniformSnapshot(packet.uniformSnapshot(), context);
            applyResourceBinding(packet.bindingPlan(), context);

            if (packet instanceof DrawPacket drawPacket) {
                executeDrawPacket(pipeline, drawPacket);
            } else if (packet instanceof DispatchPacket dispatchPacket) {
                executeDispatchPacket(dispatchPacket, context);
            } else if (packet instanceof ClearPacket clearPacket) {
                executeClearPacket(clearPacket, context);
            } else if (packet instanceof DrawBuffersPacket drawBuffersPacket) {
                executeDrawBuffersPacket(drawBuffersPacket);
            } else if (packet instanceof GenerateMipmapPacket generateMipmapPacket) {
                executeGenerateMipmapPacket(generateMipmapPacket);
            } else if (packet instanceof BindRenderTargetPacket bindRenderTargetPacket) {
                executeBindRenderTargetPacket(bindRenderTargetPacket);
            } else {
                throw new IllegalArgumentException("Unsupported render packet: " + packet.getClass().getName());
            }

            completePacket(packet, context);
        }

        private <C extends RenderContext> void applyUniformSnapshot(UniformValueSnapshot snapshot, C context) {
            if (snapshot == null || snapshot.isEmpty()) {
                return;
            }
            ShaderProvider shaderProvider = context.shaderProvider();
            if (shaderProvider == null) {
                return;
            }
            UniformHookGroup uniformHookGroup = shaderProvider.getUniformHookGroup();
            snapshot.applyTo(uniformHookGroup);
        }

        private <C extends RenderContext> void applyResourceBinding(rogo.sketch.core.packet.ResourceBindingPlan bindingPlan, C context) {
            if (bindingPlan == null) {
                return;
            }
            ResourceBinding binding = bindingPlan.binding();
            if (binding != null) {
                binding.bind(context);
            }
        }

        private <C extends RenderContext> void executeDrawPacket(GraphicsPipeline<C> pipeline, DrawPacket packet) {
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
            if (geometryBinding.vertexResource() == null) {
                traceBackendDrop(pipeline, packet, "backend_missing_vertex_resource(handle=" + packet.geometryHandle() + ")");
                return;
            }
            if (packet.drawPlan() == null) {
                traceBackendDrop(pipeline, packet, "backend_missing_draw_plan");
                return;
            }

            VertexResource vertexResource = geometryBinding.vertexResource();
            IndirectCommandBuffer indirectBuffer = geometryBinding.indirectBuffer();
            DrawPlan drawPlan = packet.drawPlan();
            if (drawPlan.submission() != DrawPlan.DrawSubmission.MULTI_DRAW_INDIRECT && drawPlan.directItems().isEmpty()) {
                traceBackendDrop(pipeline, packet, "backend_empty_direct_items");
                return;
            }

            traceBackendExecuted(pipeline, packet);
            vertexResource.bind();
            try {
                if (vertexResource.hasIndices() && vertexResource.getIndexBuffer().isDirty()) {
                    vertexResource.getIndexBuffer().upload();
                }

                if (drawPlan.submission() == DrawPlan.DrawSubmission.MULTI_DRAW_INDIRECT) {
                    if (indirectBuffer == null || drawPlan.drawCount() <= 0) {
                        traceBackendDrop(pipeline, packet, "backend_invalid_indirect_plan");
                        return;
                    }

                    indirectBuffer.bind();
                    try {
                        if (drawPlan.primitiveType().requiresIndexBuffer()) {
                            GL43.glMultiDrawElementsIndirect(
                                    drawPlan.primitiveType().glType(),
                                    vertexResource.getIndexBuffer().currentIndexType().glType(),
                                    drawPlan.indirectOffset(),
                                    drawPlan.drawCount(),
                                    drawPlan.indirectStride());
                        } else {
                            GL43.glMultiDrawArraysIndirect(
                                    drawPlan.primitiveType().glType(),
                                    drawPlan.indirectOffset(),
                                    drawPlan.drawCount(),
                                    drawPlan.indirectStride());
                        }
                    } finally {
                        IndirectCommandBuffer.unBind();
                    }
                    return;
                }

                if (drawPlan.directItems().isEmpty()) {
                    return;
                }

                for (DrawPlan.DirectDrawItem item : drawPlan.directItems()) {
                    if (!item.indexed()) {
                        if (item.instanceCount() <= 0 || item.vertexCount() <= 0) {
                            continue;
                        }
                        GL42.glDrawArraysInstancedBaseInstance(
                                drawPlan.primitiveType().glType(),
                                item.firstVertex(),
                                item.vertexCount(),
                                item.instanceCount(),
                                item.baseInstance());
                        continue;
                    }

                    if (item.indexedShard() == null || item.instanceCount() <= 0) {
                        continue;
                    }

                    GL45.glDrawElementsInstancedBaseVertexBaseInstance(
                            drawPlan.primitiveType().glType(),
                            item.indexedShard().indexCount(),
                            vertexResource.getIndexBuffer().currentIndexType().glType(),
                            item.indexedShard().indicesOffset(),
                            item.instanceCount(),
                            (int) item.indexedShard().vertexOffset(),
                            item.baseInstance());
                }
            } finally {
                vertexResource.unbind();
            }
        }

        private <C extends RenderContext> void traceBackendExecuted(GraphicsPipeline<C> pipeline, DrawPacket packet) {
            RenderTraceRecorder renderTraceRecorder = pipeline.renderTraceRecorder();
            if (renderTraceRecorder == null || packet.completionGraphics() == null) {
                return;
            }
            for (Graphics graphics : packet.completionGraphics()) {
                if (graphics != null) {
                    renderTraceRecorder.recordBackendExecuted(packet.stageId(), graphics);
                }
            }
        }

        private <C extends RenderContext> void traceBackendDrop(GraphicsPipeline<C> pipeline, DrawPacket packet, String reason) {
            RenderTraceRecorder renderTraceRecorder = pipeline.renderTraceRecorder();
            if (renderTraceRecorder == null || packet.completionGraphics() == null) {
                return;
            }
            for (Graphics graphics : packet.completionGraphics()) {
                if (graphics != null) {
                    renderTraceRecorder.recordDrop(packet.stageId(), graphics, reason);
                }
            }
        }

        private <C extends RenderContext> void executeDispatchPacket(DispatchPacket packet, C context) {
            ComputeShader computeShader = context.shaderProvider() instanceof ComputeShader shader ? shader : null;
            if (packet.dispatchFunction() != null) {
                packet.dispatchFunction().accept(context, computeShader);
            } else if (packet.computeInfo() != null) {
                packet.computeInfo().dispatch(context, computeShader);
            } else {
                api.dispatchCompute(packet.workGroupsX(), packet.workGroupsY(), packet.workGroupsZ());
            }
        }

        private <C extends RenderContext> void executeClearPacket(ClearPacket packet, C context) {
            resolveRenderTarget(packet.renderTargetId()).ifPresent(renderTarget -> {
                if (packet.colorMask() != null && context.renderStateManager() != null && packet.colorMask().length >= 4) {
                    context.renderStateManager().changeState(new ColorMaskState(
                            packet.colorMask()[0],
                            packet.colorMask()[1],
                            packet.colorMask()[2],
                            packet.colorMask()[3]), context);
                }

                renderTarget.bind();
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
            });
        }

        private void executeDrawBuffersPacket(DrawBuffersPacket packet) {
            resolveRenderTarget(packet.renderTargetId()).ifPresent(renderTarget -> {
                renderTarget.bind();

                List<Integer> activeBuffers = new ArrayList<>();
                boolean shouldApply = false;

                if (renderTarget instanceof StandardRenderTarget standardRenderTarget) {
                    shouldApply = true;
                    if (packet.colorComponents() == null) {
                        List<KeyId> attachments = standardRenderTarget.getColorAttachmentIds();
                        for (int i = 0; i < attachments.size(); i++) {
                            activeBuffers.add(GL30.GL_COLOR_ATTACHMENT0 + i);
                        }
                    } else {
                        List<KeyId> attachments = standardRenderTarget.getColorAttachmentIds();
                        for (Object component : packet.colorComponents()) {
                            if (component instanceof KeyId keyId) {
                                int index = attachments.indexOf(keyId);
                                if (index >= 0) {
                                    activeBuffers.add(GL30.GL_COLOR_ATTACHMENT0 + index);
                                }
                            } else if (component instanceof Integer value) {
                                activeBuffers.add(value);
                            }
                        }
                    }
                } else if (packet.colorComponents() != null) {
                    shouldApply = true;
                    for (Object component : packet.colorComponents()) {
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
            });
        }

        private void executeGenerateMipmapPacket(GenerateMipmapPacket packet) {
            GraphicsResourceManager.getInstance()
                    .getReference(ResourceTypes.TEXTURE, packet.textureId())
                    .ifPresent(texture -> {
                        if (texture instanceof GpuObject gpuObject) {
                            api.bindTexture(GL11.GL_TEXTURE_2D, gpuObject.getHandle());
                            api.generateMipmap(GL11.GL_TEXTURE_2D);
                            api.bindTexture(GL11.GL_TEXTURE_2D, 0);
                        }
                    });
        }

        private void executeBindRenderTargetPacket(BindRenderTargetPacket packet) {
            resolveRenderTarget(packet.renderTargetId()).ifPresent(RenderTarget::bind);
        }

        private Optional<RenderTarget> resolveRenderTarget(KeyId renderTargetId) {
            RenderTarget renderTarget = (RenderTarget) GraphicsResourceManager.getInstance()
                    .getResource(ResourceTypes.RENDER_TARGET, renderTargetId);
            return Optional.ofNullable(renderTarget);
        }

        private <C extends RenderContext> void completePacket(RenderPacket packet, C context) {
            context.set(KeyId.of("rendered"), true);
            List<? extends Graphics> completionGraphics = packet.completionGraphics();
            if (completionGraphics == null) {
                return;
            }
            for (Graphics graphics : completionGraphics) {
                graphics.afterDraw(context);
            }
        }
    }
}
