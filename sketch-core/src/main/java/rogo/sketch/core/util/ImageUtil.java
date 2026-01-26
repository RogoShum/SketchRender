package rogo.sketch.core.util;

import org.lwjgl.BufferUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.function.Function;

/**
 * Utility class for loading and converting images for use with OpenGL textures
 */
public class ImageUtil {

    public static ImageData loadImage(KeyId imageId, Function<KeyId, Optional<InputStream>> resourceProvider, boolean flipY) {
        Optional<InputStream> optionalStream = resourceProvider.apply(imageId);
        if (optionalStream.isEmpty()) {
            System.err.println("Image not found: " + imageId);
            return null;
        }

        try (InputStream stream = optionalStream.get()) {
            BufferedImage image = ImageIO.read(stream);
            if (image == null) {
                System.err.println("Failed to decode image: " + imageId);
                return null;
            }

            return new ImageData(convertToByteBuffer(image, flipY), image.getWidth(), image.getHeight(), image.getColorModel().hasAlpha()
            );

        } catch (Exception e) {
            System.err.println("Failed to load image: " + imageId);
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Convert BufferedImage to ByteBuffer in RGBA format
     */
    public static ByteBuffer convertToByteBuffer(BufferedImage image, boolean flipY) {
        int width = image.getWidth();
        int height = image.getHeight();

        int[] pixels = new int[width * height];
        image.getRGB(0, 0, width, height, pixels, 0, width);

        ByteBuffer buffer = BufferUtils.createByteBuffer(width * height * 4); // RGBA

        if (flipY) {
            // 从最后一行开始写（OpenGL 友好）
            for (int y = height - 1; y >= 0; y--) {
                int rowStart = y * width;
                for (int x = 0; x < width; x++) {
                    int pixel = pixels[rowStart + x];
                    putPixel(buffer, pixel);
                }
            }
        } else {
            // 原始顺序（AWT）
            for (int pixel : pixels) {
                putPixel(buffer, pixel);
            }
        }

        buffer.flip();
        return buffer;
    }

    private static void putPixel(ByteBuffer buffer, int pixel) {
        buffer.put((byte) ((pixel >> 16) & 0xFF)); // R
        buffer.put((byte) ((pixel >> 8) & 0xFF));  // G
        buffer.put((byte) (pixel & 0xFF));         // B
        buffer.put((byte) ((pixel >> 24) & 0xFF)); // A
    }

    /**
     * Container for loaded image data
     */
    public static class ImageData {
        public final ByteBuffer buffer;
        public final int width;
        public final int height;
        public final boolean hasAlpha;

        public ImageData(ByteBuffer buffer, int width, int height, boolean hasAlpha) {
            this.buffer = buffer;
            this.width = width;
            this.height = height;
            this.hasAlpha = hasAlpha;
        }
    }
}