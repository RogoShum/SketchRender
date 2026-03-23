package rogo.sketch.module.transform.manager;

/**
 * Published tick-owned transform snapshot containing both sync and async streams.
 */
public class TransformPreparedTickSnapshot {
    private long logicTickEpoch = -1L;
    private final TransformUploadSnapshot syncSnapshot;
    private final TransformUploadSnapshot asyncSnapshot;

    public TransformPreparedTickSnapshot(int syncInitialCapacity, int asyncInitialCapacity) {
        this.syncSnapshot = new TransformUploadSnapshot(syncInitialCapacity);
        this.asyncSnapshot = new TransformUploadSnapshot(asyncInitialCapacity);
    }

    public long logicTickEpoch() {
        return logicTickEpoch;
    }

    public void setLogicTickEpoch(long logicTickEpoch) {
        this.logicTickEpoch = logicTickEpoch;
    }

    public TransformUploadSnapshot syncSnapshot() {
        return syncSnapshot;
    }

    public TransformUploadSnapshot asyncSnapshot() {
        return asyncSnapshot;
    }

    public void cleanup() {
        syncSnapshot.cleanup();
        asyncSnapshot.cleanup();
    }
}
