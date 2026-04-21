package rogo.sketch.core.pipeline.kernel;

import rogo.sketch.core.util.KeyId;

import java.util.Objects;

/**
 * Typed handle exchanged between module/resource/submit layers.
 *
 * @param <T> payload type
 */
public final class FrameResourceHandle<T> {
    private final KeyId id;
    private final Class<T> valueType;
    private final String producerModuleId;
    private final String debugName;

    private FrameResourceHandle(KeyId id, Class<T> valueType, String producerModuleId, String debugName) {
        this.id = Objects.requireNonNull(id, "id");
        this.valueType = Objects.requireNonNull(valueType, "valueType");
        this.producerModuleId = producerModuleId != null ? producerModuleId : "unknown";
        this.debugName = debugName != null ? debugName : id.toString();
    }

    public static <T> FrameResourceHandle<T> of(
            KeyId id,
            Class<T> valueType,
            String producerModuleId,
            String debugName) {
        return new FrameResourceHandle<>(id, valueType, producerModuleId, debugName);
    }

    public KeyId id() {
        return id;
    }

    public Class<T> valueType() {
        return valueType;
    }

    public String producerModuleId() {
        return producerModuleId;
    }

    public String debugName() {
        return debugName;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof FrameResourceHandle<?> other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "FrameResourceHandle[" + id + ", producer=" + producerModuleId + "]";
    }
}
