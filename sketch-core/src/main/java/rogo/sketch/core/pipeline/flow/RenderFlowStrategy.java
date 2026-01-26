package rogo.sketch.core.pipeline.flow;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.api.graphics.Graphics;
import rogo.sketch.core.event.RenderFlowRegisterEvent;
import rogo.sketch.core.command.RenderCommand;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.parmeter.RenderParameter;
import rogo.sketch.core.pipeline.RenderSetting;
import rogo.sketch.core.pipeline.information.InstanceInfo;
import rogo.sketch.core.util.KeyId;

import java.util.Collection;
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
 */
public interface RenderFlowStrategy {

    /**
     * Get the flow type this strategy handles.
     *
     * @return The render flow type
     */
    RenderFlowType getFlowType();

    /**
     * Collect instance information from a graphics instance.
     * <p>
     * This method extracts the necessary data from a graphics instance for later
     * command creation. The returned {@link InstanceInfo} contains all data needed
     * for batching and command generation.
     * </p>
     *
     * @param instance      The graphics instance to collect from
     * @param renderParameter The render setting associated with this instance
     * @param context       The current render context
     * @param <C>           The render context type
     * @return The collected instance information, or null if the instance should be
     *         skipped
     */
    @Nullable
    <C extends RenderContext> InstanceInfo collectInstanceInfo(Graphics instance, RenderParameter renderParameter, C context);

    /**
     * Create render commands from collected instance information.
     * <p>
     * This method is called after all instances have been collected and batched.
     * It should generate the appropriate render commands for the given instances.
     * </p>
     *
     * @param infos         The collected instance information
     * @param stageId       The identifier of the current render stage
     * @param flowContext   The flow processing context with access to resources
     * @param postProcessors Consumer to register post-processing tasks (e.g. data
     *                      uploads)
     * @return Map of render commands grouped by RenderSetting
     */
    Map<RenderSetting, List<RenderCommand>> createRenderCommands(Collection<InstanceInfo> infos, KeyId stageId, RenderFlowContext flowContext, RenderPostProcessors postProcessors);

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
}