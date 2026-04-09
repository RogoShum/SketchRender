package rogo.sketch.backend.opengl.resource.descriptor;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import rogo.sketch.core.resource.descriptor.ImageFormat;

public final class OpenGLImageFormatMappings {
    private OpenGLImageFormatMappings() {
    }

    public static OpenGLImageFormatMapping resolve(ImageFormat format) {
        return switch (format) {
            case R8_UNORM -> new OpenGLImageFormatMapping(GL30.GL_R8, GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, GL30.GL_R8);
            case R8_SNORM -> new OpenGLImageFormatMapping(GL31.GL_R8_SNORM, GL11.GL_RED, GL11.GL_BYTE, GL31.GL_R8_SNORM);
            case RG8_UNORM -> new OpenGLImageFormatMapping(GL30.GL_RG8, GL30.GL_RG, GL11.GL_UNSIGNED_BYTE, GL30.GL_RG8);
            case RGB8_UNORM -> new OpenGLImageFormatMapping(GL11.GL_RGB8, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, GL11.GL_RGB8);
            case RGB8_SNORM -> new OpenGLImageFormatMapping(GL31.GL_RGB8_SNORM, GL11.GL_RGB, GL11.GL_BYTE, GL31.GL_RGB8_SNORM);
            case RGBA8_UNORM -> new OpenGLImageFormatMapping(GL11.GL_RGBA8, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, GL11.GL_RGBA8);
            case SRGB8_UNORM -> new OpenGLImageFormatMapping(GL21.GL_SRGB8, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, GL21.GL_SRGB8);
            case SRGB8_ALPHA8_UNORM -> new OpenGLImageFormatMapping(GL21.GL_SRGB8_ALPHA8, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, GL21.GL_SRGB8_ALPHA8);
            case R16_FLOAT -> new OpenGLImageFormatMapping(GL30.GL_R16F, GL11.GL_RED, GL11.GL_FLOAT, GL30.GL_R16F);
            case RG16_FLOAT -> new OpenGLImageFormatMapping(GL30.GL_RG16F, GL30.GL_RG, GL11.GL_FLOAT, GL30.GL_RG16F);
            case RGB16_FLOAT -> new OpenGLImageFormatMapping(GL30.GL_RGB16F, GL11.GL_RGB, GL11.GL_FLOAT, GL30.GL_RGB16F);
            case RGBA16_FLOAT -> new OpenGLImageFormatMapping(GL30.GL_RGBA16F, GL11.GL_RGBA, GL11.GL_FLOAT, GL30.GL_RGBA16F);
            case R32_FLOAT -> new OpenGLImageFormatMapping(GL30.GL_R32F, GL11.GL_RED, GL11.GL_FLOAT, GL30.GL_R32F);
            case RG32_FLOAT -> new OpenGLImageFormatMapping(GL30.GL_RG32F, GL30.GL_RG, GL11.GL_FLOAT, GL30.GL_RG32F);
            case RGB32_FLOAT -> new OpenGLImageFormatMapping(GL30.GL_RGB32F, GL11.GL_RGB, GL11.GL_FLOAT, GL30.GL_RGB32F);
            case RGBA32_FLOAT -> new OpenGLImageFormatMapping(GL30.GL_RGBA32F, GL11.GL_RGBA, GL11.GL_FLOAT, GL30.GL_RGBA32F);
            case D16_UNORM -> new OpenGLImageFormatMapping(GL14.GL_DEPTH_COMPONENT16, GL11.GL_DEPTH_COMPONENT, GL11.GL_UNSIGNED_SHORT, GL14.GL_DEPTH_COMPONENT16);
            case D24_UNORM -> new OpenGLImageFormatMapping(GL14.GL_DEPTH_COMPONENT24, GL11.GL_DEPTH_COMPONENT, GL11.GL_UNSIGNED_INT, GL14.GL_DEPTH_COMPONENT24);
            case D32_FLOAT -> new OpenGLImageFormatMapping(GL30.GL_DEPTH_COMPONENT32F, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, GL30.GL_DEPTH_COMPONENT32F);
            case D24_UNORM_S8_UINT -> new OpenGLImageFormatMapping(GL30.GL_DEPTH24_STENCIL8, GL30.GL_DEPTH_STENCIL, GL30.GL_UNSIGNED_INT_24_8, GL30.GL_DEPTH24_STENCIL8);
        };
    }
}

