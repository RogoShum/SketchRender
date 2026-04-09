package rogo.sketch.core.pipeline.kernel;

import rogo.sketch.core.packet.PipelineStateKey;
import rogo.sketch.core.packet.RenderPacket;
import rogo.sketch.core.packet.RenderPacketKind;
import rogo.sketch.core.packet.ResourceSetKey;
import rogo.sketch.core.pipeline.PipelineType;
import rogo.sketch.core.util.KeyId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record FrameCaptureSnapshot(List<StageCapture> stages) {
    public static FrameCaptureSnapshot empty() {
        return new FrameCaptureSnapshot(List.of());
    }

    public static FrameCaptureSnapshot fromStagePlans(Map<KeyId, StageExecutionPlan> stagePlans) {
        if (stagePlans == null || stagePlans.isEmpty()) {
            return empty();
        }

        List<StageCapture> captures = new ArrayList<>(stagePlans.size());
        for (Map.Entry<KeyId, StageExecutionPlan> stageEntry : stagePlans.entrySet()) {
            StageExecutionPlan stagePlan = stageEntry.getValue();
            Map<PipelineType, Map<PipelineStateKey, List<RenderPacket>>> packets =
                    stagePlan != null ? stagePlan.packets() : Map.of();
            Map<StateCaptureKey, MutableStateCapture> states = new LinkedHashMap<>();
            int packetCount = 0;
            int drawPacketCount = 0;

            for (Map.Entry<PipelineType, Map<PipelineStateKey, List<RenderPacket>>> pipelineEntry : packets.entrySet()) {
                PipelineType pipelineType = pipelineEntry.getKey();
                for (Map.Entry<PipelineStateKey, List<RenderPacket>> stateEntry : pipelineEntry.getValue().entrySet()) {
                    PipelineStateKey stateKey = stateEntry.getKey();
                    for (RenderPacket packet : stateEntry.getValue()) {
                        packetCount++;
                        int drawCount = packet.packetKind() == RenderPacketKind.DRAW ? 1 : 0;
                        drawPacketCount += drawCount;
                        StateCaptureKey captureKey = new StateCaptureKey(
                                pipelineType,
                                stateKey != null ? stateKey.renderTargetKey() : KeyId.of("minecraft:main_target"),
                                stateKey,
                                packet.resourceSetKey() != null ? packet.resourceSetKey() : ResourceSetKey.empty());
                        MutableStateCapture capture = states.computeIfAbsent(captureKey, ignored -> new MutableStateCapture());
                        capture.packetCount++;
                        capture.drawPacketCount += drawCount;
                    }
                }
            }

            List<StateCapture> stateCaptures = new ArrayList<>(states.size());
            for (Map.Entry<StateCaptureKey, MutableStateCapture> stateEntry : states.entrySet()) {
                StateCaptureKey key = stateEntry.getKey();
                MutableStateCapture capture = stateEntry.getValue();
                stateCaptures.add(new StateCapture(
                        key.pipelineType,
                        key.targetKey,
                        key.stateKey,
                        key.resourceSetKey,
                        capture.packetCount,
                        capture.drawPacketCount));
            }

            captures.add(new StageCapture(
                    stageEntry.getKey(),
                    Collections.unmodifiableList(stateCaptures),
                    packetCount,
                    drawPacketCount));
        }
        return new FrameCaptureSnapshot(List.copyOf(captures));
    }

    public static FrameCaptureSnapshot fromPackets(Map<PipelineType, Map<PipelineStateKey, List<RenderPacket>>> stagePackets) {
        if (stagePackets == null || stagePackets.isEmpty()) {
            return empty();
        }

        Map<KeyId, StageCaptureBuilder> builders = new LinkedHashMap<>();
        for (Map.Entry<PipelineType, Map<PipelineStateKey, List<RenderPacket>>> pipelineEntry : stagePackets.entrySet()) {
            PipelineType pipelineType = pipelineEntry.getKey();
            for (Map.Entry<PipelineStateKey, List<RenderPacket>> stateEntry : pipelineEntry.getValue().entrySet()) {
                PipelineStateKey stateKey = stateEntry.getKey();
                for (RenderPacket packet : stateEntry.getValue()) {
                    StageCaptureBuilder stageBuilder = builders.computeIfAbsent(
                            packet.stageId(),
                            key -> new StageCaptureBuilder(key));
                    stageBuilder.add(pipelineType, stateKey, packet.resourceSetKey(), packet);
                }
            }
        }

        List<StageCapture> captures = new ArrayList<>(builders.size());
        for (StageCaptureBuilder builder : builders.values()) {
            captures.add(builder.build());
        }
        return new FrameCaptureSnapshot(List.copyOf(captures));
    }

    public record StageCapture(KeyId stageId, List<StateCapture> states, int packetCount, int drawPacketCount) {
    }

    public record StateCapture(
            PipelineType pipelineType,
            KeyId targetKey,
            PipelineStateKey stateKey,
            ResourceSetKey resourceSetKey,
            int packetCount,
            int drawPacketCount
    ) {
    }

    private static final class StageCaptureBuilder {
        private final KeyId stageId;
        private final Map<StateCaptureKey, MutableStateCapture> states = new LinkedHashMap<>();
        private int packetCount;
        private int drawPacketCount;

        private StageCaptureBuilder(KeyId stageId) {
            this.stageId = stageId;
        }

        private void add(
                PipelineType pipelineType,
                PipelineStateKey stateKey,
                ResourceSetKey resourceSetKey,
                RenderPacket packet) {
            packetCount++;
            int drawCount = packet.packetKind() == RenderPacketKind.DRAW ? 1 : 0;
            drawPacketCount += drawCount;
            StateCaptureKey captureKey = new StateCaptureKey(
                    pipelineType,
                    stateKey != null ? stateKey.renderTargetKey() : KeyId.of("minecraft:main_target"),
                    stateKey,
                    resourceSetKey != null ? resourceSetKey : ResourceSetKey.empty());
            MutableStateCapture capture = states.computeIfAbsent(captureKey, ignored -> new MutableStateCapture());
            capture.packetCount++;
            capture.drawPacketCount += drawCount;
        }

        private StageCapture build() {
            List<StateCapture> captures = new ArrayList<>(states.size());
            for (Map.Entry<StateCaptureKey, MutableStateCapture> entry : states.entrySet()) {
                StateCaptureKey key = entry.getKey();
                MutableStateCapture capture = entry.getValue();
                captures.add(new StateCapture(
                        key.pipelineType,
                        key.targetKey,
                        key.stateKey,
                        key.resourceSetKey,
                        capture.packetCount,
                        capture.drawPacketCount));
            }
            return new StageCapture(stageId, Collections.unmodifiableList(captures), packetCount, drawPacketCount);
        }
    }

    private record StateCaptureKey(
            PipelineType pipelineType,
            KeyId targetKey,
            PipelineStateKey stateKey,
            ResourceSetKey resourceSetKey
    ) {
    }

    private static final class MutableStateCapture {
        private int packetCount;
        private int drawPacketCount;
    }
}
