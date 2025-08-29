package rogo.sketch.render.state.gl;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import rogo.sketch.api.RenderStateComponent;
import rogo.sketch.render.RenderContext;
import rogo.sketch.render.driver.GraphicsDriver;
import rogo.sketch.util.Identifier;

public class ScissorState implements RenderStateComponent {
    public static final Identifier TYPE = Identifier.of("scissor_test");

    private boolean enabled;
    private int x, y, width, height;

    public ScissorState() {
        this.enabled = false;
        this.x = 0;
        this.y = 0;
        this.width = 0;
        this.height = 0;
    }

    public ScissorState(boolean enabled, int x, int y, int width, int height) {
        this.enabled = enabled;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    @Override
    public Identifier getIdentifier() {
        return TYPE;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ScissorState)) return false;
        ScissorState o = (ScissorState) other;
        return enabled == o.enabled && x == o.x && y == o.y && width == o.width && height == o.height;
    }

    @Override
    public void apply(RenderContext context) {
        if (enabled) {
            GraphicsDriver.getCurrentAPI().enableScissor(x, y, width, height);
        } else {
            GraphicsDriver.getCurrentAPI().disableScissor();
        }
    }

    @Override
    public void deserializeFromJson(JsonObject json, Gson gson) {
        this.enabled = json.has("enabled") ? json.get("enabled").getAsBoolean() : false;
        this.x = json.has("x") ? json.get("x").getAsInt() : 0;
        this.y = json.has("y") ? json.get("y").getAsInt() : 0;
        this.width = json.has("width") ? json.get("width").getAsInt() : 0;
        this.height = json.has("height") ? json.get("height").getAsInt() : 0;
    }

    @Override
    public RenderStateComponent createInstance() {
        return new ScissorState();
    }

    @Override
    public int hashCode() {
        return Boolean.hashCode(enabled) ^ x ^ y ^ width ^ height;
    }
}