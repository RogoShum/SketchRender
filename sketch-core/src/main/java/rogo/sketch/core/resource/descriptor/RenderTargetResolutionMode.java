package rogo.sketch.core.resource.descriptor;

import java.util.Locale;

public enum RenderTargetResolutionMode {
    FIXED,
    SCREEN_SIZE,
    SCREEN_RELATIVE;

    public static RenderTargetResolutionMode fromString(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Resolution mode must not be blank");
        }
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "FIXED" -> FIXED;
            case "SCREEN_SIZE", "SCREEN" -> SCREEN_SIZE;
            case "SCREEN_RELATIVE", "RELATIVE" -> SCREEN_RELATIVE;
            default -> throw new IllegalArgumentException("Unsupported render target resolution mode: " + value);
        };
    }
}

