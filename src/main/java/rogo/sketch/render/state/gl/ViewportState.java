package rogo.sketch.render.state.gl;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.lwjgl.opengl.GL11;
import rogo.sketch.api.RenderStateComponent;
import rogo.sketch.render.RenderContext;
import rogo.sketch.util.Identifier;

public class ViewportState implements RenderStateComponent {
    public static final Identifier TYPE = Identifier.of("viewport");
    
    private int x, y, width, height;

    // Default constructor for prototype
    public ViewportState() {
        this.x = 0;
        this.y = 0;
        this.width = 1920;
        this.height = 1080;
    }

    public ViewportState(int x, int y, int width, int height) {
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
        if (!(other instanceof ViewportState)) return false;
        ViewportState o = (ViewportState) other;
        return x == o.x && y == o.y && width == o.width && height == o.height;
    }

    @Override
    public void apply(RenderContext context) {
        GL11.glViewport(x, y, width, height);
    }

    @Override
    public void deserializeFromJson(JsonObject json, Gson gson) {
        this.x = json.has("x") ? json.get("x").getAsInt() : 0;
        this.y = json.has("y") ? json.get("y").getAsInt() : 0;
        this.width = json.has("width") ? json.get("width").getAsInt() : 1920;
        this.height = json.has("height") ? json.get("height").getAsInt() : 1080;
    }

    @Override
    public RenderStateComponent createInstance() {
        return new ViewportState();
    }

    @Override
    public int hashCode() {
        return x ^ y ^ width ^ height;
    }
}