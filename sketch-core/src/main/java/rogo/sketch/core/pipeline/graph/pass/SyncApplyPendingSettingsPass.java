package rogo.sketch.core.pipeline.graph.pass;

import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.graph.PipelinePass;
import rogo.sketch.core.pipeline.kernel.FrameContext;
import rogo.sketch.core.pipeline.kernel.ThreadDomain;
import rogo.sketch.core.pipeline.module.runtime.ModuleRuntimeHost;

/**
 * Flushes pending UI/config writes into committed runtime settings on the sync thread
 * immediately before async render command building begins.
 */
public class SyncApplyPendingSettingsPass<C extends RenderContext> implements PipelinePass<C> {

    public static final String NAME = "sync_apply_pending_settings";

    @Override
    public String name() { return NAME; }

    @Override
    public ThreadDomain threadDomain() { return ThreadDomain.SYNC; }

    @Override
    public void execute(FrameContext<C> ctx) {
        ModuleRuntimeHost runtimeHost = ctx.kernel().moduleRegistry().runtimeHost();
        if (runtimeHost != null) {
            runtimeHost.flushPendingSettingChanges();
        }
    }
}
