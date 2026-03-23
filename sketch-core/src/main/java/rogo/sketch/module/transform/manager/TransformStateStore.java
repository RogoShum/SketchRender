package rogo.sketch.module.transform.manager;

import rogo.sketch.module.transform.AsyncTickTransformSource;
import rogo.sketch.module.transform.FrameTransformSource;
import rogo.sketch.module.transform.StaticTransformSource;
import rogo.sketch.module.transform.SyncTickTransformSource;
import rogo.sketch.module.transform.TransformData;

/**
 * Owns CPU-side authored transform state collection.
 */
public class TransformStateStore {
    public void initializeBinding(TransformBinding binding) {
        if (binding.updateDomain() == TransformUpdateDomain.STATIC
                && binding.graphics() instanceof StaticTransformSource staticSource) {
            TransformData initial = new TransformData();
            initial.reset();
            staticSource.writeStaticTransform(initial);
            binding.seedAllTickBuffers(initial);
        }

        if (binding.updateDomain() == TransformUpdateDomain.SYNC_FRAME) {
            binding.frameData().reset();
        }
    }

    public void swapTickBuffers(TransformRegistry registry) {
        for (TransformBinding binding : registry.activeBindings()) {
            if (binding.updateDomain() != TransformUpdateDomain.SYNC_FRAME) {
                binding.swapTickBuffers();
            }
        }
    }

    public void collectSyncTickTransforms(TransformRegistry registry) {
        for (TransformBinding binding : registry.syncTickBindings()) {
            if (binding.graphics() instanceof SyncTickTransformSource source) {
                source.writeSyncTickTransform(binding.pendingTickData());
            }
        }
    }

    public void collectAsyncTickTransforms(TransformRegistry registry) {
        for (TransformBinding binding : registry.asyncTickBindings()) {
            if (binding.graphics() instanceof AsyncTickTransformSource source) {
                source.writeAsyncTickTransform(binding.pendingTickData());
            }
        }
    }

    public void collectFrameTransforms(TransformRegistry registry) {
        for (TransformBinding binding : registry.frameBindings()) {
            if (binding.graphics() instanceof FrameTransformSource source) {
                source.writeFrameTransform(binding.frameData());
            }
        }
    }
}
