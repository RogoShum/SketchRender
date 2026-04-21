package rogo.sketch.core.pipeline.kernel;

import rogo.sketch.core.pipeline.kernel.ThreadDomain;

import java.util.List;
import java.util.Objects;

/**
 * Declarative module pass definition for the V4C resource graph seam.
 */
public final class ModulePassDefinition {
    @FunctionalInterface
    public interface ModulePassExecutor {
        void execute(PassExecutionContext context);
    }

    private final String moduleId;
    private final String passId;
    private final LifecyclePhase phase;
    private final ThreadDomain threadDomain;
    private final List<FrameResourceHandle<?>> reads;
    private final List<FrameResourceHandle<?>> writes;
    private final ModulePassExecutor executor;

    public ModulePassDefinition(
            String moduleId,
            String passId,
            LifecyclePhase phase,
            ThreadDomain threadDomain,
            List<FrameResourceHandle<?>> reads,
            List<FrameResourceHandle<?>> writes,
            ModulePassExecutor executor) {
        this.moduleId = Objects.requireNonNull(moduleId, "moduleId");
        this.passId = Objects.requireNonNull(passId, "passId");
        this.phase = Objects.requireNonNull(phase, "phase");
        this.threadDomain = Objects.requireNonNull(threadDomain, "threadDomain");
        this.reads = reads != null ? List.copyOf(reads) : List.of();
        this.writes = writes != null ? List.copyOf(writes) : List.of();
        this.executor = executor != null ? executor : ignored -> {};
    }

    public String moduleId() {
        return moduleId;
    }

    public String passId() {
        return passId;
    }

    public String qualifiedPassId() {
        return moduleId + ":" + passId;
    }

    public LifecyclePhase phase() {
        return phase;
    }

    public ThreadDomain threadDomain() {
        return threadDomain;
    }

    public List<FrameResourceHandle<?>> reads() {
        return reads;
    }

    public List<FrameResourceHandle<?>> writes() {
        return writes;
    }

    public ModulePassExecutor executor() {
        return executor;
    }
}
