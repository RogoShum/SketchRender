package rogo.sketchrender.render.state.gl;

import org.lwjgl.opengl.GL11;
import rogo.sketchrender.api.RenderStateComponent;
import rogo.sketchrender.render.sketch.RenderContext;

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
    public void apply(RenderContext context) {
        if (enabled) {
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthFunc(func);
        } else {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
        }
    }

    @Override
    public int hashCode() {
        return Boolean.hashCode(enabled) ^ func;
    }
}