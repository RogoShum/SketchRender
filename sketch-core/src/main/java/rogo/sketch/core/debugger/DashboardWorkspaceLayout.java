package rogo.sketch.core.debugger;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.ui.geometry.UiRect;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record DashboardWorkspaceLayout(
        String workspaceId,
        UiRect shellBounds,
        Map<DashboardDockSlotId, DashboardDockSlotSpec> slotSpecs,
        Map<DashboardDockSlotId, UiRect> slotBounds,
        List<DashboardDockResizeHandle> resizeHandles
) {
    public DashboardWorkspaceLayout {
        if (workspaceId == null || workspaceId.isBlank()) {
            throw new IllegalArgumentException("workspaceId must not be blank");
        }
        if (shellBounds == null) {
            throw new IllegalArgumentException("shellBounds must not be null");
        }
        slotSpecs = Collections.unmodifiableMap(new LinkedHashMap<>(slotSpecs));
        slotBounds = Collections.unmodifiableMap(new LinkedHashMap<>(slotBounds));
        resizeHandles = List.copyOf(resizeHandles);
    }

    public @Nullable UiRect slotBounds(DashboardDockSlotId slotId) {
        return slotId != null ? slotBounds.get(slotId) : null;
    }

    public @Nullable DashboardDockSlotSpec slotSpec(DashboardDockSlotId slotId) {
        return slotId != null ? slotSpecs.get(slotId) : null;
    }

    public @Nullable DashboardDockResizeHandle resizeHandle(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        for (DashboardDockResizeHandle handle : resizeHandles) {
            if (handle.id().equals(id)) {
                return handle;
            }
        }
        return null;
    }
}
