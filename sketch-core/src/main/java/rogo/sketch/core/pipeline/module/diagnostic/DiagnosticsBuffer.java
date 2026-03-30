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
    private long nextAlertSequence = 1L;

    public DiagnosticsBuffer(int capacity) {
        this.capacity = Math.max(1, capacity);
    }

    public synchronized DiagnosticEntry append(
            DiagnosticLevel level,
            String moduleId,
            String message,
            Throwable throwable) {
        String throwableType = throwable != null ? throwable.getClass().getName() : null;
        String stackTrace = throwable != null ? stackTraceToString(throwable) : null;
        DiagnosticEntry previous = entries.peekLast();
        if (previous != null && canMerge(previous, level, moduleId, message, throwableType, stackTrace)) {
            entries.removeLast();
            DiagnosticEntry merged = new DiagnosticEntry(
                    previous.alertSequence(),
                    Instant.now(),
                    previous.level(),
                    previous.moduleId(),
                    previous.threadName(),
                    previous.message(),
                    previous.throwableType(),
                    previous.stackTrace(),
                    previous.repeatCount() + 1
            );
            entries.addLast(merged);
            return merged;
        }
        DiagnosticEntry entry = new DiagnosticEntry(
                nextAlertSequence++,
                Instant.now(),
                level,
                moduleId,
                Thread.currentThread().getName(),
                message,
                throwableType,
                stackTrace,
                1
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

    private boolean canMerge(DiagnosticEntry previous, DiagnosticLevel level, String moduleId, String message, String throwableType, String stackTrace) {
        return previous.level() == level
                && previous.moduleId().equals(moduleId)
                && previous.threadName().equals(Thread.currentThread().getName())
                && previous.message().equals(message)
                && java.util.Objects.equals(previous.throwableType(), throwableType)
                && java.util.Objects.equals(previous.stackTrace(), stackTrace);
    }

    private String stackTraceToString(Throwable throwable) {
        java.io.StringWriter writer = new java.io.StringWriter();
        java.io.PrintWriter printWriter = new java.io.PrintWriter(writer);
        throwable.printStackTrace(printWriter);
        printWriter.flush();
        return writer.toString();
    }
}
