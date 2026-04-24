package rogo.sketch.core.pipeline.shadow;

/**
 * Process-wide shadow provider handoff between shared modules and host adapters.
 * The provider is data-only; render-target binding remains backend/host-owned.
 */
public final class ShadowProviderRegistry {
    private static volatile ShadowProvider primaryProvider = ShadowProvider.NONE;
    private static volatile ShadowProvider fallbackProvider = ShadowProvider.NONE;

    private ShadowProviderRegistry() {
    }

    public static ShadowProvider currentProvider() {
        ShadowProvider primary = sanitize(primaryProvider);
        ShadowFrameView primaryView = safeFrameView(primary);
        if (isUsable(primaryView)) {
            return primary;
        }

        ShadowProvider fallback = sanitize(fallbackProvider);
        ShadowFrameView fallbackView = safeFrameView(fallback);
        if (isUsable(fallbackView)) {
            return fallback;
        }

        if (primary != ShadowProvider.NONE) {
            return primary;
        }
        if (fallback != ShadowProvider.NONE) {
            return fallback;
        }
        return ShadowProvider.NONE;
    }

    public static ShadowFrameView currentFrameView() {
        return safeFrameView(currentProvider());
    }

    public static void setProvider(ShadowProvider nextProvider) {
        primaryProvider = sanitize(nextProvider);
    }

    public static void installFallback(ShadowProvider fallbackProvider) {
        ShadowProviderRegistry.fallbackProvider = sanitize(fallbackProvider);
    }

    public static void clearProvider(ShadowProvider owner) {
        if (primaryProvider == owner) {
            primaryProvider = ShadowProvider.NONE;
        }
        if (fallbackProvider == owner) {
            fallbackProvider = ShadowProvider.NONE;
        }
    }

    private static ShadowProvider sanitize(ShadowProvider provider) {
        return provider != null ? provider : ShadowProvider.NONE;
    }

    private static ShadowFrameView safeFrameView(ShadowProvider provider) {
        ShadowFrameView view = provider != null ? provider.currentFrameView() : null;
        return view != null ? view : ShadowFrameView.unavailable(ShadowProvider.NONE_PROVIDER_ID);
    }

    private static boolean isUsable(ShadowFrameView view) {
        if (view == null) {
            return false;
        }
        return view.available()
                || view.shadowPassActive()
                || view.renderTargetId() != null
                || view.shadowMapTextureId() != null
                || (view.nativeTargetHandle() != null && view.nativeTargetHandle().isValid())
                || view.width() > 0
                || view.height() > 0
                || view.epoch() > 0L;
    }
}
