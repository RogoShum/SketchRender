package rogo.sketch.render.state.gl;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.lwjgl.opengl.GL20;
import rogo.sketch.api.RenderStateComponent;
import rogo.sketch.render.RenderContext;
import rogo.sketch.render.resource.GraphicsResourceManager;
import rogo.sketch.render.resource.ResourceReference;
import rogo.sketch.render.resource.ResourceTypes;
import rogo.sketch.render.shader.Shader;
import rogo.sketch.util.Identifier;

import java.util.Objects;
import java.util.Optional;

public class ShaderState implements RenderStateComponent {
    public static final Identifier TYPE = Identifier.of("shader");
    private ResourceReference<Shader> shader;
    private Identifier shaderId;

    public ShaderState() {
        this.shader = GraphicsResourceManager.getInstance().getReference(ResourceTypes.SHADER_PROGRAM, Identifier.of(""));
        this.shaderId = Identifier.of("");
    }

    public ShaderState(Identifier identifier) {
        this.shader = GraphicsResourceManager.getInstance().getReference(ResourceTypes.SHADER_PROGRAM, identifier);
        this.shaderId = identifier;
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
        return Objects.equals(shaderId, that.shaderId);
    }

    @Override
    public void apply(RenderContext context) {
        Optional<Shader> shader = this.shader.get();
        if (shader.isPresent()) {
            GL20.glUseProgram(shader.get().getHandle());
            context.setShaderProvider(shader.get());
        }
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(shaderId);
    }

    @Override
    public void deserializeFromJson(JsonObject json, Gson gson) {
        if (json.has("identifier")) {
            String shaderIdStr = json.get("identifier").getAsString();
            this.shader = GraphicsResourceManager.getInstance().getReference(ResourceTypes.SHADER_PROGRAM, Identifier.of(shaderIdStr));
            this.shaderId = Identifier.of(shaderIdStr);
        }
    }

    @Override
    public RenderStateComponent createInstance() {
        return new ShaderState();
    }
}