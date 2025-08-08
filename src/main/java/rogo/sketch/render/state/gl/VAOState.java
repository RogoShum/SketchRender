package rogo.sketch.render.state.gl;

import org.lwjgl.opengl.GL30;
import rogo.sketch.api.RenderStateComponent;
import rogo.sketch.render.RenderContext;

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