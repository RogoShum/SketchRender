package rogo.sketch.core.pipeline.kernel;

import rogo.sketch.core.packet.ExecutionKey;
import rogo.sketch.core.packet.ExecutionDomain;
import rogo.sketch.core.packet.RenderPacket;
import rogo.sketch.core.packet.RenderPacketKind;
import rogo.sketch.core.packet.ResourceBindingStamp;
import rogo.sketch.core.packet.ResourceSetKey;
import rogo.sketch.core.pipeline.PipelineConfig;
import rogo.sketch.core.pipeline.PipelineType;
import rogo.sketch.core.util.KeyId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record FrameCaptureSnapshot(
        List<StageCapture> stages,
        List<ResourceBindingCapture> resourceBindings,
        RenderStateCapture renderState
) {
    public FrameCaptureSnapshot {
        stages = stages != null ? List.copyOf(stages) : List.of();
        resourceBindings = resourceBindings != null ? List.copyOf(resourceBindings) : List.of();
        renderState = renderState != null ? renderState : RenderStateCapture.empty();
    }

    public static FrameCaptureSnapshot empty() {
        return new FrameCaptureSnapshot(List.of(), List.of(), RenderStateCapture.empty());
    }

    public FrameCaptureSnapshot withRenderState(RenderStateCapture nextRenderState) {
        return new FrameCaptureSnapshot(stages, resourceBindings, nextRenderState);
    }

    public static FrameCaptureSnapshot fromStagePlans(Map<KeyId, StageExecutionPlan> stagePlans) {
        if (stagePlans == null || stagePlans.isEmpty()) {
            return empty();
        }

        List<StageCapture> captures = new ArrayList<>(stagePlans.size());
        Map<ResourceSetKey, MutableResourceBindingCapture> resourceBindings = new LinkedHashMap<>();
        for (Map.Entry<KeyId, StageExecutionPlan> stageEntry : stagePlans.entrySet()) {
            StageExecutionPlan stagePlan = stageEntry.getValue();
            Map<PipelineType, Map<ExecutionKey, List<RenderPacket>>> packets =
                    stagePlan != null ? stagePlan.packets() : Map.of();
            Map<StateCaptureKey, MutableStateCapture> states = new LinkedHashMap<>();
            int packetCount = 0;
            int drawPacketCount = 0;

            for (Map.Entry<PipelineType, Map<ExecutionKey, List<RenderPacket>>> pipelineEntry : packets.entrySet()) {
                PipelineType pipelineType = pipelineEntry.getKey();
                for (Map.Entry<ExecutionKey, List<RenderPacket>> stateEntry : pipelineEntry.getValue().entrySet()) {
                    ExecutionKey stateKey = stateEntry.getKey();
                    for (RenderPacket packet : stateEntry.getValue()) {
                        packetCount++;
                        int drawCount = packet.packetKind() == RenderPacketKind.DRAW ? 1 : 0;
                        drawPacketCount += drawCount;
                        StateCaptureKey captureKey = new StateCaptureKey(
                                pipelineType,
                                stateKey != null ? stateKey.renderTargetKey() : PipelineConfig.DEFAULT_RENDER_TARGET_ID,
                                stateKey,
                                packet.resourceSetKey() != null ? packet.resourceSetKey() : ResourceSetKey.empty());
                        MutableStateCapture capture = states.computeIfAbsent(captureKey, ignored -> new MutableStateCapture());
                        capture.packetCount++;
                        capture.drawPacketCount += drawCount;
                        recordResourceBinding(resourceBindings, packet);
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
                        key.resourceSetKey.resourceLayoutKey(),
                        capture.packetCount,
                        capture.drawPacketCount));
            }

            captures.add(new StageCapture(
                    stageEntry.getKey(),
                    Collections.unmodifiableList(stateCaptures),
                    packetCount,
                    drawPacketCount));
        }
        return new FrameCaptureSnapshot(List.copyOf(captures), buildResourceBindingCaptures(resourceBindings), RenderStateCapture.empty());
    }

    public static FrameCaptureSnapshot fromPackets(Map<PipelineType, Map<ExecutionKey, List<RenderPacket>>> stagePackets) {
        if (stagePackets == null || stagePackets.isEmpty()) {
            return empty();
        }

        Map<KeyId, StageCaptureBuilder> builders = new LinkedHashMap<>();
        Map<ResourceSetKey, MutableResourceBindingCapture> resourceBindings = new LinkedHashMap<>();
        for (Map.Entry<PipelineType, Map<ExecutionKey, List<RenderPacket>>> pipelineEntry : stagePackets.entrySet()) {
            PipelineType pipelineType = pipelineEntry.getKey();
            for (Map.Entry<ExecutionKey, List<RenderPacket>> stateEntry : pipelineEntry.getValue().entrySet()) {
                ExecutionKey stateKey = stateEntry.getKey();
                for (RenderPacket packet : stateEntry.getValue()) {
                    StageCaptureBuilder stageBuilder = builders.computeIfAbsent(
                            packet.stageId(),
                            key -> new StageCaptureBuilder(key));
                    stageBuilder.add(pipelineType, stateKey, packet.resourceSetKey(), packet);
                    recordResourceBinding(resourceBindings, packet);
                }
            }
        }

        List<StageCapture> captures = new ArrayList<>(builders.size());
        for (StageCaptureBuilder builder : builders.values()) {
            captures.add(builder.build());
        }
        return new FrameCaptureSnapshot(List.copyOf(captures), buildResourceBindingCaptures(resourceBindings), RenderStateCapture.empty());
    }

    public record StageCapture(KeyId stageId, List<StateCapture> states, int packetCount, int drawPacketCount) {
    }

    public record StateCapture(
            PipelineType pipelineType,
            KeyId targetKey,
            ExecutionKey stateKey,
            ResourceSetKey resourceSetKey,
            KeyId resourceLayoutKey,
            int packetCount,
            int drawPacketCount
    ) {
    }

    public record ResourceBindingCapture(
            ResourceSetKey resourceSetKey,
            KeyId resourceLayoutKey,
            int bindingEntryCount,
            int packetCount
    ) {
    }

    public record RenderStateCapture(
            ExecutionDomain domain,
            KeyId shaderId,
            KeyId renderTargetId,
            KeyId resourceLayoutKey,
            ResourceBindingStamp resourceBindingStamp
    ) {
        public static RenderStateCapture empty() {
            return new RenderStateCapture(null, null, null, null, ResourceBindingStamp.NONE);
        }
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
                ExecutionKey stateKey,
                ResourceSetKey resourceSetKey,
                RenderPacket packet) {
            packetCount++;
            int drawCount = packet.packetKind() == RenderPacketKind.DRAW ? 1 : 0;
            drawPacketCount += drawCount;
            StateCaptureKey captureKey = new StateCaptureKey(
                    pipelineType,
                    stateKey != null ? stateKey.renderTargetKey() : PipelineConfig.DEFAULT_RENDER_TARGET_ID,
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
                        key.resourceSetKey.resourceLayoutKey(),
                        capture.packetCount,
                        capture.drawPacketCount));
            }
            return new StageCapture(stageId, Collections.unmodifiableList(captures), packetCount, drawPacketCount);
        }
    }

    private record StateCaptureKey(
            PipelineType pipelineType,
            KeyId targetKey,
            ExecutionKey stateKey,
            ResourceSetKey resourceSetKey
    ) {
    }

    private static final class MutableStateCapture {
        private int packetCount;
        private int drawPacketCount;
    }

    private static void recordResourceBinding(
            Map<ResourceSetKey, MutableResourceBindingCapture> captures,
            RenderPacket packet) {
        if (captures == null || packet == null) {
            return;
        }
        ResourceSetKey resourceSetKey = packet.resourceSetKey() != null ? packet.resourceSetKey() : ResourceSetKey.empty();
        MutableResourceBindingCapture capture = captures.computeIfAbsent(
                resourceSetKey,
                ignored -> new MutableResourceBindingCapture());
        capture.packetCount++;
        if (packet.bindingPlan() != null) {
            capture.bindingEntryCount = Math.max(capture.bindingEntryCount, packet.bindingPlan().entries().length);
        }
    }

    private static List<ResourceBindingCapture> buildResourceBindingCaptures(
            Map<ResourceSetKey, MutableResourceBindingCapture> captures) {
        if (captures == null || captures.isEmpty()) {
            return List.of();
        }
        List<ResourceBindingCapture> result = new ArrayList<>(captures.size());
        for (Map.Entry<ResourceSetKey, MutableResourceBindingCapture> entry : captures.entrySet()) {
            ResourceSetKey key = entry.getKey();
            MutableResourceBindingCapture capture = entry.getValue();
            result.add(new ResourceBindingCapture(
                    key,
                    key.resourceLayoutKey(),
                    capture.bindingEntryCount,
                    capture.packetCount));
        }
        return List.copyOf(result);
    }

    private static final class MutableResourceBindingCapture {
        private int bindingEntryCount;
        private int packetCount;
    }
}
