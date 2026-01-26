package rogo.sketch.core.state.gl;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import rogo.sketch.core.api.RenderStateComponent;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.driver.GraphicsDriver;
import rogo.sketch.core.util.KeyId;

public class DepthMaskState implements RenderStateComponent {
    public static final KeyId TYPE = KeyId.of("depth_mask");

    private boolean writable;

    public DepthMaskState() {
        this.writable = true;
    }

    public DepthMaskState(boolean writable) {
        this.writable = writable;
    }

    @Override
    public KeyId getIdentifier() {
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