package rogo.sketchrender.render.state.gl;

import org.lwjgl.opengl.GL11;
import rogo.sketchrender.api.RenderStateComponent;

public class PolygonModeState implements RenderStateComponent {
    private final int face, mode;

    public PolygonModeState(int face, int mode) {
        this.face = face; this.mode = mode;
    }

    @Override
    public Class<? extends RenderStateComponent> getType() {
        return PolygonModeState.class;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof PolygonModeState)) return false;
        PolygonModeState o = (PolygonModeState) other;
        return face == o.face && mode == o.mode;
    }

    @Override
    public void apply(RenderStateComponent prev) {
        PolygonModeState p = prev instanceof PolygonModeState ? (PolygonModeState) prev : null;
        if (p == null || face != p.face || mode != p.mode) {
            GL11.glPolygonMode(face, mode);
        }
    }
}