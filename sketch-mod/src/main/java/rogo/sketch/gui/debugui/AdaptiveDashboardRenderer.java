package rogo.sketch.gui.debugui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import rogo.sketch.core.debugger.DashboardController;
import rogo.sketch.core.debugger.DiagnosticsPanelMode;
import rogo.sketch.core.debugger.ui.UiNode;
import rogo.sketch.core.debugger.ui.UiNodeType;
import rogo.sketch.core.debugger.ui.UiPass;
import rogo.sketch.core.debugger.ui.UiRect;
import rogo.sketch.core.debugger.ui.UiScene;
import rogo.sketch.core.ui.control.ChoiceOptionSpec;
import rogo.sketch.core.ui.control.ChoicePresentation;
import rogo.sketch.core.ui.control.ChoiceSpec;
import rogo.sketch.core.ui.control.ControlKind;
import rogo.sketch.core.ui.control.ControlSpec;
import rogo.sketch.core.ui.control.NumericSpec;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class AdaptiveDashboardRenderer {
    private static final int TEXT = 0xFFE4E7EB;
    private static final int SUBTLE = 0xFF8B99AB;
    private static final int MUTED = 0xFF64748B;
    private static final int BORDER = 0xFF324150;
    private static final int DIVIDER = 0x453C4D62;
    private static final int ROW = 0x5A18222E;
    private static final int ROW_ALT = 0x42111A25;
    private static final int HOVER = 0x281E2A38;
    private static final int ACTIVE = 0xFF34D399;
    private static final int ACTIVE_DIM = 0xFF10B981;
    private static final int WARNING = 0xFFF59E0B;
    private static final int ERROR = 0xFFF87171;
    private static final String ENABLE_PATH_PREFIX = "dashboard.enable-path|";
    private final Minecraft minecraft = Minecraft.getInstance();

    public void render(UiScene scene, DashboardController controller, UiCanvas canvas, int mouseX, int mouseY) {
        UiNode hovered = findTopNode(scene, mouseX, mouseY);
        UiNode openDropdown = null;

        for (UiPass pass : UiPass.values()) {
            for (UiNode node : scene.nodes()) {
                if (node.pass() != pass) {
                    continue;
                }
                if (node.clipRect() != null) {
                    canvas.pushClip(node.clipRect());
                }
                boolean isHovered = nodeContains(node, mouseX, mouseY);
                renderNode(node, controller, canvas, isHovered);
                if (node.type() == UiNodeType.TREE_CONTROL && controlSpec(node) != null && controlSpec(node).kind() == ControlKind.CHOICE
                        && node.props().get("controlId") != null
                        && node.props().get("controlId").equals(controller.openChoiceControlId())) {
                    openDropdown = node;
                }
                if (node.clipRect() != null) {
                    canvas.popClip();
                }
            }
        }

        boolean hoveringDropdown = false;
        if (openDropdown != null) {
            hoveringDropdown = dropdownBounds(openDropdown).contains(mouseX, mouseY);
            renderChoiceDropdown(openDropdown, canvas, mouseX, mouseY);
        }
        if (hovered != null && !hoveringDropdown) {
            renderTooltip(hovered, canvas, mouseX, mouseY);
        }
    }

    public UiNode findTopNode(UiScene scene, double mouseX, double mouseY) {
        List<UiNode> nodes = new ArrayList<>(scene.nodes());
        nodes.sort(Comparator.comparingInt(node -> node.pass().ordinal()));
        for (int i = nodes.size() - 1; i >= 0; i--) {
            UiNode node = nodes.get(i);
            if (!isInteractive(node)) {
                continue;
            }
            if (nodeContains(node, mouseX, mouseY)) {
                return node;
            }
        }
        return null;
    }

    public List<ChoiceHitBox> choiceHitBoxes(UiNode node, DashboardController controller) {
        ControlSpec controlSpec = controlSpec(node);
        if (controlSpec == null || controlSpec.kind() != ControlKind.CHOICE || controlSpec.choiceSpec() == null) {
            return List.of();
        }
        ChoiceSpec choiceSpec = controlSpec.choiceSpec();
        List<ChoiceOptionSpec> options = choiceSpec.options();
        UiRect controlBounds = controlBounds(node.bounds(), scale(node));
        boolean segmented = choiceSpec.presentation() == ChoicePresentation.SEGMENTED
                || (choiceSpec.presentation() == ChoicePresentation.AUTO && options.size() <= 3);
        List<ChoiceHitBox> hits = new ArrayList<>();
        if (segmented) {
            int optionWidth = Math.max(1, controlBounds.width() / Math.max(1, options.size()));
            for (int i = 0; i < options.size(); i++) {
                ChoiceOptionSpec option = options.get(i);
                int x = controlBounds.x() + i * optionWidth;
                int width = i == options.size() - 1 ? controlBounds.right() - x : optionWidth;
                hits.add(new ChoiceHitBox(option.value(), new UiRect(x, controlBounds.y(), width, controlBounds.height())));
            }
            return hits;
        }
        if (!node.props().get("controlId").equals(controller.openChoiceControlId())) {
            return List.of();
        }
        UiRect dropdown = dropdownBounds(node);
        int y = dropdown.y();
        int rowHeight = Math.max(20, Math.round(20 * scale(node)));
        for (ChoiceOptionSpec option : options) {
            hits.add(new ChoiceHitBox(option.value(), new UiRect(dropdown.x(), y, dropdown.width(), rowHeight)));
            y += rowHeight;
        }
        return hits;
    }

    private void renderNode(UiNode node, DashboardController controller, UiCanvas canvas, boolean hovered) {
        switch (node.type()) {
            case PANEL -> renderPanel(node, canvas, hovered);
            case HEADER -> renderHeader(node, canvas);
            case TAB_BUTTON -> renderTab(node, canvas, hovered);
            case TREE_GROUP -> renderGroup(node, canvas, hovered);
            case TREE_CONTROL -> renderTreeControl(node, canvas, hovered);
            case METRIC_CARD -> renderMetricCard(node, canvas, hovered);
            case BAR_CHART -> renderChart(node, canvas);
            case MACRO_SECTION_HEADER -> renderMacroSectionHeader(node, canvas, hovered);
            case MACRO_CONSTANT_ROW -> renderMacroConstant(node, canvas, hovered);
            case METRICS_LAYOUT_TOGGLE -> renderMetricsLayoutToggle(node, canvas, hovered);
            case DIAGNOSTIC_HEADER -> renderDiagnosticsHeader(node, canvas);
            case DIAGNOSTIC_FILTER -> renderDiagnosticsFilter(node, canvas, hovered);
            case DIAGNOSTIC_STATE -> renderDiagnosticsState(node, controller, canvas, hovered);
            case LOG_LINE -> renderLogLine(node, canvas, hovered);
            default -> {
            }
        }
    }

    private void renderPanel(UiNode node, UiCanvas canvas, boolean hovered) {
        int fill = intProp(node, "fill", 0);
        int border = intProp(node, "border", 0);
        if (fill != 0) {
            canvas.fillRect(node.bounds(), fill);
        }
        if (border != 0) {
            canvas.borderRect(node.bounds(), border);
        }
        String role = strProp(node, "role");
        if ("panel".equals(role)) {
            canvas.fillRect(new UiRect(node.bounds().x(), node.bounds().y(), node.bounds().width(), 1), 0x553A5268);
        } else if ("scrollbar-track".equals(role)) {
            canvas.fillRect(node.bounds(), hovered ? 0x3E314155 : fill);
        } else if ("scrollbar-track-x".equals(role)) {
            canvas.fillRect(node.bounds(), hovered ? 0x3E314155 : fill);
        } else if ("scrollbar-thumb".equals(role)) {
            canvas.fillRect(node.bounds(), hovered ? 0xFF8EA1B8 : fill);
            if (border != 0) {
                canvas.borderRect(node.bounds(), border);
            }
        } else if ("scrollbar-thumb-x".equals(role)) {
            canvas.fillRect(node.bounds(), hovered ? 0xFF8EA1B8 : fill);
            if (border != 0) {
                canvas.borderRect(node.bounds(), border);
            }
        }
    }

    private void renderHeader(UiNode node, UiCanvas canvas) {
        renderPanel(node, canvas, false);
        int pad = Math.max(8, Math.round(10 * scale(node)));
        int titleY = node.bounds().y() + Math.max(7, Math.round(9 * scale(node)));
        canvas.drawText(textOf(strProp(node, "title")), node.bounds().x() + pad, titleY, TEXT);
    }

    private void renderTab(UiNode node, UiCanvas canvas, boolean hovered) {
        boolean active = boolProp(node, "active", false);
        int fill = active ? 0xAA202C39 : (hovered ? 0x8A18232F : 0x70121C27);
        canvas.fillRect(node.bounds(), fill);
        canvas.borderRect(node.bounds(), active ? 0xB94C627A : 0x7A324150);
        canvas.fillRect(new UiRect(node.bounds().x(), node.bounds().bottom() - 2, node.bounds().width(), 2), active ? ACTIVE : 0x55324150);
        canvas.drawCenteredText(textOf(strProp(node, "title")), node.bounds().x() + node.bounds().width() / 2,
                node.bounds().y() + Math.max(5, Math.round(7 * scale(node))), active ? 0xFFFFFFFF : SUBTLE);
    }

    private void renderGroup(UiNode node, UiCanvas canvas, boolean hovered) {
        if (hovered) {
            canvas.fillRect(node.bounds(), HOVER);
        }
        boolean expanded = boolProp(node, "expanded", false);
        int y = node.bounds().y() + Math.max(3, Math.round(5 * scale(node)));
        canvas.drawText(Component.literal(expanded ? "v" : ">"), node.bounds().x() + 2, y, MUTED);
        canvas.drawText(textOf(strProp(node, "title")), node.bounds().x() + Math.max(12, Math.round(14 * scale(node))), y, 0xFFF3F6FA);
        drawDivider(canvas, node.bounds(), DIVIDER);
    }

    private void renderTreeControl(UiNode node, UiCanvas canvas, boolean hovered) {
        if (hovered) {
            canvas.fillRect(node.bounds(), HOVER);
        }
        boolean enabled = boolProp(node, "enabled", true);
        boolean active = boolProp(node, "active", true);
        float scale = scale(node);
        int titleColor = enabled && active ? TEXT : MUTED;
        int pad = Math.max(4, Math.round(6 * scale));
        canvas.drawText(textOf(strProp(node, "title")), node.bounds().x() + pad, node.bounds().y() + Math.max(3, Math.round(5 * scale)), titleColor);
        String summary = strProp(node, "summary");
        if (!summary.isEmpty()) {
            canvas.drawText(textOf(summary), node.bounds().x() + pad, node.bounds().y() + Math.max(14, Math.round(17 * scale)), MUTED);
        }

        ControlSpec controlSpec = controlSpec(node);
        if (controlSpec == null) {
            drawDivider(canvas, node.bounds(), DIVIDER);
            return;
        }
        switch (controlSpec.kind()) {
            case TOGGLE -> renderToggle(node, canvas, enabled && active);
            case SLIDER -> renderSlider(node, canvas, enabled && active);
            case NUMBER -> renderNumber(node, canvas, enabled && active);
            case CHOICE -> renderChoice(node, canvas, enabled && active);
        }
        drawDivider(canvas, node.bounds(), DIVIDER);
    }

    private void renderToggle(UiNode node, UiCanvas canvas, boolean enabled) {
        UiRect rect = controlBounds(node.bounds(), scale(node));
        boolean value = Boolean.TRUE.equals(node.props().get("value"));
        boolean active = boolProp(node, "active", true);
        boolean blocked = boolProp(node, "blocked", false);
        int size = Math.max(10, Math.round(12 * scale(node)));
        UiRect box = new UiRect(rect.right() - size - Math.max(8, Math.round(10 * scale(node))),
                rect.y() + Math.max(7, Math.round(9 * scale(node))),
                size, size);
        int border = !enabled || !active ? 0xFF64748B : blocked ? WARNING : (value ? ACTIVE : BORDER);
        canvas.fillRect(box, 0x00000000);
        canvas.borderRect(box, border);
        if (value) {
            int inset = Math.max(2, box.width() / 4);
            int innerColor = blocked ? WARNING : (!enabled || !active ? 0xFF94A3B8 : ACTIVE);
            canvas.fillRect(new UiRect(box.x() + inset, box.y() + inset,
                    Math.max(3, box.width() - inset * 2), Math.max(3, box.height() - inset * 2)), innerColor);
        }
    }

    private void renderSlider(UiNode node, UiCanvas canvas, boolean enabled) {
        UiRect rect = controlBounds(node.bounds(), scale(node));
        NumericSpec spec = controlSpec(node).numericSpec();
        double current = valueAsDouble(node.props().get("value"));
        double progress = spec.maxValue() <= spec.minValue() ? 0.0D : (current - spec.minValue()) / (spec.maxValue() - spec.minValue());
        progress = Mth.clamp(progress, 0.0D, 1.0D);
        int valueWidth = Math.max(48, Math.round(54 * scale(node)));
        UiRect track = new UiRect(rect.x() + Math.max(6, Math.round(8 * scale(node))),
                rect.y() + Math.max(10, Math.round(12 * scale(node))),
                rect.width() - valueWidth - Math.max(14, Math.round(18 * scale(node))),
                Math.max(3, Math.round(4 * scale(node))));
        canvas.fillRect(track, 0xFF243241);
        canvas.fillRect(new UiRect(track.x(), track.y(), Math.max(0, (int) (track.width() * progress)), track.height()), enabled ? ACTIVE_DIM : 0xFF475569);
        UiRect knob = new UiRect(track.x() + (int) (track.width() * progress) - Math.max(3, Math.round(3 * scale(node))),
                track.y() - Math.max(2, Math.round(3 * scale(node))),
                Math.max(6, Math.round(8 * scale(node))), Math.max(8, Math.round(10 * scale(node))));
        canvas.fillRect(knob, 0xFFF8FAFC);
        canvas.drawText(Component.literal(formatNumeric(spec, current)), rect.right() - valueWidth + Math.max(2, Math.round(4 * scale(node))),
                rect.y() + Math.max(5, Math.round(6 * scale(node))), enabled ? TEXT : MUTED);
    }

    private void renderNumber(UiNode node, UiCanvas canvas, boolean enabled) {
        UiRect rect = controlBounds(node.bounds(), scale(node));
        int boxWidth = Math.max(58, Math.round(70 * scale(node)));
        UiRect box = new UiRect(rect.right() - boxWidth, rect.y() + Math.max(4, Math.round(4 * scale(node))),
                boxWidth, Math.max(16, Math.round(18 * scale(node))));
        boolean editing = boolProp(node, "editing", false);
        String draftValue = strProp(node, "draftValue");
        NumericSpec spec = controlSpec(node).numericSpec();
        canvas.fillRect(box, editing ? 0xCC0F1720 : 0xAA101820);
        canvas.borderRect(box, editing ? ACTIVE : (enabled ? BORDER : 0xFF475569));
        int textInset = Math.max(4, Math.round(6 * scale(node)));
        int availableWidth = Math.max(8, box.width() - textInset * 2);
        if (editing) {
            String cursor = (minecraft.level != null && (minecraft.level.getGameTime() / 6L) % 2L == 0L) ? "|" : "";
            String text = minecraft.font.plainSubstrByWidth(draftValue + cursor, availableWidth);
            canvas.drawText(Component.literal(text), box.x() + textInset,
                    box.y() + Math.max(3, Math.round(4 * scale(node))), enabled ? TEXT : MUTED);
            return;
        }
        canvas.drawCenteredText(Component.literal(formatNumeric(spec, valueAsDouble(node.props().get("value")))),
                box.x() + box.width() / 2, box.y() + Math.max(3, Math.round(4 * scale(node))), enabled ? TEXT : MUTED);
    }

    private void renderChoice(UiNode node, UiCanvas canvas, boolean enabled) {
        ChoiceSpec choiceSpec = controlSpec(node).choiceSpec();
        if (choiceSpec == null) {
            return;
        }
        UiRect rect = controlBounds(node.bounds(), scale(node));
        boolean segmented = choiceSpec.presentation() == ChoicePresentation.SEGMENTED
                || (choiceSpec.presentation() == ChoicePresentation.AUTO && choiceSpec.options().size() <= 3);
        if (segmented) {
            int optionWidth = Math.max(1, rect.width() / Math.max(1, choiceSpec.options().size()));
            for (int i = 0; i < choiceSpec.options().size(); i++) {
                ChoiceOptionSpec option = choiceSpec.options().get(i);
                int x = rect.x() + i * optionWidth;
                int width = i == choiceSpec.options().size() - 1 ? rect.right() - x : optionWidth;
                UiRect optionRect = new UiRect(x, rect.y() + Math.max(4, Math.round(4 * scale(node))), width - 2, Math.max(16, Math.round(18 * scale(node))));
                boolean selected = optionSelected(node.props().get("value"), option.value());
                canvas.fillRect(optionRect, selected ? ACTIVE_DIM : 0x66131D27);
                canvas.borderRect(optionRect, selected ? ACTIVE : BORDER);
                canvas.drawCenteredText(textOf(option.displayKey()), optionRect.x() + optionRect.width() / 2,
                        optionRect.y() + Math.max(3, Math.round(4 * scale(node))), selected ? 0xFFFFFFFF : (enabled ? TEXT : MUTED));
            }
            return;
        }
        UiRect box = new UiRect(rect.x(), rect.y() + Math.max(4, Math.round(4 * scale(node))), rect.width(), Math.max(16, Math.round(18 * scale(node))));
        canvas.fillRect(box, 0x7A101820);
        canvas.borderRect(box, enabled ? BORDER : 0xFF475569);
        canvas.drawText(textOf(selectedChoiceLabel(choiceSpec, node.props().get("value"))), box.x() + Math.max(4, Math.round(6 * scale(node))),
                box.y() + Math.max(3, Math.round(4 * scale(node))), enabled ? TEXT : MUTED);
        canvas.drawText(Component.literal("v"), box.right() - Math.max(11, Math.round(12 * scale(node))), box.y() + Math.max(3, Math.round(4 * scale(node))), SUBTLE);
    }

    private void renderChoiceDropdown(UiNode node, UiCanvas canvas, int mouseX, int mouseY) {
        ControlSpec controlSpec = controlSpec(node);
        if (controlSpec == null || controlSpec.choiceSpec() == null) {
            return;
        }
        UiRect dropdown = dropdownBounds(node);
        canvas.fillRect(dropdown, 0xEE0E141C);
        canvas.borderRect(dropdown, BORDER);
        int y = dropdown.y();
        int rowHeight = Math.max(20, Math.round(20 * scale(node)));
        for (ChoiceOptionSpec option : controlSpec.choiceSpec().options()) {
            UiRect row = new UiRect(dropdown.x(), y, dropdown.width(), rowHeight);
            boolean selected = optionSelected(node.props().get("value"), option.value());
            boolean hovered = row.contains(mouseX, mouseY);
            if (hovered) {
                canvas.fillRect(row, selected ? 0x5A10B981 : 0xAA18232F);
            } else if (selected) {
                canvas.fillRect(row, 0x3A10B981);
            }
            canvas.drawText(textOf(option.displayKey()), row.x() + Math.max(4, Math.round(6 * scale(node))),
                    row.y() + Math.max(5, Math.round(6 * scale(node))), selected || hovered ? 0xFFFFFFFF : TEXT);
            y += rowHeight;
        }
    }

    private void drawDivider(UiCanvas canvas, UiRect bounds, int color) {
        canvas.fillRect(new UiRect(bounds.x(), bounds.bottom() - 1, bounds.width(), 1), color);
    }

    private void renderMetricCard(UiNode node, UiCanvas canvas, boolean hovered) {
        String mode = strProp(node, "mode");
        if ("summary-row".equals(mode)) {
            renderSummaryMetric(node, canvas, hovered);
            return;
        }
        if ("ratio-row".equals(mode)) {
            renderRatioMetric(node, canvas, hovered);
        }
    }

    private void renderMetricsLayoutToggle(UiNode node, UiCanvas canvas, boolean hovered) {
        String layoutMode = strProp(node, "layoutMode");
        int columns = intProp(node, "columns", 1);
        boolean auto = "AUTO".equals(layoutMode);
        int fill = auto ? 0xA01E2E3E : (hovered ? 0x8A18232F : 0x70111A25);
        canvas.fillRect(node.bounds(), fill);
        canvas.borderRect(node.bounds(), auto ? ACTIVE : BORDER);
        String label = auto ? "A" + columns : switch (layoutMode) {
            case "ONE" -> "1C";
            case "TWO" -> "2C";
            case "THREE" -> "3C";
            default -> columns + "C";
        };
        canvas.drawCenteredText(Component.literal(label),
                node.bounds().x() + node.bounds().width() / 2,
                node.bounds().y() + Math.max(3, Math.round(4 * scale(node))),
                auto ? 0xFFFFFFFF : SUBTLE);
    }

    private void renderSummaryMetric(UiNode node, UiCanvas canvas, boolean hovered) {
        float scale = scale(node);
        int pad = Math.max(8, Math.round(10 * scale));
        canvas.fillRect(node.bounds(), hovered ? ROW : ROW_ALT);
        canvas.fillRect(new UiRect(node.bounds().x(), node.bounds().y(), 3, node.bounds().height()), intProp(node, "accent", ACTIVE_DIM));
        canvas.drawText(textOf(strProp(node, "title")), node.bounds().x() + pad, node.bounds().y() + Math.max(8, Math.round(10 * scale)), SUBTLE);

        String valueText = strProp(node, "value");
        String unitText = strProp(node, "unit");
        Component valueComponent = Component.literal(unitText.isEmpty() ? valueText : valueText + " " + unitText);
        int valueWidth = canvas.width(valueComponent);
        canvas.drawText(valueComponent, node.bounds().right() - valueWidth - pad, node.bounds().y() + Math.max(8, Math.round(10 * scale)), TEXT);
        drawDivider(canvas, node.bounds(), DIVIDER);
    }

    private void renderRatioMetric(UiNode node, UiCanvas canvas, boolean hovered) {
        float scale = scale(node);
        int pad = Math.max(8, Math.round(10 * scale));
        int top = node.bounds().y() + pad;
        canvas.fillRect(node.bounds(), hovered ? ROW : ROW_ALT);
        canvas.fillRect(new UiRect(node.bounds().x(), node.bounds().y(), 3, node.bounds().height()), intProp(node, "accent", ACTIVE_DIM));

        int hidden = intProp(node, "hidden", 0);
        int visible = intProp(node, "visible", 0);
        int total = intProp(node, "total", 0);
        double ratio = doubleProp(node, "ratio", 0.0D);

        canvas.drawText(textOf(strProp(node, "title")), node.bounds().x() + pad, top, SUBTLE);
        String primary = hidden + " / " + total;
        Component primaryComponent = Component.literal(primary);
        int primaryWidth = canvas.width(primaryComponent);
        canvas.drawText(primaryComponent, node.bounds().right() - primaryWidth - pad, top, TEXT);

        String secondary = "visible " + visible;
        String percentage = String.format(Locale.ROOT, "%.1f%% hidden", ratio * 100.0D);
        int secondaryY = top + Math.max(14, Math.round(18 * scale));
        canvas.drawText(Component.literal(secondary), node.bounds().x() + pad, secondaryY, MUTED);
        Component percentageComponent = Component.literal(percentage);
        int percentageWidth = canvas.width(percentageComponent);
        canvas.drawText(percentageComponent, node.bounds().right() - percentageWidth - pad, secondaryY, SUBTLE);

        UiRect bar = new UiRect(node.bounds().x() + pad, node.bounds().bottom() - Math.max(12, Math.round(14 * scale)),
                node.bounds().width() - pad * 2, Math.max(5, Math.round(6 * scale)));
        canvas.fillRect(bar, 0xFF243241);
        int fillWidth = Math.max(0, Math.min(bar.width(), (int) Math.round(bar.width() * Mth.clamp((float) ratio, 0.0f, 1.0f))));
        canvas.fillRect(new UiRect(bar.x(), bar.y(), fillWidth, bar.height()), intProp(node, "accent", ACTIVE_DIM));
        drawDivider(canvas, node.bounds(), DIVIDER);
    }

    private void renderChart(UiNode node, UiCanvas canvas) {
        canvas.fillRect(node.bounds(), ROW_ALT);
        canvas.borderRect(node.bounds(), 0x66324150);
        float scale = scale(node);
        int pad = Math.max(8, Math.round(10 * scale));
        canvas.drawText(textOf(strProp(node, "title")), node.bounds().x() + pad, node.bounds().y() + Math.max(5, Math.round(7 * scale)), TEXT);
        @SuppressWarnings("unchecked")
        List<Double> bars = (List<Double>) node.props().get("bars");
        double threshold = doubleProp(node, "threshold", 33.0D);
        if (bars == null || bars.isEmpty()) {
            return;
        }
        int chartX = node.bounds().x() + pad;
        int chartY = node.bounds().y() + Math.max(18, Math.round(24 * scale));
        int chartHeight = Math.max(4, node.bounds().height() - Math.max(26, Math.round(34 * scale)));
        int barWidth = Math.max(1, (node.bounds().width() - pad * 2) / bars.size());
        double max = bars.stream().mapToDouble(Double::doubleValue).max().orElse(1.0D);
        for (int i = 0; i < bars.size(); i++) {
            double value = bars.get(i);
            int height = max <= 0.0D ? 0 : (int) Math.max(2, (value / max) * chartHeight);
            int x = chartX + i * barWidth;
            int y = chartY + chartHeight - height;
            canvas.fillRect(new UiRect(x, y, Math.max(1, barWidth - 1), height), value >= threshold ? WARNING : ACTIVE_DIM);
        }
    }

    private void renderMacroSectionHeader(UiNode node, UiCanvas canvas, boolean hovered) {
        if (hovered) {
            canvas.fillRect(node.bounds(), HOVER);
        }
        boolean expanded = boolProp(node, "expanded", false);
        int y = node.bounds().y() + Math.max(3, Math.round(5 * scale(node)));
        canvas.drawText(Component.literal(expanded ? "v" : ">"), node.bounds().x() + 2, y, MUTED);
        canvas.drawText(textOf(strProp(node, "title")), node.bounds().x() + Math.max(12, Math.round(14 * scale(node))), y, TEXT);
        drawDivider(canvas, node.bounds(), DIVIDER);
    }

    private void renderMacroConstant(UiNode node, UiCanvas canvas, boolean hovered) {
        float scale = scale(node);
        int pad = Math.max(8, Math.round(10 * scale));
        if (hovered) {
            canvas.fillRect(node.bounds(), HOVER);
        }
        canvas.drawText(Component.literal(strProp(node, "name")), node.bounds().x() + pad, node.bounds().y() + Math.max(4, Math.round(6 * scale)), TEXT);
        canvas.drawText(Component.literal(strProp(node, "source")), node.bounds().x() + pad, node.bounds().y() + Math.max(13, Math.round(14 * scale)), MUTED);
        Component type = Component.literal(strProp(node, "type"));
        int typeWidth = canvas.width(type);
        Component value = Component.literal(strProp(node, "value"));
        int valueWidth = canvas.width(value);
        int right = node.bounds().right() - pad;
        canvas.drawText(type, right - valueWidth - typeWidth - Math.max(16, Math.round(18 * scale)), node.bounds().y() + Math.max(4, Math.round(6 * scale)), SUBTLE);
        canvas.drawText(value, right - valueWidth, node.bounds().y() + Math.max(4, Math.round(6 * scale)), boolProp(node, "flag", false) ? ACTIVE : 0xFF93C5FD);
        drawDivider(canvas, node.bounds(), DIVIDER);
    }

    private void renderDiagnosticsHeader(UiNode node, UiCanvas canvas) {
        boolean unreadAlerts = boolProp(node, "unreadAlerts", false);
        int warningCount = intProp(node, "warningCount", 0);
        int errorCount = intProp(node, "errorCount", 0);
        int accent = unreadAlerts ? (errorCount > 0 ? ERROR : WARNING) : 0x7A37485F;
        canvas.fillRect(node.bounds(), 0xC8192230);
        canvas.borderRect(node.bounds(), accent);
        float scale = scale(node);
        int pad = Math.max(8, Math.round(10 * scale));
        int titleY = node.bounds().y() + Math.max(7, Math.round(9 * scale));
        if (unreadAlerts) {
            canvas.fillRect(new UiRect(node.bounds().x(), node.bounds().y(), 3, node.bounds().height()), accent);
        }
        canvas.drawText(textOf(strProp(node, "title")), node.bounds().x() + pad, titleY, unreadAlerts ? 0xFFFFFFFF : TEXT);

        if (DiagnosticsPanelMode.COLLAPSED.name().equals(String.valueOf(node.props().get("mode")))) {
            String preview = strProp(node, "preview");
            int badgeX = node.bounds().right() - pad;
            if (warningCount > 0) {
                badgeX = drawAlertBadge(canvas, badgeX, node.bounds().y() + Math.max(5, Math.round(6 * scale)), "W " + warningCount, WARNING);
            }
            if (errorCount > 0) {
                badgeX = drawAlertBadge(canvas, badgeX, node.bounds().y() + Math.max(5, Math.round(6 * scale)), "E " + errorCount, ERROR);
            }
            if (!preview.isEmpty()) {
                int previewX = node.bounds().x() + Math.max(96, Math.round(104 * scale));
                String clipped = minecraft.font.plainSubstrByWidth(preview, Math.max(20, badgeX - previewX - pad));
                canvas.drawText(Component.literal(clipped), previewX, titleY, unreadAlerts ? 0xFFD1D5DB : SUBTLE);
            }
        } else {
            int dividerY = node.bounds().y() + Math.max(22, Math.round(26 * scale));
            canvas.fillRect(new UiRect(node.bounds().x() + pad, dividerY, node.bounds().width() - pad * 2, 1), DIVIDER);
        }
    }

    private int drawAlertBadge(UiCanvas canvas, int rightX, int y, String text, int color) {
        Component component = Component.literal(text);
        int width = canvas.width(component) + 8;
        UiRect badge = new UiRect(rightX - width, y, width, 12);
        canvas.fillRect(badge, 0x5A111827);
        canvas.borderRect(badge, color);
        canvas.drawCenteredText(component, badge.x() + badge.width() / 2, badge.y() + 2, color);
        return badge.x() - 6;
    }

    private void renderDiagnosticsFilter(UiNode node, UiCanvas canvas, boolean hovered) {
        boolean active = boolProp(node, "active", false);
        canvas.fillRect(node.bounds(), active ? 0xA01E2E3E : (hovered ? 0x8A18232F : 0x70111A25));
        canvas.borderRect(node.bounds(), active ? ACTIVE : BORDER);
        canvas.drawCenteredText(Component.literal(strProp(node, "level").toLowerCase(Locale.ROOT)),
                node.bounds().x() + node.bounds().width() / 2, node.bounds().y() + Math.max(4, Math.round(5 * scale(node))), active ? 0xFFFFFFFF : SUBTLE);
    }

    private void renderDiagnosticsState(UiNode node, DashboardController controller, UiCanvas canvas, boolean hovered) {
        String mode = strProp(node, "mode");
        boolean active = controller.diagnosticsPanelMode().name().equals(mode);
        canvas.fillRect(node.bounds(), active ? ACTIVE_DIM : (hovered ? 0x8A18232F : 0x70111A25));
        canvas.borderRect(node.bounds(), active ? ACTIVE : BORDER);
        canvas.drawCenteredText(Component.literal(mode.substring(0, 1)), node.bounds().x() + node.bounds().width() / 2,
                node.bounds().y() + Math.max(4, Math.round(5 * scale(node))), active ? 0xFFFFFFFF : SUBTLE);
    }

    private void renderLogLine(UiNode node, UiCanvas canvas, boolean hovered) {
        float scale = scale(node);
        if (hovered) {
            canvas.fillRect(node.bounds(), 0x301E2A38);
        }
        int pad = Math.max(4, Math.round(6 * scale));
        int x = node.bounds().x() + pad;
        int y = node.bounds().y() + Math.max(3, Math.round(5 * scale));
        canvas.drawText(Component.literal(strProp(node, "time")), x, y, MUTED);
        x += Math.max(44, Math.round(50 * scale));
        int repeat = intProp(node, "repeat", 1);
        if (repeat > 1) {
            Component repeatComponent = Component.literal("[" + repeat + "]");
            canvas.drawText(repeatComponent, x, y, ACTIVE);
            x += canvas.width(repeatComponent) + Math.max(8, Math.round(10 * scale));
        }
        canvas.drawText(Component.literal(strProp(node, "level").toLowerCase(Locale.ROOT)), x, y, levelColor(strProp(node, "level")));
        x += Math.max(38, Math.round(42 * scale));
        String module = strProp(node, "module");
        if (!module.isEmpty()) {
            canvas.drawText(Component.literal(module), x, y, SUBTLE);
            x += Math.max(54, Math.round(72 * scale));
        }
        int available = Math.max(20, node.bounds().right() - x - pad);
        String message = minecraft.font.plainSubstrByWidth(strProp(node, "message"), available);
        canvas.drawText(Component.literal(message), x, y, TEXT);
        drawDivider(canvas, node.bounds(), 0x252F3F52);
    }

    private void renderTooltip(UiNode node, UiCanvas canvas, int mouseX, int mouseY) {
        List<String> lines = tooltipLines(node, 220);
        if (lines.isEmpty()) {
            return;
        }
        int width = 0;
        for (String line : lines) {
            width = Math.max(width, canvas.width(Component.literal(line)));
        }
        int height = lines.size() * (canvas.lineHeight() + 2) + 8;
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        int x = Math.min(mouseX + 10, screenWidth - width - 16);
        int y = Math.min(mouseY + 10, screenHeight - height - 16);
        UiRect rect = new UiRect(Math.max(6, x), Math.max(6, y), width + 10, height);
        canvas.fillRect(rect, 0xEE0A1016);
        canvas.borderRect(rect, BORDER);
        int drawY = rect.y() + 4;
        for (String line : lines) {
            canvas.drawText(Component.literal(line), rect.x() + 5, drawY, TEXT);
            drawY += canvas.lineHeight() + 2;
        }
    }

    private List<String> tooltipLines(UiNode node, int width) {
        List<String> lines = new ArrayList<>();
        if (boolProp(node, "blocked", false)) {
            lines.addAll(wrapTooltip(formatBlockedPathTooltip(node), width));
        }
        String detail = strProp(node, "detail");
        if (!detail.isEmpty()) {
            lines.addAll(wrapTooltip(detail, width));
        }
        return lines;
    }

    private List<String> wrapTooltip(String text, int width) {
        if (text.startsWith(ENABLE_PATH_PREFIX)) {
            return List.of(formatEnablePathTooltip(text));
        }
        List<String> lines = new ArrayList<>();
        String remaining = textOf(text).getString();
        while (!remaining.isEmpty()) {
            String part = minecraft.font.plainSubstrByWidth(remaining, width);
            if (part.isEmpty()) {
                break;
            }
            lines.add(part);
            remaining = remaining.substring(part.length()).stripLeading();
        }
        return lines.isEmpty() ? List.of(text) : lines;
    }

    private String formatBlockedPathTooltip(UiNode node) {
        Object rawPath = node.props().get("blockedByDisplayPath");
        if (!(rawPath instanceof List<?> path) || path.isEmpty()) {
            return "Enabled, but currently blocked by a parent setting.";
        }
        StringBuilder builder = new StringBuilder("Enabled, but currently blocked by ");
        for (int i = 0; i < path.size(); i++) {
            if (i > 0) {
                builder.append(" / ");
            }
            builder.append(textOf(String.valueOf(path.get(i))).getString());
        }
        return builder.toString();
    }

    private String formatEnablePathTooltip(String encoded) {
        String[] parts = encoded.substring(ENABLE_PATH_PREFIX.length()).split("\\|");
        StringBuilder builder = new StringBuilder("Enable path: ");
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                builder.append(" / ");
            }
            builder.append(textOf(parts[i]).getString());
        }
        return builder.toString();
    }

    private UiRect dropdownBounds(UiNode node) {
        ControlSpec controlSpec = controlSpec(node);
        int optionCount = controlSpec != null && controlSpec.choiceSpec() != null ? controlSpec.choiceSpec().options().size() : 0;
        UiRect control = controlBounds(node.bounds(), scale(node));
        int rowHeight = Math.max(20, Math.round(20 * scale(node)));
        return new UiRect(control.x(), control.bottom() + 2, control.width(), optionCount * rowHeight);
    }

    private UiRect controlBounds(UiRect row, float scale) {
        int minWidth = Math.max(132, Math.round(146 * scale));
        int maxWidth = Math.max(minWidth, Math.min(Math.round(row.width() * 0.45f), Math.round(176 * scale)));
        return new UiRect(row.right() - maxWidth, row.y(), maxWidth - Math.max(4, Math.round(6 * scale)), row.height());
    }

    private boolean nodeContains(UiNode node, double mouseX, double mouseY) {
        return node.bounds().contains(mouseX, mouseY) && (node.clipRect() == null || node.clipRect().contains(mouseX, mouseY));
    }

    private boolean isInteractive(UiNode node) {
        if (boolProp(node, "interactive", false)) {
            return true;
        }
        return switch (node.type()) {
            case TAB_BUTTON, TREE_GROUP, TREE_CONTROL, MACRO_SECTION_HEADER, METRICS_LAYOUT_TOGGLE, DIAGNOSTIC_HEADER, DIAGNOSTIC_FILTER, DIAGNOSTIC_STATE -> true;
            default -> false;
        };
    }

    private ControlSpec controlSpec(UiNode node) {
        Object value = node.props().get("controlSpec");
        return value instanceof ControlSpec controlSpec ? controlSpec : null;
    }

    private Component textOf(String keyOrLiteral) {
        if (keyOrLiteral == null || keyOrLiteral.isEmpty()) {
            return Component.empty();
        }
        return I18n.exists(keyOrLiteral) ? Component.translatable(keyOrLiteral) : Component.literal(keyOrLiteral);
    }

    private int intProp(UiNode node, String key, int fallback) {
        Object value = node.props().get(key);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private double doubleProp(UiNode node, String key, double fallback) {
        Object value = node.props().get(key);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private boolean boolProp(UiNode node, String key, boolean fallback) {
        Object value = node.props().get(key);
        return value instanceof Boolean bool ? bool : fallback;
    }

    private String strProp(UiNode node, String key) {
        Object value = node.props().get(key);
        return value != null ? String.valueOf(value) : "";
    }

    private float scale(UiNode node) {
        Object value = node.props().get("scale");
        return value instanceof Number number ? number.floatValue() : 1.0f;
    }

    private boolean optionSelected(Object currentValue, String optionValue) {
        if (currentValue instanceof Enum<?> enumValue) {
            return enumValue.name().equals(optionValue);
        }
        return String.valueOf(currentValue).equals(optionValue);
    }

    private String selectedChoiceLabel(ChoiceSpec choiceSpec, Object currentValue) {
        for (ChoiceOptionSpec option : choiceSpec.options()) {
            if (optionSelected(currentValue, option.value())) {
                return option.displayKey();
            }
        }
        return choiceSpec.options().isEmpty() ? "-" : choiceSpec.options().get(0).displayKey();
    }

    private double valueAsDouble(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0.0D;
    }

    private String formatNumeric(NumericSpec spec, double value) {
        return switch (spec.numericKind()) {
            case INTEGER -> String.format(Locale.ROOT, spec.formatPattern(), (int) Math.round(value));
            case FLOAT -> String.format(Locale.ROOT, spec.formatPattern(), value);
        };
    }

    private int levelColor(String level) {
        return switch (level) {
            case "ERROR" -> 0xFFF87171;
            case "WARN" -> WARNING;
            case "INFO" -> 0xFF60A5FA;
            default -> SUBTLE;
        };
    }

    public record ChoiceHitBox(String value, UiRect bounds) {
    }
}







