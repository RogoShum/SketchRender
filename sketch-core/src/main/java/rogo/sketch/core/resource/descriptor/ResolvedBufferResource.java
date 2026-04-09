package rogo.sketch.core.resource.descriptor;

import rogo.sketch.core.util.KeyId;

import java.util.Objects;

public final class ResolvedBufferResource {
    private final KeyId identifier;
    private final BufferRole role;
    private final BufferUpdatePolicy updatePolicy;
    private final long elementCount;
    private final long strideBytes;
    private final long capacityBytes;

    public ResolvedBufferResource(
            KeyId identifier,
            BufferRole role,
            BufferUpdatePolicy updatePolicy,
            long elementCount,
            long strideBytes,
            long capacityBytes) {
        this.identifier = Objects.requireNonNull(identifier, "identifier");
        this.role = Objects.requireNonNull(role, "role");
        this.updatePolicy = Objects.requireNonNull(updatePolicy, "updatePolicy");
        this.elementCount = Math.max(0L, elementCount);
        this.strideBytes = Math.max(0L, strideBytes);
        this.capacityBytes = Math.max(0L, capacityBytes);
    }

    public KeyId identifier() {
        return identifier;
    }

    public BufferRole role() {
        return role;
    }

    public BufferUpdatePolicy updatePolicy() {
        return updatePolicy;
    }

    public long elementCount() {
        return elementCount;
    }

    public long strideBytes() {
        return strideBytes;
    }

    public long capacityBytes() {
        return capacityBytes;
    }
}

