package rogo.sketch.render.state.gl;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.lwjgl.opengl.GL11;
import rogo.sketch.api.RenderStateComponent;
import rogo.sketch.render.RenderContext;
import rogo.sketch.util.Identifier;

public class DepthMaskState implements RenderStateComponent {
    public static final Identifier TYPE = Identifier.of("depth_mask");
    
    private boolean writable;

    public DepthMaskState() {
        this.writable = true;
    }

    public DepthMaskState(boolean writable) {
        this.writable = writable;
    }

    @Override
    public Identifier getIdentifier() {
        return TYPE;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof DepthMaskState)) return false;
        DepthMaskState o = (DepthMaskState) other;
        return writable == o.writable;
    }

    @Override
    public void apply(RenderContext context) {
        GL11.glDepthMask(writable);
    }

    @Override
    public int hashCode() {
        return Boolean.hashCode(writable);
    }

    @Override
    public void deserializeFromJson(JsonObject json, Gson gson) {
        this.writable = json.has("writable") ? json.get("writable").getAsBoolean() : true;
    }

    @Override
    public RenderStateComponent createInstance() {
        return new DepthMaskState();
    }
}
