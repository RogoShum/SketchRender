package rogo.sketchrender.render.state;

import rogo.sketchrender.api.RenderStateComponent;

public class DepthState implements RenderStateComponent {
    public final boolean enabled;
    public final int func;

    public DepthState(boolean enabled, int func) {
        this.enabled = enabled;
        this.func = func;
    }

    @Override
    public Class<? extends RenderStateComponent> getType() {
        return DepthState.class;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DepthState)) return false;
        DepthState d = (DepthState) o;
        return enabled == d.enabled && func == d.func;
    }

    @Override
    public void apply(RenderStateComponent prev) {

    }

    @Override
    public int hashCode() {
        return Boolean.hashCode(enabled) ^ func;
    }
}