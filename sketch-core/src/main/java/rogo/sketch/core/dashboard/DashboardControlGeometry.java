package rogo.sketch.core.dashboard;

import rogo.sketch.core.ui.control.ChoicePresentation;
import rogo.sketch.core.ui.control.ChoiceSpec;
import rogo.sketch.core.ui.control.ControlSpec;
import rogo.sketch.core.ui.geometry.UiRect;

public final class DashboardControlGeometry {
    private DashboardControlGeometry() {
    }

    public static UiRect controlBounds(UiRect row, ControlSpec spec, float scale) {
        int width = preferredWidth(row, spec, scale);
        return new UiRect(row.right() - width, row.y(), width, row.height());
    }

    public static UiRect sliderTrackBounds(UiRect control, float scale) {
        int inset = Math.max(4, Math.round(6 * scale));
        int gap = Math.max(5, Math.round(6 * scale));
        int valueWidth = sliderValueWidth(control, scale);
        int x = control.x() + inset;
        int right = control.right() - valueWidth - gap;
        int width = Math.max(Math.max(12, Math.round(20 * scale)), right - x);
        int height = Math.max(3, Math.round(4 * scale));
        int y = control.y() + Math.max(0, (control.height() - height) / 2) - 1;
        return new UiRect(x, y, width, height);
    }

    public static int sliderValueWidth(UiRect control, float scale) {
        int preferred = Math.round(46 * scale);
        int proportional = Math.round(control.width() * 0.24f);
        return Math.max(Math.max(26, Math.round(30 * scale)), Math.min(preferred, proportional));
    }

    public static UiRect choiceBounds(UiRect controlBounds, float scale) {
        int height = Math.max(16, Math.round(18 * scale));
        return centeredBox(controlBounds, controlBounds.width(), height);
    }

    public static UiRect choiceDropdownBounds(UiRect rowBounds, ControlSpec spec, float scale, int optionCount, UiRect screenBounds) {
        UiRect control = choiceBounds(controlBounds(rowBounds, spec, scale), scale);
        int rowHeight = choiceDropdownRowHeight(scale);
        int height = optionCount * rowHeight;
        int belowY = control.bottom() + 2;
        int maxBottom = screenBounds.bottom() - 6;
        if (belowY + height <= maxBottom || control.y() - 2 < height) {
            return new UiRect(control.x(), belowY, control.width(), height);
        }
        return new UiRect(control.x(), control.y() - 2 - height, control.width(), height);
    }

    public static int choiceDropdownRowHeight(float scale) {
        return Math.max(20, Math.round(20 * scale));
    }

    private static UiRect centeredBox(UiRect region, int width, int height) {
        int x = region.x() + Math.max(0, (region.width() - width) / 2);
        int y = region.y() + Math.max(0, (region.height() - height) / 2) - 1;
        return new UiRect(x, y, Math.min(width, region.width()), Math.min(height, region.height()));
    }

    private static int preferredWidth(UiRect row, ControlSpec spec, float scale) {
        int rowWidth = row.width();
        int minWidth = Math.max(44, Math.round(48 * scale));
        int width = Math.max(minWidth, Math.min(Math.round(rowWidth * 0.42f), Math.round(176 * scale)));
        if (spec == null) {
            return width;
        }
        return switch (spec.kind()) {
            case TOGGLE -> Math.max(Math.round(28 * scale), Math.min(Math.round(rowWidth * 0.12f), Math.round(42 * scale)));
            case NUMBER -> Math.max(Math.round(52 * scale), Math.min(Math.round(rowWidth * 0.22f), Math.round(78 * scale)));
            case SLIDER -> Math.max(Math.round(86 * scale), Math.min(Math.round(rowWidth * 0.50f), Math.round(220 * scale)));
            case CHOICE -> choiceWidth(rowWidth, spec.choiceSpec(), scale);
        };
    }

    private static int choiceWidth(int rowWidth, ChoiceSpec choiceSpec, float scale) {
        if (choiceSpec == null) {
            return Math.max(Math.round(92 * scale), Math.min(Math.round(rowWidth * 0.42f), Math.round(178 * scale)));
        }
        boolean segmented = choiceSpec.presentation() == ChoicePresentation.SEGMENTED
                || (choiceSpec.presentation() == ChoicePresentation.AUTO && choiceSpec.options().size() <= 3);
        if (segmented) {
            int optionCount = Math.max(1, choiceSpec.options().size());
            int perOption = Math.max(Math.round(34 * scale), Math.min(Math.round(rowWidth * 0.18f), Math.round(54 * scale)));
            int preferred = optionCount * perOption + Math.max(0, optionCount - 1) * 2;
            return Math.max(Math.round(88 * scale), Math.min(Math.round(rowWidth * 0.58f), preferred));
        }
        return Math.max(Math.round(92 * scale), Math.min(Math.round(rowWidth * 0.42f), Math.round(178 * scale)));
    }
}
