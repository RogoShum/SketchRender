package rogo.sketch.render.state.gl;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.lwjgl.opengl.GL11;
import rogo.sketch.api.RenderStateComponent;
import rogo.sketch.render.RenderContext;
import rogo.sketch.util.Identifier;

public class BlendState implements RenderStateComponent {
    public static final Identifier TYPE = Identifier.of("blend_state");
    
    private boolean enabled;
    private int srcFactor, dstFactor;

    // Default constructor for prototype
    public BlendState() {
        this.enabled = false;
        this.srcFactor = GL11.GL_SRC_ALPHA;
        this.dstFactor = GL11.GL_ONE_MINUS_SRC_ALPHA;
    }

    public BlendState(boolean enabled, int src, int dst) {
        this.enabled = enabled;
        this.srcFactor = src;
        this.dstFactor = dst;
    }

    @Override
    public Identifier getIdentifier() {
        return TYPE;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof BlendState)) return false;
        BlendState b = (BlendState) o;
        return enabled == b.enabled && srcFactor == b.srcFactor && dstFactor == b.dstFactor;
    }

    @Override
    public void apply(RenderContext context) {
        if (enabled) {
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(srcFactor, dstFactor);
        } else {
            GL11.glDisable(GL11.GL_BLEND);
        }
    }

    @Override
    public int hashCode() {
        return Boolean.hashCode(enabled) ^ srcFactor ^ dstFactor;
    }

    @Override
    public void deserializeFromJson(JsonObject json, Gson gson) {
        this.enabled = json.has("enabled") ? json.get("enabled").getAsBoolean() : true;
        
        if (json.has("srcFactor")) {
            this.srcFactor = parseBlendFactor(json.get("srcFactor").getAsString());
        }
        
        if (json.has("dstFactor")) {
            this.dstFactor = parseBlendFactor(json.get("dstFactor").getAsString());
        }
    }

    @Override
    public RenderStateComponent createInstance() {
        return new BlendState();
    }
    
    private static int parseBlendFactor(String factor) {
        return switch (factor.toUpperCase()) {
            case "ZERO" -> GL11.GL_ZERO;
            case "ONE" -> GL11.GL_ONE;
            case "SRC_COLOR" -> GL11.GL_SRC_COLOR;
            case "ONE_MINUS_SRC_COLOR" -> GL11.GL_ONE_MINUS_SRC_COLOR;
            case "DST_COLOR" -> GL11.GL_DST_COLOR;
            case "ONE_MINUS_DST_COLOR" -> GL11.GL_ONE_MINUS_DST_COLOR;
            case "SRC_ALPHA" -> GL11.GL_SRC_ALPHA;
            case "ONE_MINUS_SRC_ALPHA" -> GL11.GL_ONE_MINUS_SRC_ALPHA;
            case "DST_ALPHA" -> GL11.GL_DST_ALPHA;
            case "ONE_MINUS_DST_ALPHA" -> GL11.GL_ONE_MINUS_DST_ALPHA;
            case "SRC_ALPHA_SATURATE" -> GL11.GL_SRC_ALPHA_SATURATE;
            default -> {
                System.err.println("Unknown blend factor: " + factor);
                yield GL11.GL_SRC_ALPHA;
            }
        };
    }
}