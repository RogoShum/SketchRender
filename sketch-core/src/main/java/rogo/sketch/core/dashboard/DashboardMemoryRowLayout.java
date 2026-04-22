package rogo.sketch.core.dashboard;

import rogo.sketch.core.ui.text.UiMeasuredTextBlock;

public record DashboardMemoryRowLayout(
        int padding,
        int blockGap,
        int barGap,
        int barHeight,
        int titleY,
        int detailY,
        int usageLabelY,
        int usageBarY,
        int peakLabelY,
        int peakBarY,
        int rowHeight,
        UiMeasuredTextBlock titleLayout,
        UiMeasuredTextBlock reservedLayout,
        UiMeasuredTextBlock detailLayout,
        UiMeasuredTextBlock tailLayout
) {
}
