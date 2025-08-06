package rogo.sketchrender.render.state.gl;

import org.lwjgl.opengl.GL11;
import rogo.sketchrender.api.RenderStateComponent;

public class StencilState implements RenderStateComponent {
    private final boolean enabled;
    private final int func, ref, mask, fail, zfail, zpass;

    public StencilState(boolean enabled, int func, int ref, int mask, int fail, int zfail, int zpass) {
        this.enabled = enabled;
        this.func = func;
        this.ref = ref;
        this.mask = mask;
        this.fail = fail;
        this.zfail = zfail;
        this.zpass = zpass;
    }

    @Override
    public Class<? extends RenderStateComponent> getType() {
        return StencilState.class;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof StencilState)) return false;
        StencilState o = (StencilState) other;
        return enabled == o.enabled && func == o.func && ref == o.ref && mask == o.mask
                && fail == o.fail && zfail == o.zfail && zpass == o.zpass;
    }

    @Override
    public void apply(RenderStateComponent prev) {
        StencilState p = prev instanceof StencilState ? (StencilState) prev : null;
        if (p == null || enabled != p.enabled) {
            if (enabled) GL11.glEnable(GL11.GL_STENCIL_TEST);
            else GL11.glDisable(GL11.GL_STENCIL_TEST);
        }
        if (enabled && (p == null || func != p.func || ref != p.ref || mask != p.mask)) {
            GL11.glStencilFunc(func, ref, mask);
        }
        if (enabled && (p == null || fail != p.fail || zfail != p.zfail || zpass != p.zpass)) {
            GL11.glStencilOp(fail, zfail, zpass);
        }
    }
}