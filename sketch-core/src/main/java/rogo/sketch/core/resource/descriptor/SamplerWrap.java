package rogo.sketch.core.resource.descriptor;

import java.util.Locale;

public enum SamplerWrap {
    REPEAT,
    CLAMP_TO_EDGE,
    CLAMP_TO_BORDER,
    MIRRORED_REPEAT;

    public static SamplerWrap fromString(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Sampler wrap must not be blank");
        }
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "REPEAT" -> REPEAT;
            case "CLAMP", "CLAMP_TO_EDGE" -> CLAMP_TO_EDGE;
            case "CLAMP_TO_BORDER" -> CLAMP_TO_BORDER;
            case "MIRRORED_REPEAT" -> MIRRORED_REPEAT;
            default -> throw new IllegalArgumentException("Unsupported sampler wrap: " + value);
        };
    }
}

