package rogo.sketch.core.shader.uniform;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.pipeline.RenderContext;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Immutable per-frame uniform source snapshot captured on the sync thread.
 */
public final class FrameUniformSnapshot {
    private static final FrameUniformSnapshot EMPTY = new FrameUniformSnapshot(null);

    private final RenderContext source;
    private final Map<CacheKey, UniformHook<?>[]> hookCache = new ConcurrentHashMap<>();
    private final Map<CacheKey, UniformValueSnapshot> snapshotCache = new ConcurrentHashMap<>();

    private FrameUniformSnapshot(@Nullable RenderContext source) {
        this.source = source;
    }

    public static FrameUniformSnapshot empty() {
        return EMPTY;
    }

    public static FrameUniformSnapshot capture(@Nullable RenderContext renderContext) {
        if (renderContext == null) {
            return EMPTY;
        }
        return new FrameUniformSnapshot(renderContext.snapshot());
    }

    public boolean isEmpty() {
        return source == null;
    }

    @Nullable
    public RenderContext source() {
        return source;
    }

    public UniformValueSnapshot snapshotFor(@Nullable UniformHookGroup hookGroup) {
        if (source == null || hookGroup == null) {
            return UniformValueSnapshot.empty();
        }
        CacheKey cacheKey = new CacheKey(System.identityHashCode(hookGroup), source.getClass());
        UniformHook<?>[] cachedHooks = hookCache.computeIfAbsent(
                cacheKey,
                ignored -> hookGroup.getAllMatchingHooks(source.getClass(), UniformCaptureTiming.FRAME_SYNC));
        return snapshotCache.computeIfAbsent(
                cacheKey,
                ignored -> UniformValueSnapshot.captureFrom(
                        hookGroup,
                        source,
                        cachedHooks,
                        UniformCaptureTiming.FRAME_SYNC));
    }

    private record CacheKey(int hookGroupIdentity, Class<?> sourceClass) {
    }
}
