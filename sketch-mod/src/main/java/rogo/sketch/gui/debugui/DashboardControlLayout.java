package rogo.sketch.gui.debugui;

import rogo.sketch.core.ui.geometry.UiRect;
import rogo.sketch.core.ui.control.ChoicePresentation;
import rogo.sketch.core.ui.control.ChoiceSpec;
import rogo.sketch.core.ui.control.ControlKind;
import rogo.sketch.core.ui.control.ControlSpec;

final class DashboardControlLayout {
    private DashboardControlLayout() {
    }

    static UiRect controlBounds(UiRect row, float scale) {
        return controlBounds(row, null, scale);
    }

    static UiRect controlBounds(UiRect row, ControlSpec spec, float scale) {
        int width = preferredWidth(row, spec, scale);
        return new UiRect(row.right() - width, row.y(), width, row.height());
    }

    static UiRect sliderTrackBounds(UiRect control, float scale) {
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

    static int sliderValueWidth(UiRect control, float scale) {
        int preferred = Math.round(54 * scale);
        int proportional = Math.round(control.width() * 0.26f);
        return Math.max(Math.max(36, Math.round(40 * scale)), Math.min(preferred, proportional));
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
            case NUMBER -> Math.max(Math.round(70 * scale), Math.min(Math.round(rowWidth * 0.24f), Math.round(88 * scale)));
            case SLIDER -> Math.max(Math.round(148 * scale), Math.min(Math.round(rowWidth * 0.68f), Math.round(304 * scale)));
            case CHOICE -> choiceWidth(rowWidth, spec.choiceSpec(), scale);
        };
    }

    private static int choiceWidth(int rowWidth, ChoiceSpec choiceSpec, float scale) {
        if (choiceSpec == null) {
            return Math.max(Math.round(88 * scale), Math.min(Math.round(rowWidth * 0.36f), Math.round(160 * scale)));
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


