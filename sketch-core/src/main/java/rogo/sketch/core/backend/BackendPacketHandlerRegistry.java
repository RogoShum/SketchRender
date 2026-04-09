package rogo.sketch.core.backend;

import rogo.sketch.core.packet.RenderPacket;
import rogo.sketch.core.packet.RenderPacketType;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class BackendPacketHandlerRegistry<H> {
    private final Map<RenderPacketType, H> handlers = new ConcurrentHashMap<>();

    public void register(RenderPacketType type, H handler) {
        if (type == null || handler == null) {
            return;
        }
        handlers.put(type, handler);
    }

    public H handlerFor(RenderPacket packet) {
        if (packet == null || packet.packetType() == null) {
            return null;
        }
        return handlers.get(packet.packetType());
    }

    public H handlerFor(RenderPacketType type) {
        if (type == null) {
            return null;
        }
        return handlers.get(type);
    }
}
