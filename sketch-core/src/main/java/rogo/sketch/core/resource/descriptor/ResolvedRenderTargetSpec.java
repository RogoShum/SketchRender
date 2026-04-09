package rogo.sketch.core.resource.descriptor;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.util.KeyId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class ResolvedRenderTargetSpec {
    private final KeyId identifier;
    private final RenderTargetResolutionMode resolutionMode;
    private final int baseWidth;
    private final int baseHeight;
    private final float scaleX;
    private final float scaleY;
    private final List<KeyId> colorAttachments;
    @Nullable
    private final KeyId depthAttachment;
    @Nullable
    private final KeyId stencilAttachment;

    public ResolvedRenderTargetSpec(
            KeyId identifier,
            RenderTargetResolutionMode resolutionMode,
            int baseWidth,
            int baseHeight,
            float scaleX,
            float scaleY,
            List<KeyId> colorAttachments,
            @Nullable KeyId depthAttachment,
            @Nullable KeyId stencilAttachment) {
        this.identifier = Objects.requireNonNull(identifier, "identifier");
        this.resolutionMode = Objects.requireNonNull(resolutionMode, "resolutionMode");
        this.baseWidth = Math.max(1, baseWidth);
        this.baseHeight = Math.max(1, baseHeight);
        this.scaleX = scaleX;
        this.scaleY = scaleY;
        this.colorAttachments = List.copyOf(colorAttachments == null ? List.of() : colorAttachments);
        this.depthAttachment = depthAttachment;
        this.stencilAttachment = stencilAttachment;
        if (this.colorAttachments.isEmpty() && this.depthAttachment == null && this.stencilAttachment == null) {
            throw new IllegalArgumentException("Render target must expose at least one attachment: " + identifier);
        }
    }

    public KeyId identifier() {
        return identifier;
    }

    public RenderTargetResolutionMode resolutionMode() {
        return resolutionMode;
    }

    public int baseWidth() {
        return baseWidth;
    }

    public int baseHeight() {
        return baseHeight;
    }

    public float scaleX() {
        return scaleX;
    }

    public float scaleY() {
        return scaleY;
    }

    public List<KeyId> colorAttachments() {
        return Collections.unmodifiableList(colorAttachments);
    }

    @Nullable
    public KeyId depthAttachment() {
        return depthAttachment;
    }

    @Nullable
    public KeyId stencilAttachment() {
        return stencilAttachment;
    }

    public List<KeyId> attachmentIds() {
        List<KeyId> attachments = new ArrayList<>(colorAttachments);
        if (depthAttachment != null) {
            attachments.add(depthAttachment);
        }
        if (stencilAttachment != null) {
            attachments.add(stencilAttachment);
        }
        return attachments;
    }
}

