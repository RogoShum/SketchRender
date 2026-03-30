package rogo.sketch.core.ui.control;

public record NumericSpec(
        NumericKind numericKind,
        double minValue,
        double maxValue,
        double step,
        String formatPattern
) {
    public static NumericSpec integer(int minValue, int maxValue, int step, String formatPattern) {
        return new NumericSpec(NumericKind.INTEGER, minValue, maxValue, step, formatPattern);
    }

    public static NumericSpec floating(double minValue, double maxValue, double step, String formatPattern) {
        return new NumericSpec(NumericKind.FLOAT, minValue, maxValue, step, formatPattern);
    }
}
