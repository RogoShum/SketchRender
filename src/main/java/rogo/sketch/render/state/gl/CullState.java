package rogo.sketch.render.state.gl;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.lwjgl.opengl.GL11;
import rogo.sketch.api.RenderStateComponent;
import rogo.sketch.render.RenderContext;
import rogo.sketch.render.driver.GraphicsDriver;
import rogo.sketch.util.Identifier;

public class CullState implements RenderStateComponent {
    public static final Identifier TYPE = Identifier.of("cull_face");
    
    private boolean enabled;
    private int face;
    private int frontFace;

    public CullState() {
        this.enabled = true;
        this.face = GL11.GL_BACK;
        this.frontFace = GL11.GL_CCW;
    }

    public CullState(boolean enabled, int face, int frontFace) {
        this.enabled = enabled;
        this.face = face;
        this.frontFace = frontFace;
    }

    @Override
    public Identifier getIdentifier() {
        return TYPE;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CullState)) return false;
        CullState c = (CullState) o;
        return enabled == c.enabled && face == c.face && frontFace == c.frontFace;
    }

    @Override
    public void apply(RenderContext context) {
        if (enabled) {
            GraphicsDriver.getCurrentAPI().enableCullFace();
            GraphicsDriver.getCurrentAPI().cullFace(face);
            GraphicsDriver.getCurrentAPI().frontFace(frontFace);
        } else {
            GraphicsDriver.getCurrentAPI().disableCullFace();
        }
    }

    @Override
    public int hashCode() {
        return Boolean.hashCode(enabled) ^ face ^ frontFace;
    }

    @Override
    public void deserializeFromJson(JsonObject json, Gson gson) {
        this.enabled = json.has("enabled") ? json.get("enabled").getAsBoolean() : true;
        
        if (json.has("face")) {
            this.face = parseCullFace(json.get("face").getAsString());
        }
        
        if (json.has("frontFace")) {
            this.frontFace = parseFrontFace(json.get("frontFace").getAsString());
        }
    }

    @Override
    public RenderStateComponent createInstance() {
        return new CullState();
    }
    
    private static int parseCullFace(String face) {
        return switch (face.toUpperCase()) {
            case "FRONT" -> GL11.GL_FRONT;
            case "BACK" -> GL11.GL_BACK;
            case "FRONT_AND_BACK" -> GL11.GL_FRONT_AND_BACK;
            default -> {
                System.err.println("Unknown cull face: " + face);
                yield GL11.GL_BACK;
            }
        };
    }
    
    private static int parseFrontFace(String frontFace) {
        return switch (frontFace.toUpperCase()) {
            case "CW" -> GL11.GL_CW;
            case "CCW" -> GL11.GL_CCW;
            default -> {
                System.err.println("Unknown front face: " + frontFace);
                yield GL11.GL_CCW;
            }
        };
    }
}