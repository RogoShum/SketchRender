package rogo.sketch.core.pipeline.module.diagnostic;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Bounded in-memory diagnostic history for in-game inspection.
 */
public class DiagnosticsBuffer {
    private final int capacity;
    private final Deque<DiagnosticEntry> entries = new ArrayDeque<>();

    public DiagnosticsBuffer(int capacity) {
        this.capacity = Math.max(1, capacity);
    }

    public synchronized DiagnosticEntry append(
            DiagnosticLevel level,
            String moduleId,
            String message,
            Throwable throwable) {
        DiagnosticEntry entry = new DiagnosticEntry(
                Instant.now(),
                level,
                moduleId,
                Thread.currentThread().getName(),
                message,
                throwable != null ? throwable.getClass().getName() : null,
                throwable != null ? stackTraceToString(throwable) : null
        );
        while (entries.size() >= capacity) {
            entries.removeFirst();
        }
        entries.addLast(entry);
        return entry;
    }

    public synchronized List<DiagnosticEntry> snapshot() {
        return new ArrayList<>(entries);
    }

    public synchronized void clear() {
        entries.clear();
    }

    private String stackTraceToString(Throwable throwable) {
        java.io.StringWriter writer = new java.io.StringWriter();
        java.io.PrintWriter printWriter = new java.io.PrintWriter(writer);
        throwable.printStackTrace(printWriter);
        printWriter.flush();
        return writer.toString();
    }
}
