package rogo.sketch.render.state.gl;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.lwjgl.opengl.GL11;
import rogo.sketch.api.RenderStateComponent;
import rogo.sketch.render.RenderContext;
import rogo.sketch.util.Identifier;

public class DepthState implements RenderStateComponent {
    public static final Identifier TYPE = Identifier.of("depth_state");
    
    private boolean enabled;
    private int func;
    private boolean write;

    public DepthState() {
        this.enabled = true;
        this.func = GL11.GL_LESS;
        this.write = true;
    }

    public DepthState(boolean enabled, int func, boolean write) {
        this.enabled = enabled;
        this.func = func;
        this.write = write;
    }

    public DepthState(boolean enabled, int func) {
        this(enabled, func, true);
    }

    @Override
    public Identifier getIdentifier() {
        return TYPE;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DepthState)) return false;
        DepthState d = (DepthState) o;
        return enabled == d.enabled && func == d.func && write == d.write;
    }

    @Override
    public void apply(RenderContext context) {
        if (enabled) {
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthFunc(func);
        } else {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
        }
        GL11.glDepthMask(write);
    }

    @Override
    public int hashCode() {
        return Boolean.hashCode(enabled) ^ func ^ Boolean.hashCode(write);
    }

    @Override
    public void deserializeFromJson(JsonObject json, Gson gson) {
        this.enabled = !json.has("enabled") || json.get("enabled").getAsBoolean();
        this.write = !json.has("write") || json.get("write").getAsBoolean();
        
        if (json.has("function")) {
            this.func = parseDepthFunction(json.get("function").getAsString());
        }
    }

    @Override
    public RenderStateComponent createInstance() {
        return new DepthState();
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