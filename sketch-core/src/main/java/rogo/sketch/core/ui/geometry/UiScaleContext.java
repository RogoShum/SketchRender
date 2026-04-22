package rogo.sketch.core.ui.geometry;

public record UiScaleContext(
        float rootScale,
        UiRect physicalViewport,
        UiRect logicalViewport
) {
    public UiScaleContext {
        rootScale = sanitizeScale(rootScale);
        physicalViewport = physicalViewport != null ? physicalViewport : new UiRect(0, 0, 1, 1);
        logicalViewport = logicalViewport != null ? logicalViewport : logicalViewport(rootScale, physicalViewport);
    }

    public static UiScaleContext of(float rootScale, int physicalWidth, int physicalHeight) {
        UiRect physicalViewport = new UiRect(0, 0, Math.max(1, physicalWidth), Math.max(1, physicalHeight));
        return new UiScaleContext(rootScale, physicalViewport, logicalViewport(rootScale, physicalViewport));
    }

    public double toLogicalX(double physicalX) {
        return physicalX / rootScale;
    }

    public double toLogicalY(double physicalY) {
        return physicalY / rootScale;
    }

    public int toLogicalX(int physicalX) {
        return (int) Math.round(toLogicalX(physicalX));
    }

    public int toLogicalY(int physicalY) {
        return (int) Math.round(toLogicalY(physicalY));
    }

    public UiRect toPhysicalRect(UiRect logicalRect) {
        if (logicalRect == null) {
            return new UiRect(0, 0, 0, 0);
        }
        int x = scaleCoordinate(logicalRect.x());
        int y = scaleCoordinate(logicalRect.y());
        int width = Math.max(0, scaleLength(logicalRect.width()));
        int height = Math.max(0, scaleLength(logicalRect.height()));
        return new UiRect(x, y, width, height);
    }

    public int logicalUnitsForPhysicalPx(int physicalPx) {
        if (physicalPx <= 0) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(physicalPx / (double) rootScale));
    }

    public int scaleCoordinate(int logicalCoordinate) {
        return Math.round(logicalCoordinate * rootScale);
    }

    public int scaleLength(int logicalLength) {
        return Math.max(0, Math.round(logicalLength * rootScale));
    }

    private static float sanitizeScale(float scale) {
        return Math.abs(scale) < 0.000001f ? 1.0f : scale;
    }

    private static UiRect logicalViewport(float rootScale, UiRect physicalViewport) {
        int width = Math.max(1, (int) Math.floor(physicalViewport.width() / sanitizeScale(rootScale)));
        int height = Math.max(1, (int) Math.floor(physicalViewport.height() / sanitizeScale(rootScale)));
        return new UiRect(0, 0, width, height);
    }
}
