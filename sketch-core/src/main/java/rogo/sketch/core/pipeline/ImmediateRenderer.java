package rogo.sketch.core.pipeline;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.graphics.ecs.GraphicsBuiltinComponents;
import rogo.sketch.core.graphics.ecs.GraphicsEntityAssembler;
import rogo.sketch.core.graphics.ecs.GraphicsEntityBlueprint;
import rogo.sketch.core.graphics.ecs.GraphicsEntityId;
import rogo.sketch.core.graphics.ecs.GraphicsWorld;
import rogo.sketch.core.driver.GraphicsDriver;
import rogo.sketch.core.packet.ExecutionKey;
import rogo.sketch.core.packet.RenderPacket;
import rogo.sketch.core.packet.RenderPacketQueue;
import rogo.sketch.core.pipeline.data.FrameDataDomain;
import rogo.sketch.core.pipeline.flow.RenderFlowType;
import rogo.sketch.core.pipeline.flow.RenderPostProcessors;
import rogo.sketch.core.pipeline.flow.impl.RasterizationPostProcessor;
import rogo.sketch.core.pipeline.flow.v2.StageEntityView;
import rogo.sketch.core.pipeline.flow.v2.StageFlowScene;
import rogo.sketch.core.pipeline.module.diagnostic.RenderTraceRecorder;
import rogo.sketch.core.shader.uniform.FrameUniformSnapshot;
import rogo.sketch.core.util.KeyId;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

final class ImmediateRenderer<C extends RenderContext> {
    private final GraphicsPipeline<C> pipeline;
    private final KeyId immediateStageId;
    private final GraphicsWorld graphicsWorld;
    private final GraphicsEntityAssembler graphicsEntityAssembler;
    private final RenderPacketQueue<C> renderPacketQueue;
    private final RenderStateManager renderStateManager;
    private final RenderTraceRecorder renderTraceRecorder;

    ImmediateRenderer(
            GraphicsPipeline<C> pipeline,
            KeyId immediateStageId,
            GraphicsWorld graphicsWorld,
            GraphicsEntityAssembler graphicsEntityAssembler,
            RenderPacketQueue<C> renderPacketQueue,
            RenderStateManager renderStateManager,
            RenderTraceRecorder renderTraceRecorder) {
        this.pipeline = pipeline;
        this.immediateStageId = immediateStageId;
        this.graphicsWorld = graphicsWorld;
        this.graphicsEntityAssembler = graphicsEntityAssembler;
        this.renderPacketQueue = renderPacketQueue;
        this.renderStateManager = renderStateManager;
        this.renderTraceRecorder = renderTraceRecorder;
    }

    void renderImmediate(
            GraphicsEntityBlueprint blueprint,
            @Nullable C contextOverride,
            @Nullable C currentContext,
            Function<GraphicsEntityId, StageEntityView.Entry> snapshotEntity) {
        List<RenderPacket> packets = buildImmediatePackets(blueprint, contextOverride, currentContext, snapshotEntity);
        C context = contextOverride != null ? contextOverride : currentContext;
        if (context == null || packets.isEmpty()) {
            return;
        }
        for (RenderPacket packet : packets) {
            renderPacketQueue.executeImmediate(packet, renderStateManager, context);
        }
    }

    List<RenderPacket> buildImmediatePackets(
            GraphicsEntityBlueprint blueprint,
            @Nullable C contextOverride,
            @Nullable C currentContext,
            Function<GraphicsEntityId, StageEntityView.Entry> snapshotEntity) {
        if (blueprint == null || blueprint.isDisposed()) {
            return List.of();
        }

        C context = contextOverride != null ? contextOverride : currentContext;
        if (context == null) {
            return List.of();
        }

        GraphicsBuiltinComponents.StageBindingComponent stageBinding = blueprint.component(GraphicsBuiltinComponents.STAGE_BINDING);
        if (stageBinding == null || stageBinding.renderParameter() == null || stageBinding.renderParameter().isInvalid()) {
            return List.of();
        }

        GraphicsEntityId entityId = graphicsEntityAssembler.spawn(withImmediateStageBinding(blueprint));
        StageEntityView immediateView = new StageEntityView(
                immediateStageId,
                stageBinding.pipelineType(),
                List.of(snapshotEntity.apply(entityId)));

        PipelineType pipelineType = stageBinding.pipelineType();
        StageFlowScene<C> immediateScene = createImmediateStageScene(pipelineType);
        RenderPostProcessors postProcessors = new RenderPostProcessors();
        if (pipelineType == PipelineType.RASTERIZATION || pipelineType == PipelineType.TRANSLUCENT) {
            postProcessors.register(RenderFlowType.RASTERIZATION, new RasterizationPostProcessor());
        }

        try {
            FrameUniformSnapshot frameUniformSnapshot = FrameUniformSnapshot.capture(context);
            immediateScene.prepareForFrame(graphicsWorld, immediateView, context, frameUniformSnapshot);

            Map<ExecutionKey, List<RenderPacket>> packets = immediateScene.createRenderPackets(
                    immediateView,
                    pipelineType.getDefaultFlowType(),
                    postProcessors,
                    context,
                    frameUniformSnapshot);
            if (packets.isEmpty()) {
                return List.of();
            }

            GraphicsDriver.renderDevice().installImmediateGeometryBindings(pipeline, pipelineType, postProcessors);
            postProcessors.executeAllExcept(RenderFlowType.RASTERIZATION);

            List<RenderPacket> flattenedPackets = new ArrayList<>();
            for (List<RenderPacket> statePackets : packets.values()) {
                flattenedPackets.addAll(statePackets);
            }
            GraphicsDriver.resourceAllocator().installImmediateResourceBindings(flattenedPackets);
            return List.copyOf(flattenedPackets);
        } finally {
            graphicsEntityAssembler.destroy(entityId);
        }
    }

    private StageFlowScene<C> createImmediateStageScene(PipelineType pipelineType) {
        return pipelineType.createStageScene(
                immediateStageId,
                pipeline,
                FrameDataDomain.SYNC_READ,
                renderTraceRecorder);
    }

    private GraphicsEntityBlueprint withImmediateStageBinding(GraphicsEntityBlueprint blueprint) {
        GraphicsEntityBlueprint.Builder builder = GraphicsEntityBlueprint.builder();
        for (Map.Entry<rogo.sketch.core.graphics.ecs.GraphicsComponentType<?>, Object> entry : blueprint.components().entrySet()) {
            copyComponent(builder, entry.getKey(), entry.getValue());
        }
        GraphicsBuiltinComponents.StageBindingComponent existing = blueprint.component(GraphicsBuiltinComponents.STAGE_BINDING);
        if (existing != null) {
            builder.put(
                    GraphicsBuiltinComponents.STAGE_BINDING,
                    new GraphicsBuiltinComponents.StageBindingComponent(
                            immediateStageId,
                            existing.pipelineType(),
                            existing.renderParameter()));
        }
        return builder.build();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void copyComponent(
            GraphicsEntityBlueprint.Builder builder,
            rogo.sketch.core.graphics.ecs.GraphicsComponentType componentType,
            Object value) {
        builder.put(componentType, value);
    }
}
