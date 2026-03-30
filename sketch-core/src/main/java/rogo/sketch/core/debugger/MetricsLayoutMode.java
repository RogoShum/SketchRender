package rogo.sketch.core.debugger;

public enum MetricsLayoutMode {
    AUTO,
    ONE,
    TWO,
    THREE;

    public MetricsLayoutMode next() {
        return switch (this) {
            case AUTO -> ONE;
            case ONE -> TWO;
            case TWO -> THREE;
            case THREE -> AUTO;
        };
    }

    public int resolveColumns(int autoColumns) {
        return switch (this) {
            case AUTO -> Math.max(1, Math.min(3, autoColumns));
            case ONE -> 1;
            case TWO -> 2;
            case THREE -> 3;
        };
    }
}
