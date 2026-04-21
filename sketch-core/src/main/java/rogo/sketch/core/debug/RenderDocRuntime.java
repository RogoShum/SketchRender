package rogo.sketch.core.debug;

import org.lwjgl.system.Platform;
import org.lwjgl.system.windows.WinBase;

/**
 * RenderDoc can be injected externally without going through our launch tasks,
 * so relying only on a JVM property misses the common capture path. On Windows
 * we also probe the loaded module list for renderdoc.dll and treat that as an
 * active capture session.
 */
public final class RenderDocRuntime {
    public static final String RENDERDOC_PROPERTY = "sketch.renderdoc.capture";
    private static final String RENDERDOC_DLL = "renderdoc.dll";

    private RenderDocRuntime() {
    }

    public static boolean enabled() {
        if (Boolean.getBoolean(RENDERDOC_PROPERTY)) {
            return true;
        }
        if (Platform.get() != Platform.WINDOWS) {
            return false;
        }
        try {
            return WinBase.GetModuleHandle(RENDERDOC_DLL) != 0L;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
