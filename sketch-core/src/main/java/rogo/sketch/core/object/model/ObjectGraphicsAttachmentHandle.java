package rogo.sketch.core.object.model;

import rogo.sketch.core.object.ObjectGraphicsHandle;
import rogo.sketch.core.util.KeyId;

/**
 * Stable external handle for attachment-backed render parts.
 */
public record ObjectGraphicsAttachmentHandle(
        long attachmentId,
        ObjectGraphicsHandle rootHandle,
        KeyId partKey
) {
}
