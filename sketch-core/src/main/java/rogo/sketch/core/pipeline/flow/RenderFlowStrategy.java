package rogo.sketch.core.pipeline.flow;

import rogo.sketch.core.api.graphics.Graphics;
import rogo.sketch.core.event.RenderFlowRegisterEvent;
import rogo.sketch.core.command.RenderCommand;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.parmeter.RenderParameter;
import rogo.sketch.core.pipeline.RenderSetting;
import rogo.sketch.core.pipeline.information.InstanceInfo;
import rogo.sketch.core.util.KeyId;

import java.util.List;
import java.util.Map;

/**
 * Strategy interface for processing graphics instances with different rendering
 * modes.
 * <p>
 * Each flow strategy handles a specific type of rendering (e.g., rasterization,
 * compute).
 * Strategies are registered with {@link RenderFlowRegistry} and selected based
 * on
 * the {@link RenderFlowType} from
 * {@link RenderParameter#getFlowType()}.
 * </p>
 * <p>
 * Third-party mods can implement this interface to add custom processing flows
 * by registering them during the initialization phase via
 * {@link RenderFlowRegisterEvent}.
 * </p>
 * 
 * @param <G> The graphics type this strategy handles
 * @param <I> The instance info type this strategy produces
 */
public interface RenderFlowStrategy<G extends Graphics, I extends InstanceInfo<G>> {

    /**
     * Get the flow type this strategy handles.
     *
     * @return The render flow type
     */
    RenderFlowType getFlowType();
    
    /**
     * Get the expected graphics type for this strategy.
     *
     * @return The graphics class
     */
    Class<G> getGraphicsType();
    
    /**
     * Get the expected instance info type for this strategy.
     *
     * @return The instance info class
     */
    Class<I> getInfoType();

    /**
     * Create render commands from a BatchContainer.
     * <p>
     * This method gets batches from the container, filters visible instances,
     * updates uniforms, and generates render commands.
     * </p>
     *
     * @param batchContainer The container holding all batches for this strategy
     * @param stageId        The identifier of the current render stage
     * @param flowContext    The flow processing context with access to resources
     * @param postProcessors Consumer to register post-processing tasks
     * @return Map of render commands grouped by RenderSetting
     */
    Map<RenderSetting, List<RenderCommand>> createRenderCommands(
            BatchContainer<G, I> batchContainer,
            KeyId stageId,
            RenderFlowContext flowContext,
            RenderPostProcessors postProcessors,
            RenderContext context);

    /**
     * Create a post-processor for this strategy.
     *
     * @return The post-processor instance, or null if none needed.
     */
    default RenderPostProcessor createPostProcessor() {
        return null;
    }

    /**
     * Check if instances processed by this strategy can be batched together.
     * <p>
     * Rasterization typically supports batching for efficient rendering,
     * while compute operations may not benefit from batching.
     * </p>
     *
     * @return true if instances can be batched, false otherwise
     */
    default boolean supportsBatching() {
        return true;
    }

    default boolean supportsParallel() {
        return false;
    }
}
