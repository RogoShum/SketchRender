package rogo.sketchrender.util;

import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;

public final class GLFeatureChecker {

    private GLFeatureChecker() {
    }

    private static volatile boolean initialized = false;
    private static boolean cachedPersistentMapping = false;
    private static boolean cachedIndirectDrawCount46 = false;
    private static boolean cachedIndirectDrawCountARB = false;

    public static synchronized void initialize() {
        if (initialized) return;

        GLCapabilities caps = GL.getCapabilities();
        if (caps == null) {
            throw new IllegalStateException("GL context is not available when initializing GLFeatureChecker");
        }

        cachedPersistentMapping = caps.OpenGL44 || caps.GL_ARB_buffer_storage;
        cachedIndirectDrawCount46 = caps.OpenGL46;
        cachedIndirectDrawCountARB = caps.GL_ARB_indirect_parameters;

        initialized = true;
    }

    public static boolean supportsPersistentMapping() {
        ensureInitialized();
        return cachedPersistentMapping;
    }

    public static boolean supportsIndirectDrawCount() {
        ensureInitialized();
        return cachedIndirectDrawCount46 || cachedIndirectDrawCountARB;
    }

    public static boolean supportsIndirectDrawCount46() {
        ensureInitialized();
        return cachedIndirectDrawCount46;
    }

    public static boolean supportsIndirectDrawCountARB() {
        ensureInitialized();
        return cachedIndirectDrawCountARB;
    }

    private static void ensureInitialized() {
        if (!initialized) {
            throw new IllegalStateException("GLFeatureChecker not initialized. Call GLFeatureChecker.initialize() in a GL-capable thread first.");
        }
    }
}
