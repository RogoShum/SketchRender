package rogo.sketch.core.graphics.ecs;

import rogo.sketch.core.util.KeyId;

public record GraphicsCapabilityId(KeyId id) {
    public static GraphicsCapabilityId of(String value) {
        return new GraphicsCapabilityId(KeyId.of("sketch", value));
    }

    @Override
    public String toString() {
        return id != null ? id.toString() : "sketch:unknown_capability";
    }
}
