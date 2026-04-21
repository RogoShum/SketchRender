package rogo.sketch.core.backend;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.util.KeyId;

public record BufferedResourceDescriptor(
        KeyId resourceId,
        BufferingMode bufferingMode,
        int ringSize,
        @Nullable String debugLabel
) {
    public BufferedResourceDescriptor {
        ringSize = Math.max(1, ringSize);
    }
}
