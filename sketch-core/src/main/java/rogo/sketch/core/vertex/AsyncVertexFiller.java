package rogo.sketch.core.vertex;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Handles async vertex buffer filling.
 * Simplified to a task executor for DynamicTypeMesh.
 */
public class AsyncVertexFiller {
    private static final AsyncVertexFiller INSTANCE = new AsyncVertexFiller();
    private final ExecutorService executor;

    private AsyncVertexFiller() {
        this.executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "AsyncVertexFiller");
            t.setDaemon(true);
            return t;
        });
    }

    public static AsyncVertexFiller getInstance() {
        return INSTANCE;
    }


    public void shutdown() {
        executor.shutdown();
    }
}
