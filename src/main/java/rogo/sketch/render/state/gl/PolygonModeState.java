package rogo.sketch.render.state.gl;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.lwjgl.opengl.GL11;
import rogo.sketch.api.RenderStateComponent;
import rogo.sketch.render.pipeline.RenderContext;
import rogo.sketch.util.KeyId;

public class PolygonModeState implements RenderStateComponent {
    public static final KeyId TYPE = KeyId.of("polygon_mode");

    private int face, mode;

    public PolygonModeState() {
        this.face = GL11.GL_FRONT_AND_BACK;
        this.mode = GL11.GL_FILL;
    }

    public PolygonModeState(int face, int mode) {
        this.face = face;
        this.mode = mode;
    }

    @Override
    public KeyId getIdentifier() {
        return TYPE;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof PolygonModeState)) return false;
        PolygonModeState o = (PolygonModeState) other;
        return face == o.face && mode == o.mode;
    }

    @Override
    public void apply(RenderContext context) {
        //GraphicsDriver.getCurrentAPI().polygonMode(face, mode);
    }

    @Override
    public void deserializeFromJson(JsonObject json, Gson gson) {
        if (json.has("face")) {
            this.face = parseFace(json.get("face").getAsString());
        }

        if (json.has("mode")) {
            this.mode = parseMode(json.get("mode").getAsString());
        }
    }

    @Override
    public RenderStateComponent createInstance() {
        return new PolygonModeState();
    }

    private static int parseFace(String face) {
        return switch (face.toUpperCase()) {
            case "FRONT" -> GL11.GL_FRONT;
            case "BACK" -> GL11.GL_BACK;
            case "FRONT_AND_BACK" -> GL11.GL_FRONT_AND_BACK;
            default -> {
                System.err.println("Unknown polygon face: " + face);
                yield GL11.GL_FRONT_AND_BACK;
            }
        };
    }

    private static int parseMode(String mode) {
        return switch (mode.toUpperCase()) {
            case "POINT" -> GL11.GL_POINT;
            case "LINE" -> GL11.GL_LINE;
            case "FILL" -> GL11.GL_FILL;
            default -> {
                System.err.println("Unknown polygon mode: " + mode);
                yield GL11.GL_FILL;
            }
        };
    }

    @Override
    public int hashCode() {
        return face ^ mode;
    }
}