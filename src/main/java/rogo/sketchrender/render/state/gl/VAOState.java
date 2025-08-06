package rogo.sketchrender.render.state.gl;

import org.lwjgl.opengl.GL30;
import rogo.sketchrender.api.RenderStateComponent;

public class VAOState implements RenderStateComponent {
    private final int vaoId;

    public VAOState(int vaoId) {
        this.vaoId = vaoId;
    }

    @Override
    public Class<? extends RenderStateComponent> getType() {
        return VAOState.class;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof VAOState)) return false;
        return this.vaoId == ((VAOState) other).vaoId;
    }

    @Override
    public void apply(RenderStateComponent prev) {
        int prevId = prev instanceof VAOState ? ((VAOState) prev).vaoId : -1;
        if (vaoId != prevId) {
            GL30.glBindVertexArray(vaoId);
        }
    }
}