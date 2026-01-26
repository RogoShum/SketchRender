package rogo.sketch.core.command;

import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.RenderSetting;
import rogo.sketch.core.pipeline.UniformBatchGroup;
import rogo.sketch.core.resource.ResourceBinding;
import rogo.sketch.core.util.KeyId;

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
    protected final KeyId stageId;
    protected final List<UniformBatchGroup> uniformBatches;

    protected RenderCommand(RenderSetting renderSetting, ResourceBinding resourceBinding, KeyId stageId, List<UniformBatchGroup> uniformBatches) {
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

    public KeyId getStageId() {
        return stageId;
    }

    public List<UniformBatchGroup> getUniformBatches() {
        return uniformBatches;
    }

    @Override
    public String toString() {
        return getCommandType() + "Command{stageId=" + stageId + "}";
    }
}