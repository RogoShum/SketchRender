package rogo.sketch.core.driver.state.component;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import rogo.sketch.core.api.RenderStateComponent;
import rogo.sketch.core.driver.state.CompareOp;
import rogo.sketch.core.util.KeyId;

public class DepthTestState implements RenderStateComponent {
    public static final KeyId TYPE = KeyId.of("depth_test");

    private boolean enabled;
    private CompareOp compareOp;

    public DepthTestState() {
        this(true, CompareOp.LESS);
    }

    public DepthTestState(boolean enabled, int func) {
        this(enabled, fromLegacyFunc(func));
    }

    public DepthTestState(boolean enabled, CompareOp compareOp) {
        this.enabled = enabled;
        this.compareOp = compareOp != null ? compareOp : CompareOp.LESS;
    }

    @Override
    public KeyId getIdentifier() {
        return TYPE;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DepthTestState)) return false;
        DepthTestState d = (DepthTestState) o;
        return enabled == d.enabled && compareOp == d.compareOp;
    }

    @Override
    public int hashCode() {
        return Boolean.hashCode(enabled) ^ compareOp.hashCode();
    }

    public boolean enabled() {
        return enabled;
    }

    public CompareOp compareOp() {
        return compareOp;
    }

    @Override
    public void deserializeFromJson(JsonObject json, Gson gson, rogo.sketch.core.resource.GraphicsResourceManager resourceManager) {
        this.enabled = !json.has("enabled") || json.get("enabled").getAsBoolean();

        if (json.has("function")) {
            this.compareOp = CompareOp.parse(json.get("function").getAsString());
        }
    }

    @Override
    public RenderStateComponent createInstance() {
        return new DepthTestState();
    }

    private static CompareOp fromLegacyFunc(int func) {
        return switch (func) {
            case 0x0200 -> CompareOp.NEVER;
            case 0x0201 -> CompareOp.LESS;
            case 0x0202 -> CompareOp.EQUAL;
            case 0x0203 -> CompareOp.LEQUAL;
            case 0x0204 -> CompareOp.GREATER;
            case 0x0205 -> CompareOp.NOTEQUAL;
            case 0x0206 -> CompareOp.GEQUAL;
            case 0x0207 -> CompareOp.ALWAYS;
            default -> CompareOp.LESS;
        };
    }
}


