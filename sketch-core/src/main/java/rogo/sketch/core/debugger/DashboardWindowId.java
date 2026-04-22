package rogo.sketch.core.debugger;

import java.util.Locale;

public enum DashboardWindowId {
    SETTINGS("settings", "debug.dashboard.window.settings", DashboardPanelId.SETTINGS),
    SHADER_MACROS("shader_macros", "debug.dashboard.window.shader_macros", DashboardPanelId.SETTINGS),
    METRICS("metrics", "debug.dashboard.window.metrics", DashboardPanelId.METRICS),
    FRAME_CAPTURE("frame_capture", "debug.dashboard.window.frame_capture", DashboardPanelId.METRICS),
    MEMORY("memory", "debug.dashboard.window.memory", DashboardPanelId.METRICS),
    CONSOLE("console", "debug.dashboard.window.console", DashboardPanelId.DIAGNOSTICS);

    private final String id;
    private final String titleKey;
    private final DashboardPanelId defaultPanelId;

    DashboardWindowId(String id, String titleKey, DashboardPanelId defaultPanelId) {
        this.id = id;
        this.titleKey = titleKey;
        this.defaultPanelId = defaultPanelId;
    }

    public String id() {
        return id;
    }

    public String titleKey() {
        return titleKey;
    }

    public DashboardPanelId defaultPanelId() {
        return defaultPanelId;
    }

    public static DashboardWindowId byId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (DashboardWindowId windowId : values()) {
            if (windowId.id.equals(normalized) || windowId.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return windowId;
            }
        }
        return null;
    }
}
