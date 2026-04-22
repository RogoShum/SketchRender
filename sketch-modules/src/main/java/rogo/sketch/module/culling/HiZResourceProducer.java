package rogo.sketch.module.culling;

import org.joml.Vector3i;
import rogo.sketch.core.resource.vision.Texture;

/**
 * Normalizes host-provided Hi-Z resources into core-friendly dispatch inputs.
 */
public final class HiZResourceProducer {
    public Texture hizTexture(CullingHostAdapter adapter) {
        return adapter != null ? adapter.hizTexture() : null;
    }

    public Vector3i[] depthInfo(CullingHostAdapter adapter) {
        return adapter != null ? adapter.hizDepthInfo() : new Vector3i[0];
    }

    public int[] dispatchGroups(CullingHostAdapter adapter, boolean firstPass) {
        Vector3i[] info = depthInfo(adapter);
        if (info.length == 0) {
            return new int[]{0, 0};
        }
        Vector3i screenSize = firstPass ? info[0] : info[Math.min(3, info.length - 1)];
        if (screenSize == null || screenSize.x <= 0 || screenSize.y <= 0) {
            return new int[]{0, 0};
        }
        return new int[]{
                (screenSize.x + 15) / 16,
                (screenSize.y + 15) / 16
        };
    }

    public boolean hasActiveHiZInputs(CullingHostAdapter adapter) {
        Texture texture = hizTexture(adapter);
        int[] groups = dispatchGroups(adapter, true);
        return texture != null && groups[0] > 0 && groups[1] > 0;
    }
}
