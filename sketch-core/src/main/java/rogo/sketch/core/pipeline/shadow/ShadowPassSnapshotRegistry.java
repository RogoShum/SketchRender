package rogo.sketch.core.pipeline.shadow;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.pipeline.RenderContext;

public final class ShadowPassSnapshotRegistry {
    private static volatile ShadowPassSnapshotSource primarySource = ShadowPassSnapshotSource.NONE;
    private static volatile ShadowPassSnapshotSource fallbackSource = ShadowPassSnapshotSource.NONE;

    private ShadowPassSnapshotRegistry() {
    }

    public static ShadowPassSnapshotSource currentSource() {
        ShadowPassSnapshotSource primary = sanitize(primarySource);
        if (primary != ShadowPassSnapshotSource.NONE) {
            return primary;
        }
        ShadowPassSnapshotSource fallback = sanitize(fallbackSource);
        if (fallback != ShadowPassSnapshotSource.NONE) {
            return fallback;
        }
        return ShadowPassSnapshotSource.NONE;
    }

    public static ShadowPassSnapshot currentSnapshot(@Nullable RenderContext renderContext, ShadowFrameView shadowFrameView) {
        ShadowPassSnapshot snapshot = currentSource().capture(renderContext, shadowFrameView);
        return snapshot != null ? snapshot : ShadowPassSnapshot.fallback(shadowFrameView);
    }

    public static void setSource(@Nullable ShadowPassSnapshotSource nextSource) {
        primarySource = sanitize(nextSource);
    }

    public static void installFallback(@Nullable ShadowPassSnapshotSource nextFallback) {
        fallbackSource = sanitize(nextFallback);
    }

    public static void clearSource(ShadowPassSnapshotSource owner) {
        if (owner == null) {
            return;
        }
        if (primarySource == owner) {
            primarySource = ShadowPassSnapshotSource.NONE;
        }
        if (fallbackSource == owner) {
            fallbackSource = ShadowPassSnapshotSource.NONE;
        }
    }

    private static ShadowPassSnapshotSource sanitize(@Nullable ShadowPassSnapshotSource source) {
        return source != null ? source : ShadowPassSnapshotSource.NONE;
    }
}
