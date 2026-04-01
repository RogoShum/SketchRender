package rogo.sketch.core.debugger;

import rogo.sketch.core.ui.geometry.UiRect;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DashboardWorkspaceProfiles {
    public static final DashboardDockSlotId LEFT_SIDEBAR = DashboardDockSlotId.of("left_sidebar");
    public static final DashboardDockSlotId RIGHT_SIDEBAR = DashboardDockSlotId.of("right_sidebar");
    public static final DashboardDockSlotId BOTTOM_OUTPUT = DashboardDockSlotId.of("bottom_output");
    public static final DashboardDockSlotId INSPECTOR_LEFT = DashboardDockSlotId.of("inspector_left");
    public static final DashboardDockSlotId MAIN_VIEWPORT = DashboardDockSlotId.of("main_viewport");
    public static final DashboardDockSlotId TOOL_RIGHT = DashboardDockSlotId.of("tool_right");

    private static final DashboardWorkspaceProfile DASHBOARD_DEFAULT = new DashboardWorkspaceProfile() {
        private final List<DashboardDockSlotSpec> slots = List.of(
                new DashboardDockSlotSpec(LEFT_SIDEBAR, DashboardDockSlotRole.LEFT_SIDEBAR, "debug.dashboard.slot.left_sidebar"),
                new DashboardDockSlotSpec(RIGHT_SIDEBAR, DashboardDockSlotRole.RIGHT_SIDEBAR, "debug.dashboard.slot.right_sidebar"),
                new DashboardDockSlotSpec(BOTTOM_OUTPUT, DashboardDockSlotRole.BOTTOM_OUTPUT, "debug.dashboard.slot.bottom_output"));

        @Override
        public String workspaceId() {
            return "dashboard_default";
        }

        @Override
        public List<DashboardDockSlotSpec> slots() {
            return slots;
        }

        @Override
        public DashboardWorkspaceLayout layout(int screenWidth, int screenHeight, float uiScaleInput, DashboardController controller) {
            WorkspaceMetrics metrics = new WorkspaceMetrics(uiScaleInput, screenWidth, screenHeight);
            UiRect shell = new UiRect(metrics.shellInset, metrics.shellInset,
                    Math.max(1, screenWidth - metrics.shellInset * 2),
                    Math.max(1, screenHeight - metrics.shellInset * 2));
            double minLeftRatio = metrics.minPanelWidth / (double) shell.width();
            double maxLeftRatio = Math.max(minLeftRatio, (shell.width() - metrics.minPanelWidth - metrics.columnGap) / (double) shell.width());
            double leftRatio = resolveRatio(controller, LEFT_SIDEBAR, metrics.leftRatio, minLeftRatio, maxLeftRatio);
            int leftWidth = clamp(Math.round(shell.width() * (float) leftRatio), metrics.minPanelWidth,
                    shell.width() - metrics.minPanelWidth - metrics.columnGap);
            int rightWidth = Math.max(metrics.minPanelWidth, shell.width() - leftWidth - metrics.columnGap);

            double minBottomRatio = metrics.minDiagnosticsHeight / (double) shell.height();
            double defaultBottomRatio = clamp(metrics.diagnosticsDockedHeight / (double) shell.height(), minBottomRatio,
                    Math.max(minBottomRatio, (shell.height() - metrics.minPanelHeight - metrics.panelGap) / (double) shell.height()));
            double maxBottomRatio = Math.max(minBottomRatio, (shell.height() - metrics.minPanelHeight - metrics.panelGap) / (double) shell.height());
            double bottomRatio = resolveRatio(controller, BOTTOM_OUTPUT, defaultBottomRatio, minBottomRatio, maxBottomRatio);
            int diagnosticsHeight = clamp(Math.round(shell.height() * (float) bottomRatio), metrics.minDiagnosticsHeight,
                    shell.height() - metrics.minPanelHeight - metrics.panelGap);
            int topHeight = Math.max(metrics.minPanelHeight, shell.height() - diagnosticsHeight - metrics.panelGap);

            Map<DashboardDockSlotId, DashboardDockSlotSpec> slotSpecs = new LinkedHashMap<>();
            for (DashboardDockSlotSpec slot : slots) {
                slotSpecs.put(slot.slotId(), slot);
            }

            Map<DashboardDockSlotId, UiRect> slotBounds = new LinkedHashMap<>();
            UiRect leftSlot = new UiRect(shell.x(), shell.y(), leftWidth, topHeight);
            UiRect rightSlot = new UiRect(leftSlot.right() + metrics.columnGap, shell.y(), rightWidth, topHeight);
            UiRect bottomSlot = new UiRect(shell.x(), leftSlot.bottom() + metrics.panelGap, shell.width(), diagnosticsHeight);
            slotBounds.put(LEFT_SIDEBAR, leftSlot);
            slotBounds.put(RIGHT_SIDEBAR, rightSlot);
            slotBounds.put(BOTTOM_OUTPUT, bottomSlot);

            List<DashboardDockResizeHandle> resizeHandles = List.of(
                    verticalResizeHandle("slot-resize/" + LEFT_SIDEBAR.value() + "/E", LEFT_SIDEBAR, shell, leftSlot.right(), shell.y(), topHeight,
                            metrics.columnGap, minLeftRatio, maxLeftRatio),
                    horizontalResizeHandle("slot-resize/" + BOTTOM_OUTPUT.value() + "/N", BOTTOM_OUTPUT, shell, bottomSlot.y(), shell.width(),
                            metrics.panelGap, minBottomRatio, maxBottomRatio));
            return new DashboardWorkspaceLayout(workspaceId(), shell, slotSpecs, slotBounds, resizeHandles);
        }

        @Override
        public DashboardDockSlotId defaultHomeSlot(DashboardPanelId panelId) {
            return switch (panelId) {
                case SETTINGS -> LEFT_SIDEBAR;
                case METRICS -> RIGHT_SIDEBAR;
                case DIAGNOSTICS -> BOTTOM_OUTPUT;
            };
        }
    };

    private static final DashboardWorkspaceProfile VIEWPORT_INSPECTOR = new DashboardWorkspaceProfile() {
        private final List<DashboardDockSlotSpec> slots = List.of(
                new DashboardDockSlotSpec(INSPECTOR_LEFT, DashboardDockSlotRole.LEFT_SIDEBAR, "debug.dashboard.slot.inspector_left"),
                new DashboardDockSlotSpec(MAIN_VIEWPORT, DashboardDockSlotRole.MAIN_VIEWPORT, "debug.dashboard.slot.main_viewport"),
                new DashboardDockSlotSpec(TOOL_RIGHT, DashboardDockSlotRole.RIGHT_SIDEBAR, "debug.dashboard.slot.tool_right"),
                new DashboardDockSlotSpec(BOTTOM_OUTPUT, DashboardDockSlotRole.BOTTOM_OUTPUT, "debug.dashboard.slot.bottom_output"));

        @Override
        public String workspaceId() {
            return "viewport_inspector";
        }

        @Override
        public List<DashboardDockSlotSpec> slots() {
            return slots;
        }

        @Override
        public DashboardWorkspaceLayout layout(int screenWidth, int screenHeight, float uiScaleInput, DashboardController controller) {
            WorkspaceMetrics metrics = new WorkspaceMetrics(uiScaleInput, screenWidth, screenHeight);
            UiRect shell = new UiRect(metrics.shellInset, metrics.shellInset,
                    Math.max(1, screenWidth - metrics.shellInset * 2),
                    Math.max(1, screenHeight - metrics.shellInset * 2));
            double minBottomRatio = metrics.minDiagnosticsHeight / (double) shell.height();
            double defaultBottomRatio = clamp(metrics.diagnosticsDockedHeight / (double) shell.height(), minBottomRatio,
                    Math.max(minBottomRatio, (shell.height() - metrics.minPanelHeight - metrics.panelGap) / (double) shell.height()));
            double maxBottomRatio = Math.max(minBottomRatio, (shell.height() - metrics.minPanelHeight - metrics.panelGap) / (double) shell.height());
            double bottomRatio = resolveRatio(controller, BOTTOM_OUTPUT, defaultBottomRatio, minBottomRatio, maxBottomRatio);
            int bottomHeight = clamp(Math.round(shell.height() * (float) bottomRatio), metrics.minDiagnosticsHeight,
                    shell.height() - metrics.minPanelHeight - metrics.panelGap);
            int topHeight = Math.max(metrics.minPanelHeight, shell.height() - bottomHeight - metrics.panelGap);

            double minLeftRatio = metrics.minPanelWidth / (double) shell.width();
            double minRightRatio = metrics.minPanelWidth / (double) shell.width();
            double leftRatio = resolveRatio(controller, INSPECTOR_LEFT, 0.24D, minLeftRatio, 0.45D);
            double rightRatio = resolveRatio(controller, TOOL_RIGHT, 0.22D, minRightRatio, 0.38D);

            int leftWidth = Math.max(metrics.minPanelWidth, Math.round(shell.width() * (float) leftRatio));
            int rightWidth = Math.max(metrics.minPanelWidth, Math.round(shell.width() * (float) rightRatio));
            int centerWidth = shell.width() - leftWidth - rightWidth - metrics.columnGap * 2;
            if (centerWidth < metrics.minPanelWidth) {
                int overflow = metrics.minPanelWidth - centerWidth;
                int rightShrink = Math.min(overflow, rightWidth - metrics.minPanelWidth);
                rightWidth -= rightShrink;
                overflow -= rightShrink;
                if (overflow > 0) {
                    leftWidth = Math.max(metrics.minPanelWidth, leftWidth - overflow);
                }
                centerWidth = Math.max(metrics.minPanelWidth, shell.width() - leftWidth - rightWidth - metrics.columnGap * 2);
            }

            Map<DashboardDockSlotId, DashboardDockSlotSpec> slotSpecs = new LinkedHashMap<>();
            for (DashboardDockSlotSpec slot : slots) {
                slotSpecs.put(slot.slotId(), slot);
            }

            Map<DashboardDockSlotId, UiRect> slotBounds = new LinkedHashMap<>();
            UiRect leftSlot = new UiRect(shell.x(), shell.y(), leftWidth, topHeight);
            UiRect viewportSlot = new UiRect(leftSlot.right() + metrics.columnGap, shell.y(), centerWidth, topHeight);
            UiRect rightSlot = new UiRect(viewportSlot.right() + metrics.columnGap, shell.y(), rightWidth, topHeight);
            UiRect bottomSlot = new UiRect(shell.x(), leftSlot.bottom() + metrics.panelGap, shell.width(), bottomHeight);
            slotBounds.put(INSPECTOR_LEFT, leftSlot);
            slotBounds.put(MAIN_VIEWPORT, viewportSlot);
            slotBounds.put(TOOL_RIGHT, rightSlot);
            slotBounds.put(BOTTOM_OUTPUT, bottomSlot);

            double maxLeftRatio = Math.max(minLeftRatio, (shell.width() - rightWidth - metrics.minPanelWidth - metrics.columnGap * 2) / (double) shell.width());
            double maxRightRatio = Math.max(minRightRatio, (shell.width() - leftWidth - metrics.minPanelWidth - metrics.columnGap * 2) / (double) shell.width());
            List<DashboardDockResizeHandle> resizeHandles = List.of(
                    verticalResizeHandle("slot-resize/" + INSPECTOR_LEFT.value() + "/E", INSPECTOR_LEFT, shell, leftSlot.right(), shell.y(), topHeight,
                            metrics.columnGap, minLeftRatio, maxLeftRatio),
                    verticalResizeHandle("slot-resize/" + TOOL_RIGHT.value() + "/W", TOOL_RIGHT, shell, rightSlot.x(), shell.y(), topHeight,
                            metrics.columnGap, minRightRatio, maxRightRatio),
                    horizontalResizeHandle("slot-resize/" + BOTTOM_OUTPUT.value() + "/N", BOTTOM_OUTPUT, shell, bottomSlot.y(), shell.width(),
                            metrics.panelGap, minBottomRatio, maxBottomRatio));
            return new DashboardWorkspaceLayout(workspaceId(), shell, slotSpecs, slotBounds, resizeHandles);
        }

        @Override
        public DashboardDockSlotId defaultHomeSlot(DashboardPanelId panelId) {
            return switch (panelId) {
                case SETTINGS -> INSPECTOR_LEFT;
                case METRICS -> TOOL_RIGHT;
                case DIAGNOSTICS -> BOTTOM_OUTPUT;
            };
        }
    };

    private DashboardWorkspaceProfiles() {
    }

    public static DashboardWorkspaceProfile dashboardDefault() {
        return DASHBOARD_DEFAULT;
    }

    public static DashboardWorkspaceProfile viewportInspector() {
        return VIEWPORT_INSPECTOR;
    }

    private static DashboardDockResizeHandle verticalResizeHandle(String id, DashboardDockSlotId slotId, UiRect shell, int boundaryX, int y,
                                                                  int height, int gap, double minRatio, double maxRatio) {
        int thickness = Math.max(6, gap);
        int x = boundaryX - thickness / 2;
        return new DashboardDockResizeHandle(id, slotId, slotId == TOOL_RIGHT ? DashboardDockResizeEdge.WEST : DashboardDockResizeEdge.EAST,
                new UiRect(x, y, thickness, Math.max(1, height)), minRatio, maxRatio);
    }

    private static DashboardDockResizeHandle horizontalResizeHandle(String id, DashboardDockSlotId slotId, UiRect shell, int boundaryY, int width,
                                                                    int gap, double minRatio, double maxRatio) {
        int thickness = Math.max(6, gap);
        int y = boundaryY - thickness / 2;
        return new DashboardDockResizeHandle(id, slotId, DashboardDockResizeEdge.NORTH,
                new UiRect(shell.x(), y, Math.max(1, width), thickness), minRatio, maxRatio);
    }

    private static double resolveRatio(DashboardController controller, DashboardDockSlotId slotId, double fallback, double min, double max) {
        return clamp(controller != null ? controller.slotSizeRatio(slotId, fallback) : fallback, min, max);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(value, max));
    }

    private static final class WorkspaceMetrics {
        private final float uiScale;
        private final float leftRatio;
        private final int shellInset;
        private final int columnGap;
        private final int panelGap;
        private final int minPanelWidth;
        private final int minPanelHeight;
        private final int minDiagnosticsHeight;
        private final int diagnosticsDockedHeight;

        private WorkspaceMetrics(float inputScale, int screenWidth, int screenHeight) {
            this.uiScale = Math.max(0.70f, inputScale * 0.90f);
            this.leftRatio = this.uiScale <= 0.80f ? 0.37f : this.uiScale <= 0.94f ? 0.41f : this.uiScale <= 1.08f ? 0.45f : 0.49f;
            this.shellInset = Math.max(scaled(14), Math.min(screenWidth, screenHeight) / 40);
            this.columnGap = scaled(10);
            this.panelGap = scaled(10);
            this.minPanelWidth = scaled(180);
            this.minPanelHeight = scaled(160);
            this.minDiagnosticsHeight = scaled(140);
            this.diagnosticsDockedHeight = Math.max(scaled(170), Math.min(screenHeight / 3, scaled(220)));
        }

        private int scaled(int base) {
            return Math.max(1, Math.round(base * uiScale));
        }
    }
}
