package rogo.sketch.core.pipeline;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.driver.state.RenderStatePatch;
import rogo.sketch.core.graphics.ecs.GraphicsBuiltinComponents;
import rogo.sketch.core.pipeline.parmeter.RenderParameter;
import rogo.sketch.core.resource.GraphicsResourceManager;
import rogo.sketch.core.shader.variant.ShaderVariantKey;
import rogo.sketch.core.util.KeyId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Route-aware compiled render-setting resolver used by stage flow scenes.
 */
public final class StageRouteCompiler {
    private StageRouteCompiler() {
    }

    public static CompiledRenderSetting compile(
            @Nullable CompiledRenderSetting baseCompiledRenderSetting,
            @Nullable RenderParameter renderParameter,
            @Nullable GraphicsResourceManager resourceManager,
            @Nullable StageRouteDescriptor route,
            @Nullable ShaderVariantKey stageVariantKey) {
        RenderSetting baseRenderSetting = baseCompiledRenderSetting != null ? baseCompiledRenderSetting.renderSetting() : null;
        RenderParameter effectiveRenderParameter = route != null && route.renderParameter() != null
                ? route.renderParameter()
                : renderParameter != null ? renderParameter : baseRenderSetting != null ? baseRenderSetting.renderParameter() : null;
        if (baseRenderSetting == null && effectiveRenderParameter != null) {
            baseRenderSetting = RenderSetting.fromPartial(effectiveRenderParameter, PartialRenderSetting.EMPTY);
        }
        if (baseRenderSetting == null) {
            return baseCompiledRenderSetting;
        }

        ShaderVariantKey routeVariant = route != null && route.additionalVariant() != null
                ? route.additionalVariant()
                : ShaderVariantKey.EMPTY;
        boolean routeOverridesPresent = route != null
                && (route.targetBindingOverride() != null
                || route.renderStateOverride() != null
                || !routeVariant.isEmpty());
        boolean stageVariantPresent = stageVariantKey != null && !stageVariantKey.isEmpty();
        if (!routeOverridesPresent
                && !stageVariantPresent
                && baseCompiledRenderSetting != null
                && Objects.equals(effectiveRenderParameter, baseRenderSetting.renderParameter())) {
            return baseCompiledRenderSetting;
        }

        RenderStatePatch mergedRenderState = mergeRenderStates(
                baseRenderSetting.renderState(),
                route != null ? route.renderStateOverride() : null);
        TargetBinding targetBinding = route != null && route.targetBindingOverride() != null
                ? route.targetBindingOverride()
                : baseRenderSetting.targetBinding();
        ShaderVariantKey effectiveVariant = routeVariant.merge(stageVariantKey);
        PartialRenderSetting partialSetting = PartialRenderSetting.create(
                baseRenderSetting.executionDomain(),
                mergedRenderState,
                targetBinding,
                baseRenderSetting.resourceBinding(),
                baseRenderSetting.shouldSwitchRenderState(),
                baseRenderSetting.aliasPolicy());
        RenderSetting routeAwareRenderSetting = RenderSetting.fromPartial(effectiveRenderParameter, partialSetting);
        return RenderSettingCompiler.compile(routeAwareRenderSetting, resourceManager, effectiveVariant);
    }

    public static StageRouteDescriptor resolveRoute(
            @Nullable GraphicsBuiltinComponents.StageBindingComponent stageBinding,
            @Nullable GraphicsBuiltinComponents.StageRoutesComponent stageRoutes,
            @Nullable KeyId stageId,
            @Nullable PipelineType pipelineType) {
        if (stageRoutes != null) {
            if (stageId == null || pipelineType == null) {
                return stageRoutes.firstEnabledRoute();
            }
            for (StageRouteDescriptor route : stageRoutes.routes()) {
                if (route != null && route.matches(stageId, pipelineType)) {
                    return route;
                }
            }
            return null;
        }
        if (stageBinding == null) {
            return null;
        }
        if (stageId != null && !Objects.equals(stageBinding.stageId(), stageId)) {
            return null;
        }
        if (pipelineType != null && !Objects.equals(stageBinding.pipelineType(), pipelineType)) {
            return null;
        }
        return StageRouteDescriptor.fromStageBinding(stageBinding);
    }

    private static RenderStatePatch mergeRenderStates(
            @Nullable RenderStatePatch baseRenderState,
            @Nullable RenderStatePatch overrideRenderState) {
        if (overrideRenderState == null || overrideRenderState.isEmpty()) {
            return baseRenderState != null ? baseRenderState : RenderStatePatch.empty();
        }
        if (baseRenderState == null || baseRenderState.isEmpty()) {
            return overrideRenderState;
        }
        Map<rogo.sketch.core.util.KeyId, rogo.sketch.core.api.RenderStateComponent> merged = new LinkedHashMap<>(baseRenderState.overrides());
        merged.putAll(overrideRenderState.overrides());
        return RenderStatePatch.of(merged);
    }
}
