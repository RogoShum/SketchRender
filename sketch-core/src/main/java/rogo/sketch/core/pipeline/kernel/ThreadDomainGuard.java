package rogo.sketch.core.pipeline.kernel;

import rogo.sketch.core.driver.GraphicsDriver;

/**
 * Thin utility that stores the main thread reference.
 * <p>
 * Thread-domain assertions have moved to the active backend runtime.
 * This class only provides legacy-compatible {@link #registerMainThread()} and
 * {@link #isMainThread()} helpers used by existing code that hasn't migrated yet.
 * </p>
 */
public final class ThreadDomainGuard {

    private static volatile Thread mainThread;

    private ThreadDomainGuard() {}

    /**
     * Register the current thread as the main/render thread.
     * Also registers it with the active backend runtime.
     */
    public static void registerMainThread() {
        mainThread = Thread.currentThread();
        GraphicsDriver.runtime().registerMainThread();
    }

    /**
     * Check if the current thread is the registered main thread.
     */
    public static boolean isMainThread() {
        return mainThread != null && Thread.currentThread() == mainThread;
    }
}
