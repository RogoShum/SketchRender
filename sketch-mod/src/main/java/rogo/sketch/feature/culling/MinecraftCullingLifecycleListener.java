package rogo.sketch.feature.culling;

import net.minecraftforge.eventbus.api.SubscribeEvent;
import rogo.sketch.core.event.GraphicsPipelineStageEvent;
import rogo.sketch.event.ProxyEvent;
import rogo.sketch.vanilla.McRenderContext;
import rogo.sketch.vanilla.event.MinecraftHostEventContracts;

/**
 * Host-side relay that keeps Minecraft event ingress outside the module system
 * until stage 8 normalizes event registration.
 */
public final class MinecraftCullingLifecycleListener {
    @SubscribeEvent
    public void renderStage(ProxyEvent<GraphicsPipelineStageEvent<McRenderContext>> proxyEvent) {
        GraphicsPipelineStageEvent<McRenderContext> event = proxyEvent.getWrapped();
        if (event.getPhase() != GraphicsPipelineStageEvent.Phase.PRE) {
            return;
        }
        event.getPipeline().extensionHost().objectLifecycleEventBus().post(
                MinecraftHostEventContracts.RENDER_STAGE_PRE,
                new MinecraftHostEventContracts.RenderStagePreEvent(
                        event.getPipeline(),
                        event.getStage(),
                        event.getContext()));
    }
}
