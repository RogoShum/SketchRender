package rogo.sketch.core.packet;

import rogo.sketch.core.util.KeyId;

public record RenderPacketType(
        KeyId id,
        RenderPacketKind kind
) {
    public static final RenderPacketType DRAW = new RenderPacketType(KeyId.of("sketch:draw_packet"), RenderPacketKind.DRAW);
    public static final RenderPacketType DISPATCH = new RenderPacketType(KeyId.of("sketch:dispatch_packet"), RenderPacketKind.DISPATCH);
    public static final RenderPacketType CLEAR = new RenderPacketType(KeyId.of("sketch:clear_packet"), RenderPacketKind.CLEAR);
    public static final RenderPacketType COPY_TEXTURE = new RenderPacketType(
            KeyId.of("sketch:copy_texture_packet"),
            RenderPacketKind.COPY_TEXTURE);
    public static final RenderPacketType GENERATE_MIPMAP = new RenderPacketType(
            KeyId.of("sketch:generate_mipmap_packet"),
            RenderPacketKind.GENERATE_MIPMAP);

    public RenderPacketType {
        if (id == null) {
            throw new IllegalArgumentException("RenderPacketType id must not be null");
        }
        kind = kind != null ? kind : RenderPacketKind.CUSTOM;
    }

    public static RenderPacketType custom(KeyId id) {
        return new RenderPacketType(id, RenderPacketKind.CUSTOM);
    }
}
