package rogo.sketch.render.state.gl;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.lwjgl.opengl.GL11;
import rogo.sketch.api.RenderStateComponent;
import rogo.sketch.render.pipeline.RenderContext;
import rogo.sketch.util.Identifier;

import java.util.Objects;

public class PolygonOffsetState implements RenderStateComponent {
    public static final Identifier TYPE = Identifier.of("polygon_offset_state");

    private boolean enabled;
    private float factor;
    private float units;

    public PolygonOffsetState() {
        this.enabled = false;
        this.factor = 0.0f;
        this.units = 0.0f;
    }

    public PolygonOffsetState(boolean enabled, float factor, float units) {
        this.enabled = enabled;
        this.factor = factor;
        this.units = units;
    }

    @Override
    public Identifier getIdentifier() {
        return TYPE;
    }

    @Override
    public void apply(RenderContext context) {
        if (enabled) {
            GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
            GL11.glPolygonOffset(factor, units);
        } else {
            GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof PolygonOffsetState))
            return false;
        PolygonOffsetState that = (PolygonOffsetState) o;
        return enabled == that.enabled && Float.compare(that.factor, factor) == 0
                && Float.compare(that.units, units) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, factor, units);
    }

    @Override
    public void deserializeFromJson(JsonObject json, Gson gson) {
        if (json.has("enabled"))
            this.enabled = json.get("enabled").getAsBoolean();
        if (json.has("factor"))
            this.factor = json.get("factor").getAsFloat();
        if (json.has("units"))
            this.units = json.get("units").getAsFloat();
    }

    @Override
    public RenderStateComponent createInstance() {
        return new PolygonOffsetState();
    }
}
