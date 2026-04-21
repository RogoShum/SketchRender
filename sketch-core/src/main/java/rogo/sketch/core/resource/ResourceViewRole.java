package rogo.sketch.core.resource;

import rogo.sketch.core.util.KeyId;

import java.util.Locale;

public enum ResourceViewRole {
    SAMPLED_TEXTURE,
    STORAGE_IMAGE,
    UNIFORM_BUFFER,
    STORAGE_BUFFER,
    ATTACHMENT,
    TRANSFER_SRC,
    TRANSFER_DST;

    public static ResourceViewRole parse(String value, ResourceViewRole fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return switch (value.trim().toUpperCase(Locale.ROOT).replace('-', '_')) {
            case "SAMPLED", "SAMPLED_TEXTURE", "TEXTURE" -> SAMPLED_TEXTURE;
            case "STORAGE", "STORAGE_IMAGE", "IMAGE" -> STORAGE_IMAGE;
            case "UNIFORM", "UNIFORM_BUFFER", "UBO" -> UNIFORM_BUFFER;
            case "STORAGE_BUFFER", "SSBO", "SHADER_STORAGE_BUFFER" -> STORAGE_BUFFER;
            case "ATTACHMENT", "COLOR_ATTACHMENT", "DEPTH_ATTACHMENT" -> ATTACHMENT;
            case "TRANSFER_SRC", "COPY_SRC" -> TRANSFER_SRC;
            case "TRANSFER_DST", "COPY_DST" -> TRANSFER_DST;
            default -> throw new IllegalArgumentException("Unsupported resource view role: " + value);
        };
    }

    public static ResourceViewRole defaultForResourceType(KeyId resourceType) {
        KeyId normalized = ResourceTypes.normalize(resourceType);
        if (ResourceTypes.IMAGE.equals(normalized)) {
            return STORAGE_IMAGE;
        }
        if (ResourceTypes.TEXTURE.equals(normalized)) {
            return SAMPLED_TEXTURE;
        }
        if (ResourceTypes.UNIFORM_BUFFER.equals(normalized)) {
            return UNIFORM_BUFFER;
        }
        if (ResourceTypes.STORAGE_BUFFER.equals(normalized) || ResourceTypes.ATOMIC_COUNTER.equals(normalized)) {
            return STORAGE_BUFFER;
        }
        if (ResourceTypes.RENDER_TARGET.equals(normalized)) {
            return ATTACHMENT;
        }
        return SAMPLED_TEXTURE;
    }

    public static ResourceAccess defaultAccessFor(ResourceViewRole role) {
        if (role == null) {
            return ResourceAccess.READ;
        }
        return switch (role) {
            case SAMPLED_TEXTURE, UNIFORM_BUFFER, TRANSFER_SRC -> ResourceAccess.READ;
            case STORAGE_IMAGE, STORAGE_BUFFER -> ResourceAccess.READ_WRITE;
            case ATTACHMENT, TRANSFER_DST -> ResourceAccess.WRITE;
        };
    }

    public KeyId descriptorResourceType(KeyId fallbackType) {
        return switch (this) {
            case SAMPLED_TEXTURE -> ResourceTypes.TEXTURE;
            case STORAGE_IMAGE -> ResourceTypes.IMAGE;
            case UNIFORM_BUFFER -> ResourceTypes.UNIFORM_BUFFER;
            case STORAGE_BUFFER -> ResourceTypes.STORAGE_BUFFER;
            case ATTACHMENT, TRANSFER_SRC, TRANSFER_DST -> ResourceTypes.normalize(fallbackType);
        };
    }
}
