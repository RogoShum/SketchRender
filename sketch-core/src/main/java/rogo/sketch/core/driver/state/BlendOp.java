package rogo.sketch.core.driver.state;

public enum BlendOp {
    ADD,
    SUBTRACT,
    REVERSE_SUBTRACT,
    MIN,
    MAX;

    public static BlendOp parse(String value) {
        if (value == null || value.isBlank()) {
            return ADD;
        }
        return switch (value.toUpperCase()) {
            case "ADD" -> ADD;
            case "SUBTRACT" -> SUBTRACT;
            case "REVERSE_SUBTRACT" -> REVERSE_SUBTRACT;
            case "MIN" -> MIN;
            case "MAX" -> MAX;
            default -> ADD;
        };
    }
}

