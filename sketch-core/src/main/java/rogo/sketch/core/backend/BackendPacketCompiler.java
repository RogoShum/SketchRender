package rogo.sketch.core.backend;

import rogo.sketch.core.packet.RenderPacket;

public interface BackendPacketCompiler {
    default RenderPacket compile(RenderPacket packet) {
        return packet;
    }
}
