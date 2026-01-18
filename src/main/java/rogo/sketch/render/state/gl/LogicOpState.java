package rogo.sketch.render.state.gl;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.lwjgl.opengl.GL11;
import rogo.sketch.api.RenderStateComponent;
import rogo.sketch.render.pipeline.RenderContext;
import rogo.sketch.util.Identifier;

import java.util.Objects;

public class LogicOpState implements RenderStateComponent {
    public static final Identifier TYPE = Identifier.of("logic_op_state");

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
    public Identifier getIdentifier() {
        return TYPE;
    }

    /*
     * Note: "Color Logic Op" is a legacy feature and might not be supported in core
     * profile contexts,
     * but LWJGL/Minecraft env usually supports it (or compatibility profile).
     * The enum is GL_COLOR_LOGIC_OP.
     */
    @Override
    public void apply(RenderContext context) {
        if (enabled) {
            GL11.glEnable(GL11.GL_COLOR_LOGIC_OP);
            GL11.glLogicOp(opcode);
        } else {
            GL11.glDisable(GL11.GL_COLOR_LOGIC_OP);
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
        if (json.has("enabled"))
            this.enabled = json.get("enabled").getAsBoolean();
        if (json.has("opcode"))
            this.opcode = json.get("opcode").getAsInt();
    }

    @Override
    public RenderStateComponent createInstance() {
        return new LogicOpState();
    }
}
