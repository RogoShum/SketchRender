package rogo.sketch.core.graphics.ecs;

import rogo.sketch.core.api.graphics.ComputeDispatchCommand;
import rogo.sketch.core.api.graphics.DescriptorStability;
import rogo.sketch.core.api.graphics.SubmissionCapability;
import rogo.sketch.core.pipeline.CompiledRenderSetting;
import rogo.sketch.core.packet.ExecutionDomain;
import rogo.sketch.core.pipeline.PartialRenderSetting;
import rogo.sketch.core.pipeline.PipelineType;
import rogo.sketch.core.pipeline.RenderSetting;
import rogo.sketch.core.pipeline.RenderSettingCompiler;
import rogo.sketch.core.pipeline.flow.RenderFlowType;
import rogo.sketch.core.pipeline.flow.ecs.GraphicsContainerHints;
import rogo.sketch.core.pipeline.parmeter.FunctionParameter;
import rogo.sketch.core.pipeline.parmeter.RenderParameter;
import rogo.sketch.core.util.KeyId;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.LongSupplier;

public final class GraphicsEntityPresets {
    private GraphicsEntityPresets() {
    }

    public static GraphicsEntityBlueprint.Builder auxiliary(
            KeyId identifier,
            BooleanSupplier shouldRender,
            BooleanSupplier shouldDiscard) {
        return base(identifier, shouldRender, shouldDiscard);
    }

    public static GraphicsEntityBlueprint.Builder raster(
            KeyId identifier,
            KeyId stageId,
            PipelineType pipelineType,
            RenderParameter renderParameter,
            KeyId containerType,
            Object sortKey,
            long orderHint,
            int layerHint,
            BooleanSupplier shouldRender,
            BooleanSupplier shouldDiscard,
            SubmissionCapability submissionCapability,
            DescriptorStability descriptorStability,
            LongSupplier descriptorVersionSupplier,
            Function<RenderParameter, CompiledRenderSetting> descriptorBuilder) {
        return base(identifier, shouldRender, shouldDiscard)
                .put(GraphicsBuiltinComponents.STAGE_BINDING, new GraphicsBuiltinComponents.StageBindingComponent(
                        stageId,
                        pipelineType,
                        renderParameter))
                .put(GraphicsBuiltinComponents.CONTAINER_HINT, new GraphicsBuiltinComponents.ContainerHintComponent(
                        containerType != null ? containerType : GraphicsContainerHints.DEFAULT,
                        sortKey != null ? sortKey : Long.valueOf(orderHint),
                        orderHint,
                        layerHint))
                .put(GraphicsBuiltinComponents.RASTER_RENDERABLE, new GraphicsBuiltinComponents.RasterRenderableComponent(true))
                .put(GraphicsBuiltinComponents.SUBMISSION_CAPABILITY, new GraphicsBuiltinComponents.SubmissionCapabilityComponent(
                        submissionCapability))
                .put(GraphicsBuiltinComponents.RENDER_DESCRIPTOR, new GraphicsBuiltinComponents.RenderDescriptorComponent(
                        descriptorStability,
                        descriptorVersionSupplier,
                        descriptorBuilder))
                .put(GraphicsBuiltinComponents.DESCRIPTOR_VERSION, new GraphicsBuiltinComponents.DescriptorVersionComponent(descriptorVersionSupplier));
    }

    public static GraphicsEntityBlueprint.Builder compute(
            KeyId identifier,
            KeyId stageId,
            RenderParameter renderParameter,
            int priority,
            BooleanSupplier shouldRender,
            BooleanSupplier shouldDiscard,
            DescriptorStability descriptorStability,
            LongSupplier descriptorVersionSupplier,
            Function<RenderParameter, CompiledRenderSetting> descriptorBuilder,
            ComputeDispatchCommand dispatchCommand) {
        return base(identifier, shouldRender, shouldDiscard)
                .put(GraphicsBuiltinComponents.STAGE_BINDING, new GraphicsBuiltinComponents.StageBindingComponent(
                        stageId,
                        PipelineType.COMPUTE,
                        renderParameter))
                .put(GraphicsBuiltinComponents.CONTAINER_HINT, new GraphicsBuiltinComponents.ContainerHintComponent(
                        GraphicsContainerHints.PRIORITY,
                        Integer.valueOf(priority),
                        0L,
                        0))
                .put(GraphicsBuiltinComponents.SUBMISSION_CAPABILITY, new GraphicsBuiltinComponents.SubmissionCapabilityComponent(
                        SubmissionCapability.DIRECT_BATCHABLE))
                .put(GraphicsBuiltinComponents.COMPUTE_DISPATCH, new GraphicsBuiltinComponents.ComputeDispatchComponent(dispatchCommand))
                .put(GraphicsBuiltinComponents.RENDER_DESCRIPTOR, new GraphicsBuiltinComponents.RenderDescriptorComponent(
                        descriptorStability,
                        descriptorVersionSupplier,
                        descriptorBuilder))
                .put(GraphicsBuiltinComponents.DESCRIPTOR_VERSION, new GraphicsBuiltinComponents.DescriptorVersionComponent(descriptorVersionSupplier));
    }

