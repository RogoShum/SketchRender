package rogo.sketchrender.render.state.gl;

import org.lwjgl.opengl.GL11;
import rogo.sketchrender.api.RenderStateComponent;
import rogo.sketchrender.render.sketch.RenderContext;

public class CullState implements RenderStateComponent {
    private final boolean enabled;
    private final int mode;

    public CullState(boolean enabled, int mode) {
        this.enabled = enabled;
        this.mode = mode;
    }

    @Override
    public Class<? extends RenderStateComponent> getType() {
        return CullState.class;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof CullState)) return false;
        CullState o = (CullState) other;
        return enabled == o.enabled && mode == o.mode;
    }
    @Override
    public void apply(RenderContext context) {
        if (enabled) {
            GL11.glEnable(GL11.GL_CULL_FACE);
            GL11.glCullFace(mode);
        } else {
            GL11.glDisable(GL11.GL_CULL_FACE);
        }
    }
}