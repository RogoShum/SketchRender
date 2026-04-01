package rogo.sketch.core.debugger;

import rogo.sketch.core.ui.geometry.UiRect;

public final class DashboardPanelState {
    private final DashboardPanelId panelId;
    private DashboardPanelMode mode = DashboardPanelMode.DOCKED;
    private DashboardDockSlotId homeSlotId;
    private DashboardDockSlotId dockedSlotId;
    private UiRect floatingBounds = new UiRect(0, 0, 0, 0);

    public DashboardPanelState(DashboardPanelId panelId) {
        this.panelId = panelId;
    }

    public DashboardPanelId panelId() {
        return panelId;
    }

    public DashboardPanelMode mode() {
        return mode;
    }

    public void setMode(DashboardPanelMode mode) {
        this.mode = mode != null ? mode : DashboardPanelMode.DOCKED;
    }

    public DashboardDockSlotId homeSlotId() {
        return homeSlotId;
    }

    public void setHomeSlotId(DashboardDockSlotId homeSlotId) {
        this.homeSlotId = homeSlotId;
    }

    public DashboardDockSlotId dockedSlotId() {
        return dockedSlotId;
    }

    public void setDockedSlotId(DashboardDockSlotId dockedSlotId) {
        this.dockedSlotId = dockedSlotId;
    }

    public UiRect floatingBounds() {
        return floatingBounds;
    }

    public void setFloatingBounds(UiRect floatingBounds) {
        if (floatingBounds == null) {
            return;
        }
        this.floatingBounds = new UiRect(
                floatingBounds.x(),
                floatingBounds.y(),
                Math.max(1, floatingBounds.width()),
                Math.max(1, floatingBounds.height()));
    }
}
