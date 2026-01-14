package rogo.sketch.render.command;

import rogo.sketch.render.pipeline.RenderContext;
import rogo.sketch.render.pipeline.RenderSetting;
import rogo.sketch.render.pipeline.UniformBatchGroup;
import rogo.sketch.render.resource.ResourceBinding;
import rogo.sketch.util.Identifier;

import java.util.List;

/**
 * Abstract base class for render commands.
 * <p>
 * RenderCommand provides a unified interface for all types of GPU operations:
 * <ul>
 * <li>{@link DrawRenderCommand} - Rasterization draw calls (glDraw*)</li>
 * <li>{@link ComputeRenderCommand} - Compute shader dispatches
 * (glDispatchCompute)</li>
 * </ul>
 * </p>
 */
public abstract class RenderCommand {
    protected final RenderSetting renderSetting;
    protected final ResourceBinding resourceBinding;
    protected final Identifier stageId;
    protected final List<UniformBatchGroup> uniformBatches;

    protected RenderCommand(
            RenderSetting renderSetting,
            ResourceBinding resourceBinding,
            Identifier stageId,
            List<UniformBatchGroup> uniformBatches) {
        this.renderSetting = renderSetting;
        this.resourceBinding = resourceBinding;
        this.stageId = stageId;
        this.uniformBatches = uniformBatches != null ? List.copyOf(uniformBatches) : List.of();
    }

    // ===== Abstract Methods =====

    /**
     * Execute this render command with the given context.
     *
     * @param context The render context providing shader and state access
     */
    public abstract void execute(RenderContext context);

    /**
     * Bind resources required for this command (e.g., VAO/VBO for draw, SSBO for
     * compute).
     */
    public abstract void bindResources();

    /**
     * Unbind resources after command execution.
     */
    public abstract void unbindResources();

    /**
     * Get the command type identifier.
     *
     * @return A string identifying the command type (e.g., "draw", "compute")
     */
    public abstract String getCommandType();

    /**
     * Check if this command is valid and can be executed.
     *
     * @return true if the command has valid parameters for execution
     */
    public abstract boolean isValid();

    // ===== Optional Override =====

    /**
     * Check if this command requires resource binding/unbinding.
     * Subclasses can override to skip binding for certain commands.
     *
     * @return true if bindResources/unbindResources should be called
     */
    public boolean requiresResourceBinding() {
        return true;
    }

    // ===== Common Getters =====

    public RenderSetting getRenderSetting() {
        return renderSetting;
    }

    public ResourceBinding getResourceBinding() {
        return resourceBinding;
    }

    public Identifier getStageId() {
        return stageId;
    }

    public List<UniformBatchGroup> getUniformBatches() {
        return uniformBatches;
    }

    public boolean isDrawCommand() {
        return this instanceof DrawRenderCommand;
    }

    public boolean isComputeCommand() {
        return this instanceof ComputeRenderCommand;
    }

    public DrawRenderCommand asDrawCommand() {
        return this instanceof DrawRenderCommand dc ? dc : null;
    }

    public ComputeRenderCommand asComputeCommand() {
        return this instanceof ComputeRenderCommand cc ? cc : null;
    }

    @Override
    public String toString() {
        return getCommandType() + "Command{stageId=" + stageId + "}";
    }
}
