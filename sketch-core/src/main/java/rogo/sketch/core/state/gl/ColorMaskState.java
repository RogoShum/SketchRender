package rogo.sketch.core.state.gl;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import rogo.sketch.core.api.RenderStateComponent;
import rogo.sketch.core.driver.GraphicsDriver;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.util.KeyId;

import java.util.Objects;

public class ColorMaskState implements RenderStateComponent {
    public static final KeyId TYPE = KeyId.of("color_mask_state");

    private boolean red;
    private boolean green;
    private boolean blue;
    private boolean alpha;

    public ColorMaskState() {
        this.red = true;
        this.green = true;
        this.blue = true;
        this.alpha = true;
    }

    public ColorMaskState(boolean red, boolean green, boolean blue, boolean alpha) {
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.alpha = alpha;
    }

    @Override
    public KeyId getIdentifier() {
        return TYPE;
    }

    @Override
    public void apply(RenderContext context) {
        GraphicsDriver.getCurrentAPI().colorMask(red, green, blue, alpha);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof ColorMaskState))
            return false;
        ColorMaskState that = (ColorMaskState) o;
        return red == that.red && green == that.green && blue == that.blue && alpha == that.alpha;
    }

    @Override
    public int hashCode() {
        return Objects.hash(red, green, blue, alpha);
    }

    @Override
    public void deserializeFromJson(JsonObject json, Gson gson) {
        if (json.has("red"))
            this.red = json.get("red").getAsBoolean();
        if (json.has("green"))
            this.green = json.get("green").getAsBoolean();
        if (json.has("blue"))
            this.blue = json.get("blue").getAsBoolean();
        if (json.has("alpha"))
            this.alpha = json.get("alpha").getAsBoolean();
    }

    @Override
    public RenderStateComponent createInstance() {
        return new ColorMaskState();
    }
}
