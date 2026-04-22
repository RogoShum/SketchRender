package rogo.sketch.core.driver.state.component;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import rogo.sketch.core.api.RenderStateComponent;
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
    public int hashCode() {
        return Boolean.hashCode(writable);
    }

    @Override
    public void deserializeFromJson(JsonObject json, Gson gson, rogo.sketch.core.resource.GraphicsResourceManager resourceManager) {
        this.writable = !json.has("writable") || json.get("writable").getAsBoolean();
    }

    @Override
    public RenderStateComponent createInstance() {
        return new DepthMaskState();
    }

    public boolean writable() {
        return writable;
    }
}


