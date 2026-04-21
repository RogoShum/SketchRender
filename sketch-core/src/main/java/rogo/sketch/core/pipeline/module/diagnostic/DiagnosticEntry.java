package rogo.sketch.core.pipeline.module.diagnostic;

import java.time.Instant;

public record DiagnosticEntry(
        long alertSequence,
        Instant timestamp,
        DiagnosticLevel level,
        String moduleId,
        String threadName,
        String message,
        String throwableType,
        String stackPreview,
        String stackTrace,
        int repeatCount
) {
}
