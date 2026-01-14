package rogo.sketch.render.pipeline.information;

import rogo.sketch.api.graphics.Graphics;
import rogo.sketch.render.pipeline.RenderContext;
import rogo.sketch.render.pipeline.RenderSetting;
import rogo.sketch.render.resource.ResourceBinding;
import rogo.sketch.render.shader.ComputeShader;

import java.util.function.BiConsumer;

/**
 * Instance information for compute shader operations.
 * <p>
 * Contains the dispatch configuration for compute shader execution.
 * Unlike rasterization, compute instances don't have mesh or vertex data.
 * </p>
 */
public class ComputeInstanceInfo extends InstanceInfo {
    private final BiConsumer<RenderContext, ComputeShader> dispatchCommand;

    public ComputeInstanceInfo(
            Graphics instance,
            RenderSetting renderSetting,
            ResourceBinding resourceBinding,
            BiConsumer<RenderContext, ComputeShader> dispatchCommand) {
        super(instance, renderSetting, resourceBinding);
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
    public BiConsumer<RenderContext, ComputeShader> getDispatchCommand() {
        return dispatchCommand;
    }

    /**
     * Execute the dispatch command with the given context and shader.
     *
     * @param context The render context
     * @param shader  The compute shader to dispatch
     */
    public void dispatch(RenderContext context, ComputeShader shader) {
        if (dispatchCommand != null) {
            dispatchCommand.accept(context, shader);
        }
    }
}
