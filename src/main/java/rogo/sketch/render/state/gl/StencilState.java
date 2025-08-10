package rogo.sketch.render.state.gl;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.lwjgl.opengl.GL11;
import rogo.sketch.api.RenderStateComponent;
import rogo.sketch.render.RenderContext;
import rogo.sketch.util.Identifier;

public class StencilState implements RenderStateComponent {
    public static final Identifier TYPE = Identifier.of("stencil_test");
    
    private boolean enabled;
    private int func, ref, mask, fail, zfail, zpass;

    // Default constructor for prototype
    public StencilState() {
        this.enabled = false;
        this.func = GL11.GL_ALWAYS;
        this.ref = 0;
        this.mask = 0xFF;
        this.fail = GL11.GL_KEEP;
        this.zfail = GL11.GL_KEEP;
        this.zpass = GL11.GL_KEEP;
    }

    public StencilState(boolean enabled, int func, int ref, int mask, int fail, int zfail, int zpass) {
        this.enabled = enabled;
        this.func = func;
        this.ref = ref;
        this.mask = mask;
        this.fail = fail;
        this.zfail = zfail;
        this.zpass = zpass;
    }

    @Override
    public Identifier getIdentifier() {
        return TYPE;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof StencilState)) return false;
        StencilState o = (StencilState) other;
        return enabled == o.enabled && func == o.func && ref == o.ref && mask == o.mask
                && fail == o.fail && zfail == o.zfail && zpass == o.zpass;
    }

    @Override
    public void apply(RenderContext context) {
        if (enabled) {
            GL11.glEnable(GL11.GL_STENCIL_TEST);
            GL11.glStencilFunc(func, ref, mask);
            GL11.glStencilOp(fail, zfail, zpass);
        } else {
            GL11.glDisable(GL11.GL_STENCIL_TEST);
        }
    }

    @Override
    public void deserializeFromJson(JsonObject json, Gson gson) {
        this.enabled = json.has("enabled") ? json.get("enabled").getAsBoolean() : false;
        
        if (json.has("function")) {
            this.func = parseStencilFunction(json.get("function").getAsString());
        }
        
        this.ref = json.has("ref") ? json.get("ref").getAsInt() : 0;
        this.mask = json.has("mask") ? json.get("mask").getAsInt() : 0xFF;
        
        if (json.has("fail")) {
            this.fail = parseStencilOp(json.get("fail").getAsString());
        }
        
        if (json.has("zfail")) {
            this.zfail = parseStencilOp(json.get("zfail").getAsString());
        }
        
        if (json.has("zpass")) {
            this.zpass = parseStencilOp(json.get("zpass").getAsString());
        }
    }

    @Override
    public RenderStateComponent createInstance() {
        return new StencilState();
    }

    private static int parseStencilFunction(String function) {
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
                System.err.println("Unknown stencil function: " + function);
                yield GL11.GL_ALWAYS;
            }
        };
    }

    private static int parseStencilOp(String op) {
        return switch (op.toUpperCase()) {
            case "KEEP" -> GL11.GL_KEEP;
            case "ZERO" -> GL11.GL_ZERO;
            case "REPLACE" -> GL11.GL_REPLACE;
            case "INCR" -> GL11.GL_INCR;
            case "DECR" -> GL11.GL_DECR;
            case "INVERT" -> GL11.GL_INVERT;
            default -> {
                System.err.println("Unknown stencil operation: " + op);
                yield GL11.GL_KEEP;
            }
        };
    }

    @Override
    public int hashCode() {
        return Boolean.hashCode(enabled) ^ func ^ ref ^ mask ^ fail ^ zfail ^ zpass;
    }
}