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
import rogo.sketch.core.pipeline.PipelineType;
import rogo.sketch.core.pipeline.StageRouteDescriptor;
import rogo.sketch.core.pipeline.TargetBinding;
import rogo.sketch.core.pipeline.parmeter.RenderParameter;
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
    private static final TargetBinding SHADOW_TARGET_BINDING = new TargetBinding(
            ShadowModuleDescriptor.SHADOW_RENDER_TARGET,
            List.of(),
            Boolean.FALSE,
            Boolean.TRUE);
    private static final ShaderVariantKey SHADOW_VARIANT = ShaderVariantKey.of(ShadowModuleDescriptor.SHADOW_PASS_MACRO);
    private static final RenderStatePatch SHADOW_CASTER_RENDER_STATE = createShadowCasterRenderState();

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
                SHADOW_CASTER_RENDER_STATE,
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

    private static RenderStatePatch createShadowCasterRenderState() {
        Map<KeyId, rogo.sketch.core.api.RenderStateComponent> overrides = new LinkedHashMap<>();
        overrides.put(DepthTestState.TYPE, new DepthTestState(true, CompareOp.LESS));
        overrides.put(DepthMaskState.TYPE, new DepthMaskState(true));
        overrides.put(ColorMaskState.TYPE, new ColorMaskState(false, false, false, false));
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
