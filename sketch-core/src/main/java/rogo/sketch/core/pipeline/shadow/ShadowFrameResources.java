package rogo.sketch.core.pipeline.shadow;

import rogo.sketch.core.pipeline.kernel.FrameResourceHandle;
import rogo.sketch.core.util.KeyId;

public final class ShadowFrameResources {
    public static final FrameResourceHandle<ShadowPassSnapshot> SHADOW_PASS_SNAPSHOT = FrameResourceHandle.of(
            KeyId.of("sketch_render", "shadow_pass_snapshot"),
            ShadowPassSnapshot.class,
            "shadow",
            "Shadow Pass Snapshot");

    private ShadowFrameResources() {
    }
}
