package rogo.sketch.core.driver.state.component;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import rogo.sketch.core.api.RenderStateComponent;
import rogo.sketch.core.driver.state.BlendFactor;
import rogo.sketch.core.driver.state.BlendOp;
import rogo.sketch.core.util.KeyId;

public class BlendState implements RenderStateComponent {
    public static final KeyId TYPE = KeyId.of("blend_state");

    private boolean enabled;
    private BlendFactor colorSrcFactor;
    private BlendFactor colorDstFactor;
    private BlendFactor alphaSrcFactor;
    private BlendFactor alphaDstFactor;
    private BlendOp colorOp;
    private BlendOp alphaOp;

    public BlendState() {
        this(false, BlendFactor.SRC_ALPHA, BlendFactor.ONE_MINUS_SRC_ALPHA);
    }

    public BlendState(boolean enabled, int src, int dst) {
        this(enabled, fromLegacyFactor(src), fromLegacyFactor(dst));
    }

    public BlendState(boolean enabled, BlendFactor src, BlendFactor dst) {
        this(enabled, src, dst, src, dst, BlendOp.ADD, BlendOp.ADD);
    }

    public BlendState(
            boolean enabled,
            BlendFactor colorSrcFactor,
            BlendFactor colorDstFactor,
            BlendFactor alphaSrcFactor,
            BlendFactor alphaDstFactor,
            BlendOp colorOp,
            BlendOp alphaOp) {
        this.enabled = enabled;
        this.colorSrcFactor = colorSrcFactor != null ? colorSrcFactor : BlendFactor.SRC_ALPHA;
        this.colorDstFactor = colorDstFactor != null ? colorDstFactor : BlendFactor.ONE_MINUS_SRC_ALPHA;
        this.alphaSrcFactor = alphaSrcFactor != null ? alphaSrcFactor : this.colorSrcFactor;
        this.alphaDstFactor = alphaDstFactor != null ? alphaDstFactor : this.colorDstFactor;
        this.colorOp = colorOp != null ? colorOp : BlendOp.ADD;
        this.alphaOp = alphaOp != null ? alphaOp : this.colorOp;
    }

    @Override
    public KeyId getIdentifier() {
        return TYPE;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof BlendState)) return false;
        BlendState b = (BlendState) o;
        return enabled == b.enabled
                && colorSrcFactor == b.colorSrcFactor
                && colorDstFactor == b.colorDstFactor
                && alphaSrcFactor == b.alphaSrcFactor
                && alphaDstFactor == b.alphaDstFactor
                && colorOp == b.colorOp
                && alphaOp == b.alphaOp;
    }

    @Override
    public int hashCode() {
        int hash = Boolean.hashCode(enabled);
        hash = 31 * hash + colorSrcFactor.hashCode();
        hash = 31 * hash + colorDstFactor.hashCode();
        hash = 31 * hash + alphaSrcFactor.hashCode();
        hash = 31 * hash + alphaDstFactor.hashCode();
        hash = 31 * hash + colorOp.hashCode();
        hash = 31 * hash + alphaOp.hashCode();
        return hash;
    }

    public boolean enabled() {
        return enabled;
    }

    public BlendFactor colorSrcFactor() {
        return colorSrcFactor;
    }

    public BlendFactor colorDstFactor() {
        return colorDstFactor;
    }

    public BlendFactor alphaSrcFactor() {
        return alphaSrcFactor;
    }

    public BlendFactor alphaDstFactor() {
        return alphaDstFactor;
    }

    public BlendOp colorOp() {
        return colorOp;
    }

    public BlendOp alphaOp() {
        return alphaOp;
    }

    @Override
    public void deserializeFromJson(JsonObject json, Gson gson, rogo.sketch.core.resource.GraphicsResourceManager resourceManager) {
        this.enabled = !json.has("enabled") || json.get("enabled").getAsBoolean();

        if (json.has("srcFactor")) {
            BlendFactor factor = BlendFactor.parse(json.get("srcFactor").getAsString());
            this.colorSrcFactor = factor;
            this.alphaSrcFactor = factor;
        }

        if (json.has("dstFactor")) {
            BlendFactor factor = BlendFactor.parse(json.get("dstFactor").getAsString());
            this.colorDstFactor = factor;
            this.alphaDstFactor = factor;
        }

        if (json.has("colorSrcFactor")) {
            this.colorSrcFactor = BlendFactor.parse(json.get("colorSrcFactor").getAsString());
        }

        if (json.has("colorDstFactor")) {
            this.colorDstFactor = BlendFactor.parse(json.get("colorDstFactor").getAsString());
        }

        if (json.has("alphaSrcFactor")) {
            this.alphaSrcFactor = BlendFactor.parse(json.get("alphaSrcFactor").getAsString());
        }

        if (json.has("alphaDstFactor")) {
            this.alphaDstFactor = BlendFactor.parse(json.get("alphaDstFactor").getAsString());
        }

        if (json.has("op")) {
            BlendOp op = BlendOp.parse(json.get("op").getAsString());
            this.colorOp = op;
            this.alphaOp = op;
        }

        if (json.has("colorOp")) {
            this.colorOp = BlendOp.parse(json.get("colorOp").getAsString());
        }

        if (json.has("alphaOp")) {
            this.alphaOp = BlendOp.parse(json.get("alphaOp").getAsString());
        }
    }

    @Override
    public RenderStateComponent createInstance() {
        return new BlendState();
    }

    private static BlendFactor fromLegacyFactor(int factor) {
        return switch (factor) {
            case 0 -> BlendFactor.ZERO;
            case 1 -> BlendFactor.ONE;
            case 0x0300 -> BlendFactor.SRC_COLOR;
            case 0x0301 -> BlendFactor.ONE_MINUS_SRC_COLOR;
            case 0x0306 -> BlendFactor.DST_COLOR;
            case 0x0307 -> BlendFactor.ONE_MINUS_DST_COLOR;
            case 0x0302 -> BlendFactor.SRC_ALPHA;
            case 0x0303 -> BlendFactor.ONE_MINUS_SRC_ALPHA;
            case 0x0304 -> BlendFactor.DST_ALPHA;
            case 0x0305 -> BlendFactor.ONE_MINUS_DST_ALPHA;
            case 0x0308 -> BlendFactor.SRC_ALPHA_SATURATE;
            default -> BlendFactor.SRC_ALPHA;
        };
    }
}


