package rogo.sketch.core.pipeline.module.runtime;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.graphics.ecs.GraphicsEntityId;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.graph.RenderGraphBuilder;
import rogo.sketch.core.pipeline.graph.TickGraphBuilder;
import rogo.sketch.core.pipeline.kernel.ModulePassDefinition;
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

    default void registerEntitySubscriptions(ModuleSubscriptionRegistrar registrar) {
    }

    default void onEntityAttached(GraphicsEntityId entityId, GraphicsEntitySnapshot snapshot, EntityAttachContext context) {
    }

    default void onEntityDetached(GraphicsEntityId entityId, EntityAttachContext context) {
    }

    default <C extends RenderContext> void contributeToTickGraph(TickGraphBuilder<C> builder) {
    }

    default <C extends RenderContext> void contributeToFrameGraph(RenderGraphBuilder<C> builder) {
    }

    default void describeFrameResources(ModuleGraphAssemblyContext context) {
    }

    default void contributeModulePasses(ModuleGraphAssemblyContext context) {
    }

    default void onShutdown(ModuleRuntimeContext context) {
    }
}