    public static GraphicsEntityBlueprint.Builder function(
            KeyId identifier,
            KeyId stageId,
            int priority,
            BooleanSupplier shouldRender,
            BooleanSupplier shouldDiscard,
            Object payload) {
        return base(identifier, shouldRender, shouldDiscard)
                .put(GraphicsBuiltinComponents.STAGE_BINDING, new GraphicsBuiltinComponents.StageBindingComponent(
                        stageId,
                        PipelineType.FUNCTION,
                        FunctionParameter.FUNCTION_PARAMETER))
                .put(GraphicsBuiltinComponents.CONTAINER_HINT, new GraphicsBuiltinComponents.ContainerHintComponent(
                        GraphicsContainerHints.PRIORITY,
                        Integer.valueOf(priority),
                        0L,
                        0))
                .put(GraphicsBuiltinComponents.SUBMISSION_CAPABILITY, new GraphicsBuiltinComponents.SubmissionCapabilityComponent(
                        SubmissionCapability.DIRECT_BATCHABLE))
                .put(GraphicsBuiltinComponents.FUNCTION_INVOKE, new GraphicsBuiltinComponents.FunctionInvokeComponent(
                        null,
                        payload,
                        priority));
    }

    public static GraphicsEntityBlueprint.Builder withResourceOrigin(
            GraphicsEntityBlueprint.Builder builder,
            KeyId resourceType) {
        return builder.put(GraphicsBuiltinComponents.RESOURCE_ORIGIN, new GraphicsBuiltinComponents.ResourceOriginComponent(resourceType));
    }

    public static GraphicsEntityBlueprint.Builder withTags(
            GraphicsEntityBlueprint.Builder builder,
            KeyId... tags) {
        Set<KeyId> values = new LinkedHashSet<>();
        if (tags != null) {
            values.addAll(Arrays.asList(tags));
            values.remove(null);
        }
        return builder.put(GraphicsBuiltinComponents.GRAPHICS_TAGS, new GraphicsBuiltinComponents.GraphicsTagsComponent(values));
    }

    public static long partialDescriptorVersion(PartialRenderSetting partialRenderSetting) {
        return partialRenderSetting != null ? partialRenderSetting.hashCode() : PartialRenderSetting.EMPTY.hashCode();
    }

    public static CompiledRenderSetting compilePartialDescriptor(
            RenderParameter renderParameter,
            PartialRenderSetting partialRenderSetting) {
        PartialRenderSetting effectiveSetting = partialRenderSetting != null ? partialRenderSetting : PartialRenderSetting.EMPTY;
        if (renderParameter != null && RenderFlowType.COMPUTE.equals(renderParameter.getFlowType())
                && effectiveSetting.executionDomain() != ExecutionDomain.COMPUTE) {
            throw new IllegalArgumentException(
                    "Compute graphics preset requires COMPUTE partial setting, found: "
                            + effectiveSetting.executionDomain());
        }
        return RenderSettingCompiler.compile(RenderSetting.fromPartial(
                renderParameter,
                effectiveSetting));
    }

    private static GraphicsEntityBlueprint.Builder base(
            KeyId identifier,
            BooleanSupplier shouldRender,
            BooleanSupplier shouldDiscard) {
        GraphicsBuiltinComponents.LifecycleState initialLifecycle = sampleLifecycle(shouldRender, shouldDiscard);
        return GraphicsEntityBlueprint.builder()
                .put(GraphicsBuiltinComponents.IDENTITY, new GraphicsBuiltinComponents.IdentityComponent(identifier))
                .put(GraphicsBuiltinComponents.LIFECYCLE, new GraphicsBuiltinComponents.LifecycleComponent(
                        initialLifecycle.shouldRender(),
                        initialLifecycle.shouldDiscard()))
                .put(GraphicsBuiltinComponents.LIFECYCLE_BINDING, new GraphicsBuiltinComponents.LifecycleBindingComponent(
                        GraphicsUpdateDomain.ASYNC_TICK,
                        new GraphicsBuiltinComponents.LifecycleAuthoring() {
                            @Override
                            public GraphicsBuiltinComponents.LifecycleState sampleLifecycle() {
                                return GraphicsEntityPresets.sampleLifecycle(shouldRender, shouldDiscard);
                            }

                            @Override
                            public long version() {
                                boolean render = shouldRender == null || shouldRender.getAsBoolean();
                                boolean discard = shouldDiscard != null && shouldDiscard.getAsBoolean();
                                return (render ? 2L : 0L) | (discard ? 1L : 0L);
                            }
                        }))
                .put(GraphicsBuiltinComponents.GRAPHICS_TAGS, new GraphicsBuiltinComponents.GraphicsTagsComponent(Set.of()));
    }

    private static GraphicsBuiltinComponents.LifecycleState sampleLifecycle(
            BooleanSupplier shouldRender,
            BooleanSupplier shouldDiscard) {
        return new GraphicsBuiltinComponents.LifecycleState(
                shouldRender == null || shouldRender.getAsBoolean(),
                shouldDiscard != null && shouldDiscard.getAsBoolean());
    }
}
