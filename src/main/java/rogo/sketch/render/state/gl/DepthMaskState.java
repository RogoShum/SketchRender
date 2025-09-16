package rogo.sketch.render.state.gl;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import rogo.sketch.api.RenderStateComponent;
import rogo.sketch.render.pipeline.RenderContext;
import rogo.sketch.render.driver.GraphicsDriver;
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
        GraphicsDriver.getCurrentAPI().depthMask(writable);
    }

    @Override
    public int hashCode() {
        return Boolean.hashCode(writable);
    }

    @Override
    public void deserializeFromJson(JsonObject json, Gson gson) {
        this.writable = !json.has("writable") || json.get("writable").getAsBoolean();
    }

    @Override
    public RenderStateComponent createInstance() {
        return new DepthMaskState();
    }
}