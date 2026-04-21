package rogo.sketch.module.culling;

import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Process-level registry for the active Minecraft culling host adapter.
 */
public final class CullingHostRegistry {
    private static final AtomicReference<CullingHostAdapter> CURRENT = new AtomicReference<>();

    private CullingHostRegistry() {
    }

    public static void register(CullingHostAdapter adapter) {
        CURRENT.set(adapter);
    }

    public static void clear() {
        CURRENT.set(null);
    }

    public static @Nullable CullingHostAdapter current() {
        return CURRENT.get();
    }
}
