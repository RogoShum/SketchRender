package rogo.sketchrender.render.state.gl;

import org.lwjgl.opengl.GL11;
import rogo.sketchrender.api.RenderStateComponent;

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
    public void apply(RenderStateComponent prev) {
        BlendState p = prev instanceof BlendState ? (BlendState) prev : null;
        if (p == null || enabled != p.enabled) {
            if (enabled) GL11.glEnable(GL11.GL_BLEND);
            else GL11.glDisable(GL11.GL_BLEND);
        }
        if (enabled && (p == null || srcFactor != p.srcFactor || dstFactor != p.dstFactor)) {
            GL11.glBlendFunc(srcFactor, dstFactor);
        }
    }

    @Override
    public int hashCode() {
        return Boolean.hashCode(enabled) ^ srcFactor ^ dstFactor;
    }
}