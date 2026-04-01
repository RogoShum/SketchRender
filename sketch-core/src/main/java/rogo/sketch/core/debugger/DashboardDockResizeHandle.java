package rogo.sketch.core.debugger;

import rogo.sketch.core.ui.geometry.UiRect;

public record DashboardDockResizeHandle(
        String id,
        DashboardDockSlotId slotId,
        DashboardDockResizeEdge edge,
        UiRect bounds,
        double minRatio,
        double maxRatio
) {
    public DashboardDockResizeHandle {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (slotId == null) {
            throw new IllegalArgumentException("slotId must not be null");
        }
        if (edge == null) {
            throw new IllegalArgumentException("edge must not be null");
        }
        if (bounds == null) {
            throw new IllegalArgumentException("bounds must not be null");
        }
        minRatio = Math.max(0.0D, minRatio);
        maxRatio = Math.max(minRatio, Math.min(1.0D, maxRatio));
    }
}
