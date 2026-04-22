package rogo.sketch.gui.debugui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import rogo.sketch.core.dashboard.DashboardControlGeometry;
import rogo.sketch.core.dashboard.DashboardMemoryRowLayout;
import rogo.sketch.core.dashboard.DashboardPrimitive;
import rogo.sketch.core.debugger.DashboardController;
import rogo.sketch.core.debugger.DashboardPanelId;
import rogo.sketch.core.debugger.ui.UiNodeType;
import rogo.sketch.core.ui.control.ChoiceOptionSpec;
import rogo.sketch.core.ui.control.ChoicePresentation;
import rogo.sketch.core.ui.control.ChoiceSpec;
import rogo.sketch.core.ui.control.ControlKind;
import rogo.sketch.core.ui.control.ControlSpec;
import rogo.sketch.core.ui.control.NumericSpec;
import rogo.sketch.core.ui.frame.UiFrame;
import rogo.sketch.core.ui.frame.UiInteractionSurface;
import rogo.sketch.core.ui.frame.UiLayer;
import rogo.sketch.core.ui.frame.UiPaintPass;
import rogo.sketch.core.ui.frame.UiPrimitive;
import rogo.sketch.core.ui.frame.TexturePrimitive;
import rogo.sketch.core.ui.geometry.UiScaleContext;
import rogo.sketch.core.ui.input.HitRegion;
import rogo.sketch.core.ui.geometry.UiRect;
import rogo.sketch.core.ui.text.UiMeasuredTextBlock;
import rogo.sketch.core.ui.text.UiText;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class AdaptiveDashboardRenderer {
    private static final int TEXT = 0xFFE4E7EB;
    private static final int SUBTLE = 0xFF8B99AB;
    private static final int MUTED = 0xFF64748B;
    private static final int BORDER = 0xFF324150;
    private static final int DIVIDER = 0x453C4D62;
    private static final int ROW = 0x5A18222E;
    private static final int ROW_ALT = 0x42111A25;
    private static final int HOVER = 0x50304052;
    private static final int ACTIVE = 0xFF34D399;
    private static final int ACTIVE_DIM = 0xFF10B981;
    private static final int WARNING = 0xFFF59E0B;
    private static final int ERROR = 0xFFF87171;
    private static final String ENABLE_PATH_PREFIX = "dashboard.enable-path|";
    private final Minecraft minecraft = Minecraft.getInstance();
    private final MinecraftUiTextMetrics textMetrics = new MinecraftUiTextMetrics(minecraft);
    private UiScaleContext activeScaleContext = UiScaleContext.of(1.0f, 1, 1);
    private UiRect activeScreenBounds = new UiRect(0, 0, 1, 1);

    public void render(UiFrame frame, DashboardController controller, UiCanvas canvas, double mouseX, double mouseY,
                       DashboardPanelId resizingPanelId, String resizingPanelEdge, String resizingSlotId, String resizingSlotEdge) {
        activeScaleContext = frame.scaleContext();
        activeScreenBounds = activeScaleContext.logicalViewport();
        DashboardPrimitive hovered = findTopPrimitive(frame, mouseX, mouseY);
        UiInteractionSurface hoveredSurface = hovered != null ? hovered.surface() : null;
        UiRect screenBounds = activeScreenBounds;

        List<RenderStep> renderSteps = renderSteps(frame);
        List<DashboardPrimitive> primitives = dashboardPrimitives(frame);
        FloatingHoverScope floatingHoverScope = floatingHoverScope(primitives, controller, mouseX, mouseY);
        UiRect activeClip = null;
        Integer activeSurfaceOrder = null;
        UiPaintPass activePass = null;
        for (RenderStep step : renderSteps) {
            UiPrimitive primitive = step.primitive();
            UiPaintPass pass = step.pass();
            if (!isRenderable(primitive, screenBounds)) {
                continue;
            }
            int surfaceOrder = primitive.surface().sortOrder();
            if (!Objects.equals(activeSurfaceOrder, surfaceOrder) || activePass != pass) {
                canvas.flush();
                activeSurfaceOrder = surfaceOrder;
                activePass = pass;
            }
            if (!Objects.equals(activeClip, primitive.clipRect())) {
                canvas.flush();
                if (activeClip != null) {
                    canvas.popClip();
                }
                if (primitive.clipRect() != null) {
                    canvas.pushClip(primitive.clipRect());
                }
                activeClip = primitive.clipRect();
            }
            if (primitive instanceof TexturePrimitive texturePrimitive) {
                canvas.drawTexture(texturePrimitive.texture(), texturePrimitive.rect(), texturePrimitive.uv(), texturePrimitive.tintArgb());
                continue;
            }
            if (primitive instanceof DashboardPrimitive node) {
                boolean isHovered = isHoveredNode(node, hovered, hoveredSurface, floatingHoverScope, mouseX, mouseY);
                renderNodePass(node, controller, canvas, pass, isHovered, hovered, resizingPanelId, resizingPanelEdge, resizingSlotId, resizingSlotEdge);
            }
        }
        canvas.flush();
        if (activeClip != null) {
            canvas.popClip();
        }
        if (hovered != null) {
            renderTooltip(hovered, canvas, mouseX, mouseY);
        }
        canvas.flush();
    }

    public DashboardPrimitive findTopPrimitive(UiFrame frame, double mouseX, double mouseY) {
        List<HitRegion> hitRegions = new ArrayList<>(frame.hitRegions());
        hitRegions.sort(Comparator.comparingInt((HitRegion region) -> region.layer().ordinal())
                .thenComparingInt(region -> region.surface().sortOrder())
                .thenComparingInt(HitRegion::order));
        for (int i = hitRegions.size() - 1; i >= 0; i--) {
            HitRegion region = hitRegions.get(i);
            if (!region.contains(mouseX, mouseY)) {
                continue;
            }
            Object primitive = region.props().get("primitive");
            if (primitive instanceof DashboardPrimitive dashboardPrimitive) {
                return dashboardPrimitive;
            }
        }
        return null;
    }

    private List<RenderStep> renderSteps(UiFrame frame) {
        List<RenderStep> steps = new ArrayList<>();
        for (UiPrimitive primitive : frame.primitives()) {
            if (primitive instanceof TexturePrimitive) {
                steps.add(new RenderStep(primitive, UiPaintPass.TEXTURE));
            } else if (primitive instanceof DashboardPrimitive) {
                for (UiPaintPass pass : UiPaintPass.values()) {
                    steps.add(new RenderStep(primitive, pass));
                }
            } else {
                steps.add(new RenderStep(primitive, primitive.paintPass()));
            }
        }
        steps.sort(Comparator.comparingInt((RenderStep step) -> step.primitive().layer().ordinal())
                .thenComparingInt(step -> step.primitive().surface().sortOrder())
                .thenComparingInt(step -> step.pass().ordinal())
                .thenComparingInt(step -> step.primitive().order()));
        return steps;
    }

    private List<DashboardPrimitive> dashboardPrimitives(UiFrame frame) {
        List<DashboardPrimitive> primitives = new ArrayList<>();
        for (UiPrimitive primitive : frame.primitives()) {
            if (primitive instanceof DashboardPrimitive dashboardPrimitive) {
                primitives.add(dashboardPrimitive);
            }
        }
        primitives.sort(Comparator.comparingInt((DashboardPrimitive primitive) -> primitive.layer().ordinal())
                .thenComparingInt(primitive -> primitive.surface().sortOrder())
                .thenComparingInt(DashboardPrimitive::order));
        return primitives;
    }

    public List<ChoiceHitBox> choiceHitBoxes(DashboardPrimitive node, DashboardController controller) {
        ControlSpec controlSpec = controlSpec(node);
        if (controlSpec == null || controlSpec.kind() != ControlKind.CHOICE || controlSpec.choiceSpec() == null) {
            return List.of();
        }
        ChoiceSpec choiceSpec = controlSpec.choiceSpec();
        List<ChoiceOptionSpec> options = choiceSpec.options();
        boolean segmented = choiceSpec.presentation() == ChoicePresentation.SEGMENTED
                || (choiceSpec.presentation() == ChoicePresentation.AUTO && options.size() <= 3);
        if (!segmented) {
            return List.of();
        }
        List<ChoiceHitBox> hits = new ArrayList<>();
        UiRect segmentedBounds = choiceBounds(controlBounds(node), scale(node));
        int optionWidth = Math.max(1, segmentedBounds.width() / Math.max(1, options.size()));
        for (int i = 0; i < options.size(); i++) {
            ChoiceOptionSpec option = options.get(i);
            int x = segmentedBounds.x() + i * optionWidth;
            int width = i == options.size() - 1 ? segmentedBounds.right() - x : optionWidth;
            hits.add(new ChoiceHitBox(option.value(), new UiRect(x, segmentedBounds.y(), width, segmentedBounds.height())));
        }
        return hits;
    }

    private boolean isHoveredNode(DashboardPrimitive node, DashboardPrimitive hoveredPrimitive, UiInteractionSurface hoveredSurface,
                                  FloatingHoverScope floatingHoverScope, double mouseX, double mouseY) {
        if (hoveredPrimitive == null || hoveredSurface == null) {
            return false;
        }
        return hoveredSurface.equals(node.surface())
                && nodeContains(node, mouseX, mouseY)
                && allowsHover(node, floatingHoverScope);
    }

    private void renderNodePass(DashboardPrimitive node, DashboardController controller, UiCanvas canvas, UiPaintPass pass, boolean hovered,
                                DashboardPrimitive hoveredPrimitive, DashboardPanelId resizingPanelId, String resizingPanelEdge,
                                String resizingSlotId, String resizingSlotEdge) {
        switch (node.type()) {
            case PANEL -> renderPanel(node, canvas, pass, hovered, hoveredPrimitive, resizingPanelId, resizingPanelEdge, resizingSlotId, resizingSlotEdge);
            case HEADER -> renderHeader(node, canvas, pass, hovered, hoveredPrimitive, resizingPanelId, resizingPanelEdge, resizingSlotId, resizingSlotEdge);
            case TAB_BUTTON -> renderTab(node, canvas, pass, hovered);
            case TREE_GROUP -> renderGroup(node, canvas, pass, hovered);
            case TREE_CONTROL -> renderTreeControl(node, canvas, pass, hovered);
            case METRIC_CARD -> renderMetricCard(node, canvas, pass, hovered);
            case BAR_CHART -> renderChart(node, canvas, pass);
            case MACRO_SECTION_HEADER -> renderMacroSectionHeader(node, canvas, pass, hovered);
            case MACRO_CONSTANT_ROW -> renderMacroConstant(node, canvas, pass, hovered);
            case METRICS_LAYOUT_TOGGLE -> renderMetricsLayoutToggle(node, canvas, pass, hovered);
            case CAPTURE_BUTTON -> renderCaptureButton(node, canvas, pass, hovered);
            case TOPBAR -> renderTopbar(node, canvas, pass, hovered);
            case TOPBAR_MENU_ITEM -> renderTopbarMenuItem(node, canvas, pass, hovered);
            case POPUP_PANEL -> renderPopupPanel(node, canvas, pass, hovered);
            case POPUP_MENU_ITEM -> renderPopupMenuItem(node, canvas, pass, hovered);
            case WINDOW_TAB -> renderWindowTab(node, canvas, pass, hovered);
            case DIAGNOSTIC_HEADER -> renderDiagnosticsHeader(node, canvas, pass, hovered, hoveredPrimitive, resizingPanelId, resizingPanelEdge, resizingSlotId, resizingSlotEdge);
            case DIAGNOSTIC_FILTER -> renderDiagnosticsFilter(node, canvas, pass, hovered);
            case LOG_LINE -> renderLogLine(node, canvas, pass, hovered);
            case LOG_COPY_BUTTON -> renderLogCopyButton(node, canvas, pass, hovered);
            default -> {
            }
        }
    }

    private boolean isGeometryPass(UiPaintPass pass) {
        return pass == UiPaintPass.BACKGROUND;
    }

    private boolean isTextPass(UiPaintPass pass) {
        return pass == UiPaintPass.TEXT;
    }

    private void renderPanel(DashboardPrimitive node, UiCanvas canvas, UiPaintPass pass, boolean hovered, DashboardPrimitive hoveredPrimitive,
                             DashboardPanelId resizingPanelId, String resizingPanelEdge, String resizingSlotId, String resizingSlotEdge) {
        if (!isGeometryPass(pass) && !isTextPass(pass)) {
            return;
        }
        int fill = intProp(node, "fill", 0);
        int border = intProp(node, "border", 0);
        if (isGeometryPass(pass) && fill != 0) {
            canvas.fillRect(node.bounds(), fill);
        }
        if (isGeometryPass(pass) && border != 0) {
            canvas.borderRect(node.bounds(), border);
        }
        String role = strProp(node, "role");
        if ("panel".equals(role)) {
            if (isGeometryPass(pass)) {
                canvas.fillRect(new UiRect(node.bounds().x(), node.bounds().y(), node.bounds().width(), 1), 0x553A5268);
                String highlightedEdges = highlightedPanelEdges(node, hoveredPrimitive, resizingPanelId, resizingPanelEdge);
                if (!highlightedEdges.isEmpty()) {
                    drawEdgeHighlights(canvas, node.bounds(), highlightedEdges, 0xFF93C5FD);
                }
            }
        } else if ("panel-mode-toggle".equals(role)) {
            if (isGeometryPass(pass)) {
                canvas.fillRect(node.bounds(), hovered ? 0xC3344B62 : fill);
                if (border != 0) {
                    canvas.borderRect(node.bounds(), border);
                }
            }
            if (isTextPass(pass)) {
                canvas.drawCenteredText(Component.literal(strProp(node, "label")),
                        node.bounds().x() + node.bounds().width() / 2,
                        node.bounds().y() + Math.max(2, (node.bounds().height() - canvas.lineHeight()) / 2),
                        TEXT);
            }
        } else if ("panel-slot-preview".equals(role)) {
            if (isGeometryPass(pass)) {
                canvas.fillRect(node.bounds(), hovered ? 0x4A4ADEB0 : fill);
                if (border != 0) {
                    canvas.borderRect(node.bounds(), border);
                }
            }
        } else if ("tab-drop-preview".equals(role)) {
            if (isGeometryPass(pass)) {
                canvas.fillRect(node.bounds(), fill != 0 ? fill : 0x3034D399);
                canvas.borderRect(node.bounds(), border != 0 ? border : 0xCC34D399);
                canvas.fillRect(new UiRect(node.bounds().x(), node.bounds().bottom() - 2, node.bounds().width(), 2), ACTIVE);
            }
        } else if ("texture-preview-card".equals(role)) {
            if (isGeometryPass(pass)) {
                canvas.fillRect(node.bounds(), hovered ? 0x7A243242 : fill);
                if (border != 0) {
                    canvas.borderRect(node.bounds(), border);
                }
            }
            if (isTextPass(pass)) {
                float scale = scale(node);
                int pad = Math.max(8, Math.round(10 * scale));
                int titleY = node.bounds().y() + pad;
                canvas.drawText(fitText(textOf(strProp(node, "title")), Math.max(20, node.bounds().width() - pad * 2)),
                        node.bounds().x() + pad, titleY, TEXT);
                String detail = strProp(node, "detail");
                if (!detail.isEmpty()) {
                    canvas.drawText(fitText(Component.literal(detail), Math.max(20, node.bounds().width() - pad * 2)),
                            node.bounds().x() + pad, titleY + canvas.lineHeight() + Math.max(1, Math.round(2 * scale)), SUBTLE);
                }
            }
        } else if ("panel-home-slot".equals(role)) {
            if (isGeometryPass(pass)) {
                if (boolProp(node, "activeDockTarget", false)) {
                    int slotFill = hovered ? 0x2A4F9DFF : 0x18324458;
                    canvas.fillRect(node.bounds(), slotFill);
                    if (border != 0) {
                        canvas.borderRect(node.bounds(), hovered ? 0xC080C0FF : border);
                    }
                }
                String highlightedEdges = highlightedSlotEdges(node, hoveredPrimitive, resizingSlotId, resizingSlotEdge);
                if (!highlightedEdges.isEmpty()) {
                    drawEdgeHighlights(canvas, node.bounds(), highlightedEdges, 0xFFA3E635);
                }
            }
        } else if ("slot-resize-handle".equals(role)) {
            if (isGeometryPass(pass)) {
                String edge = strProp(node, "edge");
                int line = hovered ? 0xFFA3E635 : 0x7A64748B;
                if ("E".equals(edge) || "W".equals(edge)) {
                    int x = node.bounds().x() + node.bounds().width() / 2;
                    canvas.fillRect(new UiRect(x, node.bounds().y(), 1, node.bounds().height()), line);
                } else {
                    int y = node.bounds().y() + node.bounds().height() / 2;
                    canvas.fillRect(new UiRect(node.bounds().x(), y, node.bounds().width(), 1), line);
                }
            }
        } else if ("scrollbar-track".equals(role)) {
            if (isGeometryPass(pass)) {
                canvas.fillRect(node.bounds(), hovered ? 0x3E314155 : fill);
            }
        } else if ("scrollbar-track-x".equals(role)) {
            if (isGeometryPass(pass)) {
                canvas.fillRect(node.bounds(), hovered ? 0x3E314155 : fill);
            }
        } else if ("scrollbar-thumb".equals(role)) {
            if (isGeometryPass(pass)) {
                canvas.fillRect(node.bounds(), hovered ? 0xFF8EA1B8 : fill);
                if (border != 0) {
                    canvas.borderRect(node.bounds(), border);
                }
            }
        } else if ("scrollbar-thumb-x".equals(role)) {
            if (isGeometryPass(pass)) {
                canvas.fillRect(node.bounds(), hovered ? 0xFF8EA1B8 : fill);
                if (border != 0) {
                    canvas.borderRect(node.bounds(), border);
                }
            }
        }
    }

    private void renderHeader(DashboardPrimitive node, UiCanvas canvas, UiPaintPass pass, boolean hovered, DashboardPrimitive hoveredPrimitive,
                              DashboardPanelId resizingPanelId, String resizingPanelEdge, String resizingSlotId, String resizingSlotEdge) {
        renderPanel(node, canvas, pass, hovered, null, resizingPanelId, resizingPanelEdge, resizingSlotId, resizingSlotEdge);
        if (isTextPass(pass)) {
            int pad = Math.max(8, Math.round(10 * scale(node)));
            int titleY = node.bounds().y() + Math.max(7, Math.round(9 * scale(node)));
            int titleX = node.bounds().x() + pad;
            if (boolProp(node, "floating", false)) {
                titleX += 14;
            }
            canvas.drawText(textOf(strProp(node, "title")), titleX, titleY, TEXT);
            return;
        }
        if (isGeometryPass(pass) && boolProp(node, "floating", false)) {
            drawDragGrip(node.bounds(), canvas, hovered);
        }
    }

    private void renderTab(DashboardPrimitive node, UiCanvas canvas, UiPaintPass pass, boolean hovered) {
        boolean active = boolProp(node, "active", false);
        if (isGeometryPass(pass)) {
            int fill = active ? 0xAA202C39 : (hovered ? 0xC2314458 : 0x70121C27);
            canvas.fillRect(node.bounds(), fill);
            canvas.borderRect(node.bounds(), active ? 0xB94C627A : 0x7A324150);
            canvas.fillRect(new UiRect(node.bounds().x(), node.bounds().bottom() - 2, node.bounds().width(), 2), active ? ACTIVE : 0x55324150);
        } else if (isTextPass(pass)) {
            canvas.drawCenteredText(textOf(strProp(node, "title")), node.bounds().x() + node.bounds().width() / 2,
                    node.bounds().y() + Math.max(5, Math.round(7 * scale(node))), active ? 0xFFFFFFFF : SUBTLE);
        }
    }

    private void renderWindowTab(DashboardPrimitive node, UiCanvas canvas, UiPaintPass pass, boolean hovered) {
        boolean active = boolProp(node, "active", false);
        boolean disabled = boolProp(node, "disabled", false);
        if (isGeometryPass(pass)) {
            int fill = disabled ? 0x44111A25 : active ? 0xAA202C39 : (hovered ? 0xC2314458 : 0x70121C27);
            canvas.fillRect(node.bounds(), fill);
            canvas.borderRect(node.bounds(), active ? ACTIVE : 0x7A324150);
            if (active) {
                canvas.fillRect(new UiRect(node.bounds().x(), node.bounds().bottom() - 2, node.bounds().width(), 2), ACTIVE);
            }
        } else if (isTextPass(pass)) {
            int labelWidth = Math.max(8, node.bounds().width() - Math.max(10, Math.round(12 * scale(node))));
            canvas.drawCenteredText(fitText(textOf(strProp(node, "title")), labelWidth),
                    node.bounds().x() + node.bounds().width() / 2,
                    node.bounds().y() + Math.max(5, Math.round(7 * scale(node))),
                    disabled ? MUTED : active ? 0xFFFFFFFF : SUBTLE);
        }
    }

    private void renderTopbar(DashboardPrimitive node, UiCanvas canvas, UiPaintPass pass, boolean hovered) {
        String role = strProp(node, "role");
        int fill = intProp(node, "fill", 0xE80B111A);
        int border = intProp(node, "border", 0);
        if ("topbar-button".equals(role)) {
            boolean active = boolProp(node, "active", false);
            if (isGeometryPass(pass)) {
                canvas.fillRect(node.bounds(), active ? 0xA01E2E3E : hovered ? 0x9A243242 : fill);
                canvas.borderRect(node.bounds(), active ? ACTIVE : border);
            } else if (isTextPass(pass)) {
                canvas.drawCenteredText(fitText(textOf(strProp(node, "label")), Math.max(8, node.bounds().width() - 10)),
                        node.bounds().x() + node.bounds().width() / 2,
                        centeredTextY(node.bounds(), canvas),
                        active ? 0xFFFFFFFF : TEXT);
            }
            return;
        }
        if (isGeometryPass(pass)) {
            canvas.fillRect(node.bounds(), fill);
            if (border != 0) {
                canvas.fillRect(new UiRect(node.bounds().x(), node.bounds().bottom() - 1, node.bounds().width(), 1), border);
            }
        }
    }

    private void renderTopbarMenuItem(DashboardPrimitive node, UiCanvas canvas, UiPaintPass pass, boolean hovered) {
        boolean active = boolProp(node, "active", false);
        renderPopupOptionRow(node, canvas, pass, hovered, active, boolProp(node, "multiSelect", false),
                textOf(strProp(node, "label")));
    }

    private void renderPopupPanel(DashboardPrimitive node, UiCanvas canvas, UiPaintPass pass, boolean hovered) {
        if (!isGeometryPass(pass)) {
            return;
        }
        canvas.fillRect(node.bounds(), hovered ? 0xF0111823 : 0xEE0E141C);
        canvas.borderRect(node.bounds(), hovered ? ACTIVE : BORDER);
    }

    private void renderPopupMenuItem(DashboardPrimitive node, UiCanvas canvas, UiPaintPass pass, boolean hovered) {
        boolean active = optionSelected(node.props().get("value"), strProp(node, "optionValue"));
        renderPopupOptionRow(node, canvas, pass, hovered, active, boolProp(node, "multiSelect", false),
                textOf(strProp(node, "label")));
    }

    private void renderPopupOptionRow(DashboardPrimitive node, UiCanvas canvas, UiPaintPass pass, boolean hovered,
                                      boolean active, boolean multiSelect, Component label) {
        float scale = scale(node);
        if (isGeometryPass(pass)) {
            canvas.fillRect(node.bounds(), active ? 0x3A10B981 : hovered ? 0xC2314458 : 0x00000000);
            if (active) {
                canvas.fillRect(new UiRect(node.bounds().x(), node.bounds().y(), Math.max(2, Math.round(2 * scale)), node.bounds().height()), ACTIVE);
            }
            if (multiSelect) {
                int boxSize = Math.max(8, Math.round(10 * scale));
                int boxX = node.bounds().x() + Math.max(5, Math.round(6 * scale));
                int boxY = node.bounds().y() + Math.max(0, (node.bounds().height() - boxSize) / 2);
                UiRect box = new UiRect(boxX, boxY, boxSize, boxSize);
                canvas.borderRect(box, active ? ACTIVE : SUBTLE);
                if (active) {
                    int inset = Math.max(2, Math.round(2 * scale));
                    canvas.fillRect(new UiRect(box.x() + inset, box.y() + inset,
                            Math.max(1, box.width() - inset * 2),
                            Math.max(1, box.height() - inset * 2)), ACTIVE);
                }
            }
        } else if (isTextPass(pass)) {
            int pad = Math.max(5, Math.round(6 * scale));
            int labelX = node.bounds().x() + pad;
            if (multiSelect) {
                labelX += Math.max(12, Math.round(14 * scale));
            }
            int labelWidth = Math.max(8, node.bounds().right() - labelX - pad);
            canvas.drawText(fitText(label, labelWidth),
                    labelX,
                    centeredTextY(node.bounds(), canvas),
                    active || hovered ? 0xFFFFFFFF : TEXT);
        }
    }

    private void renderGroup(DashboardPrimitive node, UiCanvas canvas, UiPaintPass pass, boolean hovered) {
        if (isGeometryPass(pass)) {
            if (hovered) {
                canvas.fillRect(node.bounds(), HOVER);
            }
            drawDivider(canvas, node.bounds(), DIVIDER);
            return;
        }
        if (!isTextPass(pass)) {
            return;
        }
        boolean expanded = boolProp(node, "expanded", false);
        float scale = scale(node);
        int y = centeredTextY(node.bounds(), canvas);
        int arrowX = node.bounds().x() + Math.max(2, Math.round(3 * scale));
        int titleX = node.bounds().x() + Math.max(12, Math.round(14 * scale));
        int titleWidth = Math.max(16, node.bounds().right() - titleX - Math.max(6, Math.round(8 * scale)));
        canvas.drawText(Component.literal(expanded ? "v" : ">"), arrowX, y, MUTED);
        canvas.drawText(fitText(textOf(strProp(node, "title")), titleWidth), titleX, y, 0xFFF3F6FA);
    }

    private void renderTreeControl(DashboardPrimitive node, UiCanvas canvas, UiPaintPass pass, boolean hovered) {
        if (!isGeometryPass(pass) && !isTextPass(pass)) {
            return;
        }
        if (isGeometryPass(pass) && hovered) {
            canvas.fillRect(node.bounds(), HOVER);
        }
        boolean enabled = boolProp(node, "enabled", true);
        boolean active = boolProp(node, "active", true);
        boolean expandable = boolProp(node, "expandable", false);
        boolean expanded = boolProp(node, "expanded", false);
        TreeRowLayout layout = treeRowLayout(node, canvas);
        float scale = layout.scale();
        int titleColor = enabled && active ? TEXT : MUTED;
        if (isTextPass(pass)) {
            if (expandable) {
                canvas.drawText(Component.literal(expanded ? "v" : ">"),
                        node.bounds().x() + Math.max(2, Math.round(3 * scale)),
                        layout.titleY(), MUTED);
            }
            canvas.drawText(fitText(textOf(strProp(node, "title")), layout.titleWidth()), layout.titleX(), layout.titleY(), titleColor);
            String summary = strProp(node, "summary");
            if (!layout.compact() && !summary.isEmpty()) {
                canvas.drawText(fitText(textOf(summary), layout.titleWidth()), layout.titleX(), layout.summaryY(), MUTED);
            }
        }

        ControlSpec controlSpec = controlSpec(node);
        if (controlSpec == null) {
            if (isGeometryPass(pass)) {
                drawDivider(canvas, node.bounds(), DIVIDER);
            }
            return;
        }
        switch (controlSpec.kind()) {
            case TOGGLE -> renderToggle(node, canvas, pass, enabled && active);
            case SLIDER -> renderSlider(node, canvas, pass, enabled && active);
            case NUMBER -> renderNumber(node, canvas, pass, enabled && active);
            case CHOICE -> renderChoice(node, canvas, pass, enabled && active);
        }
        if (isGeometryPass(pass)) {
            drawDivider(canvas, node.bounds(), DIVIDER);
        }
    }

    private void renderToggle(DashboardPrimitive node, UiCanvas canvas, UiPaintPass pass, boolean enabled) {
        if (!isGeometryPass(pass)) {
            return;
        }
        float scale = scale(node);
        UiRect rect = controlBounds(node);
        boolean value = Boolean.TRUE.equals(node.props().get("value"));
        boolean active = boolProp(node, "active", true);
        boolean blocked = boolProp(node, "blocked", false);
        int size = Math.max(10, Math.round(12 * scale));
        UiRect box = alignRight(rect, size, size, Math.max(8, Math.round(10 * scale)));
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

    private void renderSlider(DashboardPrimitive node, UiCanvas canvas, UiPaintPass pass, boolean enabled) {
        float scale = scale(node);
        UiRect rect = controlBounds(node);
        NumericSpec spec = controlSpec(node).numericSpec();
        double current = valueAsDouble(node.props().get("value"));
        double progress = spec.maxValue() <= spec.minValue() ? 0.0D : (current - spec.minValue()) / (spec.maxValue() - spec.minValue());
        progress = Mth.clamp(progress, 0.0D, 1.0D);
        UiRect track = DashboardControlLayout.sliderTrackBounds(rect, scale);
        int valueWidth = DashboardControlLayout.sliderValueWidth(rect, scale);
        if (isGeometryPass(pass)) {
            UiRect groove = new UiRect(track.x(), track.y() - Math.max(2, Math.round(2 * scale)), track.width(), track.height() + Math.max(4, Math.round(4 * scale)));
            canvas.fillRect(groove, enabled ? 0xAA0F1720 : 0x7A111827);
            canvas.borderRect(groove, enabled ? 0x8A40576E : 0x66475669);
            canvas.fillRect(track, enabled ? 0xFF223142 : 0xFF334155);
            UiRect fill = new UiRect(track.x(), track.y(), Math.max(0, (int) Math.round(track.width() * progress)), track.height());
            if (fill.width() > 0) {
                canvas.fillRect(fill, enabled ? ACTIVE_DIM : 0xFF64748B);
                if (fill.height() > 2) {
                    canvas.fillRect(new UiRect(fill.x(), fill.y() + 1, fill.width(), fill.height() - 2), enabled ? 0xFF34D399 : 0xFF94A3B8);
                }
            }
            int knobWidth = Math.max(7, Math.round(9 * scale));
            int knobHeight = Math.max(10, Math.round(12 * scale));
            UiRect knob = new UiRect(track.x() + (int) Math.round(track.width() * progress) - knobWidth / 2,
                    groove.y() + (groove.height() - knobHeight) / 2,
                    knobWidth, knobHeight);
            canvas.fillRect(knob, 0xFFF8FAFC);
            canvas.borderRect(knob, enabled ? 0xFFCBD5E1 : 0xFF94A3B8);
            return;
        }
        if (!isTextPass(pass)) {
            return;
        }
        Component valueText = fitText(Component.literal(formatNumeric(spec, current)), valueWidth);
        int valueX = Math.min(rect.right() - canvas.width(valueText),
                track.right() + Math.max(4, Math.round(5 * scale)));
        canvas.drawText(valueText, valueX, centeredTextY(rect, canvas), enabled ? TEXT : MUTED);
    }

    private void renderNumber(DashboardPrimitive node, UiCanvas canvas, UiPaintPass pass, boolean enabled) {
        float scale = scale(node);
        UiRect rect = controlBounds(node);
        int boxWidth = Math.min(rect.width(), Math.max(58, Math.round(74 * scale)));
        UiRect box = alignRight(rect, boxWidth, Math.max(16, Math.round(18 * scale)), 0);
        boolean editing = boolProp(node, "editing", false);
        String draftValue = strProp(node, "draftValue");
        NumericSpec spec = controlSpec(node).numericSpec();
        if (isGeometryPass(pass)) {
            canvas.fillRect(box, editing ? 0xCC0F1720 : 0xAA101820);
            canvas.borderRect(box, editing ? ACTIVE : (enabled ? BORDER : 0xFF475569));
            return;
        }
        if (!isTextPass(pass)) {
            return;
        }
        int textInset = Math.max(4, Math.round(6 * scale));
        int availableWidth = Math.max(8, box.width() - textInset * 2);
        if (editing) {
            String cursor = (minecraft.level != null && (minecraft.level.getGameTime() / 6L) % 2L == 0L) ? "|" : "";
            Component text = fitText(Component.literal(draftValue + cursor), availableWidth);
            canvas.drawText(text, box.x() + textInset, centeredTextY(box, canvas), enabled ? TEXT : MUTED);
            return;
        }
        canvas.drawCenteredText(fitText(Component.literal(formatNumeric(spec, valueAsDouble(node.props().get("value")))), availableWidth),
                box.x() + box.width() / 2, centeredTextY(box, canvas), enabled ? TEXT : MUTED);
    }

    private void renderChoice(DashboardPrimitive node, UiCanvas canvas, UiPaintPass pass, boolean enabled) {
        ChoiceSpec choiceSpec = controlSpec(node).choiceSpec();
        if (choiceSpec == null) {
            return;
        }
        float scale = scale(node);
        UiRect rect = controlBounds(node);
        boolean segmented = choiceSpec.presentation() == ChoicePresentation.SEGMENTED
                || (choiceSpec.presentation() == ChoicePresentation.AUTO && choiceSpec.options().size() <= 3);
        if (segmented) {
            UiRect choiceRect = choiceBounds(rect, scale);
            int optionWidth = Math.max(1, choiceRect.width() / Math.max(1, choiceSpec.options().size()));
            for (int i = 0; i < choiceSpec.options().size(); i++) {
                ChoiceOptionSpec option = choiceSpec.options().get(i);
                int x = choiceRect.x() + i * optionWidth;
                int width = i == choiceSpec.options().size() - 1 ? choiceRect.right() - x : optionWidth;
                UiRect optionRect = new UiRect(x, choiceRect.y(), Math.max(1, width - 2), choiceRect.height());
                boolean selected = optionSelected(node.props().get("value"), option.value());
                if (isGeometryPass(pass)) {
                    canvas.fillRect(optionRect, selected ? ACTIVE_DIM : 0x66131D27);
                    canvas.borderRect(optionRect, selected ? ACTIVE : BORDER);
                } else if (isTextPass(pass)) {
                    int labelWidth = Math.max(8, optionRect.width() - Math.max(8, Math.round(10 * scale)));
                    canvas.drawCenteredText(fitText(textOf(option.displayKey()), labelWidth), optionRect.x() + optionRect.width() / 2,
                            centeredTextY(optionRect, canvas), selected ? 0xFFFFFFFF : (enabled ? TEXT : MUTED));
                }
            }
            return;
        }
        UiRect box = choiceBounds(rect, scale);
        if (isGeometryPass(pass)) {
            canvas.fillRect(box, 0x7A101820);
            canvas.borderRect(box, enabled ? BORDER : 0xFF475569);
        } else if (isTextPass(pass)) {
            int textInset = Math.max(4, Math.round(6 * scale));
            int arrowWidth = Math.max(10, Math.round(12 * scale));
            int labelWidth = Math.max(8, box.width() - textInset * 2 - arrowWidth);
            canvas.drawText(fitText(textOf(selectedChoiceLabel(choiceSpec, node.props().get("value"))), labelWidth), box.x() + textInset,
                    centeredTextY(box, canvas), enabled ? TEXT : MUTED);
            canvas.drawText(Component.literal("v"), box.right() - arrowWidth, centeredTextY(box, canvas), SUBTLE);
        }
    }

    private void drawDivider(UiCanvas canvas, UiRect bounds, int color) {
        canvas.fillRect(new UiRect(bounds.x(), bounds.bottom() - 1, bounds.width(), 1), color);
    }

    private void renderMetricCard(DashboardPrimitive node, UiCanvas canvas, UiPaintPass pass, boolean hovered) {
        String mode = strProp(node, "mode");
        if ("summary-row".equals(mode)) {
            renderSummaryMetric(node, canvas, pass, hovered);
            return;
        }
        if ("memory-domain-row".equals(mode)) {
            renderMemoryDomainMetric(node, canvas, pass, hovered);
            return;
        }
        if ("capture-stage-row".equals(mode)) {
            renderCaptureStageMetric(node, canvas, pass, hovered);
            return;
        }
        if ("ratio-row".equals(mode)) {
            renderRatioMetric(node, canvas, pass, hovered);
        }
    }

    private void renderMetricsLayoutToggle(DashboardPrimitive node, UiCanvas canvas, UiPaintPass pass, boolean hovered) {
        String layoutMode = strProp(node, "layoutMode");
        int columns = intProp(node, "columns", 1);
        boolean auto = "AUTO".equals(layoutMode);
        if (isGeometryPass(pass)) {
            int fill = auto ? 0xA01E2E3E : (hovered ? 0xC2314458 : 0x70111A25);
            canvas.fillRect(node.bounds(), fill);
            canvas.borderRect(node.bounds(), auto ? ACTIVE : BORDER);
            return;
        }
        if (!isTextPass(pass)) {
            return;
        }
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

    private void renderCaptureButton(DashboardPrimitive node, UiCanvas canvas, UiPaintPass pass, boolean hovered) {
        float scale = scale(node);
        int inset = Math.max(3, Math.round(4 * scale));
        UiRect body = new UiRect(
                node.bounds().x() + inset,
                node.bounds().y() + Math.max(inset, node.bounds().height() / 4),
                Math.max(4, node.bounds().width() - inset * 2),
                Math.max(4, node.bounds().height() - inset * 2));
        int iconColor = hovered ? 0xFFFFFFFF : SUBTLE;
        if (!isGeometryPass(pass)) {
            return;
        }
        canvas.fillRect(node.bounds(), hovered ? 0xC2314458 : 0x70111A25);
        canvas.borderRect(node.bounds(), hovered ? ACTIVE : BORDER);
        canvas.borderRect(body, iconColor);
        int topWidth = Math.max(3, body.width() / 3);
        canvas.fillRect(new UiRect(body.x() + 1, Math.max(node.bounds().y() + 1, body.y() - 2), topWidth, 2), iconColor);
        int lensSize = Math.max(2, Math.min(4, Math.min(body.width(), body.height()) - 2));
        canvas.fillRect(new UiRect(
                body.x() + Math.max(1, (body.width() - lensSize) / 2),
                body.y() + Math.max(1, (body.height() - lensSize) / 2),
                lensSize,
                lensSize), hovered ? ACTIVE : iconColor);
    }

    private void renderSummaryMetric(DashboardPrimitive node, UiCanvas canvas, UiPaintPass pass, boolean hovered) {
        float scale = scale(node);
        int pad = Math.max(8, Math.round(10 * scale));
        if (isGeometryPass(pass)) {
            canvas.fillRect(node.bounds(), hovered ? 0x7A243242 : ROW_ALT);
            canvas.fillRect(new UiRect(node.bounds().x(), node.bounds().y(), 3, node.bounds().height()), intProp(node, "accent", ACTIVE_DIM));
        }

        String valueText = strProp(node, "value");
        String unitText = strProp(node, "unit");
        UiMeasuredTextBlock valueLayout = textBlockProp(node, "valueLayout");
        UiMeasuredTextBlock titleLayout = textBlockProp(node, "titleLayout");
        List<String> valueLines = valueLayout != null ? valueLayout.lines() : stringListProp(node, "valueLines");
        List<String> titleLines = titleLayout != null ? titleLayout.lines() : stringListProp(node, "titleLines");
        if (valueLines.isEmpty()) {
            valueLines = List.of(unitText.isEmpty() ? valueText : valueText + " " + unitText);
        }
        if (titleLines.isEmpty()) {
            titleLines = List.of(textOf(strProp(node, "title")).getString());
        }
        int valueMaxWidth = Math.max(40, Math.round(node.bounds().width() * 0.38f));
        int titleWidth = Math.max(20, node.bounds().width() - pad * 3 - valueMaxWidth);
        int textY = node.bounds().y() + Math.max(6, Math.round(8 * scale));
        int lineHeight = titleLayout != null ? titleLayout.lineHeight() : canvas.lineHeight();
        int lineGap = titleLayout != null ? titleLayout.lineGap() : Math.max(1, Math.round(2 * scale));
        int lineStep = lineHeight + lineGap;
        if (isTextPass(pass)) {
            int lines = Math.max(titleLines.size(), valueLines.size());
            for (int i = 0; i < lines; i++) {
                if (i < titleLines.size()) {
                    canvas.drawText(fitText(Component.literal(titleLines.get(i)), titleWidth), node.bounds().x() + pad, textY + i * lineStep, SUBTLE);
                }
                if (i < valueLines.size()) {
                    Component clippedValue = fitText(Component.literal(valueLines.get(i)), valueMaxWidth);
                    int valueWidth = canvas.width(clippedValue);
                    canvas.drawText(clippedValue, node.bounds().right() - valueWidth - pad, textY + i * lineStep, TEXT);
                }
            }
        }
        if (isGeometryPass(pass)) {
            drawDivider(canvas, node.bounds(), DIVIDER);
        }
    }

    private void renderRatioMetric(DashboardPrimitive node, UiCanvas canvas, UiPaintPass pass, boolean hovered) {
        float scale = scale(node);
        int pad = Math.max(8, Math.round(10 * scale));
        int top = node.bounds().y() + pad;
        if (isGeometryPass(pass)) {
            canvas.fillRect(node.bounds(), hovered ? 0x7A243242 : ROW_ALT);
            canvas.fillRect(new UiRect(node.bounds().x(), node.bounds().y(), 3, node.bounds().height()), intProp(node, "accent", ACTIVE_DIM));
        }

        int hidden = intProp(node, "hidden", 0);
        int visible = intProp(node, "visible", 0);
        int total = intProp(node, "total", 0);
        double ratio = doubleProp(node, "ratio", 0.0D);

        int primaryMaxWidth = Math.max(32, Math.round(node.bounds().width() * 0.30f));
        String primary = hidden + " / " + total;
        Component primaryComponent = fitText(Component.literal(primary), primaryMaxWidth);
        int primaryWidth = canvas.width(primaryComponent);
        int titleWidth = Math.max(20, node.bounds().right() - node.bounds().x() - pad * 3 - primaryWidth);
        String secondary = "visible " + visible;
        String percentage = String.format(Locale.ROOT, "%.1f%% hidden", ratio * 100.0D);
        int secondaryY = top + Math.max(14, Math.round(18 * scale));
        Component percentageComponent = fitText(Component.literal(percentage), primaryMaxWidth);
        int percentageWidth = canvas.width(percentageComponent);
        int secondaryWidth = Math.max(20, node.bounds().right() - node.bounds().x() - pad * 3 - percentageWidth);
        if (isTextPass(pass)) {
            canvas.drawText(fitText(textOf(strProp(node, "title")), titleWidth), node.bounds().x() + pad, top, SUBTLE);
            canvas.drawText(primaryComponent, node.bounds().right() - primaryWidth - pad, top, TEXT);
            canvas.drawText(fitText(Component.literal(secondary), secondaryWidth), node.bounds().x() + pad, secondaryY, MUTED);
            canvas.drawText(percentageComponent, node.bounds().right() - percentageWidth - pad, secondaryY, SUBTLE);
        } else if (isGeometryPass(pass)) {
            UiRect bar = new UiRect(node.bounds().x() + pad, node.bounds().bottom() - Math.max(12, Math.round(14 * scale)),
                    node.bounds().width() - pad * 2, Math.max(5, Math.round(6 * scale)));
            canvas.fillRect(bar, 0xFF243241);
            int fillWidth = Math.max(0, Math.min(bar.width(), (int) Math.round(bar.width() * Mth.clamp((float) ratio, 0.0f, 1.0f))));
            canvas.fillRect(new UiRect(bar.x(), bar.y(), fillWidth, bar.height()), intProp(node, "accent", ACTIVE_DIM));
            drawDivider(canvas, node.bounds(), DIVIDER);
        }
    }

    private void renderMemoryDomainMetric(DashboardPrimitive node, UiCanvas canvas, UiPaintPass pass, boolean hovered) {
        DashboardMemoryRowLayout layout = node.props().get("memoryLayout") instanceof DashboardMemoryRowLayout value ? value : null;
        int pad = layout != null ? layout.padding() : 10;
        int topY = node.bounds().y() + (layout != null ? layout.titleY() : pad);
        int accent = intProp(node, "accent", ACTIVE_DIM);
        if (isGeometryPass(pass)) {
            canvas.fillRect(node.bounds(), hovered ? 0x7A243242 : ROW_ALT);
            canvas.fillRect(new UiRect(node.bounds().x(), node.bounds().y(), 3, node.bounds().height()), accent);
        }

        UiMeasuredTextBlock titleLayout = textBlockProp(node, "titleLayout");
        UiMeasuredTextBlock reservedLayout = textBlockProp(node, "reservedLayout");
        UiMeasuredTextBlock detailLayout = textBlockProp(node, "detailLayout");
        UiMeasuredTextBlock tailLayout = textBlockProp(node, "tailLayout");
        String reserved = strProp(node, "reserved");
        Component reservedComponent = fitText(Component.literal(reserved), Math.max(42, Math.round(node.bounds().width() * 0.30f)));
        int reservedWidth = canvas.width(reservedComponent);
        int rightX = node.bounds().right() - pad;
        int titleWidth = Math.max(20, rightX - node.bounds().x() - pad * 2 - reservedWidth);

        String livePeak = "live " + strProp(node, "live") + " / peak " + strProp(node, "peak");
        String budget = strProp(node, "budget");
        String fragmentation = strProp(node, "fragmentation");
        String tailText = "budget " + budget + " | fragmentation " + fragmentation;
        Component tailComponent = fitText(Component.literal(tailText), Math.max(36, Math.round(node.bounds().width() * 0.42f)));
        int tailWidth = canvas.width(tailComponent);
        int bottomY = node.bounds().y() + (layout != null ? layout.detailY() : topY + canvas.lineHeight() + 4);
        int secondaryWidth = Math.max(20, rightX - node.bounds().x() - pad * 2 - tailWidth);
        if (isTextPass(pass)) {
            drawMeasuredBlock(canvas, titleLayout, topY, node.bounds().x() + pad, titleWidth, TEXT);
            drawRightMeasuredBlock(canvas, reservedLayout, topY, rightX, Math.max(42, Math.round(node.bounds().width() * 0.30f)), TEXT);
            drawMeasuredBlock(canvas, detailLayout, bottomY, node.bounds().x() + pad, secondaryWidth, MUTED);
            drawRightMeasuredBlock(canvas, tailLayout, bottomY, rightX, Math.max(36, Math.round(node.bounds().width() * 0.42f)), SUBTLE);
            canvas.drawText(Component.literal(I18n.get("debug.dashboard.memory.label.usage") + " " + strProp(node, "usagePercent")),
                    node.bounds().x() + pad,
                    node.bounds().y() + (layout != null ? layout.usageLabelY() : bottomY + canvas.lineHeight() + 5),
                    SUBTLE);
            canvas.drawText(Component.literal(I18n.get("debug.dashboard.memory.label.peak") + " " + strProp(node, "peakPercent")),
                    node.bounds().x() + pad,
                    node.bounds().y() + (layout != null ? layout.peakLabelY() : bottomY + canvas.lineHeight() * 2 + 16),
                    SUBTLE);
        } else if (isGeometryPass(pass)) {
            int barHeight = layout != null ? layout.barHeight() : 6;
            int usageBarY = node.bounds().y() + (layout != null ? layout.usageBarY() : bottomY + canvas.lineHeight() + 10);
            int peakBarY = node.bounds().y() + (layout != null ? layout.peakBarY() : usageBarY + 22);
            UiRect usageBar = new UiRect(
                    node.bounds().x() + pad,
                    usageBarY,
                    Math.max(24, node.bounds().width() - pad * 2),
                    barHeight);
            canvas.fillRect(usageBar, 0xFF243241);
            double usageRatio = Mth.clamp((float) doubleProp(node, "usageRatio", 0.0D), 0.0f, 1.0f);
            int fillWidth = Math.max(0, Math.min(usageBar.width(), (int) Math.round(usageBar.width() * usageRatio)));
            if (fillWidth > 0) {
                canvas.fillRect(new UiRect(usageBar.x(), usageBar.y(), fillWidth, usageBar.height()), accent);
            }
            UiRect peakBar = new UiRect(
                    node.bounds().x() + pad,
                    peakBarY,
                    Math.max(24, node.bounds().width() - pad * 2),
                    barHeight);
            canvas.fillRect(peakBar, 0xFF243241);
            double peakRatio = Mth.clamp((float) doubleProp(node, "peakRatio", 0.0D), 0.0f, 1.0f);
            int peakFillWidth = Math.max(0, Math.min(peakBar.width(), (int) Math.round(peakBar.width() * peakRatio)));
            if (peakFillWidth > 0) {
                canvas.fillRect(new UiRect(peakBar.x(), peakBar.y(), peakFillWidth, peakBar.height()), 0xFF93C5FD);
            }
            drawDivider(canvas, node.bounds(), DIVIDER);
        }
    }

    private void renderCaptureStageMetric(DashboardPrimitive node, UiCanvas canvas, UiPaintPass pass, boolean hovered) {
        float scale = scale(node);
        int pad = Math.max(8, Math.round(10 * scale));
        if (isGeometryPass(pass)) {
            canvas.fillRect(node.bounds(), hovered ? 0x7A243242 : ROW_ALT);
            canvas.fillRect(new UiRect(node.bounds().x(), node.bounds().y(), 3, node.bounds().height()), intProp(node, "accent", ACTIVE_DIM));
            drawDivider(canvas, node.bounds(), DIVIDER);
            return;
        }
        if (!isTextPass(pass)) {
            return;
        }
        UiMeasuredTextBlock titleLayout = textBlockProp(node, "titleLayout");
        UiMeasuredTextBlock summaryLayout = textBlockProp(node, "summaryLayout");
        int titleY = node.bounds().y() + Math.max(6, Math.round(8 * scale));
        drawMeasuredBlock(canvas, titleLayout, titleY, node.bounds().x() + pad,
                Math.max(24, node.bounds().width() - pad * 2), SUBTLE);
        int summaryY = titleY + Math.max(canvas.lineHeight() + 4, titleLayout != null ? titleLayout.height() + Math.max(2, titleLayout.lineGap()) : 0);
        drawMeasuredBlock(canvas, summaryLayout, summaryY, node.bounds().x() + pad,
                Math.max(24, node.bounds().width() - pad * 2), TEXT);
    }

    private void renderChart(DashboardPrimitive node, UiCanvas canvas, UiPaintPass pass) {
        float scale = scale(node);
        int pad = Math.max(8, Math.round(10 * scale));
        if (isGeometryPass(pass)) {
            canvas.fillRect(node.bounds(), ROW_ALT);
            canvas.borderRect(node.bounds(), 0x66324150);
        } else if (!isTextPass(pass)) {
            return;
        }
        if (isTextPass(pass)) {
            canvas.drawText(textOf(strProp(node, "title")), node.bounds().x() + pad, node.bounds().y() + Math.max(5, Math.round(7 * scale)), TEXT);
        }
        @SuppressWarnings("unchecked")
        List<Double> bars = (List<Double>) node.props().get("bars");
        double threshold = doubleProp(node, "threshold", 33.0D);
        if (bars == null || bars.isEmpty()) {
            return;
        }
        if (!isGeometryPass(pass)) {
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

    private void renderMacroSectionHeader(DashboardPrimitive node, UiCanvas canvas, UiPaintPass pass, boolean hovered) {
        if (isGeometryPass(pass)) {
            if (hovered) {
                canvas.fillRect(node.bounds(), HOVER);
            }
            drawDivider(canvas, node.bounds(), DIVIDER);
            return;
        }
        if (!isTextPass(pass)) {
            return;
        }
        boolean expanded = boolProp(node, "expanded", false);
        int y = node.bounds().y() + Math.max(3, Math.round(5 * scale(node)));
        canvas.drawText(Component.literal(expanded ? "v" : ">"), node.bounds().x() + 2, y, MUTED);
        canvas.drawText(textOf(strProp(node, "title")), node.bounds().x() + Math.max(12, Math.round(14 * scale(node))), y, TEXT);
    }

    private void renderMacroConstant(DashboardPrimitive node, UiCanvas canvas, UiPaintPass pass, boolean hovered) {
        float scale = scale(node);
        int pad = Math.max(8, Math.round(10 * scale));
        if (isGeometryPass(pass) && hovered) {
            canvas.fillRect(node.bounds(), HOVER);
        }
        Component type = Component.literal(strProp(node, "type"));
        Component value = Component.literal(strProp(node, "value"));
        int rightBudget = Math.max(48, Math.round(node.bounds().width() * 0.42f));
        Component clippedType = fitText(type, Math.max(18, rightBudget / 3));
        Component clippedValue = fitText(value, Math.max(24, rightBudget - canvas.width(clippedType) - Math.max(8, Math.round(10 * scale))));
        int typeWidth = canvas.width(clippedType);
        int valueWidth = canvas.width(clippedValue);
        int right = node.bounds().right() - pad;
        int topY = node.bounds().y() + Math.max(4, Math.round(6 * scale));
        int bottomY = node.bounds().y() + Math.max(13, Math.round(14 * scale));
        int leftWidth = Math.max(20, right - pad - (valueWidth + typeWidth + Math.max(16, Math.round(18 * scale))));
        if (isTextPass(pass)) {
            canvas.drawText(fitText(Component.literal(strProp(node, "name")), leftWidth), node.bounds().x() + pad, topY, TEXT);
            canvas.drawText(fitText(Component.literal(strProp(node, "source")), leftWidth), node.bounds().x() + pad, bottomY, MUTED);
            canvas.drawText(clippedType, right - valueWidth - typeWidth - Math.max(16, Math.round(18 * scale)), topY, SUBTLE);
            canvas.drawText(clippedValue, right - valueWidth, topY, boolProp(node, "flag", false) ? ACTIVE : 0xFF93C5FD);
        } else if (isGeometryPass(pass)) {
            drawDivider(canvas, node.bounds(), DIVIDER);
        }
    }

    private void renderDiagnosticsHeader(DashboardPrimitive node, UiCanvas canvas, UiPaintPass pass, boolean hovered, DashboardPrimitive hoveredPrimitive,
                                         DashboardPanelId resizingPanelId, String resizingPanelEdge, String resizingSlotId, String resizingSlotEdge) {
        boolean unreadAlerts = boolProp(node, "unreadAlerts", false);
        int warningCount = intProp(node, "warningCount", 0);
        int errorCount = intProp(node, "errorCount", 0);
        int accent = unreadAlerts ? (errorCount > 0 ? ERROR : WARNING) : 0x7A37485F;
        if (isGeometryPass(pass)) {
            canvas.fillRect(node.bounds(), 0xC8192230);
            canvas.borderRect(node.bounds(), accent);
        }
        float scale = scale(node);
        int pad = Math.max(8, Math.round(10 * scale));
        int titleX = node.bounds().x() + pad;
        int titleY = node.bounds().y() + Math.max(7, Math.round(9 * scale));
        if (isGeometryPass(pass) && unreadAlerts) {
            canvas.fillRect(new UiRect(node.bounds().x(), node.bounds().y(), 3, node.bounds().height()), accent);
        }
        if (boolProp(node, "floating", false)) {
            if (isGeometryPass(pass)) {
                drawDragGrip(node.bounds(), canvas, hovered);
            }
            titleX += 14;
        }
        int badgeGap = intProp(node, "badgeGap", 6);
        int badgeX = intProp(node, "badgeLaneRight", node.bounds().right() - pad);
        int titleRight = intProp(node, "titleRight", badgeX - badgeGap);
        if (isTextPass(pass)) {
            if (unreadAlerts) {
                titleX += 4;
            }
            canvas.drawText(fitText(textOf(strProp(node, "title")), Math.max(16, titleRight - titleX)), titleX, titleY, unreadAlerts ? 0xFFFFFFFF : TEXT);
            if (errorCount > 0) {
                badgeX = drawAlertBadge(canvas, badgeX, node.bounds().y() + Math.max(5, Math.round(6 * scale)), "E " + errorCount, ERROR, badgeGap);
            }
            if (warningCount > 0) {
                badgeX = drawAlertBadge(canvas, badgeX, node.bounds().y() + Math.max(5, Math.round(6 * scale)), "W " + warningCount, WARNING, badgeGap);
            }
        } else if (isGeometryPass(pass)) {
            int dividerY = node.bounds().bottom() - 1;
            int dividerRight = Math.max(intProp(node, "actionLaneLeft", badgeX), badgeX);
            canvas.fillRect(new UiRect(node.bounds().x() + pad, dividerY, Math.max(1, dividerRight - node.bounds().x() - pad), 1), DIVIDER);
        }
    }

    private int drawAlertBadge(UiCanvas canvas, int rightX, int y, String text, int color, int gapAfter) {
        Component component = Component.literal(text);
        int width = canvas.width(component) + 8;
        UiRect badge = new UiRect(rightX - width, y, width, 12);
        canvas.fillRect(badge, 0x5A111827);
        canvas.borderRect(badge, color);
        canvas.drawCenteredText(component, badge.x() + badge.width() / 2, badge.y() + 2, color);
        return badge.x() - gapAfter;
    }

    private void renderDiagnosticsFilter(DashboardPrimitive node, UiCanvas canvas, UiPaintPass pass, boolean hovered) {
        boolean active = boolProp(node, "active", false);
        if (isGeometryPass(pass)) {
            canvas.fillRect(node.bounds(), active ? 0xA01E2E3E : (hovered ? 0xC2314458 : 0x70111A25));
            canvas.borderRect(node.bounds(), active ? ACTIVE : BORDER);
        } else if (isTextPass(pass)) {
            canvas.drawCenteredText(Component.literal(strProp(node, "level").toLowerCase(Locale.ROOT)),
                    node.bounds().x() + node.bounds().width() / 2, node.bounds().y() + Math.max(4, Math.round(5 * scale(node))), active ? 0xFFFFFFFF : SUBTLE);
        }
    }

    private void renderLogLine(DashboardPrimitive node, UiCanvas canvas, UiPaintPass pass, boolean hovered) {
        float scale = scale(node);
        if (isGeometryPass(pass) && hovered) {
            canvas.fillRect(node.bounds(), 0x301E2A38);
        }
        int pad = Math.max(4, Math.round(6 * scale));
        int x = node.bounds().x() + pad;
        int y = node.bounds().y() + Math.max(3, Math.round(5 * scale));
        boolean expanded = boolProp(node, "expanded", false);
        if (isTextPass(pass)) {
            canvas.drawText(Component.literal(expanded ? "v" : ">"), x, y, MUTED);
            x += Math.max(10, Math.round(12 * scale));
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
            String message = textMetrics.clipWithEllipsis(UiText.literal(strProp(node, "message")), available);
            canvas.drawText(Component.literal(message), x, y, TEXT);
            if (expanded) {
                UiMeasuredTextBlock messageLayout = textBlockProp(node, "messageLayout");
                UiMeasuredTextBlock detailLayout = textBlockProp(node, "detailLayout");
                int lineHeight = messageLayout != null ? messageLayout.lineHeight() : canvas.lineHeight();
                int lineGap = messageLayout != null ? messageLayout.lineGap() : Math.max(1, Math.round(2 * scale));
                int lineY = y + lineHeight + Math.max(2, Math.round(3 * scale));
                int bodyX = node.bounds().x() + pad + Math.max(10, Math.round(12 * scale));
                int bodyWidth = Math.max(20, node.bounds().width() - pad * 2 - Math.max(12, Math.round(14 * scale)));
                List<String> messageLines = messageLayout != null ? messageLayout.lines() : stringListProp(node, "messageLines");
                List<String> detailLines = detailLayout != null ? detailLayout.lines() : stringListProp(node, "detailLines");
                for (String line : messageLines) {
                    canvas.drawText(fitText(Component.literal(line), bodyWidth), bodyX, lineY, TEXT);
                    lineY += lineHeight + lineGap;
                }
                for (String line : detailLines) {
                    canvas.drawText(fitText(Component.literal(line), bodyWidth), bodyX, lineY, SUBTLE);
                    lineY += lineHeight + lineGap;
                }
            }
        }
        if (isGeometryPass(pass)) {
            drawDivider(canvas, node.bounds(), 0x252F3F52);
        }
    }

    private void renderLogCopyButton(DashboardPrimitive node, UiCanvas canvas, UiPaintPass pass, boolean hovered) {
        if (isGeometryPass(pass)) {
            canvas.fillRect(node.bounds(), hovered ? 0xC2314458 : 0x70111A25);
            canvas.borderRect(node.bounds(), hovered ? ACTIVE : BORDER);
        } else if (isTextPass(pass)) {
            canvas.drawCenteredText(fitText(textOf(strProp(node, "label")), Math.max(8, node.bounds().width() - 6)),
                    node.bounds().x() + node.bounds().width() / 2,
                    centeredTextY(node.bounds(), canvas),
                    hovered ? 0xFFFFFFFF : SUBTLE);
        }
    }

    private void renderTooltip(DashboardPrimitive node, UiCanvas canvas, double mouseX, double mouseY) {
        List<String> lines = tooltipLines(node, 220);
        if (lines.isEmpty()) {
            return;
        }
        int width = 0;
        for (String line : lines) {
            width = Math.max(width, canvas.width(Component.literal(line)));
        }
        int height = lines.size() * (canvas.lineHeight() + 2) + 8;
        int screenWidth = activeScreenBounds.width();
        int screenHeight = activeScreenBounds.height();
        int x = Math.min((int) Math.round(mouseX + 10), screenWidth - width - 16);
        int y = Math.min((int) Math.round(mouseY + 10), screenHeight - height - 16);
        UiRect rect = new UiRect(Math.max(6, x), Math.max(6, y), width + 10, height);
        canvas.fillRect(rect, 0xEE0A1016);
        canvas.borderRect(rect, BORDER);
        int drawY = rect.y() + 4;
        for (String line : lines) {
            canvas.drawText(Component.literal(line), rect.x() + 5, drawY, TEXT);
            drawY += canvas.lineHeight() + 2;
        }
    }

    private List<String> tooltipLines(DashboardPrimitive node, int width) {
        List<String> lines = new ArrayList<>();
        if (boolProp(node, "blocked", false)) {
            lines.addAll(wrapTooltip(formatBlockedPathTooltip(node), width));
        }
        String numericDetail = numericTooltip(node);
        if (!numericDetail.isEmpty()) {
            lines.add(numericDetail);
        }
        String detail = strProp(node, "detail");
        if (!detail.isEmpty()) {
            lines.addAll(wrapTooltip(detail, width));
        }
        return lines;
    }

    private void drawMeasuredBlock(UiCanvas canvas, UiMeasuredTextBlock layout, int startY, int startX, int width, int color) {
        if (layout == null) {
            return;
        }
        List<String> lines = layout.lines();
        for (int i = 0; i < lines.size(); i++) {
            canvas.drawText(fitText(Component.literal(lines.get(i)), width), startX, layout.lineY(startY, i), color);
        }
    }

    private void drawRightMeasuredBlock(UiCanvas canvas, UiMeasuredTextBlock layout, int startY, int rightX, int width, int color) {
        if (layout == null) {
            return;
        }
        List<String> lines = layout.lines();
        for (int i = 0; i < lines.size(); i++) {
            Component clipped = fitText(Component.literal(lines.get(i)), width);
            canvas.drawText(clipped, rightX - canvas.width(clipped), layout.lineY(startY, i), color);
        }
    }

    private List<String> wrapTooltip(String text, int width) {
        if (text.startsWith(ENABLE_PATH_PREFIX)) {
            return List.of(formatEnablePathTooltip(text));
        }
        List<String> lines = textMetrics.wrap(UiText.literal(textOf(text).getString()), width);
        return lines.isEmpty() ? List.of(text) : lines;
    }

    private String formatBlockedPathTooltip(DashboardPrimitive node) {
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

    private UiRect controlBounds(DashboardPrimitive node) {
        return DashboardControlLayout.controlBounds(node.bounds(), controlSpec(node), scale(node));
    }

    private UiRect choiceBounds(UiRect controlBounds, float scale) {
        return DashboardControlGeometry.choiceBounds(controlBounds, scale);
    }

    private UiRect alignRight(UiRect region, int width, int height, int insetRight) {
        int actualWidth = Math.min(width, region.width());
        int actualHeight = Math.min(height, region.height());
        int x = Math.max(region.x(), region.right() - actualWidth - insetRight);
        int y = region.y() + Math.max(0, (region.height() - actualHeight) / 2) - 1;
        return new UiRect(x, y, actualWidth, actualHeight);
    }

    private int centeredTextY(UiRect rect, UiCanvas canvas) {
        return rect.y() + Math.max(0, (rect.height() - canvas.lineHeight()) / 2);
    }

    private int drawDragGrip(UiRect bounds, UiCanvas canvas, boolean hovered) {
        int gripX = bounds.x() + 8;
        int gripY = bounds.y() + Math.max(6, (bounds.height() - 10) / 2);
        int color = hovered ? 0xFFCBD5E1 : 0xFF64748B;
        for (int row = 0; row < 3; row++) {
            int y = gripY + row * 3;
            canvas.fillRect(new UiRect(gripX, y, 2, 2), color);
            canvas.fillRect(new UiRect(gripX + 4, y, 2, 2), color);
        }
        return 14;
    }

    private String highlightedPanelEdges(DashboardPrimitive node, DashboardPrimitive hoveredPrimitive,
                                         DashboardPanelId resizingPanelId, String resizingPanelEdge) {
        DashboardPanelId panelId = DashboardPanelId.byId(strProp(node, "panelId"));
        if (panelId != null && panelId == resizingPanelId) {
            return normalizeEdgeString(resizingPanelEdge);
        }
        if (hoveredPrimitive == null || !"panel-resize-handle".equals(strProp(hoveredPrimitive, "role"))) {
            return "";
        }
        String nodePanelId = strProp(node, "panelId");
        if (nodePanelId.isEmpty() || !nodePanelId.equals(strProp(hoveredPrimitive, "panelId"))) {
            return "";
        }
        return normalizeEdgeString(strProp(hoveredPrimitive, "edge"));
    }

    private String highlightedSlotEdges(DashboardPrimitive node, DashboardPrimitive hoveredPrimitive,
                                        String resizingSlotId, String resizingSlotEdge) {
        String slotId = strProp(node, "slotId");
        if (!slotId.isEmpty() && slotId.equals(resizingSlotId)) {
            return normalizeEdgeString(resizingSlotEdge);
        }
        if (hoveredPrimitive == null || !"slot-resize-handle".equals(strProp(hoveredPrimitive, "role"))) {
            return "";
        }
        if (slotId.isEmpty() || !slotId.equals(strProp(hoveredPrimitive, "slotId"))) {
            return "";
        }
        return normalizeEdgeString(strProp(hoveredPrimitive, "edge"));
    }

    private String normalizeEdgeString(String edge) {
        String normalized = edge == null ? "" : edge.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "NORTH" -> "N";
            case "SOUTH" -> "S";
            case "WEST" -> "W";
            case "EAST" -> "E";
            case "NORTHEAST" -> "NE";
            case "NORTHWEST" -> "NW";
            case "SOUTHEAST" -> "SE";
            case "SOUTHWEST" -> "SW";
            default -> normalized;
        };
    }

    private void drawEdgeHighlights(UiCanvas canvas, UiRect bounds, String edges, int color) {
        int thickness = 2;
        if (edges.contains("N")) {
            canvas.fillRect(new UiRect(bounds.x(), bounds.y(), bounds.width(), thickness), color);
        }
        if (edges.contains("S")) {
            canvas.fillRect(new UiRect(bounds.x(), bounds.bottom() - thickness, bounds.width(), thickness), color);
        }
        if (edges.contains("W")) {
            canvas.fillRect(new UiRect(bounds.x(), bounds.y(), thickness, bounds.height()), color);
        }
        if (edges.contains("E")) {
            canvas.fillRect(new UiRect(bounds.right() - thickness, bounds.y(), thickness, bounds.height()), color);
        }
    }

    private Component fitText(Component text, int maxWidth) {
        if (maxWidth <= 0) {
            return Component.empty();
        }
        return Component.literal(textMetrics.clipWithEllipsis(UiText.literal(text.getString()), maxWidth));
    }

    private TreeRowLayout treeRowLayout(DashboardPrimitive node, UiCanvas canvas) {
        float scale = scale(node);
        UiRect row = node.bounds();
        UiRect control = controlBounds(node);
        int pad = Math.max(4, Math.round(6 * scale));
        int arrowWidth = boolProp(node, "expandable", false) ? Math.max(10, Math.round(12 * scale)) : 0;
        int titleX = row.x() + pad + arrowWidth;
        int titleRight = control.x() - Math.max(8, Math.round(10 * scale));
        int titleWidth = Math.max(0, titleRight - titleX);
        boolean compact = titleWidth < Math.round(92 * scale);
        int lineGap = Math.max(1, Math.round(2 * scale));
        int titleY;
        int summaryY;
        if (compact) {
            titleY = centeredTextY(row, canvas);
            summaryY = titleY;
        } else {
            int totalTextHeight = canvas.lineHeight() * 2 + lineGap;
            titleY = row.y() + Math.max(0, (row.height() - totalTextHeight) / 2);
            summaryY = titleY + canvas.lineHeight() + lineGap;
        }
        return new TreeRowLayout(scale, control, titleX, titleWidth, compact, titleY, summaryY);
    }

    private String numericTooltip(DashboardPrimitive node) {
        ControlSpec controlSpec = controlSpec(node);
        if (controlSpec == null || controlSpec.numericSpec() == null) {
            return "";
        }
        NumericSpec spec = controlSpec.numericSpec();
        return "Range: " + trimNumber(spec.minValue()) + " .. " + trimNumber(spec.maxValue())
                + " | Step: " + trimNumber(spec.step());
    }

    private String trimNumber(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.000001D) {
            return Long.toString(Math.round(value));
        }
        return String.format(Locale.ROOT, "%.3f", value)
                .replaceAll("0+$", "")
                .replaceAll("\\.$", "");
    }

    private boolean nodeContains(DashboardPrimitive node, double mouseX, double mouseY) {
        return node.bounds().contains(mouseX, mouseY) && (node.clipRect() == null || node.clipRect().contains(mouseX, mouseY));
    }

    private boolean isRenderable(UiPrimitive primitive, UiRect screenBounds) {
        UiRect bounds = primitiveBounds(primitive);
        if (bounds == null || bounds.width() <= 0 || bounds.height() <= 0) {
            return false;
        }
        if (!intersects(bounds, screenBounds)) {
            return false;
        }
        return primitive.clipRect() == null
                || (primitive.clipRect().width() > 0 && primitive.clipRect().height() > 0
                && intersects(bounds, primitive.clipRect())
                && intersects(primitive.clipRect(), screenBounds));
    }

    private UiRect primitiveBounds(UiPrimitive primitive) {
        if (primitive instanceof DashboardPrimitive dashboardPrimitive) {
            return dashboardPrimitive.bounds();
        }
        if (primitive instanceof TexturePrimitive texturePrimitive) {
            return texturePrimitive.rect();
        }
        return null;
    }

    private boolean intersects(UiRect a, UiRect b) {
        return a.right() > b.x() && b.right() > a.x() && a.bottom() > b.y() && b.bottom() > a.y();
    }

    private FloatingHoverScope floatingHoverScope(List<DashboardPrimitive> primitives, DashboardController controller, double mouseX, double mouseY) {
        for (int i = primitives.size() - 1; i >= 0; i--) {
            DashboardPrimitive primitive = primitives.get(i);
            if (!"panel".equals(strProp(primitive, "role")) || !boolProp(primitive, "floating", false)) {
                continue;
            }
            DashboardPanelId panelId = DashboardPanelId.byId(strProp(primitive, "panelId"));
            if (panelId == null || !controller.isFloating(panelId) || !primitive.bounds().contains(mouseX, mouseY)) {
                continue;
            }
            return new FloatingHoverScope(panelId, primitive.bounds());
        }
        return null;
    }

    private boolean allowsHover(DashboardPrimitive node, FloatingHoverScope floatingHoverScope) {
        if (floatingHoverScope == null) {
            return true;
        }
        DashboardPanelId nodePanelId = DashboardPanelId.byId(strProp(node, "panelId"));
        if (nodePanelId != null) {
            return nodePanelId == floatingHoverScope.panelId();
        }
        if (node.layer() != UiLayer.OVERLAY) {
            return false;
        }
        String role = strProp(node, "role");
        if ("panel-home-slot".equals(role) || "panel-slot-preview".equals(role) || "slot-resize-handle".equals(role)) {
            return false;
        }
        return floatingHoverScope.bounds().contains(node.bounds().x(), node.bounds().y())
                && floatingHoverScope.bounds().contains(node.bounds().right() - 1, node.bounds().bottom() - 1);
    }

    private record FloatingHoverScope(DashboardPanelId panelId, UiRect bounds) {
    }

    private ControlSpec controlSpec(DashboardPrimitive node) {
        Object value = node.props().get("controlSpec");
        return value instanceof ControlSpec controlSpec ? controlSpec : null;
    }

    private Component textOf(String keyOrLiteral) {
        if (keyOrLiteral == null || keyOrLiteral.isEmpty()) {
            return Component.empty();
        }
        return I18n.exists(keyOrLiteral) ? Component.translatable(keyOrLiteral) : Component.literal(keyOrLiteral);
    }

    private int intProp(DashboardPrimitive node, String key, int fallback) {
        Object value = node.props().get(key);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private double doubleProp(DashboardPrimitive node, String key, double fallback) {
        Object value = node.props().get(key);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private boolean boolProp(DashboardPrimitive node, String key, boolean fallback) {
        Object value = node.props().get(key);
        return value instanceof Boolean bool ? bool : fallback;
    }

    private String strProp(DashboardPrimitive node, String key) {
        Object value = node.props().get(key);
        return value != null ? String.valueOf(value) : "";
    }

    private List<String> stringListProp(DashboardPrimitive node, String key) {
        Object value = node.props().get(key);
        if (!(value instanceof List<?> raw)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object item : raw) {
            result.add(item != null ? String.valueOf(item) : "");
        }
        return result;
    }

    private UiMeasuredTextBlock textBlockProp(DashboardPrimitive node, String key) {
        Object value = node.props().get(key);
        return value instanceof UiMeasuredTextBlock block ? block : null;
    }

    private float scale(DashboardPrimitive node) {
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

    private record RenderStep(UiPrimitive primitive, UiPaintPass pass) {
    }

    public record ChoiceHitBox(String value, UiRect bounds) {
    }

    private record TreeRowLayout(float scale, UiRect controlBounds, int titleX, int titleWidth,
                                 boolean compact, int titleY, int summaryY) {
    }
}















