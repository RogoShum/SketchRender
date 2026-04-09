package rogo.sketch.core.driver.state;

public enum BlendFactor {
    ZERO,
    ONE,
    SRC_COLOR,
    ONE_MINUS_SRC_COLOR,
    DST_COLOR,
    ONE_MINUS_DST_COLOR,
    SRC_ALPHA,
    ONE_MINUS_SRC_ALPHA,
    DST_ALPHA,
    ONE_MINUS_DST_ALPHA,
    SRC_ALPHA_SATURATE;

    public static BlendFactor parse(String factor) {
        if (factor == null || factor.isBlank()) {
            return SRC_ALPHA;
        }
        return switch (factor.toUpperCase()) {
            case "ZERO" -> ZERO;
            case "ONE" -> ONE;
            case "SRC_COLOR" -> SRC_COLOR;
            case "ONE_MINUS_SRC_COLOR" -> ONE_MINUS_SRC_COLOR;
            case "DST_COLOR" -> DST_COLOR;
            case "ONE_MINUS_DST_COLOR" -> ONE_MINUS_DST_COLOR;
            case "SRC_ALPHA" -> SRC_ALPHA;
            case "ONE_MINUS_SRC_ALPHA" -> ONE_MINUS_SRC_ALPHA;
            case "DST_ALPHA" -> DST_ALPHA;
            case "ONE_MINUS_DST_ALPHA" -> ONE_MINUS_DST_ALPHA;
            case "SRC_ALPHA_SATURATE" -> SRC_ALPHA_SATURATE;
            default -> SRC_ALPHA;
        };
    }
}

