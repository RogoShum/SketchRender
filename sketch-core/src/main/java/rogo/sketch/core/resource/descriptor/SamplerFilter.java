package rogo.sketch.core.resource.descriptor;

import java.util.Locale;

public enum SamplerFilter {
    NEAREST,
    LINEAR,
    NEAREST_MIPMAP_NEAREST,
    LINEAR_MIPMAP_NEAREST,
    NEAREST_MIPMAP_LINEAR,
    LINEAR_MIPMAP_LINEAR;

    public static SamplerFilter fromString(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Sampler filter must not be blank");
        }
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "NEAREST" -> NEAREST;
            case "LINEAR" -> LINEAR;
            case "NEAREST_MIPMAP_NEAREST" -> NEAREST_MIPMAP_NEAREST;
            case "LINEAR_MIPMAP_NEAREST" -> LINEAR_MIPMAP_NEAREST;
            case "NEAREST_MIPMAP_LINEAR" -> NEAREST_MIPMAP_LINEAR;
            case "LINEAR_MIPMAP_LINEAR" -> LINEAR_MIPMAP_LINEAR;
            default -> throw new IllegalArgumentException("Unsupported sampler filter: " + value);
        };
    }

    public boolean usesMipmaps() {
        return switch (this) {
            case NEAREST_MIPMAP_NEAREST, LINEAR_MIPMAP_NEAREST, NEAREST_MIPMAP_LINEAR, LINEAR_MIPMAP_LINEAR -> true;
            default -> false;
        };
    }
}

