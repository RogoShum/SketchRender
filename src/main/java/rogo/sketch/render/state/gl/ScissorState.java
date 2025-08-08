package rogo.sketch.render.state.gl;

import org.lwjgl.opengl.GL11;
import rogo.sketch.api.RenderStateComponent;
import rogo.sketch.render.RenderContext;

public class ScissorState implements RenderStateComponent {
    private final boolean enabled;
    private final int x, y, width, height;

    public ScissorState(boolean enabled, int x, int y, int width, int height) {
        this.enabled = enabled;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    @Override
    public Class<? extends RenderStateComponent> getType() {
        return ScissorState.class;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ScissorState)) return false;
        ScissorState o = (ScissorState) other;
        return enabled == o.enabled && x == o.x && y == o.y && width == o.width && height == o.height;
    }

    @Override
    public void apply(RenderContext context) {
        if (enabled) {
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor(x, y, width, height);
        } else {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }
    }
}