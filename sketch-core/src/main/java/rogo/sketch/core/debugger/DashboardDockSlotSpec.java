package rogo.sketch.core.debugger;

public record DashboardDockSlotSpec(
        DashboardDockSlotId slotId,
        DashboardDockSlotRole role,
        String displayNameKey
) {
    public DashboardDockSlotSpec {
        if (slotId == null) {
            throw new IllegalArgumentException("slotId must not be null");
        }
        if (role == null) {
            throw new IllegalArgumentException("role must not be null");
        }
        if (displayNameKey == null) {
            displayNameKey = "";
        }
    }
}
