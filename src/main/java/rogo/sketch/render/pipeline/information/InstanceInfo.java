package rogo.sketch.render.pipeline.information;

import rogo.sketch.api.graphics.Graphics;
import rogo.sketch.render.pipeline.RenderSetting;
import rogo.sketch.render.pipeline.flow.RenderFlowType;
import rogo.sketch.render.resource.ResourceBinding;

import java.util.Objects;

/**
 * Base class for instance information collected from graphics instances.
 * <p>
 * This is a generic base class that can represent different types of instances,
 * including rendering instances (rasterization) and non-rendering instances
 * (compute).
 * </p>
 * <p>
 * Subclasses should provide type-specific information:
 * <ul>
 * <li>{@link RasterizationInstanceInfo} - For geometry rendering with mesh
 * data</li>
 * <li>{@link ComputeInstanceInfo} - For compute shader dispatch operations</li>
 * </ul>
 * </p>
 */
public abstract class InstanceInfo {
    protected final Graphics instance;
    protected final RenderSetting renderSetting;
    protected final ResourceBinding resourceBinding;

    protected InstanceInfo(
            Graphics instance,
            RenderSetting renderSetting,
            ResourceBinding resourceBinding) {
        this.instance = Objects.requireNonNull(instance);
        this.renderSetting = Objects.requireNonNull(renderSetting);
        this.resourceBinding = resourceBinding;
    }

    /**
     * Get the graphics instance this info represents.
     *
     * @return The graphics instance
     */
    public Graphics getInstance() {
        return instance;
    }

    /**
     * Get the render setting for this instance.
     *
     * @return The render setting
     */
    public RenderSetting getRenderSetting() {
        return renderSetting;
    }

    /**
     * Get the resource binding for this instance.
     *
     * @return The resource binding
     */
    public ResourceBinding getResourceBinding() {
        return resourceBinding;
    }

    /**
     * Get the flow type for this instance info.
     * Derived from the render setting's render parameter.
     *
     * @return The render flow type
     */
    public RenderFlowType getFlowType() {
        return renderSetting.renderParameter().getFlowType();
    }

    /**
     * Get the unique identifier for the info type.
     * Subclasses should return a descriptive identifier.
     *
     * @return The info type identifier
     */
    public abstract String getInfoType();
}
