package rogo.sketch.render;

import rogo.sketch.api.RenderStateComponent;
import rogo.sketch.render.resource.ResourceBinding;
import rogo.sketch.render.state.FullRenderState;
import rogo.sketch.util.Identifier;

import java.util.Objects;

public class RenderStateManager {
    private FullRenderState currentState;
    private ResourceBinding currentResourceBinding;

    public void accept(RenderSetting setting, RenderContext context) {
        // Only switch render state if requested (compute shaders don't need it)
        if (setting.shouldSwitchRenderState() && setting.renderState() != null) {
            FullRenderState newState = setting.renderState();
            
            if (currentState == null) {
                // First time, apply all components
                for (RenderStateComponent comp : newState.getComponentTypes().stream().map(newState::get).toList()) {
                    comp.apply(context);
                }
            } else {
                // Only apply changed components
                for (Identifier type : newState.getComponentTypes()) {
                    RenderStateComponent newComp = newState.get(type);
                    RenderStateComponent oldComp = currentState.get(type);
                    if (!newComp.equals(oldComp)) {
                        newComp.apply(context);
                    }
                }
            }
            currentState = newState;
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
            for (RenderStateComponent comp : currentState.getComponentTypes().stream().map(currentState::get).toList()) {
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
}