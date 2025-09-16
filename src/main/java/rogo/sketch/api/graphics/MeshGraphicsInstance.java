package rogo.sketch.api.graphics;

import rogo.sketch.render.pipeline.RenderContext;
import rogo.sketch.render.pipeline.RenderSetting;
import rogo.sketch.render.model.ModelMesh;

/**
 * A unified graphics instance interface for the new mesh-based rendering system.
 * This interface combines all the functionality needed for both legacy and new pipeline rendering.
 * 
 * For the new three-stage pipeline, instances should implement ModelMeshProvider.
 * For the legacy pipeline, this provides a unified interface for all graphics types.
 */
public interface MeshGraphicsInstance extends GraphicsInstance, 
        ModelMeshProvider, RenderSettingProvider {
    
    /**
     * Get the model mesh for this graphics instance
     * This is the primary method for the new pipeline
     */
    @Override
    ModelMesh getModelMesh();
    
    /**
     * Get the render setting for this graphics instance
     * This provides the rendering state configuration
     */
    @Override
    <C extends RenderContext> RenderSetting getRenderSetting(C context);
    
    /**
     * Check if this instance needs to be updated this frame
     * This can be used to optimize rendering by skipping unchanged instances
     */
    default boolean needsUpdate() {
        return true;
    }
    
    /**
     * Get the priority for rendering order within a stage
     * Lower numbers render first
     */
    default int getRenderPriority() {
        return 0;
    }
    
    /**
     * Check if this instance is visible and should be rendered
     * This is called after shouldRender() for additional culling
     */
    default boolean isVisible() {
        return shouldRender();
    }
}
