package rogo.sketch.core.driver.state;

public enum CompareOp {
    NEVER,
    LESS,
    EQUAL,
    LEQUAL,
    GREATER,
    NOTEQUAL,
    GEQUAL,
    ALWAYS;

    public static CompareOp parse(String value) {
        if (value == null || value.isBlank()) {
            return LESS;
        }
        return switch (value.toUpperCase()) {
            case "NEVER" -> NEVER;
            case "LESS" -> LESS;
            case "EQUAL" -> EQUAL;
            case "LEQUAL" -> LEQUAL;
            case "GREATER" -> GREATER;
            case "NOTEQUAL" -> NOTEQUAL;
            case "GEQUAL" -> GEQUAL;
            case "ALWAYS" -> ALWAYS;
            default -> LESS;
        };
    }
}

