package rogo.sketch.render.state.gl;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.lwjgl.opengl.GL11;
import rogo.sketch.api.RenderStateComponent;
import rogo.sketch.render.pipeline.RenderContext;
import rogo.sketch.util.KeyId;

import java.util.Objects;

public class ViewportState implements RenderStateComponent {
    public static final KeyId TYPE = KeyId.of("viewport");

    private int x, y, width, height;
    private boolean auto = true;

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
    public KeyId getIdentifier() {
        return TYPE;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ViewportState)) return false;
        ViewportState o = (ViewportState) other;
        return x == o.x && y == o.y && width == o.width && height == o.height && auto == o.auto;
    }

    @Override
    public void apply(RenderContext context) {
        if (auto) {
            GL11.glViewport(0, 0, context.windowWidth(), context.windowHeight());
        } else {
            GL11.glViewport(x, y, width, height);
        }

    }

    @Override
    public void deserializeFromJson(JsonObject json, Gson gson) {
        if (json.has("x") && json.has("y") && json.has("width") && json.has("height")) {
            x = json.get("x").getAsInt();
            y = json.get("y").getAsInt();
            width = json.get("width").getAsInt();
            height = json.get("height").getAsInt();

            auto = false;
        }
    }

    @Override
    public RenderStateComponent createInstance() {
        return new ViewportState();
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, width, height, auto);
    }
}