package rogo.sketchrender.render.state.gl;

import org.lwjgl.opengl.GL20;
import rogo.sketchrender.api.RenderStateComponent;
import rogo.sketchrender.api.ShaderProvider;

import java.util.Objects;

public class ShaderState implements RenderStateComponent {
    private final ShaderProvider program;

    public ShaderState(ShaderProvider program) {
        this.program = program;
    }

    @Override
    public Class<? extends RenderStateComponent> getType() {
        return ShaderState.class;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ShaderState that = (ShaderState) o;
        return Objects.equals(program.getHandle(), that.program.getHandle());
    }

    @Override
    public void apply(RenderStateComponent prev) {
        int prevId = prev instanceof ShaderState ? ((ShaderState) prev).program.getHandle() : -1;
        if (program.getHandle() != prevId) {
            GL20.glUseProgram(program.getHandle());
        }
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(program.getHandle());
    }
}