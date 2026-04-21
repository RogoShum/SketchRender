package rogo.sketch.core.pipeline.kernel;

import rogo.sketch.core.packet.ExecutionKey;
import rogo.sketch.core.packet.RenderPacket;
import rogo.sketch.core.pipeline.PipelineType;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class PipelineExecutionSlice {
    private final PipelineType pipelineType;
    private final PacketGroup[] groups;
    private final Map<ExecutionKey, List<RenderPacket>> packetMap;
    private final int pipelineHash;

    public PipelineExecutionSlice(
            PipelineType pipelineType,
            PacketGroup[] groups,
            Map<ExecutionKey, List<RenderPacket>> packetMap,
            int pipelineHash) {
        this.pipelineType = pipelineType;
        this.groups = groups != null ? groups.clone() : new PacketGroup[0];
        this.packetMap = packetMap != null ? packetMap : Map.of();
        this.pipelineHash = pipelineHash;
    }

    public PipelineType pipelineType() {
        return pipelineType;
    }

    public PacketGroup[] groups() {
        return groups.clone();
    }

    public PacketGroup groupAt(int index) {
        return groups[index];
    }

    public int groupCount() {
        return groups.length;
    }

    public Map<ExecutionKey, List<RenderPacket>> packetMap() {
        return packetMap;
    }

    public int pipelineHash() {
        return pipelineHash;
    }

    public boolean isEmpty() {
        return groups.length == 0;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof PipelineExecutionSlice other)) {
            return false;
        }
        return pipelineHash == other.pipelineHash
                && pipelineType == other.pipelineType
                && Arrays.equals(groups, other.groups);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pipelineType, pipelineHash);
    }
}
