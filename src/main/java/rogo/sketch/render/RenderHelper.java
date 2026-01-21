package rogo.sketch.render;

import rogo.sketch.api.ShaderProvider;
import rogo.sketch.api.graphics.Graphics;
import rogo.sketch.render.command.RenderCommand;
import rogo.sketch.render.command.prosessor.GeometryBatchProcessor;
import rogo.sketch.render.pipeline.GraphicsPipeline;
import rogo.sketch.render.pipeline.RenderContext;
import rogo.sketch.render.pipeline.RenderParameter;
import rogo.sketch.render.pipeline.RenderSetting;
import rogo.sketch.render.pipeline.flow.RenderFlowType;
import rogo.sketch.render.pipeline.flow.RenderPostProcessors;
import rogo.sketch.render.pipeline.flow.impl.RasterizationPostProcessor;
import rogo.sketch.render.pipeline.data.IndirectBufferData;
import rogo.sketch.render.pipeline.data.InstancedOffsetData;
import rogo.sketch.render.pipeline.data.PipelineDataStore;
import rogo.sketch.render.vertex.VertexResourceManager;
import rogo.sketch.util.KeyId;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class RenderHelper {
    private static final KeyId IMMEDIATE_STAGE_ID = KeyId.of("sketch_render", "immediate");

    private final GraphicsPipeline<?> pipeline;
    private final PipelineDataStore pipelineDataRegistry = new PipelineDataStore();
    private final GeometryBatchProcessor batchProcessor;

    public RenderHelper(GraphicsPipeline<?> pipeline) {
        this.pipeline = pipeline;
        pipelineDataRegistry.register(KeyId.of("indirect_buffers"), new IndirectBufferData());
        pipelineDataRegistry.register(KeyId.of("instanced_offsets"), new InstancedOffsetData());
        this.batchProcessor = new GeometryBatchProcessor(VertexResourceManager.globalInstance(), pipelineDataRegistry);
    }

    public void renderInstanceImmediately(Graphics instance, @Nonnull RenderParameter renderParameter) {
        RenderContext context = pipeline.currentContext();
        RenderSetting setting = RenderSetting.fromPartial(renderParameter, instance.getPartialRenderSetting());

        // 1. Setup Render State
        pipeline.renderStateManager().accept(setting, context);
        ShaderProvider shader = context.shaderProvider();
        shader.getUniformHookGroup().updateUniforms(context);

        // 2. Tick Instance
        if (instance.shouldTick()) {
            instance.tick(context);
        }

        // 3. Create Commands using GeometryBatchProcessor (new flow)
        Map<RenderParameter, Collection<Graphics>> instanceGroups = Map.of(renderParameter, List.of(instance));

        // Use batch processor facade to create commands for supported flows
        // Execute post-processing tasks immediately (e.g. upload)
        RenderPostProcessors postProcessors = new RenderPostProcessors();
        RasterizationPostProcessor rasterProcessor = new RasterizationPostProcessor();
        postProcessors.register(RenderFlowType.RASTERIZATION, rasterProcessor);

        pipelineDataRegistry.reset();
        Map<RenderSetting, List<RenderCommand>> commandMap = batchProcessor.createAllCommands(instanceGroups, IMMEDIATE_STAGE_ID, context, postProcessors); // Execute immediately via custom postProcessor

        // 4. Execute Commands Immediately
        for (List<RenderCommand> commands : commandMap.values()) {
            for (RenderCommand command : commands) {
                pipeline.getRenderCommandQueue().executeImmediate(command, setting, pipeline().renderStateManager(),
                        context);
            }
        }

        // Execute post-processing
        postProcessors.executeAll();

        // 5. Cleanup local buffers after immediate rendering
        // Keeping them might save allocation if reused often for same type,
        // but immediate rendering usually implies one-off or dynamic nature.
        // Clearing is safer to release memory if not reused.
        // However, IndirectCommandBuffer manages its own memory and grows.
        // Clearing it just resets counters.
        pipelineDataRegistry.reset();
    }

    public void addGraphicsInstance(Graphics instance, RenderParameter renderParameter) {
        pipeline.addGraphInstance(instance.getIdentifier(), instance, renderParameter);
    }

    public GraphicsPipeline<?> pipeline() {
        return pipeline;
    }
}