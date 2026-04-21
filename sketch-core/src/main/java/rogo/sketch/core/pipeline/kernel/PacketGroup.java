package rogo.sketch.core.pipeline.kernel;

import rogo.sketch.core.packet.ExecutionKey;
import rogo.sketch.core.packet.RenderPacket;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class PacketGroup {
    private final ExecutionKey stateKey;
    private final RenderPacket[] packets;
    private final List<RenderPacket> packetView;
    private final int groupHash;

    public PacketGroup(ExecutionKey stateKey, RenderPacket[] packets) {
        this.stateKey = stateKey;
        this.packets = packets != null ? packets.clone() : new RenderPacket[0];
        this.packetView = List.of(this.packets);
        this.groupHash = Objects.hash(this.stateKey, Arrays.hashCode(this.packets));
    }

    public ExecutionKey stateKey() {
        return stateKey;
    }

    public RenderPacket[] packets() {
        return packets.clone();
    }

    public List<RenderPacket> packetView() {
        return packetView;
    }

    public int groupHash() {
        return groupHash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof PacketGroup other)) {
            return false;
        }
        return groupHash == other.groupHash
                && Objects.equals(stateKey, other.stateKey)
                && Arrays.equals(packets, other.packets);
    }

    @Override
    public int hashCode() {
        return groupHash;
    }
}
