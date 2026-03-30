package rogo.sketch.core.debugger;

import rogo.sketch.core.pipeline.module.diagnostic.DiagnosticLevel;

public record DashboardDiagnosticLine(
        String timeText,
        DiagnosticLevel level,
        String moduleId,
        String message,
        int repeatCount
) {
}
