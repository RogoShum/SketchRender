package rogo.sketch.module.shadow;

import rogo.sketch.core.api.model.PreparedMesh;
import rogo.sketch.core.driver.state.CompareOp;
import rogo.sketch.core.driver.state.CullFaceMode;
import rogo.sketch.core.driver.state.FrontFaceMode;
import rogo.sketch.core.driver.state.RenderStatePatch;
import rogo.sketch.core.driver.state.component.ColorMaskState;
import rogo.sketch.core.driver.state.component.CullState;
import rogo.sketch.core.driver.state.component.DepthMaskState;
import rogo.sketch.core.driver.state.component.DepthTestState;
import rogo.sketch.core.graphics.ecs.GraphicsEntityBlueprint;
import rogo.sketch.core.graphics.ecs.GraphicsEntityPresets;
import rogo.sketch.core.packet.ExecutionDomain;
import rogo.sketch.core.pipeline.PartialRenderSetting;
import rogo.sketch.core.pipeline.PipelineType;
import rogo.sketch.core.pipeline.StageRouteDescriptor;
import rogo.sketch.core.pipeline.TargetBinding;
import rogo.sketch.core.pipeline.parmeter.RenderParameter;
import rogo.sketch.core.resource.ResourceBinding;
import rogo.sketch.core.resource.ResourceTypes;
import rogo.sketch.core.shader.variant.ShaderVariantKey;
import rogo.sketch.core.util.KeyId;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Shared code-side authoring helpers for own-shadow stage routes.
 */
public final class ShadowStageRoutes {
    public static final String SHADOW_SAMPLING_INCLUDE = "sketch_render:sketch_shadow_sampling.glsl";
    public static final KeyId SHADOW_MAP_BINDING = KeyId.of("u_ShadowMap");

    private static final TargetBinding SHADOW_TARGET_BINDING = new TargetBinding(
            ShadowModuleDescriptor.SHADOW_RENDER_TARGET,
            List.of(),
            Boolean.FALSE,
            Boolean.TRUE);
    private static final TargetBinding SHADOW_COLOR0_TARGET_BINDING = new TargetBinding(
            ShadowModuleDescriptor.SHADOW_RENDER_TARGET,
            List.of(ShadowModuleDescriptor.SHADOW_COLOR0_TEXTURE),
            Boolean.FALSE,
            Boolean.TRUE);
    private static final ShaderVariantKey SHADOW_VARIANT = ShaderVariantKey.of(ShadowModuleDescriptor.SHADOW_PASS_MACRO);
    private static final RenderStatePatch SHADOW_DEPTH_ONLY_RENDER_STATE = createShadowCasterRenderState(false);
    private static final RenderStatePatch SHADOW_COLOR_RENDER_STATE = createShadowCasterRenderState(true);

    private ShadowStageRoutes() {
    }

    public static StageRouteDescriptor shadowCasterRoute(RenderParameter renderParameter) {
        Objects.requireNonNull(renderParameter, "renderParameter");
        return new StageRouteDescriptor(
                ShadowModuleDescriptor.SHADOW_DEPTH_STAGE_ID,
                PipelineType.RASTERIZATION,
                renderParameter,
                SHADOW_TARGET_BINDING,
                SHADOW_VARIANT,
                SHADOW_DEPTH_ONLY_RENDER_STATE,
                true);
    }

    public static StageRouteDescriptor shadowColorCasterRoute(RenderParameter renderParameter) {
        Objects.requireNonNull(renderParameter, "renderParameter");
        return new StageRouteDescriptor(
                ShadowModuleDescriptor.SHADOW_DEPTH_STAGE_ID,
                PipelineType.RASTERIZATION,
                renderParameter,
                SHADOW_COLOR0_TARGET_BINDING,
                SHADOW_VARIANT,
                SHADOW_COLOR_RENDER_STATE,
                true);
    }

    public static List<StageRouteDescriptor> mainAndShadowRoutes(
            KeyId mainStageId,
            PipelineType mainPipelineType,
            RenderParameter renderParameter) {
        Objects.requireNonNull(mainStageId, "mainStageId");
        Objects.requireNonNull(mainPipelineType, "mainPipelineType");
        Objects.requireNonNull(renderParameter, "renderParameter");
        return List.of(
                StageRouteDescriptor.of(mainStageId, mainPipelineType, renderParameter),
                shadowCasterRoute(renderParameter));
    }

    public static boolean isPreparedMeshCasterEligible(
            boolean enabled,
            PreparedMesh preparedMesh,
            RenderParameter renderParameter) {
        return evaluatePreparedMeshCaster(enabled, preparedMesh, renderParameter).eligible();
    }

    public static PreparedMeshCasterRouteResult evaluatePreparedMeshCaster(
            boolean enabled,
            PreparedMesh preparedMesh,
            RenderParameter renderParameter) {
        if (!enabled) {
            return PreparedMeshCasterRouteResult.ineligibleResult("shadow_disabled");
        }
        if (preparedMesh == null) {
            return PreparedMeshCasterRouteResult.ineligibleResult("missing_prepared_mesh");
        }
        if (preparedMesh.getVertexCount() <= 0 && preparedMesh.getIndicesCount() <= 0) {
            return PreparedMeshCasterRouteResult.ineligibleResult("empty_prepared_mesh");
        }
        if (renderParameter == null) {
            return PreparedMeshCasterRouteResult.ineligibleResult("missing_render_parameter");
        }
        if (!renderParameter.isRasterization()) {
            return PreparedMeshCasterRouteResult.ineligibleResult("non_raster_parameter");
        }
        return PreparedMeshCasterRouteResult.eligibleResult();
    }

