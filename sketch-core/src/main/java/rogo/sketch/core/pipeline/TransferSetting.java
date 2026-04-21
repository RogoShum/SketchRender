package rogo.sketch.core.pipeline;

import rogo.sketch.core.driver.state.RenderStatePatch;
import rogo.sketch.core.packet.ExecutionDomain;
import rogo.sketch.core.resource.ResourceBinding;

public class TransferSetting extends PartialRenderSetting {
    public TransferSetting(
            ResourceBinding resourceBinding,
            boolean shouldSwitchRenderState) {
        this(resourceBinding, shouldSwitchRenderState, null);
    }

    public TransferSetting(
            ResourceBinding resourceBinding,
            boolean shouldSwitchRenderState,
            String aliasPolicy) {
        super(ExecutionDomain.TRANSFER, RenderStatePatch.empty(), null, resourceBinding, shouldSwitchRenderState, aliasPolicy);
    }
}
