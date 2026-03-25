package rogo.sketch.core.pipeline.module.diagnostic;

import java.io.PrintStream;
import java.util.List;

/**
 * Core diagnostics entry point used by the pipeline, loaders, and modules.
 */
public final class SketchDiagnostics {
    private static final SketchDiagnostics INSTANCE = new SketchDiagnostics();
    private final DiagnosticsBuffer buffer = new DiagnosticsBuffer(512);

    private SketchDiagnostics() {
    }

    public static SketchDiagnostics get() {
        return INSTANCE;
    }

    public DiagnosticsBuffer buffer() {
        return buffer;
    }

    public List<DiagnosticEntry> snapshot() {
        return buffer.snapshot();
    }

    public DiagnosticEntry log(DiagnosticLevel level, String moduleId, String message) {
        return log(level, moduleId, message, null);
    }

    public DiagnosticEntry log(DiagnosticLevel level, String moduleId, String message, Throwable throwable) {
        DiagnosticEntry entry = buffer.append(level, moduleId, message, throwable);
        PrintStream stream = level == DiagnosticLevel.ERROR || level == DiagnosticLevel.WARN
                ? System.err
                : System.out;
        stream.println("[" + level + "][" + moduleId + "] " + message);
        if (throwable != null) {
            throwable.printStackTrace(stream);
        }
        return entry;
    }

    public DiagnosticEntry debug(String moduleId, String message) {
        return log(DiagnosticLevel.DEBUG, moduleId, message, null);
    }

    public DiagnosticEntry info(String moduleId, String message) {
        return log(DiagnosticLevel.INFO, moduleId, message, null);
    }

    public DiagnosticEntry warn(String moduleId, String message) {
        return log(DiagnosticLevel.WARN, moduleId, message, null);
    }

    public DiagnosticEntry warn(String moduleId, String message, Throwable throwable) {
        return log(DiagnosticLevel.WARN, moduleId, message, throwable);
    }

    public DiagnosticEntry error(String moduleId, String message, Throwable throwable) {
        return log(DiagnosticLevel.ERROR, moduleId, message, throwable);
    }
}
