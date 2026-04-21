package rogo.sketch.core.pipeline.information;

import rogo.sketch.core.api.graphics.ComputeDispatchCommand;
import rogo.sketch.core.api.graphics.ComputeDispatchContext;
import rogo.sketch.core.graphics.ecs.GraphicsEntityId;
import rogo.sketch.core.pipeline.RenderSetting;

import java.util.Objects;

/**
 * ECS-backed compute dispatch information.
 */
public final class ComputeInstanceInfo {
    private final GraphicsEntityId entityId;
    private final RenderSetting renderSetting;
    private final ComputeDispatchCommand dispatchCommand;

    public ComputeInstanceInfo(
            GraphicsEntityId entityId,
            RenderSetting renderSetting,
            ComputeDispatchCommand dispatchCommand) {
        this.entityId = Objects.requireNonNull(entityId, "entityId");
        this.renderSetting = Objects.requireNonNull(renderSetting, "renderSetting");
        this.dispatchCommand = dispatchCommand;
    }

    public GraphicsEntityId entityId() {
        return entityId;
    }

    public RenderSetting renderSetting() {
        return renderSetting;
    }

    public String getInfoType() {
        return "compute";
    }

    public ComputeDispatchCommand getDispatchCommand() {
        return dispatchCommand;
    }

    public void dispatch(ComputeDispatchContext dispatchContext) {
        if (dispatchCommand != null) {
            dispatchCommand.dispatch(dispatchContext);
        }
    }
}
