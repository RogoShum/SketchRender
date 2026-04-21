package rogo.sketch.core.memory;

import rogo.sketch.core.resource.descriptor.ImageFormat;
import rogo.sketch.core.resource.descriptor.ResolvedImageResource;

public final class ImageMemoryEstimator {
    private ImageMemoryEstimator() {
    }

    public static long estimateBytes(ResolvedImageResource descriptor) {
        if (descriptor == null) {
            return 0L;
        }
        return estimateBytes(descriptor.width(), descriptor.height(), descriptor.mipLevels(), descriptor.format());
    }

    public static long estimateBytes(int width, int height, int mipLevels, ImageFormat format) {
        int safeWidth = Math.max(1, width);
        int safeHeight = Math.max(1, height);
        int safeMipLevels = Math.max(1, mipLevels);
        long total = 0L;
        int currentWidth = safeWidth;
        int currentHeight = safeHeight;
        int bytesPerPixel = bytesPerPixel(format);
        for (int level = 0; level < safeMipLevels; level++) {
            total += (long) currentWidth * currentHeight * bytesPerPixel;
            currentWidth = Math.max(1, currentWidth >> 1);
            currentHeight = Math.max(1, currentHeight >> 1);
        }
        return total;
    }

    private static int bytesPerPixel(ImageFormat format) {
        if (format == null) {
            return 4;
        }
        return switch (format) {
            case R8_UNORM, R8_SNORM -> 1;
            case RG8_UNORM, R16_FLOAT, D16_UNORM -> 2;
            case RGB8_UNORM, RGB8_SNORM, SRGB8_UNORM, D24_UNORM, D32_FLOAT, R32_FLOAT -> 4;
            case RGBA8_UNORM, SRGB8_ALPHA8_UNORM, RG16_FLOAT, D24_UNORM_S8_UINT -> 4;
            case RGB16_FLOAT -> 6;
            case RGBA16_FLOAT, RG32_FLOAT -> 8;
            case RGB32_FLOAT -> 12;
            case RGBA32_FLOAT -> 16;
        };
    }
}
