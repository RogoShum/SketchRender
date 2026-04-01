package rogo.sketch.core.debugger;

import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface DashboardWorkspaceProfile {
    String workspaceId();

    List<DashboardDockSlotSpec> slots();

    DashboardWorkspaceLayout layout(int screenWidth, int screenHeight, float uiScale, DashboardController controller);

    @Nullable DashboardDockSlotId defaultHomeSlot(DashboardPanelId panelId);
}
