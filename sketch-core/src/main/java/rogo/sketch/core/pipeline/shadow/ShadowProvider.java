package rogo.sketch.core.pipeline.shadow;

import rogo.sketch.core.util.KeyId;

public interface ShadowProvider {
    KeyId NONE_PROVIDER_ID = KeyId.of("sketch_render", "shadow_none");

    ShadowProvider NONE = new ShadowProvider() {
        @Override
        public KeyId providerId() {
            return NONE_PROVIDER_ID;
        }

        @Override
        public ShadowFrameView currentFrameView() {
            return ShadowFrameView.unavailable(NONE_PROVIDER_ID);
        }
    };

    KeyId providerId();

    ShadowFrameView currentFrameView();

    default boolean available() {
        ShadowFrameView view = currentFrameView();
        return view != null && view.available();
    }

    default boolean shadowPassActive() {
        ShadowFrameView view = currentFrameView();
        return view != null && view.shadowPassActive();
    }
}
