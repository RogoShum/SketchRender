package rogo.sketch.core.pipeline.information;

import rogo.sketch.core.api.graphics.DispatchableGraphics;
import rogo.sketch.core.api.graphics.ComputeDispatchCommand;
import rogo.sketch.core.api.graphics.ComputeDispatchContext;
import rogo.sketch.core.api.graphics.Graphics;
import rogo.sketch.core.pipeline.RenderSetting;

/**
 * Instance information for compute shader operations.
 * <p>
 * Contains the dispatch configuration for compute shader execution.
 * Unlike rasterization, compute instances don't have mesh or vertex data.
 * </p>
 */
public class ComputeInstanceInfo extends InstanceInfo<DispatchableGraphics> {
    private final ComputeDispatchCommand dispatchCommand;

    public ComputeInstanceInfo(
            DispatchableGraphics instance,
            RenderSetting renderSetting,
            ComputeDispatchCommand dispatchCommand) {
        super(instance, renderSetting);
        this.dispatchCommand = dispatchCommand;
    }

    @Override
    public String getInfoType() {
        return "compute";
    }

    /**
     * Get the dispatch command consumer.
     * This will be called when the compute shader is ready to dispatch.
     *
     * @return The dispatch command consumer
     */
    public ComputeDispatchCommand getDispatchCommand() {
        return dispatchCommand;
    }

    /**
     * Execute the dispatch command with the given context and shader.
     *
     * @param dispatchContext The backend-neutral dispatch context
     */
    public void dispatch(ComputeDispatchContext dispatchContext) {
        if (dispatchCommand != null) {
            dispatchCommand.dispatch(dispatchContext);
        }
    }
}

