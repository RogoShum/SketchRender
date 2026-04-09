package rogo.sketch.core.pipeline;

import rogo.sketch.core.api.RenderStateComponent;
import rogo.sketch.core.driver.state.CompiledRenderState;
import rogo.sketch.core.driver.state.RenderStateCompiler;
import rogo.sketch.core.driver.state.RenderStatePatch;
import rogo.sketch.core.driver.GraphicsDriver;
import rogo.sketch.core.resource.ResourceBinding;
import rogo.sketch.core.packet.PipelineStateKey;
import rogo.sketch.core.packet.ResourceBindingPlan;

import java.util.Objects;

public class RenderStateManager {
    private CompiledRenderState defaultState;
    private CompiledRenderState currentState;
    private ResourceBinding currentResourceBinding;

    public void accept(RenderSetting setting, RenderContext context) {
        accept(PipelineStateKey.from(setting), context);
    }

    public void accept(PipelineStateKey stateKey, RenderContext context) {
        // Only switch render state if requested (compute shaders don't need it)
        if (stateKey.shouldSwitchRenderState() && stateKey.compiledRenderState() != null) {
            CompiledRenderState newState = stateKey.compiledRenderState();
            changeState(newState, context);
        }

        // Always bind resource bindings (needed for both graphics and compute)
        ResourceBindingPlan bindingPlan = stateKey.bindingPlan();
        ResourceBinding binding = bindingPlan != null ? bindingPlan.binding() : null;
        if (!Objects.equals(binding, currentResourceBinding)) {
            if (binding != null) {
                binding.bind(context);
            }
            currentResourceBinding = binding;
        }
    }

    /**
     * Force apply all render state components (e.g., after context reset)
     */
    public void forceApplyState(RenderContext context) {
        if (currentState != null) {
            applyCompiledState(currentState, context);
        }

        if (currentResourceBinding != null) {
            currentResourceBinding.bind(context);
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
        return currentResourceBinding;
    }

    /**
     * Reset state manager
     */
    public void reset() {
        currentState = null;
        currentResourceBinding = null;
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
        if (currentState == null) {
            applyCompiledState(newState, context);
        } else if (forceReplace) {
            applyDiff(currentState, newState, context);
        }

        currentState = newState;
    }

    private void applyCompiledState(CompiledRenderState state, RenderContext context) {
        if (state == null) {
            return;
        }
        if (state.pipelineRasterState() != null) {
            GraphicsDriver.runtime().stateApplier().applyPipelineRasterState(state.pipelineRasterState(), context);
        }
        if (state.dynamicRenderState() != null) {
            GraphicsDriver.runtime().stateApplier().applyDynamicRenderState(state.dynamicRenderState(), context);
        }
        if (state.passBindingState() != null) {
            GraphicsDriver.runtime().stateApplier().applyPassBindingState(state.passBindingState(), context);
        }
    }

    private void applyDiff(CompiledRenderState oldState, CompiledRenderState newState, RenderContext context) {
        if (!Objects.equals(oldState.pipelineRasterState(), newState.pipelineRasterState())) {
            GraphicsDriver.runtime().stateApplier().applyPipelineRasterState(newState.pipelineRasterState(), context);
        }
        if (!Objects.equals(oldState.dynamicRenderState(), newState.dynamicRenderState())) {
            GraphicsDriver.runtime().stateApplier().applyDynamicRenderState(newState.dynamicRenderState(), context);
        }
        if (!Objects.equals(oldState.passBindingState(), newState.passBindingState())) {
            GraphicsDriver.runtime().stateApplier().applyPassBindingState(newState.passBindingState(), context);
        }
    }
}