    public static boolean attachPreparedMeshCasterRoutes(
            GraphicsEntityBlueprint.Builder builder,
            boolean enabled,
            PreparedMesh preparedMesh,
            KeyId mainStageId,
            PipelineType mainPipelineType,
            RenderParameter renderParameter) {
        return attachPreparedMeshCasterRoutesWithResult(
                builder,
                enabled,
                preparedMesh,
                mainStageId,
                mainPipelineType,
                renderParameter).attached();
    }

    public static PreparedMeshCasterRouteResult attachPreparedMeshCasterRoutesWithResult(
            GraphicsEntityBlueprint.Builder builder,
            boolean enabled,
            PreparedMesh preparedMesh,
            KeyId mainStageId,
            PipelineType mainPipelineType,
            RenderParameter renderParameter) {
        Objects.requireNonNull(builder, "builder");
        PreparedMeshCasterRouteResult result = evaluatePreparedMeshCaster(enabled, preparedMesh, renderParameter);
        if (!result.eligible()) {
            return result;
        }
        Objects.requireNonNull(mainStageId, "mainStageId");
        Objects.requireNonNull(mainPipelineType, "mainPipelineType");
        GraphicsEntityPresets.withStageRoutes(
                builder,
                mainAndShadowRoutes(mainStageId, mainPipelineType, renderParameter));
        return result.withAttached();
    }

    public static ResourceBinding shadowSamplingBinding() {
        return shadowSamplingBinding(SHADOW_MAP_BINDING, ShadowModuleDescriptor.SHADOW_MAP_TEXTURE);
    }

    public static ResourceBinding shadowSamplingBinding(KeyId bindingName) {
        return shadowSamplingBinding(bindingName, ShadowModuleDescriptor.SHADOW_MAP_TEXTURE);
    }

    public static ResourceBinding shadowSamplingBinding(KeyId bindingName, KeyId shadowMapResourceId) {
        Objects.requireNonNull(bindingName, "bindingName");
        Objects.requireNonNull(shadowMapResourceId, "shadowMapResourceId");
        ResourceBinding binding = new ResourceBinding();
        binding.addBinding(ResourceTypes.TEXTURE, bindingName, shadowMapResourceId);
        return binding;
    }

    public static PartialRenderSetting withShadowSampling(PartialRenderSetting base) {
        return withShadowSampling(base, SHADOW_MAP_BINDING, ShadowModuleDescriptor.SHADOW_MAP_TEXTURE);
    }

    public static PartialRenderSetting withShadowSampling(PartialRenderSetting base, KeyId bindingName) {
        return withShadowSampling(base, bindingName, ShadowModuleDescriptor.SHADOW_MAP_TEXTURE);
    }

    public static PartialRenderSetting withShadowSampling(
            PartialRenderSetting base,
            KeyId bindingName,
            KeyId shadowMapResourceId) {
        Objects.requireNonNull(base, "base");
        ResourceBinding mergedBinding = new ResourceBinding();
        if (base.resourceBinding() != null) {
            mergedBinding.merge(base.resourceBinding());
        }
        mergedBinding.merge(shadowSamplingBinding(bindingName, shadowMapResourceId));
        return PartialRenderSetting.create(
                base.executionDomain() != null ? base.executionDomain() : ExecutionDomain.RASTER,
                base.renderState(),
                base.targetBinding(),
                mergedBinding,
                base.shouldSwitchRenderState(),
                base.aliasPolicy());
    }

    private static RenderStatePatch createShadowCasterRenderState(boolean enableColorWrites) {
        Map<KeyId, rogo.sketch.core.api.RenderStateComponent> overrides = new LinkedHashMap<>();
        overrides.put(DepthTestState.TYPE, new DepthTestState(true, CompareOp.LESS));
        overrides.put(DepthMaskState.TYPE, new DepthMaskState(true));
        overrides.put(ColorMaskState.TYPE, enableColorWrites
                ? new ColorMaskState(true, true, true, true)
                : new ColorMaskState(false, false, false, false));
        overrides.put(CullState.TYPE, new CullState(false, CullFaceMode.BACK, FrontFaceMode.CCW));
        return RenderStatePatch.of(overrides);
    }

    public record PreparedMeshCasterRouteResult(boolean eligible, boolean attached, String reason) {
        private static PreparedMeshCasterRouteResult eligibleResult() {
            return new PreparedMeshCasterRouteResult(true, false, "eligible");
        }

        private static PreparedMeshCasterRouteResult ineligibleResult(String reason) {
            return new PreparedMeshCasterRouteResult(false, false, reason);
        }

        private PreparedMeshCasterRouteResult withAttached() {
            return new PreparedMeshCasterRouteResult(true, true, "attached");
        }
    }
}
