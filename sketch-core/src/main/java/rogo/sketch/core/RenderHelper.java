package rogo.sketch.core;

import org.jetbrains.annotations.NotNull;
import rogo.sketch.core.api.ShaderProvider;
import rogo.sketch.core.api.graphics.DispatchableGraphics;
import rogo.sketch.core.api.graphics.FunctionalGraphics;
import rogo.sketch.core.api.graphics.Graphics;
import rogo.sketch.core.api.graphics.MeshBasedGraphics;
import rogo.sketch.core.command.RenderCommand;
import rogo.sketch.core.command.prosessor.GeometryBatchProcessor;
import rogo.sketch.core.pipeline.GraphicsPipeline;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.RenderSetting;
import rogo.sketch.core.pipeline.data.IndirectBufferData;
import rogo.sketch.core.pipeline.data.InstancedOffsetData;
import rogo.sketch.core.pipeline.data.PipelineDataStore;
import rogo.sketch.core.pipeline.flow.BatchContainer;
import rogo.sketch.core.pipeline.flow.RenderFlowType;
import rogo.sketch.core.pipeline.flow.RenderPostProcessors;
import rogo.sketch.core.pipeline.flow.impl.ComputeBatchContainer;
import rogo.sketch.core.pipeline.flow.impl.FunctionBatchContainer;
import rogo.sketch.core.pipeline.flow.impl.RasterizationBatchContainer;
import rogo.sketch.core.pipeline.flow.impl.RasterizationPostProcessor;
import rogo.sketch.core.pipeline.parmeter.RenderParameter;
import rogo.sketch.core.util.KeyId;
import rogo.sketch.core.vertex.VertexResourceManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RenderHelper {
    private static final KeyId IMMEDIATE_STAGE_ID = KeyId.of("sketch_render", "immediate");

    private final GraphicsPipeline<?> pipeline;
    private final PipelineDataStore pipelineDataRegistry = new PipelineDataStore();
    private final GeometryBatchProcessor batchProcessor;

    // Persistent BatchContainers for each flow type to avoid creation overhead
    private final Map<RenderFlowType, BatchContainer<?, ?>> persistentBatchContainers = new HashMap<>();

    public RenderHelper(GraphicsPipeline<?> pipeline) {
        this.pipeline = pipeline;
        pipelineDataRegistry.register(KeyId.of("indirect_buffers"), new IndirectBufferData());
        pipelineDataRegistry.register(KeyId.of("instanced_offsets"), new InstancedOffsetData());
        this.batchProcessor = new GeometryBatchProcessor(VertexResourceManager.globalInstance(), pipelineDataRegistry);

        // Initialize persistent BatchContainers for each flow type
        persistentBatchContainers.put(RenderFlowType.RASTERIZATION, new RasterizationBatchContainer());
        persistentBatchContainers.put(RenderFlowType.COMPUTE, new ComputeBatchContainer());
        persistentBatchContainers.put(RenderFlowType.FUNCTION, new FunctionBatchContainer());
    }

    public void renderInstanceImmediately(Graphics instance, @NotNull RenderParameter renderParameter) {
        RenderContext context = pipeline.currentContext();
        RenderSetting setting = RenderSetting.fromPartial(renderParameter, instance.getPartialRenderSetting());

        // 1. Setup Render State
        pipeline.renderStateManager().accept(setting, context);
        ShaderProvider shader = context.shaderProvider();
        shader.getUniformHookGroup().updateUniforms(context);

        // 2. Create Commands using GeometryBatchProcessor (new flow)
        RenderFlowType flowType = renderParameter.getFlowType();
        RenderPostProcessors postProcessors = new RenderPostProcessors();

        // Register appropriate post-processor based on flow type
        if (flowType == RenderFlowType.RASTERIZATION) {
            RasterizationPostProcessor rasterProcessor = new RasterizationPostProcessor();
            postProcessors.register(RenderFlowType.RASTERIZATION, rasterProcessor);
        }

        pipelineDataRegistry.reset();

        // Get persistent BatchContainer for this flow type
        BatchContainer<?, ?> batchContainer = persistentBatchContainers.get(flowType);
        if (batchContainer == null) {
            throw new IllegalArgumentException("Unsupported flow type: " + flowType);
        }

        // Clear previous instance data before registering new instance
        batchContainer.clear();

        // Register instance to persistent container
        registerInstanceToContainer(batchContainer, flowType, instance, renderParameter);

        // Create commands using new createCommands method
        @SuppressWarnings("unchecked")
        Map<RenderSetting, List<RenderCommand>> commandMap = batchProcessor.createCommands(
                (BatchContainer) batchContainer,
                flowType,
                IMMEDIATE_STAGE_ID,
                postProcessors,
                context);

        // Cleanup instance data after rendering (but keep container)
        batchContainer.clear();

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

    /**
     * Register an instance to the persistent BatchContainer for the given flow
     * type.
     */
    @SuppressWarnings("unchecked")
    private void registerInstanceToContainer(
            BatchContainer<?, ?> container,
            RenderFlowType flowType,
            Graphics instance,
            RenderParameter renderParameter) {

        if (flowType == RenderFlowType.RASTERIZATION && instance instanceof MeshBasedGraphics rasterizable) {
            ((RasterizationBatchContainer) container).registerInstance(rasterizable, renderParameter);
        } else if (flowType == RenderFlowType.COMPUTE && instance instanceof DispatchableGraphics dispatchable) {
            ((ComputeBatchContainer) container).registerInstance(dispatchable, renderParameter);
        } else if (flowType == RenderFlowType.FUNCTION && instance instanceof FunctionalGraphics function) {
            ((FunctionBatchContainer) container).registerInstance(function, renderParameter);
        } else {
            throw new IllegalArgumentException(
                    "Unsupported graphics type for flow type " + flowType + ": " + instance.getClass().getName());
        }
    }
}