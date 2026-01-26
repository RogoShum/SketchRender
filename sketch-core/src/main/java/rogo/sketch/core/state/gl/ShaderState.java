package rogo.sketch.core.state.gl;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.lwjgl.opengl.GL20;
import rogo.sketch.core.api.RenderStateComponent;
import rogo.sketch.core.api.ShaderProvider;
import rogo.sketch.core.resource.GraphicsResourceManager;
import rogo.sketch.core.resource.ResourceReference;
import rogo.sketch.core.resource.ResourceTypes;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.util.KeyId;

import java.util.Objects;

public class ShaderState implements RenderStateComponent {
    public static final KeyId TYPE = ResourceTypes.SHADER_PROGRAM;
    private ResourceReference<ShaderProvider> shader;
    private KeyId shaderId;

    public ShaderState() {
        this.shader = GraphicsResourceManager.getInstance().getReference(ResourceTypes.SHADER_PROGRAM, KeyId.of("empty"));
        this.shaderId = KeyId.of("empty");
    }

    public ShaderState(KeyId keyId) {
        this.shader = GraphicsResourceManager.getInstance().getReference(ResourceTypes.SHADER_PROGRAM, keyId);
        this.shaderId = keyId;
    }

    @Override
    public KeyId getIdentifier() {
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
        if (shader.isAvailable()) {
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
            this.shader = GraphicsResourceManager.getInstance().getReference(ResourceTypes.SHADER_PROGRAM, KeyId.of(shaderIdStr));
            this.shaderId = KeyId.of(shaderIdStr);
        }
    }

    @Override
    public RenderStateComponent createInstance() {
        return new ShaderState();
    }

    public ResourceReference<ShaderProvider> shader() {
        return shader;
    }
}