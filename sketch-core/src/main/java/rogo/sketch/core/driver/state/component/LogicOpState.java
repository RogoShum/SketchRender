package rogo.sketch.core.driver.state.component;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import rogo.sketch.core.api.RenderStateComponent;
import rogo.sketch.core.driver.state.LogicOp;
import rogo.sketch.core.util.KeyId;

import java.util.Objects;

/*
 * Note: "Color Logic Op" is a legacy feature and might not be supported in core
 * profile contexts,
 * but LWJGL/Minecraft env usually supports it (or compatibility profile).
 * The enum is GL_COLOR_LOGIC_OP.
 */
public class LogicOpState implements RenderStateComponent {
    public static final KeyId TYPE = KeyId.of("logic_op_state");

    private boolean enabled;
    private LogicOp opcode;

    public LogicOpState() {
        this.enabled = false;
        this.opcode = LogicOp.COPY;
    }

    public LogicOpState(boolean enabled, LogicOp opcode) {
        this.enabled = enabled;
        this.opcode = opcode != null ? opcode : LogicOp.COPY;
    }

    @Override
    public KeyId getIdentifier() {
        return TYPE;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof LogicOpState))
            return false;
        LogicOpState that = (LogicOpState) o;
        return enabled == that.enabled && opcode == that.opcode;
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, opcode);
    }

    @Override
    public void deserializeFromJson(JsonObject json, Gson gson) {
        if (json.has("enabled")) {
            this.enabled = json.get("enabled").getAsBoolean();
        }

        if (json.has("opcode")) {
            this.opcode = parseLogicOp(json.get("opcode").getAsString());
        }
    }

    private static LogicOp parseLogicOp(String opcode) {
        return switch (opcode.toUpperCase()) {
            case "CLEAR" -> LogicOp.CLEAR;
            case "SET" -> LogicOp.SET;
            case "COPY" -> LogicOp.COPY;
            case "COPY_INVERTED" -> LogicOp.COPY_INVERTED;
            case "NOOP" -> LogicOp.NOOP;
            case "INVERT" -> LogicOp.INVERT;
            case "AND" -> LogicOp.AND;
            case "NAND" -> LogicOp.NAND;
            case "OR" -> LogicOp.OR;
            case "NOR" -> LogicOp.NOR;
            case "XOR" -> LogicOp.XOR;
            case "EQUIV" -> LogicOp.EQUIV;
            case "AND_REVERSE" -> LogicOp.AND_REVERSE;
            case "AND_INVERTED" -> LogicOp.AND_INVERTED;
            case "OR_REVERSE" -> LogicOp.OR_REVERSE;
            case "OR_INVERTED" -> LogicOp.OR_INVERTED;
            default -> {
                System.err.println("Unknown logic op: " + opcode);
                yield LogicOp.COPY;
            }
        };
    }

    @Override
    public RenderStateComponent createInstance() {
        return new LogicOpState();
    }

    public boolean enabled() {
        return enabled;
    }

    public LogicOp opcode() {
        return opcode;
    }
}


