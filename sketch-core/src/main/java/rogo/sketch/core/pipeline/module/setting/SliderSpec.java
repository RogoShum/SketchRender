package rogo.sketch.core.pipeline.module.setting;

/**
 * UI hint for numeric settings that can be rendered as sliders.
 */
public record SliderSpec(
        double min,
        double max,
        double step
) {
    public static SliderSpec of(double min, double max, double step) {
        return new SliderSpec(min, max, step);
    }
}
