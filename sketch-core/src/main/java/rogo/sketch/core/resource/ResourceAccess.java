package rogo.sketch.core.resource;

import java.util.Locale;

public enum ResourceAccess {
    READ,
    WRITE,
    READ_WRITE;

    public static ResourceAccess parse(String value, ResourceAccess fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return switch (value.trim().toUpperCase(Locale.ROOT).replace('-', '_')) {
            case "READ" -> READ;
            case "WRITE" -> WRITE;
            case "READ_WRITE", "READWRITE", "READ+WRITE" -> READ_WRITE;
            default -> throw new IllegalArgumentException("Unsupported resource access: " + value);
        };
    }

    public boolean reads() {
        return this == READ || this == READ_WRITE;
    }

    public boolean writes() {
        return this == WRITE || this == READ_WRITE;
    }
}
