package rogo.sketch.core.pipeline;

import rogo.sketch.core.api.RenderStateComponent;
import rogo.sketch.core.resource.ResourceBinding;
import rogo.sketch.core.packet.PipelineStateKey;
import rogo.sketch.core.packet.ResourceBindingPlan;
import rogo.sketch.core.driver.state.DefaultRenderStates;
import rogo.sketch.core.driver.state.FullRenderState;

import java.util.Objects;

public class RenderStateManager {
    private FullRenderState defaultState;
    private FullRenderState currentState;
    private ResourceBinding currentResourceBinding;

    public void accept(RenderSetting setting, RenderContext context) {
        accept(PipelineStateKey.from(setting), context);
    }

    public void accept(PipelineStateKey stateKey, RenderContext context) {
        // Only switch render state if requested (compute shaders don't need it)
        if (stateKey.shouldSwitchRenderState() && stateKey.renderState() != null) {
            FullRenderState newState = stateKey.renderState();
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
            for (RenderStateComponent comp : currentState.getComponents()) {
                comp.apply(context);
            }
        }

        if (currentResourceBinding != null) {
            currentResourceBinding.bind(context);
        }
    }

    /**
     * Get current render state
     */
    public FullRenderState getCurrentState() {
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
            defaultState = DefaultRenderStates.createDefaultFullRenderState();
        }

        currentState = null;
        changeState(defaultState, context);
    }

    public void changeState(RenderStateComponent component, RenderContext context) {
        if (currentState == null) {
            currentState = DefaultRenderStates.createDefaultFullRenderState();
        }

        RenderStateComponent oldComp = currentState.get(component.getIdentifier());
        if (!component.equals(oldComp)) {
            component.apply(context);
        }

        currentState = currentState.with(component);
    }

    public void changeState(FullRenderState newState, RenderContext context) {
        changeState(newState, context, true);
    }

    public void changeState(FullRenderState newState, RenderContext context, boolean forceReplace) {
        if (currentState == null) {
            // First time, apply all components
            for (RenderStateComponent comp : newState.getComponents()) {
                comp.apply(context);
            }
        } else if (forceReplace) {
            // Only apply changed components
            for (int i = 0; i < newState.getComponents().length; ++i) {
                RenderStateComponent newComp = newState.get(i);
                RenderStateComponent oldComp = currentState.get(i);
                if (!newComp.equals(oldComp)) {
                    newComp.apply(context);
                }
            }
        }

        currentState = newState;
    }
}
