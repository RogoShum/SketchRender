package rogo.sketch.core.debugger;

import java.util.Locale;

public enum DashboardPanelId {
    SETTINGS("settings"),
    METRICS("metrics"),
    DIAGNOSTICS("diagnostics");

    private final String id;

    DashboardPanelId(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static DashboardPanelId byId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (DashboardPanelId panelId : values()) {
            if (panelId.id.equals(normalized)) {
                return panelId;
            }
        }
        return null;
    }
}
