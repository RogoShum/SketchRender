package rogo.sketch.core.resource.descriptor;

import java.util.Locale;

public enum ImageFormat {
    R8_UNORM,
    R8_SNORM,
    RG8_UNORM,
    RGB8_UNORM,
    RGB8_SNORM,
    RGBA8_UNORM,
    SRGB8_UNORM,
    SRGB8_ALPHA8_UNORM,
    R16_FLOAT,
    RG16_FLOAT,
    RGB16_FLOAT,
    RGBA16_FLOAT,
    R32_FLOAT,
    RG32_FLOAT,
    RGB32_FLOAT,
    RGBA32_FLOAT,
    D16_UNORM,
    D24_UNORM,
    D32_FLOAT,
    D24_UNORM_S8_UINT;

    public boolean isDepthFormat() {
        return switch (this) {
            case D16_UNORM, D24_UNORM, D32_FLOAT, D24_UNORM_S8_UINT -> true;
            default -> false;
        };
    }

    public boolean isStencilFormat() {
        return this == D24_UNORM_S8_UINT;
    }

    public boolean isColorFormat() {
        return !isDepthFormat();
    }

    public static ImageFormat fromString(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Image format must not be blank");
        }
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "R", "R8", "R8_UNORM" -> R8_UNORM;
            case "R8_SNORM", "R_SNORM" -> R8_SNORM;
            case "RG", "RG8", "RG8_UNORM" -> RG8_UNORM;
            case "RGB", "RGB8", "RGB8_UNORM" -> RGB8_UNORM;
            case "RGB_SNORM", "RGB8_SNORM" -> RGB8_SNORM;
            case "RGBA", "RGBA8", "RGBA8_UNORM" -> RGBA8_UNORM;
            case "SRGB", "SRGB8", "SRGB8_UNORM" -> SRGB8_UNORM;
            case "SRGB_ALPHA", "SRGB8_ALPHA8", "SRGB8_ALPHA8_UNORM" -> SRGB8_ALPHA8_UNORM;
            case "R16F", "R16_FLOAT" -> R16_FLOAT;
            case "RG16F", "RG16_FLOAT" -> RG16_FLOAT;
            case "RGB16F", "RGB16_FLOAT" -> RGB16_FLOAT;
            case "RGBA16F", "RGBA16_FLOAT" -> RGBA16_FLOAT;
            case "R32F", "R32_FLOAT" -> R32_FLOAT;
            case "RG32F", "RG32_FLOAT" -> RG32_FLOAT;
            case "RGB32F", "RGB32_FLOAT" -> RGB32_FLOAT;
            case "RGBA32F", "RGBA32_FLOAT" -> RGBA32_FLOAT;
            case "DEPTH", "DEPTH16", "D16_UNORM" -> D16_UNORM;
            case "DEPTH24", "D24_UNORM" -> D24_UNORM;
            case "DEPTH32F", "D32_FLOAT" -> D32_FLOAT;
            case "DEPTH24_STENCIL8", "D24_UNORM_S8_UINT" -> D24_UNORM_S8_UINT;
            default -> throw new IllegalArgumentException("Unsupported image format: " + value);
        };
    }
}

