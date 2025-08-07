package rogo.sketchrender.render.state.gl;

import org.lwjgl.opengl.GL11;
import rogo.sketchrender.api.RenderStateComponent;
import rogo.sketchrender.render.sketch.RenderContext;

public class BlendState implements RenderStateComponent {
    public final boolean enabled;
    public final int srcFactor, dstFactor;

    public BlendState(boolean enabled, int src, int dst) {
        this.enabled = enabled;
        this.srcFactor = src;
        this.dstFactor = dst;
    }

    @Override
    public Class<? extends RenderStateComponent> getType() {
        return BlendState.class;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof BlendState)) return false;
        BlendState b = (BlendState) o;
        return enabled == b.enabled && srcFactor == b.srcFactor && dstFactor == b.dstFactor;
    }

    @Override
    public void apply(RenderContext context) {
        if (enabled) {
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(srcFactor, dstFactor);
        } else {
            GL11.glDisable(GL11.GL_BLEND);
        }
    }

    @Override
    public int hashCode() {
        return Boolean.hashCode(enabled) ^ srcFactor ^ dstFactor;
    }
}