package rogo.sketch.core.backend;

/**
 * Process-wide debug toggles driven by dashboard testing settings.
 * These are intentionally simple volatile flags so runtime hot paths can
 * branch without consulting the setting registry directly.
 */
public final class RuntimeDebugToggles {
    private static volatile boolean glAsyncGpuWorkersDisabled;

    private RuntimeDebugToggles() {
    }

    public static boolean glAsyncGpuWorkersDisabled() {
        return glAsyncGpuWorkersDisabled;
    }

    public static void setGlAsyncGpuWorkersDisabled(boolean disabled) {
        glAsyncGpuWorkersDisabled = disabled;
    }

    public static void reset() {
        glAsyncGpuWorkersDisabled = false;
    }
}
