package rogo.sketch.core.util;

import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;

public final class GLFeatureChecker {

    private GLFeatureChecker() {
    }

    private static volatile boolean initialized = false;
    private static boolean cachedPersistentMapping = false;
    private static boolean cachedIndirectDrawCount46 = false;
    private static boolean cachedIndirectDrawCountARB = false;
    private static boolean cachedDSA45 = false;
    private static boolean cachedDSA_ARB = false;
    private static boolean cachedDSA_EXT = false;

    public static synchronized void initialize() {
        if (initialized) return;

        GLCapabilities caps = GL.getCapabilities();
        if (caps == null) {
            throw new IllegalStateException("GL context is not available when initializing GLFeatureChecker");
        }

        cachedPersistentMapping = caps.OpenGL44 || caps.GL_ARB_buffer_storage;
        cachedIndirectDrawCount46 = caps.OpenGL46;
        cachedIndirectDrawCountARB = caps.GL_ARB_indirect_parameters;
        cachedDSA45 = caps.OpenGL45;
        cachedDSA_ARB = caps.GL_ARB_direct_state_access;
        cachedDSA_EXT = caps.GL_EXT_direct_state_access;

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

    public static boolean supportsDSA() {
        ensureInitialized();
        // DSA support requires GL 4.5 or ARB_direct_state_access extension
        // Note: EXT_direct_state_access has slightly different function signatures
        return cachedDSA45 || cachedDSA_ARB;
    }

    public static boolean supportsDSA45() {
        ensureInitialized();
        return cachedDSA45;
    }

    public static boolean supportsDSA_ARB() {
        ensureInitialized();
        return cachedDSA_ARB;
    }

    public static boolean supportsDSA_EXT() {
        ensureInitialized();
        return cachedDSA_EXT;
    }

    private static void ensureInitialized() {
        if (!initialized) {
            throw new IllegalStateException("GLFeatureChecker not initialized. Call GLFeatureChecker.initialize() in a GL-capable thread first.");
        }
    }
}