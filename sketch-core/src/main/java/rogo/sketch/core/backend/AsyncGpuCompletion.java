package rogo.sketch.core.backend;

public interface AsyncGpuCompletion {
    AsyncGpuCompletion COMPLETED = new AsyncGpuCompletion() {
        @Override
        public boolean isDone() {
            return true;
        }

        @Override
        public void await() {
        }
    };

    boolean isDone();

    void await();

    static AsyncGpuCompletion completed() {
        return COMPLETED;
    }
}
