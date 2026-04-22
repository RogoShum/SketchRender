package rogo.sketch.core.ui.frame;

public record UiInteractionSurface(
        UiInteractionSurfaceKind kind,
        String ownerId,
        int sortOrder
) {
    private static final UiInteractionSurface DEFAULT_CONTENT = new UiInteractionSurface(UiInteractionSurfaceKind.CONTENT, "", 0);

    public UiInteractionSurface {
        kind = kind != null ? kind : UiInteractionSurfaceKind.CONTENT;
        ownerId = ownerId != null ? ownerId : "";
    }

    public static UiInteractionSurface content() {
        return DEFAULT_CONTENT;
    }

    public static UiInteractionSurface content(String ownerId) {
        return ownerId == null || ownerId.isBlank()
                ? DEFAULT_CONTENT
                : new UiInteractionSurface(UiInteractionSurfaceKind.CONTENT, ownerId, 0);
    }

    public static UiInteractionSurface floatingPanel(String ownerId, int sortOrder) {
        return new UiInteractionSurface(UiInteractionSurfaceKind.FLOATING_PANEL, ownerId, sortOrder);
    }

    public static UiInteractionSurface popup(String ownerId, int sortOrder) {
        return new UiInteractionSurface(UiInteractionSurfaceKind.POPUP, ownerId, sortOrder);
    }

    public static UiInteractionSurface tooltip(String ownerId, int sortOrder) {
        return new UiInteractionSurface(UiInteractionSurfaceKind.TOOLTIP, ownerId, sortOrder);
    }
}
