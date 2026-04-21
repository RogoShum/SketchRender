package rogo.sketch.core.pipeline.module.session;

import rogo.sketch.core.graphics.ecs.GraphicsEntityBlueprint;
import rogo.sketch.core.pipeline.module.runtime.ModuleGraphicsLifetime;
import rogo.sketch.core.pipeline.module.runtime.ModuleRuntimeContext;

public interface ModuleSessionContext extends ModuleRuntimeContext {
    /**
     * Register a session-owned auxiliary ECS entity.
     * <p>
     * Auxiliary entities participate in module attach/detach and tick/frame
     * lifecycle ownership, but they do not enter raster/compute/function stage
     * flow, do not compile packets, and must not be used as a fallback path for
     * ordinary staged graphics.
     * </p>
     */
    void registerAuxiliaryEntity(GraphicsEntityBlueprint blueprint, ModuleGraphicsLifetime lifetime);
}

