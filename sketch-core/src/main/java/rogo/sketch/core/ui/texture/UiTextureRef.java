package rogo.sketch.core.ui.texture;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.backend.GpuHandle;

public record UiTextureRef(
        UiTextureKind kind,
        @Nullable String resourceId,
        GpuHandle handle
) {
    public UiTextureRef {
        if (kind == null) {
            throw new IllegalArgumentException("kind must not be null");
        }
        handle = handle != null ? handle : GpuHandle.NONE;
    }

    public static UiTextureRef minecraftResource(String resourceId) {
        if (resourceId == null || resourceId.isBlank()) {
            throw new IllegalArgumentException("resourceId must not be blank");
        }
        return new UiTextureRef(UiTextureKind.MINECRAFT_RESOURCE, resourceId, GpuHandle.NONE);
    }

    public static UiTextureRef gpuHandle(GpuHandle handle) {
        if (handle == null || !handle.isValid()) {
            throw new IllegalArgumentException("handle must be valid");
        }
        return new UiTextureRef(UiTextureKind.GPU_HANDLE, null, handle);
    }

    public boolean isRenderable() {
        return switch (kind) {
            case MINECRAFT_RESOURCE -> resourceId != null && !resourceId.isBlank();
            case GPU_HANDLE -> handle.isValid();
        };
    }
}
