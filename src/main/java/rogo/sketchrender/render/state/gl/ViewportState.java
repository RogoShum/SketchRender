package rogo.sketchrender.render.state.gl;

import org.lwjgl.opengl.GL11;
import rogo.sketchrender.api.RenderStateComponent;

public class ViewportState implements RenderStateComponent {
    private final int x, y, width, height;

    public ViewportState(int x, int y, int width, int height) {
        this.x = x; this.y = y; this.width = width; this.height = height;
    }

    @Override
    public Class<? extends RenderStateComponent> getType() {
        return ViewportState.class;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ViewportState)) return false;
        ViewportState o = (ViewportState) other;
        return x == o.x && y == o.y && width == o.width && height == o.height;
    }

    @Override
    public void apply(RenderStateComponent prev) {
        ViewportState p = prev instanceof ViewportState ? (ViewportState) prev : null;
        if (p == null || x != p.x || y != p.y || width != p.width || height != p.height) {
            GL11.glViewport(x, y, width, height);
        }
    }
}