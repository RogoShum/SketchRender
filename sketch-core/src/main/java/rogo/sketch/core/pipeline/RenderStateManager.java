package rogo.sketch.core.pipeline;

import rogo.sketch.core.api.RenderStateComponent;
import rogo.sketch.core.driver.state.AttachmentBindingState;
import rogo.sketch.core.driver.state.CompiledRenderState;
import rogo.sketch.core.driver.state.CompiledComputeState;
import rogo.sketch.core.driver.state.CompiledRasterState;
import rogo.sketch.core.driver.state.RenderStateCompiler;
import rogo.sketch.core.driver.state.RenderStatePatch;
import rogo.sketch.core.driver.state.ShaderBindingState;
import rogo.sketch.core.driver.state.DynamicRenderState;
import rogo.sketch.core.driver.GraphicsDriver;
import rogo.sketch.core.packet.ComputePipelineKey;
import rogo.sketch.core.packet.ExecutionDomain;
import rogo.sketch.core.packet.ExecutionKey;
import rogo.sketch.core.packet.RasterPipelineKey;
import rogo.sketch.core.packet.ResourceBindingPlan;
import rogo.sketch.core.packet.ResourceBindingStamp;
import rogo.sketch.core.pipeline.kernel.FrameCaptureSnapshot;
import rogo.sketch.core.resource.ResourceBinding;
import rogo.sketch.core.resource.GraphicsResourceManager;
import rogo.sketch.core.util.KeyId;

import java.util.Objects;

public class RenderStateManager {
    private final GraphicsResourceManager resourceManager;
    private CompiledRenderState defaultState;
    private CompiledRenderState currentState;
    private CompiledRasterState currentRasterState;
    private ShaderBindingState currentShaderBindingState;
    private AttachmentBindingState currentAttachmentBindingState;
    private ResourceBindingPlan currentResourceBindingPlan;
    private ResourceBindingStamp currentResourceBindingStamp = ResourceBindingStamp.NONE;
    private ExecutionDomain currentDomain;

    public RenderStateManager(GraphicsResourceManager resourceManager) {
        this.resourceManager = resourceManager;
    }

    public void accept(RenderSetting setting, RenderContext context) {
        CompiledRenderSetting compiledRenderSetting = RenderSettingCompiler.compile(setting, resourceManager);
        accept(compiledRenderSetting.pipelineStateKey(), context);
    }

    public void accept(ExecutionKey stateKey, RenderContext context) {
        if (stateKey == null) {
            return;
        }

        if (currentDomain != stateKey.domain()) {
            currentRasterState = null;
            currentShaderBindingState = null;
            currentAttachmentBindingState = null;
            currentState = null;
            currentDomain = stateKey.domain();
        }

        switch (stateKey.domain()) {
            case COMPUTE -> applyComputeKey((ComputePipelineKey) stateKey, context);
            case OFFSCREEN_GRAPHICS -> applyRasterKey((RasterPipelineKey) stateKey, context);
            case TRANSFER -> {
            }
            case RASTER -> applyRasterKey((RasterPipelineKey) stateKey, context);
        }

        // Always bind resource bindings (needed for both graphics and compute)
        ResourceBindingPlan bindingPlan = stateKey.bindingPlan();
        if (bindingPlan != currentResourceBindingPlan
                && !isEquivalentBindingPlan(bindingPlan)) {
            ResourceBinding binding = bindingPlan != null ? bindingPlan.binding() : null;
            if (binding != null) {
                binding.bind(context);
            }
        }
        currentResourceBindingPlan = bindingPlan;
        currentResourceBindingStamp = bindingPlan != null ? bindingPlan.stamp() : ResourceBindingStamp.NONE;
    }

    /**
     * Force apply all render state components (e.g., after context reset)
     */
    public void forceApplyState(RenderContext context) {
        if (currentRasterState != null) {
            applyRasterState(currentRasterState, context);
        } else if (currentState != null) {
            applyCompiledState(currentState, context);
        } else if (currentShaderBindingState != null) {
            GraphicsDriver.renderDevice().stateApplier().applyShaderBindingState(currentShaderBindingState, context);
        }

        if (currentResourceBindingPlan != null && currentResourceBindingPlan.binding() != null) {
            currentResourceBindingPlan.binding().bind(context);
        }
    }

    /**
     * Get current render state
     */
    public CompiledRenderState getCurrentState() {
        return currentState;
    }

    /**
     * Get current resource binding
     */
    public ResourceBinding getCurrentResourceBinding() {
        return currentResourceBindingPlan != null ? currentResourceBindingPlan.binding() : null;
    }

