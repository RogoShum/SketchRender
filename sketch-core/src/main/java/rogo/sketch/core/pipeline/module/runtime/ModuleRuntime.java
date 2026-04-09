package rogo.sketch.core.pipeline.module.runtime;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.api.graphics.Graphics;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.graph.RenderGraphBuilder;
import rogo.sketch.core.pipeline.graph.TickGraphBuilder;
import rogo.sketch.core.pipeline.module.session.ModuleSession;
import rogo.sketch.core.pipeline.parmeter.RenderParameter;
import rogo.sketch.core.util.KeyId;

/**
 * Process-lifetime module runtime.
 */
public interface ModuleRuntime {
    String id();

    default void onProcessInit(ModuleRuntimeContext context) {
    }

    default void onKernelInit(ModuleRuntimeContext context) {
    }

    default void onEnable(ModuleRuntimeContext context) {
    }

    default void onDisable(ModuleRuntimeContext context) {
    }

    default void onResourceReload(ModuleRuntimeContext context) {
    }

    default ModuleSession createSession() {
        return ModuleSession.NOOP;
    }

    default boolean supports(Graphics graphics) {
        return false;
    }

    default void onGraphicsAttached(Graphics graphics, @Nullable RenderParameter renderParameter, @Nullable KeyId containerType, ModuleRuntimeContext context) {
    }

    default void onGraphicsDetached(Graphics graphics, ModuleRuntimeContext context) {
    }

    default <C extends RenderContext> void contributeToTickGraph(TickGraphBuilder<C> builder) {
    }

    default <C extends RenderContext> void contributeToFrameGraph(RenderGraphBuilder<C> builder) {
    }

    default void onShutdown(ModuleRuntimeContext context) {
    }
}

