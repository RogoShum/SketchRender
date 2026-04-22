package rogo.sketch.backend.opengl.internal;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL41;
import rogo.sketch.core.driver.GraphicsDriver;
import rogo.sketch.core.data.type.ValueType;
import rogo.sketch.backend.opengl.util.GLFeatureChecker;

/**
 * Small helper for OpenGL installed-resource implementations that still live in
 * core during the backendization transition.
 */
public final class OpenGLRuntimeSupport {
    private static volatile IGLBufferStrategy bufferStrategy;
    private static volatile IGLTextureStrategy textureStrategy;
    private static volatile IGLVertexArrayStrategy vertexArrayStrategy;
    private static volatile IGLShaderStrategy shaderStrategy;

    private OpenGLRuntimeSupport() {
    }

    public static void assertMainThread(String caller) {
        GraphicsDriver.threadContext().assertMainThread(caller);
    }

    public static void assertRenderContext(String caller) {
        GraphicsDriver.threadContext().assertRenderContext(caller);
    }

    public static IGLBufferStrategy bufferStrategy() {
        IGLBufferStrategy local = bufferStrategy;
        if (local == null) {
            synchronized (OpenGLRuntimeSupport.class) {
                local = bufferStrategy;
                if (local == null) {
                    local = supportsDSA() ? new DSABufferStrategy() : new LegacyBufferStrategy();
                    bufferStrategy = local;
                }
            }
        }
        return local;
    }

    public static IGLTextureStrategy textureStrategy() {
        IGLTextureStrategy local = textureStrategy;
        if (local == null) {
            synchronized (OpenGLRuntimeSupport.class) {
                local = textureStrategy;
                if (local == null) {
                    local = supportsDSA() ? new DSATextureStrategy() : new LegacyTextureStrategy();
                    textureStrategy = local;
                }
            }
        }
        return local;
    }

    public static IGLVertexArrayStrategy vertexArrayStrategy() {
        IGLVertexArrayStrategy local = vertexArrayStrategy;
        if (local == null) {
            synchronized (OpenGLRuntimeSupport.class) {
                local = vertexArrayStrategy;
                if (local == null) {
                    local = supportsDSA() ? new DSAVertexArrayStrategy() : new LegacyVertexArrayStrategy();
                    vertexArrayStrategy = local;
                }
            }
        }
        return local;
    }

    public static IGLShaderStrategy shaderStrategy() {
        IGLShaderStrategy local = shaderStrategy;
        if (local == null) {
            synchronized (OpenGLRuntimeSupport.class) {
                local = shaderStrategy;
                if (local == null) {
                    local = supportsDSA() ? new DSAShaderStrategy() : new LegacyShaderStrategy();
                    shaderStrategy = local;
                }
            }
        }
        return local;
    }

    public static int glType(ValueType valueType) {
        return switch (valueType.scalarType()) {
            case FLOAT32 -> GL11.GL_FLOAT;
            case FLOAT64 -> GL11.GL_DOUBLE;
            case SINT8 -> GL11.GL_BYTE;
            case UINT8 -> GL11.GL_UNSIGNED_BYTE;
            case SINT16 -> GL11.GL_SHORT;
            case UINT16 -> GL11.GL_UNSIGNED_SHORT;
            case SINT32 -> GL11.GL_INT;
            case UINT32 -> GL11.GL_UNSIGNED_INT;
        };
    }

    public static long createFenceSync() {
        return GL32.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
    }

    public static boolean clientWaitSync(long fence, long timeoutNanos) {
        int result = GL32.glClientWaitSync(fence, GL32.GL_SYNC_FLUSH_COMMANDS_BIT, timeoutNanos);
        return result == GL32.GL_ALREADY_SIGNALED || result == GL32.GL_CONDITION_SATISFIED;
    }

    public static void deleteFenceSync(long fence) {
        if (fence != 0L) {
            GL32.glDeleteSync(fence);
        }
    }

    public static void flushMappedBufferRange(int bufferHandle, long offset, long length) {
        bufferStrategy().bindBuffer(GL15.GL_ARRAY_BUFFER, bufferHandle);
        GL30.glFlushMappedBufferRange(GL15.GL_ARRAY_BUFFER, offset, length);
        bufferStrategy().bindBuffer(GL15.GL_ARRAY_BUFFER, 0);
    }

    private static boolean supportsDSA() {
        try {
            GLFeatureChecker.initialize();
        } catch (IllegalStateException ignored) {
            // Already initialized on a different path or called before caps creation;
            // supportsDSA() below will surface a clear error if caps are still missing.
        }
        return GLFeatureChecker.supportsDSA();
    }
}

