package rogo.sketch.core.command;

import org.lwjgl.opengl.GL43;
import rogo.sketch.core.api.ShaderProvider;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.RenderSetting;
import rogo.sketch.core.pipeline.UniformBatchGroup;
import rogo.sketch.core.pipeline.information.ComputeInstanceInfo;
import rogo.sketch.core.resource.ResourceBinding;
import rogo.sketch.core.shader.ComputeShader;
import rogo.sketch.core.util.KeyId;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Render command for compute shader dispatch operations.
 * <p>
 * Encapsulates parameters for glDispatchCompute calls.
 * Executes the dispatch function from ComputeGraphics.
 * </p>
 */
public class ComputeRenderCommand extends RenderCommand {
    private final int workGroupsX;
    private final int workGroupsY;
    private final int workGroupsZ;
    private final ComputeInstanceInfo computeInfo;
    private final BiConsumer<RenderContext, ComputeShader> dispatchFunction;

    public ComputeRenderCommand(
            RenderSetting renderSetting,
            ResourceBinding resourceBinding,
            KeyId stageId,
            int workGroupsX,
            int workGroupsY,
            int workGroupsZ) {
        this(renderSetting, resourceBinding, stageId, workGroupsX, workGroupsY, workGroupsZ,
                null, null, new ArrayList<>());
    }

    public ComputeRenderCommand(
            RenderSetting renderSetting,
            ResourceBinding resourceBinding,
            KeyId stageId,
            int workGroupsX,
            int workGroupsY,
            int workGroupsZ,
            ComputeInstanceInfo computeInfo,
            BiConsumer<RenderContext, ComputeShader> dispatchFunction,
            List<UniformBatchGroup> uniformBatches) {
        super(renderSetting, resourceBinding, stageId, uniformBatches);
        this.workGroupsX = workGroupsX;
        this.workGroupsY = workGroupsY;
        this.workGroupsZ = workGroupsZ;
        this.computeInfo = computeInfo;
        this.dispatchFunction = dispatchFunction;
    }

    @Override
    public void execute(RenderContext context) {
        if (!isValid()) {
            return;
        }

        // Get compute shader from context using same pattern as
        // ComputeGraphics.afterDraw
        ShaderProvider shaderProvider = context.shaderProvider();
        ComputeShader computeShader = extractComputeShader(shaderProvider);

        if (computeShader != null && dispatchFunction != null) {
            // Use the dispatch function from ComputeGraphics
            dispatchFunction.accept(context, computeShader);
        } else if (computeInfo != null) {
            // Fallback to computeInfo dispatch
            computeInfo.dispatch(context, computeShader);
        } else {
            // Direct dispatch with work group sizes
            GL43.glDispatchCompute(workGroupsX, workGroupsY, workGroupsZ);
        }
    }

    /**
     * Extract ComputeShader from ShaderProvider using same logic as
     * ComputeGraphics.afterDraw
     */
    private ComputeShader extractComputeShader(ShaderProvider shaderProvider) {
        if (shaderProvider instanceof ComputeShader computeShader) {
            return computeShader;
        }

        return null;
    }

    @Override
    public void bindResources() {
        // Compute shaders may need SSBO/UBO bindings
        // These are typically handled by RenderStateManager through resourceBinding
        // No additional binding needed here
    }

    @Override
    public void unbindResources() {
        // No unbinding needed for compute resources
    }

    @Override
    public boolean requiresResourceBinding() {
        // Compute commands handle their resources differently
        return false;
    }

    @Override
    public String getCommandType() {
        return "Compute";
    }

    @Override
    public boolean isValid() {
        return workGroupsX > 0 && workGroupsY > 0 && workGroupsZ > 0;
    }

    // ===== Compute-Specific Getters =====

    public int getWorkGroupsX() {
        return workGroupsX;
    }

    public int getWorkGroupsY() {
        return workGroupsY;
    }

    public int getWorkGroupsZ() {
        return workGroupsZ;
    }

    public int getTotalWorkGroups() {
        return workGroupsX * workGroupsY * workGroupsZ;
    }

    public ComputeInstanceInfo getComputeInfo() {
        return computeInfo;
    }

    @Override
    public String toString() {
        return "ComputeRenderCommand{" +
                "stageId=" + stageId +
                ", workGroups=(" + workGroupsX + ", " + workGroupsY + ", " + workGroupsZ + ")" +
                ", totalWorkGroups=" + getTotalWorkGroups() +
                '}';
    }

    // ===== Builder =====

    public static class Builder {
        private RenderSetting renderSetting;
        private ResourceBinding resourceBinding;
        private KeyId stageId;
        private int workGroupsX = 1;
        private int workGroupsY = 1;
        private int workGroupsZ = 1;
        private ComputeInstanceInfo computeInfo;
        private BiConsumer<RenderContext, ComputeShader> dispatchFunction;
        private final List<UniformBatchGroup> uniformBatches = new ArrayList<>();

        public Builder renderSetting(RenderSetting renderSetting) {
            this.renderSetting = renderSetting;
            this.resourceBinding = renderSetting.resourceBinding();
            return this;
        }

        public Builder stageId(KeyId stageId) {
            this.stageId = stageId;
            return this;
        }

        public Builder workGroups(int x, int y, int z) {
            this.workGroupsX = x;
            this.workGroupsY = y;
            this.workGroupsZ = z;
            return this;
        }

        public Builder computeInfo(ComputeInstanceInfo computeInfo) {
            this.computeInfo = computeInfo;
            return this;
        }

        public Builder dispatchFunction(BiConsumer<RenderContext, ComputeShader> dispatch) {
            this.dispatchFunction = dispatch;
            return this;
        }

        public Builder addUniformBatch(List<UniformBatchGroup> batch) {
            this.uniformBatches.addAll(batch);
            return this;
        }

        public ComputeRenderCommand build() {
            return new ComputeRenderCommand(
                    renderSetting,
                    resourceBinding,
                    stageId,
                    workGroupsX,
                    workGroupsY,
                    workGroupsZ,
                    computeInfo,
                    dispatchFunction,
                    uniformBatches);
        }
    }

    // ===== Factory Methods =====

    public static ComputeRenderCommand create(
            RenderSetting renderSetting,
            KeyId stageId,
            int workGroupsX,
            int workGroupsY,
            int workGroupsZ) {
        return new ComputeRenderCommand(
                renderSetting,
                renderSetting.resourceBinding(),
                stageId,
                workGroupsX,
                workGroupsY,
                workGroupsZ);
    }

    public static ComputeRenderCommand createFromInfo(
            ComputeInstanceInfo computeInfo,
            KeyId stageId) {
        return new Builder()
                .renderSetting(computeInfo.getRenderSetting())
                .stageId(stageId)
                .computeInfo(computeInfo)
                .dispatchFunction(computeInfo.getDispatchCommand())
                .workGroups(1, 1, 1)
                .build();
    }
}