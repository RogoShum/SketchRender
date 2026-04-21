package rogo.sketch.core.pipeline;

import rogo.sketch.core.driver.state.RenderStatePatch;
import rogo.sketch.core.driver.state.component.RenderTargetState;
import rogo.sketch.core.driver.state.component.ShaderState;
import rogo.sketch.core.packet.ExecutionDomain;
import rogo.sketch.core.resource.ResourceBinding;

public class ComputeRenderSetting extends PartialRenderSetting {
    public ComputeRenderSetting(
            RenderStatePatch renderState,
            ResourceBinding resourceBinding,
            boolean shouldSwitchRenderState) {
        this(renderState, resourceBinding, shouldSwitchRenderState, null);
    }

    public ComputeRenderSetting(
            RenderStatePatch renderState,
            ResourceBinding resourceBinding,
            boolean shouldSwitchRenderState,
            String aliasPolicy) {
        super(ExecutionDomain.COMPUTE, sanitize(renderState), null, resourceBinding, shouldSwitchRenderState, aliasPolicy);
    }

    private static RenderStatePatch sanitize(RenderStatePatch renderState) {
        RenderStatePatch patch = renderState != null ? renderState : RenderStatePatch.empty();
        if (patch.contains(RenderTargetState.TYPE)) {
            throw new IllegalArgumentException("ComputeRenderSetting must not declare renderTarget state");
        }
        for (var entry : patch.overrides().entrySet()) {
            if (!ShaderState.TYPE.equals(entry.getKey())) {
                throw new IllegalArgumentException("ComputeRenderSetting only supports shader state, found: " + entry.getKey());
            }
        }
        return patch;
    }
}
