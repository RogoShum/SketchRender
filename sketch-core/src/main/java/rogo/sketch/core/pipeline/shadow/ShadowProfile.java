package rogo.sketch.core.pipeline.shadow;

import rogo.sketch.core.util.KeyId;

import java.util.Map;

public enum ShadowProfile {
    DEPTH_ONLY(
            KeyId.of("sketch_render", "shadow_profile_depth_only"),
            KeyId.of("sketch_render", "shadow_target"),
            KeyId.of("sketch_render", "shadow_depth"),
            Map.of(),
            true),
    DEPTH_PLUS_COLOR0(
            KeyId.of("sketch_render", "shadow_profile_depth_plus_color0"),
            KeyId.of("sketch_render", "shadow_target"),
            KeyId.of("sketch_render", "shadow_depth"),
            Map.of("shadow_color0", KeyId.of("sketch_render", "shadow_color0")),
            false);

    private final KeyId profileId;
    private final KeyId renderTargetId;
    private final KeyId shadowMapTextureId;
    private final Map<String, KeyId> exportedTextures;
    private final boolean depthOnly;

    ShadowProfile(
            KeyId profileId,
            KeyId renderTargetId,
            KeyId shadowMapTextureId,
            Map<String, KeyId> exportedTextures,
            boolean depthOnly) {
        this.profileId = profileId;
        this.renderTargetId = renderTargetId;
        this.shadowMapTextureId = shadowMapTextureId;
        this.exportedTextures = Map.copyOf(exportedTextures);
        this.depthOnly = depthOnly;
    }

    public KeyId profileId() {
        return profileId;
    }

    public KeyId renderTargetId() {
        return renderTargetId;
    }

    public KeyId shadowMapTextureId() {
        return shadowMapTextureId;
    }

    public Map<String, KeyId> exportedTextures() {
        return exportedTextures;
    }

    public boolean depthOnly() {
        return depthOnly;
    }

    public boolean exportsAttachment(String alias) {
        return alias != null && exportedTextures.containsKey(alias);
    }

    public KeyId exportedTextureId(String alias) {
        return alias != null ? exportedTextures.get(alias) : null;
    }
}
