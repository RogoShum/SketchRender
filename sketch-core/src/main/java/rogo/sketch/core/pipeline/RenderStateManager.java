package rogo.sketch.core.pipeline;

import rogo.sketch.core.api.RenderStateComponent;
import rogo.sketch.core.resource.ResourceBinding;
import rogo.sketch.core.state.DefaultRenderStates;
import rogo.sketch.core.state.FullRenderState;

import java.util.Objects;

public class RenderStateManager {
    private FullRenderState defaultState;
    private FullRenderState currentState;
    private ResourceBinding currentResourceBinding;

    public void accept(RenderSetting setting, RenderContext context) {
        // Only switch render state if requested (compute shaders don't need it)
        if (setting.shouldSwitchRenderState() && setting.renderState() != null) {
            FullRenderState newState = setting.renderState();
            changeState(newState, context);
        }

        // Always bind resource bindings (needed for both graphics and compute)
        if (!Objects.equals(setting.resourceBinding(), currentResourceBinding)) {
            if (setting.resourceBinding() != null) {
                setting.resourceBinding().bind(context);
            }
            currentResourceBinding = setting.resourceBinding();
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