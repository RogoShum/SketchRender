package rogo.sketch.core.driver.state.component;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import rogo.sketch.core.api.RenderStateComponent;
import rogo.sketch.core.driver.state.CompareOp;
import rogo.sketch.core.driver.state.StencilOperation;
import rogo.sketch.core.util.KeyId;

public class StencilState implements RenderStateComponent {
    public static final KeyId TYPE = KeyId.of("stencil_test");

    private boolean enabled;
    private CompareOp func;
    private int ref;
    private int mask;
    private StencilOperation fail;
    private StencilOperation zfail;
    private StencilOperation zpass;

    public StencilState() {
        this.enabled = false;
        this.func = CompareOp.ALWAYS;
        this.ref = 0;
        this.mask = 0xFF;
        this.fail = StencilOperation.KEEP;
        this.zfail = StencilOperation.KEEP;
        this.zpass = StencilOperation.KEEP;
    }

    public StencilState(boolean enabled, CompareOp func, int ref, int mask,
                        StencilOperation fail, StencilOperation zfail, StencilOperation zpass) {
        this.enabled = enabled;
        this.func = func != null ? func : CompareOp.ALWAYS;
        this.ref = ref;
        this.mask = mask;
        this.fail = fail != null ? fail : StencilOperation.KEEP;
        this.zfail = zfail != null ? zfail : StencilOperation.KEEP;
        this.zpass = zpass != null ? zpass : StencilOperation.KEEP;
    }

    @Override
    public KeyId getIdentifier() {
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

    private static CompareOp parseStencilFunction(String function) {
        return switch (function.toUpperCase()) {
            case "NEVER" -> CompareOp.NEVER;
            case "LESS" -> CompareOp.LESS;
            case "EQUAL" -> CompareOp.EQUAL;
            case "LEQUAL" -> CompareOp.LEQUAL;
            case "GREATER" -> CompareOp.GREATER;
            case "NOTEQUAL" -> CompareOp.NOTEQUAL;
            case "GEQUAL" -> CompareOp.GEQUAL;
            case "ALWAYS" -> CompareOp.ALWAYS;
            default -> {
                System.err.println("Unknown stencil function: " + function);
                yield CompareOp.ALWAYS;
            }
        };
    }

    private static StencilOperation parseStencilOp(String op) {
        return switch (op.toUpperCase()) {
            case "KEEP" -> StencilOperation.KEEP;
            case "ZERO" -> StencilOperation.ZERO;
            case "REPLACE" -> StencilOperation.REPLACE;
            case "INCR" -> StencilOperation.INCR;
            case "DECR" -> StencilOperation.DECR;
            case "INVERT" -> StencilOperation.INVERT;
            default -> {
                System.err.println("Unknown stencil operation: " + op);
                yield StencilOperation.KEEP;
            }
        };
    }

    @Override
    public int hashCode() {
        return Boolean.hashCode(enabled)
                ^ func.hashCode()
                ^ ref
                ^ mask
                ^ fail.hashCode()
                ^ zfail.hashCode()
                ^ zpass.hashCode();
    }

    public boolean enabled() {
        return enabled;
    }

    public CompareOp func() {
        return func;
    }

    public int ref() {
        return ref;
    }

    public int mask() {
        return mask;
    }

    public StencilOperation fail() {
        return fail;
    }

    public StencilOperation zfail() {
        return zfail;
    }

    public StencilOperation zpass() {
        return zpass;
    }
}


