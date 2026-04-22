package rogo.sketch.module.transform.manager;

import rogo.sketch.core.graphics.ecs.GraphicsUpdateDomain;
import rogo.sketch.module.transform.TransformData;

/**
 * Owns CPU-side authored transform state collection.
 */
public class TransformStateStore {
    public void initializeBinding(TransformBinding binding) {
        if (binding.updateDomain() == GraphicsUpdateDomain.STATIC
                && binding.bindingComponent().authoring() != null) {
            TransformData initial = new TransformData();
            initial.reset();
            binding.bindingComponent().authoring().writeTransform(initial);
            binding.seedAllTickBuffers(initial);
        }

        if (binding.updateDomain() == GraphicsUpdateDomain.SYNC_FRAME) {
            binding.frameData().reset();
        }
    }

    public void swapTickBuffers(TransformRegistry registry) {
        for (TransformBinding binding : registry.activeBindings()) {
            if (binding.updateDomain() != GraphicsUpdateDomain.SYNC_FRAME) {
                binding.swapTickBuffers();
            }
        }
    }

    public void collectSyncTickTransforms(TransformRegistry registry) {
        for (TransformBinding binding : registry.syncTickBindings()) {
            if (binding.bindingComponent().authoring() != null) {
                binding.bindingComponent().authoring().writeTransform(binding.pendingTickData());
            }
        }
    }

    public void collectAsyncTickTransforms(TransformRegistry registry) {
        for (TransformBinding binding : registry.asyncTickBindings()) {
            if (binding.bindingComponent().authoring() != null) {
                binding.bindingComponent().authoring().writeTransform(binding.pendingTickData());
            }
        }
    }

    public void collectFrameTransforms(TransformRegistry registry) {
        for (TransformBinding binding : registry.frameBindings()) {
            if (binding.bindingComponent().authoring() != null) {
                binding.bindingComponent().authoring().writeTransform(binding.frameData());
            }
        }
    }
}
