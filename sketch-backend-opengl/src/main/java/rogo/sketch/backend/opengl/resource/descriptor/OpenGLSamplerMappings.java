package rogo.sketch.backend.opengl.resource.descriptor;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;
import rogo.sketch.core.resource.descriptor.ResolvedImageResource;
import rogo.sketch.core.resource.descriptor.SamplerFilter;
import rogo.sketch.core.resource.descriptor.SamplerWrap;

public final class OpenGLSamplerMappings {
    private OpenGLSamplerMappings() {
    }

    public static int toMinFilter(ResolvedImageResource descriptor) {
        if (descriptor.usesMipmaps() && descriptor.mipmapFilter() != null) {
            return toFilter(descriptor.mipmapFilter());
        }
        return toFilter(descriptor.minFilter());
    }

    public static int toMagFilter(ResolvedImageResource descriptor) {
        return toFilter(descriptor.magFilter());
    }

    public static int toWrap(SamplerWrap wrap) {
        return switch (wrap) {
            case REPEAT -> GL11.GL_REPEAT;
            case CLAMP_TO_EDGE -> GL12.GL_CLAMP_TO_EDGE;
            case CLAMP_TO_BORDER -> GL13Like.CLAMP_TO_BORDER;
            case MIRRORED_REPEAT -> GL14.GL_MIRRORED_REPEAT;
        };
    }

    public static int toFilter(SamplerFilter filter) {
        return switch (filter) {
            case NEAREST -> GL11.GL_NEAREST;
            case LINEAR -> GL11.GL_LINEAR;
            case NEAREST_MIPMAP_NEAREST -> GL11.GL_NEAREST_MIPMAP_NEAREST;
            case LINEAR_MIPMAP_NEAREST -> GL11.GL_LINEAR_MIPMAP_NEAREST;
            case NEAREST_MIPMAP_LINEAR -> GL11.GL_NEAREST_MIPMAP_LINEAR;
            case LINEAR_MIPMAP_LINEAR -> GL11.GL_LINEAR_MIPMAP_LINEAR;
        };
    }

    private static final class GL13Like {
        private static final int CLAMP_TO_BORDER = 0x812D;

        private GL13Like() {
        }
    }
}

