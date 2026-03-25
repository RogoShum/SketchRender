package rogo.sketch.core.pipeline.module.diagnostic;

import java.time.Instant;

public record DiagnosticEntry(
        Instant timestamp,
        DiagnosticLevel level,
        String moduleId,
        String threadName,
        String message,
        String throwableType,
        String stackTrace
) {
}
