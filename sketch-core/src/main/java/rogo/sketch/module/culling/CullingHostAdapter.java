package rogo.sketch.module.culling;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.resource.vision.Texture;
import rogo.sketch.core.util.KeyId;
import org.joml.Vector3i;

import java.util.List;

/**
 * Minecraft-side seam used by the core culling module to access platform
 * resources and compute entity blueprints without depending on mod classes.
 */
public interface CullingHostAdapter {
    KeyId terrainStageId();

    KeyId hizStageId();

    KeyId frameSyncStageId();

    void refreshHiZInputs(RenderContext renderContext);

    List<TerrainRegionSource> refreshTerrainInputs(RenderContext renderContext);

    @Nullable
    Texture hizTexture();

    @Nullable
    default Texture asyncHiZWriteTexture() {
        return hizTexture();
    }

    @Nullable
    default Texture hizSourceDepthSnapshotTexture() {
        return null;
    }

    Vector3i[] hizDepthInfo();

    default void onAsyncHiZSubmitted(long epoch) {
    }

    default void onAsyncHiZCompleted(long epoch) {
    }

    boolean entityCullingHostActive();

    boolean terrainCullingHostActive();

    default boolean freezeHiZUpdates() {
        return false;
    }

    default void commitVisibleTerrainRegions(List<TerrainRegionSource> visibleTerrainRegions) {
    }

    default void clearTransientState() {
    }
}
