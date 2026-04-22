package rogo.sketch.core.pipeline.kernel;

import rogo.sketch.core.driver.state.snapshot.SnapshotScope;
import rogo.sketch.core.packet.GeometryHandleKey;
import rogo.sketch.core.packet.ExecutionKey;
import rogo.sketch.core.packet.RenderPacket;
import rogo.sketch.core.packet.RenderPacketKind;
import rogo.sketch.core.packet.ResourceSetKey;
import rogo.sketch.core.pipeline.PipelineType;
import rogo.sketch.core.util.KeyId;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record StageResourceFootprint(
        Set<PipelineType> pipelineTypes,
        Set<KeyId> shaderIds,
        Set<KeyId> renderTargetKeys,
        Set<KeyId> vertexLayoutKeys,
        Set<KeyId> resourceLayoutKeys,
        Set<ResourceSetKey> resourceSetKeys,
        Set<GeometryHandleKey> geometryHandles,
        EnumSet<SnapshotScope.StateType> stateTypes,
        int packetCount,
        int drawPacketCount
) {
    public StageResourceFootprint {
        pipelineTypes = pipelineTypes != null ? Set.copyOf(pipelineTypes) : Set.of();
        shaderIds = shaderIds != null ? Set.copyOf(shaderIds) : Set.of();
        renderTargetKeys = renderTargetKeys != null ? Set.copyOf(renderTargetKeys) : Set.of();
        vertexLayoutKeys = vertexLayoutKeys != null ? Set.copyOf(vertexLayoutKeys) : Set.of();
        resourceLayoutKeys = resourceLayoutKeys != null ? Set.copyOf(resourceLayoutKeys) : Set.of();
        resourceSetKeys = resourceSetKeys != null ? Set.copyOf(resourceSetKeys) : Set.of();
        geometryHandles = geometryHandles != null ? Set.copyOf(geometryHandles) : Set.of();
        stateTypes = stateTypes != null && !stateTypes.isEmpty()
                ? EnumSet.copyOf(stateTypes)
                : EnumSet.noneOf(SnapshotScope.StateType.class);
    }

    public static StageResourceFootprint empty() {
        return new StageResourceFootprint(
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                EnumSet.noneOf(SnapshotScope.StateType.class),
                0,
                0);
    }

    public static StageResourceFootprint fromPackets(
            Map<PipelineType, Map<ExecutionKey, List<RenderPacket>>> packets,
            SnapshotScope snapshotScope) {
        if (packets == null || packets.isEmpty()) {
            return empty();
        }

        Set<PipelineType> pipelineTypes = new LinkedHashSet<>();
        Set<KeyId> shaderIds = new LinkedHashSet<>();
        Set<KeyId> renderTargetKeys = new LinkedHashSet<>();
        Set<KeyId> vertexLayoutKeys = new LinkedHashSet<>();
        Set<KeyId> resourceLayoutKeys = new LinkedHashSet<>();
        Set<ResourceSetKey> resourceSetKeys = new LinkedHashSet<>();
        Set<GeometryHandleKey> geometryHandles = new LinkedHashSet<>();
        int packetCount = 0;
        int drawPacketCount = 0;

        for (Map.Entry<PipelineType, Map<ExecutionKey, List<RenderPacket>>> pipelineEntry : packets.entrySet()) {
            PipelineType pipelineType = pipelineEntry.getKey();
            if (pipelineType == null) {
                continue;
            }
            pipelineTypes.add(pipelineType);

            for (Map.Entry<ExecutionKey, List<RenderPacket>> stateEntry : pipelineEntry.getValue().entrySet()) {
                ExecutionKey stateKey = stateEntry.getKey();
                if (stateKey != null) {
                    addIfPresent(shaderIds, stateKey.shaderId());
                    addIfPresent(renderTargetKeys, stateKey.renderTargetKey());
                    addIfPresent(vertexLayoutKeys, stateKey.vertexLayoutKey());
                    addIfPresent(resourceLayoutKeys, stateKey.resourceLayoutKey());
                }

                for (RenderPacket packet : stateEntry.getValue()) {
                    if (packet == null) {
                        continue;
                    }
                    packetCount++;
                    addIfPresent(resourceSetKeys, packet.resourceSetKey());
                    if (packet.packetKind() == RenderPacketKind.DRAW && packet instanceof rogo.sketch.core.packet.DrawPacket drawPacket) {
                        drawPacketCount++;
                        addIfPresent(geometryHandles, drawPacket.geometryHandle());
                    }
                }
            }
        }

        EnumSet<SnapshotScope.StateType> stateTypes = snapshotScope != null && !snapshotScope.getStateTypes().isEmpty()
                ? EnumSet.copyOf(snapshotScope.getStateTypes())
                : EnumSet.noneOf(SnapshotScope.StateType.class);
        return new StageResourceFootprint(
                pipelineTypes,
                shaderIds,
                renderTargetKeys,
                vertexLayoutKeys,
                resourceLayoutKeys,
                resourceSetKeys,
                geometryHandles,
                stateTypes,
                packetCount,
                drawPacketCount);
    }

    private static <T> void addIfPresent(Set<T> target, T value) {
        if (target != null && value != null) {
            target.add(value);
        }
    }

    public List<KeyId> resourceKeysForDiagnostics() {
        List<KeyId> keys = new ArrayList<>(resourceLayoutKeys);
        keys.sort(KeyId::compareTo);
        return List.copyOf(keys);
    }
}

