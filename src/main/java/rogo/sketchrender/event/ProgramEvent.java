package rogo.sketchrender.event;

import net.minecraftforge.eventbus.api.Event;
import rogo.sketchrender.api.ExtraUniform;
import rogo.sketchrender.shader.ShaderModifier;

public abstract class ProgramEvent extends Event {
    private final int programId;
    private final ExtraUniform extraUniform;

    public ProgramEvent(int programId, ExtraUniform extraUniform) {
        this.programId = programId;
        this.extraUniform = extraUniform;
    }

    public int getProgramId() {
        return programId;
    }

    public ExtraUniform getExtraUniform() {
        return extraUniform;
    }

    public static class Init extends ProgramEvent {

        public Init(int programId, ExtraUniform extraUniform) {
            super(programId, extraUniform);
        }
    }

    public static class Bind extends ProgramEvent {

        public Bind(int programId, ExtraUniform extraUniform) {
            super(programId, extraUniform);
        }
    }
}
