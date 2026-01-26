package rogo.sketch.core.state.gl;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.lwjgl.opengl.GL11;
import rogo.sketch.core.api.RenderStateComponent;
import rogo.sketch.core.driver.GraphicsDriver;
import rogo.sketch.core.pipeline.RenderContext;
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
    private int opcode;

    public LogicOpState() {
        this.enabled = false;
        this.opcode = GL11.GL_COPY;
    }

    public LogicOpState(boolean enabled, int opcode) {
        this.enabled = enabled;
        this.opcode = opcode;
    }

    @Override
    public KeyId getIdentifier() {
        return TYPE;
    }

    @Override
    public void apply(RenderContext context) {
        if (enabled) {
            GraphicsDriver.getCurrentAPI().enableLogicOp();
            GraphicsDriver.getCurrentAPI().logicOp(opcode);
        } else {
            GraphicsDriver.getCurrentAPI().disableLogicOp();
        }
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

    private static int parseLogicOp(String opcode) {
        return switch (opcode.toUpperCase()) {
            case "CLEAR" -> GL11.GL_CLEAR;
            case "SET" -> GL11.GL_SET;
            case "COPY" -> GL11.GL_COPY;
            case "COPY_INVERTED" -> GL11.GL_COPY_INVERTED;
            case "NOOP" -> GL11.GL_NOOP;
            case "INVERT" -> GL11.GL_INVERT;
            case "AND" -> GL11.GL_AND;
            case "NAND" -> GL11.GL_NAND;
            case "OR" -> GL11.GL_OR;
            case "NOR" -> GL11.GL_NOR;
            case "XOR" -> GL11.GL_XOR;
            case "EQUIV" -> GL11.GL_EQUIV;
            case "AND_REVERSE" -> GL11.GL_AND_REVERSE;
            case "AND_INVERTED" -> GL11.GL_AND_INVERTED;
            case "OR_REVERSE" -> GL11.GL_OR_REVERSE;
            case "OR_INVERTED" -> GL11.GL_OR_INVERTED;
            default -> {
                System.err.println("Unknown logic op: " + opcode);
                yield GL11.GL_COPY;
            }
        };
    }

    @Override
    public RenderStateComponent createInstance() {
        return new LogicOpState();
    }
}