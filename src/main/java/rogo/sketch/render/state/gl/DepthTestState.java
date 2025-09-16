package rogo.sketch.render.state.gl;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.lwjgl.opengl.GL11;
import rogo.sketch.api.RenderStateComponent;
import rogo.sketch.render.pipeline.RenderContext;
import rogo.sketch.render.driver.GraphicsDriver;
import rogo.sketch.util.Identifier;

public class DepthTestState implements RenderStateComponent {
    public static final Identifier TYPE = Identifier.of("depth_test");

    private boolean enabled;
    private int func;

    public DepthTestState() {
        this.enabled = true;
        this.func = GL11.GL_LESS;
    }

    public DepthTestState(boolean enabled, int func) {
        this.enabled = enabled;
        this.func = func;
    }

    @Override
    public Identifier getIdentifier() {
        return TYPE;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DepthTestState)) return false;
        DepthTestState d = (DepthTestState) o;
        return enabled == d.enabled && func == d.func;
    }

    @Override
    public void apply(RenderContext context) {
        if (enabled) {
            GraphicsDriver.getCurrentAPI().enableDepthTest();
            GraphicsDriver.getCurrentAPI().depthFunc(func);
        } else {
            GraphicsDriver.getCurrentAPI().disableDepthTest();
        }
    }

    @Override
    public int hashCode() {
        return Boolean.hashCode(enabled) ^ func;
    }

    @Override
    public void deserializeFromJson(JsonObject json, Gson gson) {
        this.enabled = !json.has("enabled") || json.get("enabled").getAsBoolean();

        if (json.has("function")) {
            this.func = parseDepthFunction(json.get("function").getAsString());
        }
    }

    @Override
    public RenderStateComponent createInstance() {
        return new DepthTestState();
    }

    private static int parseDepthFunction(String function) {
        return switch (function.toUpperCase()) {
            case "NEVER" -> GL11.GL_NEVER;
            case "LESS" -> GL11.GL_LESS;
            case "EQUAL" -> GL11.GL_EQUAL;
            case "LEQUAL" -> GL11.GL_LEQUAL;
            case "GREATER" -> GL11.GL_GREATER;
            case "NOTEQUAL" -> GL11.GL_NOTEQUAL;
            case "GEQUAL" -> GL11.GL_GEQUAL;
            case "ALWAYS" -> GL11.GL_ALWAYS;
            default -> {
                System.err.println("Unknown depth function: " + function);
                yield GL11.GL_LESS;
            }
        };
    }
}