package rogo.sketch.backend.opengl;

import rogo.sketch.backend.opengl.driver.GraphicsAPI;
import rogo.sketch.core.backend.AsyncGpuCompletion;

final class OpenGLAsyncFenceCompletion implements AsyncGpuCompletion {
    private final GraphicsAPI api;
    private long fence;
    private boolean completed;

    OpenGLAsyncFenceCompletion(GraphicsAPI api, long fence) {
        this.api = api;
        this.fence = fence;
        this.completed = fence == 0L;
    }

    @Override
    public synchronized boolean isDone() {
        if (completed) {
            return true;
        }
        if (api.clientWaitSync(fence, 0L, false)) {
            completeAndRelease();
            return true;
        }
        return false;
    }

    @Override
    public synchronized void await() {
        while (!completed) {
            if (api.clientWaitSync(fence, 1_000_000L, false)) {
                completeAndRelease();
                break;
            }
            Thread.onSpinWait();
        }
    }

    private void completeAndRelease() {
        completed = true;
        if (fence != 0L) {
            api.deleteFenceSync(fence);
            fence = 0L;
        }
    }
}
