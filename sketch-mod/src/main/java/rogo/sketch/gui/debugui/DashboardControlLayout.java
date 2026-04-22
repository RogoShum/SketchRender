package rogo.sketch.gui.debugui;

import rogo.sketch.core.dashboard.DashboardControlGeometry;
import rogo.sketch.core.ui.control.ControlSpec;
import rogo.sketch.core.ui.geometry.UiRect;

final class DashboardControlLayout {
    private DashboardControlLayout() {
    }

    static UiRect controlBounds(UiRect row, float scale) {
        return controlBounds(row, null, scale);
    }

    static UiRect controlBounds(UiRect row, ControlSpec spec, float scale) {
        return DashboardControlGeometry.controlBounds(row, spec, scale);
    }

    static UiRect sliderTrackBounds(UiRect control, float scale) {
        return DashboardControlGeometry.sliderTrackBounds(control, scale);
    }

    static int sliderValueWidth(UiRect control, float scale) {
        return DashboardControlGeometry.sliderValueWidth(control, scale);
    }
}


