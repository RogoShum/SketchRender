package rogo.sketch.core.debugger;

import rogo.sketch.core.pipeline.module.diagnostic.DiagnosticLevel;

public record DashboardDiagnosticLine(
        long alertSequence,
        String timeText,
        DiagnosticLevel level,
        String moduleId,
        String threadName,
        String message,
        String throwableType,
        String stackPreview,
        String stackTrace,
        int repeatCount
) {
    public String fullText() {
        StringBuilder builder = new StringBuilder();
        builder.append('[').append(timeText).append("]")
                .append('[').append(level).append("]")
                .append('[').append(moduleId).append("] ");
        if (threadName != null && !threadName.isBlank()) {
            builder.append('[').append(threadName).append("] ");
        }
        builder.append(message);
        if (repeatCount > 1) {
            builder.append(" (x").append(repeatCount).append(')');
        }
        if (throwableType != null && !throwableType.isBlank()) {
            builder.append('\n').append(throwableType);
        }
        if (stackTrace != null && !stackTrace.isBlank()) {
            builder.append('\n').append(stackTrace);
        } else if (stackPreview != null && !stackPreview.isBlank()) {
            builder.append('\n').append(stackPreview);
        }
        return builder.toString();
    }
}
