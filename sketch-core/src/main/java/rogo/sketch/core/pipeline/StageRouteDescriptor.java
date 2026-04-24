package rogo.sketch.core.pipeline;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.driver.state.RenderStatePatch;
import rogo.sketch.core.graphics.ecs.GraphicsBuiltinComponents;
import rogo.sketch.core.pipeline.parmeter.RenderParameter;
import rogo.sketch.core.shader.variant.ShaderVariantKey;
import rogo.sketch.core.util.KeyId;

import java.util.Objects;

/**
 * Immutable stage-local route describing how one graphics entity contributes to
 * a concrete stage/pipeline pair.
 */
public record StageRouteDescriptor(
        KeyId stageId,
        PipelineType pipelineType,
        RenderParameter renderParameter,
        @Nullable TargetBinding targetBindingOverride,
        ShaderVariantKey additionalVariant,
        @Nullable RenderStatePatch renderStateOverride,
        boolean enabled
) {
    public StageRouteDescriptor {
        stageId = Objects.requireNonNull(stageId, "stageId");
        pipelineType = Objects.requireNonNull(pipelineType, "pipelineType");
        renderParameter = Objects.requireNonNull(renderParameter, "renderParameter");
        additionalVariant = additionalVariant != null ? additionalVariant : ShaderVariantKey.EMPTY;
        renderStateOverride = renderStateOverride != null && renderStateOverride.isEmpty() ? null : renderStateOverride;
    }

    public static StageRouteDescriptor of(
            KeyId stageId,
            PipelineType pipelineType,
            RenderParameter renderParameter) {
        return new StageRouteDescriptor(
                stageId,
                pipelineType,
                renderParameter,
                null,
                ShaderVariantKey.EMPTY,
                null,
                true);
    }

    public static StageRouteDescriptor fromStageBinding(GraphicsBuiltinComponents.StageBindingComponent stageBinding) {
        if (stageBinding == null) {
            return null;
        }
        return of(stageBinding.stageId(), stageBinding.pipelineType(), stageBinding.renderParameter());
    }

    public StageRouteDescriptor withStageId(KeyId stageId) {
        return new StageRouteDescriptor(
                stageId,
                pipelineType,
                renderParameter,
                targetBindingOverride,
                additionalVariant,
                renderStateOverride,
                enabled);
    }

    public boolean matches(KeyId stageId, PipelineType pipelineType) {
        return enabled
                && Objects.equals(this.stageId, stageId)
                && Objects.equals(this.pipelineType, pipelineType);
    }
}
