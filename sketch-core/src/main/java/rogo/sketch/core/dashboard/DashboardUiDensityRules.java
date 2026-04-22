package rogo.sketch.core.dashboard;

public final class DashboardUiDensityRules {
    private DashboardUiDensityRules() {
    }

    public static float defaultLeftRatio(int logicalWidth) {
        if (logicalWidth >= 1800) {
            return 0.37f;
        }
        if (logicalWidth >= 1450) {
            return 0.41f;
        }
        if (logicalWidth >= 1180) {
            return 0.45f;
        }
        return 0.49f;
    }

    public static int autoMetricColumns(int logicalWidth) {
        if (logicalWidth >= 1800) {
            return 3;
        }
        if (logicalWidth >= 1220) {
            return 2;
        }
        return 1;
    }
}
