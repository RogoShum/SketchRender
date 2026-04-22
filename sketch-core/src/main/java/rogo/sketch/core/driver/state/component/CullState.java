package rogo.sketch.core.driver.state.component;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import rogo.sketch.core.api.RenderStateComponent;
import rogo.sketch.core.driver.state.CullFaceMode;
import rogo.sketch.core.driver.state.FrontFaceMode;
import rogo.sketch.core.util.KeyId;

public class CullState implements RenderStateComponent {
    public static final KeyId TYPE = KeyId.of("cull_face");
    
    private boolean enabled;
    private CullFaceMode face;
    private FrontFaceMode frontFace;

    public CullState() {
        this.enabled = true;
        this.face = CullFaceMode.BACK;
        this.frontFace = FrontFaceMode.CCW;
    }

    public CullState(boolean enabled, CullFaceMode face, FrontFaceMode frontFace) {
        this.enabled = enabled;
        this.face = face != null ? face : CullFaceMode.BACK;
        this.frontFace = frontFace != null ? frontFace : FrontFaceMode.CCW;
    }

    @Override
    public KeyId getIdentifier() {
        return TYPE;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CullState)) return false;
        CullState c = (CullState) o;
        return enabled == c.enabled && face == c.face && frontFace == c.frontFace;
    }

    @Override
    public int hashCode() {
        return Boolean.hashCode(enabled) ^ face.hashCode() ^ frontFace.hashCode();
    }

    public boolean enabled() {
        return enabled;
    }

    public CullFaceMode face() {
        return face;
    }

    public FrontFaceMode frontFace() {
        return frontFace;
    }

    @Override
    public void deserializeFromJson(JsonObject json, Gson gson, rogo.sketch.core.resource.GraphicsResourceManager resourceManager) {
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
    
    private static CullFaceMode parseCullFace(String face) {
        return switch (face.toUpperCase()) {
            case "FRONT" -> CullFaceMode.FRONT;
            case "BACK" -> CullFaceMode.BACK;
            case "FRONT_AND_BACK" -> CullFaceMode.FRONT_AND_BACK;
            default -> {
                System.err.println("Unknown cull face: " + face);
                yield CullFaceMode.BACK;
            }
        };
    }
    
    private static FrontFaceMode parseFrontFace(String frontFace) {
        return switch (frontFace.toUpperCase()) {
            case "CW" -> FrontFaceMode.CW;
            case "CCW" -> FrontFaceMode.CCW;
            default -> {
                System.err.println("Unknown front face: " + frontFace);
                yield FrontFaceMode.CCW;
            }
        };
    }
}


