package rogo.sketch.render.state.gl;

import org.lwjgl.opengl.GL20;
import rogo.sketch.api.RenderStateComponent;
import rogo.sketch.api.ShaderProvider;
import rogo.sketch.render.RenderContext;
import rogo.sketch.util.Identifier;

import java.util.Objects;

public class ShaderState implements RenderStateComponent {
    private final ShaderProvider shaderProvider;

    public ShaderState(ShaderProvider program) {
        this.shaderProvider = program;
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
        return Objects.equals(shaderProvider.getHandle(), that.shaderProvider.getHandle());
    }

    @Override
    public void apply(RenderContext context) {
        GL20.glUseProgram(shaderProvider.getHandle());
        context.set(Identifier.of("shader"), shaderProvider);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(shaderProvider.getHandle());
    }
}