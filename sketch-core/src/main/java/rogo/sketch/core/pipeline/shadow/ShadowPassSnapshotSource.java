package rogo.sketch.core.pipeline.shadow;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.util.KeyId;

public interface ShadowPassSnapshotSource {
    KeyId NONE_SOURCE_ID = KeyId.of("sketch_render", "shadow_snapshot_none");

    ShadowPassSnapshotSource NONE = new ShadowPassSnapshotSource() {
        @Override
        public KeyId sourceId() {
            return NONE_SOURCE_ID;
        }

        @Override
        public ShadowPassSnapshot capture(@Nullable RenderContext renderContext, ShadowFrameView shadowFrameView) {
            return ShadowPassSnapshot.fallback(shadowFrameView);
        }
    };

    KeyId sourceId();

    ShadowPassSnapshot capture(@Nullable RenderContext renderContext, ShadowFrameView shadowFrameView);
}
