package rogo.sketch.core.packet;

import rogo.sketch.core.util.KeyId;

import java.util.Objects;

public final class TransferPlanKey implements ExecutionKey {
    private static final KeyId DEFAULT_TRANSFER_KEY = KeyId.of("sketch:transfer_plan");

    private final KeyId transferKey;
    private final KeyId renderTargetKey;
    private final KeyId resourceLayoutKey;
    private final int hashCode;

    public TransferPlanKey(KeyId transferKey, KeyId renderTargetKey, KeyId resourceLayoutKey) {
        this.transferKey = transferKey != null ? transferKey : DEFAULT_TRANSFER_KEY;
        this.renderTargetKey = renderTargetKey != null ? renderTargetKey : DEFAULT_RENDER_TARGET;
        this.resourceLayoutKey = resourceLayoutKey != null ? resourceLayoutKey : EMPTY_RESOURCE_LAYOUT;
        this.hashCode = Objects.hash(this.transferKey, this.renderTargetKey, this.resourceLayoutKey);
    }

    public static TransferPlanKey of(KeyId transferKey) {
        return new TransferPlanKey(transferKey, DEFAULT_RENDER_TARGET, EMPTY_RESOURCE_LAYOUT);
    }

    public static TransferPlanKey forRenderTarget(KeyId renderTargetKey) {
        return new TransferPlanKey(KeyId.of("sketch:transfer_target_" + String.valueOf(renderTargetKey)), renderTargetKey, EMPTY_RESOURCE_LAYOUT);
    }

    public static TransferPlanKey forTexture(KeyId textureId) {
        return new TransferPlanKey(KeyId.of("sketch:transfer_texture_" + String.valueOf(textureId)), DEFAULT_RENDER_TARGET, EMPTY_RESOURCE_LAYOUT);
    }

    @Override
    public ExecutionDomain domain() {
        return ExecutionDomain.TRANSFER;
    }

    public KeyId transferKey() {
        return transferKey;
    }

    @Override
    public KeyId renderTargetKey() {
        return renderTargetKey;
    }

    @Override
    public KeyId resourceLayoutKey() {
        return resourceLayoutKey;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof TransferPlanKey other)) {
            return false;
        }
        return Objects.equals(transferKey, other.transferKey)
                && Objects.equals(renderTargetKey, other.renderTargetKey)
                && Objects.equals(resourceLayoutKey, other.resourceLayoutKey);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }
}
