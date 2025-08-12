package rogo.sketch.render.state.gl;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.lwjgl.opengl.GL20;
import rogo.sketch.api.RenderStateComponent;
import rogo.sketch.render.RenderContext;
import rogo.sketch.render.resource.GraphicsResourceManager;
import rogo.sketch.render.resource.ResourceTypes;
import rogo.sketch.render.shader.Shader;
import rogo.sketch.util.Identifier;

import java.util.Objects;
import java.util.Optional;

public class ShaderState implements RenderStateComponent {
    public static final Identifier TYPE = Identifier.of("shader");

    private Identifier shaderIdentifier;

    public ShaderState() {
        this.shaderIdentifier = null;
    }

    public ShaderState(Identifier identifier) {
        this.shaderIdentifier = identifier;
    }

    @Override
    public Identifier getIdentifier() {
        return TYPE;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ShaderState that = (ShaderState) o;
        return Objects.equals(shaderIdentifier, that.shaderIdentifier);
    }

    @Override
    public void apply(RenderContext context) {
        Optional<Shader> shader = GraphicsResourceManager.getInstance().getResource(ResourceTypes.SHADER_PROGRAM, shaderIdentifier);
        if (shader.isPresent()) {
            GL20.glUseProgram(shader.get().getHandle());
            context.set(ResourceTypes.SHADER_PROGRAM, shaderIdentifier);
        }
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(shaderIdentifier);
    }

    @Override
    public void deserializeFromJson(JsonObject json, Gson gson) {
        if (json.has("identifier")) {
            String shaderIdStr = json.get("identifier").getAsString();
            shaderIdentifier = Identifier.of(shaderIdStr);
        }
    }

    @Override
    public RenderStateComponent createInstance() {
        return new ShaderState();
    }
}