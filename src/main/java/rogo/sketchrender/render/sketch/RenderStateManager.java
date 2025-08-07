package rogo.sketchrender.render.sketch;

import rogo.sketchrender.api.RenderStateComponent;
import rogo.sketchrender.render.sketch.component.RenderTarget;
import rogo.sketchrender.render.sketch.component.ResourceBinding;
import rogo.sketchrender.render.sketch.state.FullRenderState;

import java.util.Objects;

public class RenderStateManager {
    private FullRenderState currentState;
    private ResourceBinding currentResourceBinding;
    private RenderTarget currentRenderTarget;

    public void accept(RenderSetting setting, RenderContext context) {
        FullRenderState newState = setting.renderState();
        if (currentState == null) {
            for (RenderStateComponent comp : newState.getComponentTypes().stream().map(newState::get).toList()) {
                comp.apply(context);
            }
        } else {
            for (Class<? extends RenderStateComponent> type : newState.getComponentTypes()) {
                RenderStateComponent newComp = newState.get(type);
                RenderStateComponent oldComp = currentState.get(type);
                if (!newComp.equals(oldComp)) {
                    newComp.apply(context);
                }
            }
        }
        currentState = newState;

        if (!Objects.equals(setting.resourceBinding(), currentResourceBinding)) {
            //setting.textureBinding().apply(currentTextureBinding);
            currentResourceBinding = setting.resourceBinding();
        }

        if (!Objects.equals(setting.renderTarget(), currentRenderTarget)) {
            //setting.renderTarget().apply(currentRenderTarget);
            currentRenderTarget = setting.renderTarget();
        }
    }
}