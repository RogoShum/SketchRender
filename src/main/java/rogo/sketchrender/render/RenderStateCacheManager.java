package rogo.sketchrender.render;

import rogo.sketchrender.api.RenderStateComponent;
import rogo.sketchrender.render.component.RenderTarget;
import rogo.sketchrender.render.component.TextureBinding;
import rogo.sketchrender.render.state.FullRenderState;

import java.util.Objects;

public class RenderStateCacheManager {
    private FullRenderState currentState;
    private TextureBinding currentTextureBinding;
    private RenderTarget currentRenderTarget;

    public void accept(RenderSetting setting) {
        FullRenderState newState = setting.renderState();
        if (currentState == null) {
            for (RenderStateComponent comp : newState.getComponentTypes().stream().map(newState::get).toList()) {
                comp.apply(null);
            }
        } else {
            for (Class<? extends RenderStateComponent> type : newState.getComponentTypes()) {
                RenderStateComponent newComp = newState.get(type);
                RenderStateComponent oldComp = currentState.get(type);
                if (!newComp.equals(oldComp)) {
                    newComp.apply(oldComp);
                }
            }
        }
        currentState = newState;

        if (!Objects.equals(setting.textureBinding(), currentTextureBinding)) {
            //setting.textureBinding().apply(currentTextureBinding);
            currentTextureBinding = setting.textureBinding();
        }

        if (!Objects.equals(setting.renderTarget(), currentRenderTarget)) {
            //setting.renderTarget().apply(currentRenderTarget);
            currentRenderTarget = setting.renderTarget();
        }
    }
}