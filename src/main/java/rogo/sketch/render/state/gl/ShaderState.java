package rogo.sketch.render.state.gl;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.lwjgl.opengl.GL20;
import rogo.sketch.api.RenderStateComponent;
import rogo.sketch.api.ShaderProvider;
import rogo.sketch.render.RenderContext;
import rogo.sketch.util.Identifier;

import java.util.Objects;

public class ShaderState implements RenderStateComponent {
    public static final Identifier TYPE = Identifier.of("shader");
    
    private ShaderProvider shaderProvider;

    // Default constructor for prototype
    public ShaderState() {
        this.shaderProvider = null; // Will be set during deserialization
    }

    public ShaderState(ShaderProvider program) {
        this.shaderProvider = program;
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

    @Override
    public void deserializeFromJson(JsonObject json, Gson gson) {
        // ShaderState deserialization is complex because it needs resource manager
        // For now, we'll support setting from identifier in the JSON
        if (json.has("shaderIdentifier")) {
            String shaderIdStr = json.get("shaderIdentifier").getAsString();
            // This would need to be resolved through GraphicsResourceManager
            // For now, we'll leave it null and expect it to be set externally
            System.out.println("ShaderState needs shader with identifier: " + shaderIdStr);
        }
    }

    @Override
    public RenderStateComponent createInstance() {
        return new ShaderState();
    }
}