    public FrameCaptureSnapshot.RenderStateCapture captureCurrentState() {
        return new FrameCaptureSnapshot.RenderStateCapture(
                currentDomain,
                currentShaderId(),
                currentRenderTargetId(),
                currentResourceBindingPlan != null ? currentResourceBindingPlan.layoutKey() : null,
                currentResourceBindingStamp != null ? currentResourceBindingStamp : ResourceBindingStamp.NONE);
    }

    /**
     * Reset state manager
     */
    public void reset() {
        currentState = null;
        currentRasterState = null;
        currentShaderBindingState = null;
        currentAttachmentBindingState = null;
        currentResourceBindingPlan = null;
        currentResourceBindingStamp = ResourceBindingStamp.NONE;
        currentDomain = null;
    }

    public void resetDefault(RenderContext context) {
        if (defaultState == null) {
            defaultState = RenderStateCompiler.compile(RenderStatePatch.empty());
        }

        currentState = null;
        changeState(defaultState, context);
    }

    public void changeState(RenderStateComponent component, RenderContext context) {
        RenderStatePatch basePatch = currentState != null ? currentState.patch() : RenderStatePatch.empty();
        changeState(RenderStateCompiler.compile(basePatch.with(component)), context);
    }

    public void changeState(CompiledRenderState newState, RenderContext context) {
        changeState(newState, context, true);
    }

    public void changeState(CompiledRenderState newState, RenderContext context, boolean forceReplace) {
        if (currentState == newState) {
            return;
        }
        if (currentState == null) {
            applyCompiledState(newState, context);
        } else if (forceReplace) {
            applyDiff(currentState, newState, context);
        }

        currentState = newState;
        currentDomain = ExecutionDomain.RASTER;
        currentRasterState = null;
        currentAttachmentBindingState = null;
        currentShaderBindingState = null;
    }

    private void applyRasterKey(RasterPipelineKey stateKey, RenderContext context) {
        if (!stateKey.shouldSwitchRenderState()) {
            return;
        }
        CompiledRasterState newState = stateKey.compiledRasterState();
        if (newState == null) {
            return;
        }
        if (currentRasterState == null) {
            applyRasterState(newState, context);
        } else {
            applyRasterDiff(currentRasterState, newState, context);
        }
        currentRasterState = newState;
        currentState = null;
    }

    private void applyComputeKey(ComputePipelineKey stateKey, RenderContext context) {
        if (!stateKey.shouldSwitchRenderState()) {
            return;
        }
        CompiledComputeState newState = stateKey.compiledComputeState();
        if (newState == null || newState.shaderBindingState() == null) {
            return;
        }
        if (currentShaderBindingState != newState.shaderBindingState()
                && !Objects.equals(currentShaderBindingState, newState.shaderBindingState())) {
            GraphicsDriver.renderDevice().stateApplier().applyShaderBindingState(newState.shaderBindingState(), context);
        }
        currentShaderBindingState = newState.shaderBindingState();
        currentAttachmentBindingState = null;
        currentState = null;
        currentRasterState = null;
    }

    private void applyRasterState(CompiledRasterState state, RenderContext context) {
        if (state == null) {
            return;
        }
        if (state.pipelineRasterState() != null) {
            GraphicsDriver.renderDevice().stateApplier().applyPipelineRasterState(state.pipelineRasterState(), context);
        }
        if (state.attachmentBindingState() != null) {
            GraphicsDriver.renderDevice().stateApplier().applyAttachmentBindingState(state.attachmentBindingState(), context);
        }
        if (state.dynamicRenderState() != null) {
            GraphicsDriver.renderDevice().stateApplier().applyDynamicRenderState(state.dynamicRenderState(), context);
        }
        if (state.shaderBindingState() != null) {
            GraphicsDriver.renderDevice().stateApplier().applyShaderBindingState(state.shaderBindingState(), context);
        }
        currentAttachmentBindingState = state.attachmentBindingState();
        currentShaderBindingState = state.shaderBindingState();
    }

