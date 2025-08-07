package rogo.sketchrender.render.state.gl;

import org.lwjgl.opengl.GL30;
import rogo.sketchrender.api.RenderStateComponent;
import rogo.sketchrender.render.sketch.RenderContext;

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
    public void apply(RenderContext context) {
        GL30.glBindVertexArray(vaoId);
    }
}