    private void applyRasterDiff(CompiledRasterState oldState, CompiledRasterState newState, RenderContext context) {
        if (oldState == null) {
            applyRasterState(newState, context);
            return;
        }
        if (oldState.pipelineRasterState() != newState.pipelineRasterState()
                && !Objects.equals(oldState.pipelineRasterState(), newState.pipelineRasterState())) {
            GraphicsDriver.renderDevice().stateApplier().applyPipelineRasterState(newState.pipelineRasterState(), context);
        }
        if (oldState.attachmentBindingState() != newState.attachmentBindingState()
                && !Objects.equals(oldState.attachmentBindingState(), newState.attachmentBindingState())) {
            GraphicsDriver.renderDevice().stateApplier().applyAttachmentBindingState(newState.attachmentBindingState(), context);
        }
        if (hasAutoViewport(newState.dynamicRenderState())
                || oldState.dynamicRenderState() != newState.dynamicRenderState()
                && !Objects.equals(oldState.dynamicRenderState(), newState.dynamicRenderState())) {
            GraphicsDriver.renderDevice().stateApplier().applyDynamicRenderState(newState.dynamicRenderState(), context);
        }
        if (oldState.shaderBindingState() != newState.shaderBindingState()
                && !Objects.equals(oldState.shaderBindingState(), newState.shaderBindingState())) {
            GraphicsDriver.renderDevice().stateApplier().applyShaderBindingState(newState.shaderBindingState(), context);
        }
        currentAttachmentBindingState = newState.attachmentBindingState();
        currentShaderBindingState = newState.shaderBindingState();
    }

    private void applyCompiledState(CompiledRenderState state, RenderContext context) {
        if (state == null) {
            return;
        }
        if (state.pipelineRasterState() != null) {
            GraphicsDriver.renderDevice().stateApplier().applyPipelineRasterState(state.pipelineRasterState(), context);
        }
        if (state.passBindingState() != null) {
            GraphicsDriver.renderDevice().stateApplier().applyPassBindingState(state.passBindingState(), context);
        }
        if (state.dynamicRenderState() != null) {
            GraphicsDriver.renderDevice().stateApplier().applyDynamicRenderState(state.dynamicRenderState(), context);
        }
    }

    private void applyDiff(CompiledRenderState oldState, CompiledRenderState newState, RenderContext context) {
        if (oldState.pipelineRasterState() != newState.pipelineRasterState()
                && !Objects.equals(oldState.pipelineRasterState(), newState.pipelineRasterState())) {
            GraphicsDriver.renderDevice().stateApplier().applyPipelineRasterState(newState.pipelineRasterState(), context);
        }
        if (oldState.passBindingState() != newState.passBindingState()
                && !Objects.equals(oldState.passBindingState(), newState.passBindingState())) {
            GraphicsDriver.renderDevice().stateApplier().applyPassBindingState(newState.passBindingState(), context);
        }
        if (hasAutoViewport(newState.dynamicRenderState())
                || oldState.dynamicRenderState() != newState.dynamicRenderState()
                && !Objects.equals(oldState.dynamicRenderState(), newState.dynamicRenderState())) {
            GraphicsDriver.renderDevice().stateApplier().applyDynamicRenderState(newState.dynamicRenderState(), context);
        }
    }

    private boolean hasAutoViewport(DynamicRenderState dynamicRenderState) {
        return dynamicRenderState != null
                && dynamicRenderState.viewportState() != null
                && dynamicRenderState.viewportState().auto();
    }

    private boolean isEquivalentBindingPlan(ResourceBindingPlan bindingPlan) {
        if (bindingPlan == null && currentResourceBindingPlan == null) {
            return true;
        }
        if (bindingPlan == null || currentResourceBindingPlan == null) {
            return false;
        }
        return Objects.equals(currentResourceBindingStamp, bindingPlan.stamp());
    }

    private KeyId currentShaderId() {
        if (currentShaderBindingState != null
                && currentShaderBindingState.shaderState() != null) {
            return currentShaderBindingState.shaderState().getShaderId();
        }
        if (currentRasterState != null
                && currentRasterState.shaderBindingState() != null
                && currentRasterState.shaderBindingState().shaderState() != null) {
            return currentRasterState.shaderBindingState().shaderState().getShaderId();
        }
        if (currentState != null && currentState.shaderState() != null) {
            return currentState.shaderState().getShaderId();
        }
        return null;
    }

    private KeyId currentRenderTargetId() {
        if (currentAttachmentBindingState != null
                && currentAttachmentBindingState.renderTargetState() != null) {
            return currentAttachmentBindingState.renderTargetState().renderTargetId();
        }
        if (currentRasterState != null
                && currentRasterState.attachmentBindingState() != null
                && currentRasterState.attachmentBindingState().renderTargetState() != null) {
            return currentRasterState.attachmentBindingState().renderTargetState().renderTargetId();
        }
        if (currentState != null && currentState.renderTargetState() != null) {
            return currentState.renderTargetState().renderTargetId();
        }
        return null;
    }
}

