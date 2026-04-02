package rogo.sketch.core.pipeline.kernel;

import rogo.sketch.core.driver.state.snapshot.SnapshotScope;
import rogo.sketch.core.packet.BindRenderTargetPacket;
import rogo.sketch.core.packet.ClearPacket;
import rogo.sketch.core.packet.DispatchPacket;
import rogo.sketch.core.packet.DrawBuffersPacket;
import rogo.sketch.core.packet.DrawPacket;
import rogo.sketch.core.packet.GenerateMipmapPacket;
import rogo.sketch.core.packet.PipelineStateKey;
import rogo.sketch.core.packet.RenderPacket;
import rogo.sketch.core.pipeline.PipelineType;
import rogo.sketch.core.util.KeyId;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record StageExecutionPlan(
        KeyId stageId,
        Map<PipelineType, Map<PipelineStateKey, List<RenderPacket>>> packets,
        SnapshotScope stageSnapshotScope,
        StageResourceFootprint stageResourceFootprint
) {
    public StageExecutionPlan {
        packets = packets != null ? normalizePackets(packets) : Map.of();
        stageSnapshotScope = stageSnapshotScope != null ? stageSnapshotScope : SnapshotScope.empty();
        stageResourceFootprint = stageResourceFootprint != null
                ? stageResourceFootprint
                : StageResourceFootprint.fromPackets(packets, stageSnapshotScope);
    }

    public static StageExecutionPlan empty(KeyId stageId) {
        return new StageExecutionPlan(stageId, Map.of(), SnapshotScope.empty(), StageResourceFootprint.empty());
    }

    public static StageExecutionPlan fromPackets(
            KeyId stageId,
            Map<PipelineType, Map<PipelineStateKey, List<RenderPacket>>> packets) {
        SnapshotScope snapshotScope = deriveSnapshotScope(packets);
        return new StageExecutionPlan(
                stageId,
                packets,
                snapshotScope,
                StageResourceFootprint.fromPackets(packets, snapshotScope));
    }

    public boolean isEmpty() {
        return packets.isEmpty();
    }

    private static Map<PipelineType, Map<PipelineStateKey, List<RenderPacket>>> normalizePackets(
            Map<PipelineType, Map<PipelineStateKey, List<RenderPacket>>> packets) {
        Map<PipelineType, Map<PipelineStateKey, List<RenderPacket>>> normalized = new LinkedHashMap<>();
        for (Map.Entry<PipelineType, Map<PipelineStateKey, List<RenderPacket>>> pipelineEntry : packets.entrySet()) {
            if (pipelineEntry.getKey() == null || pipelineEntry.getValue() == null || pipelineEntry.getValue().isEmpty()) {
                continue;
            }
            Map<PipelineStateKey, List<RenderPacket>> states = new LinkedHashMap<>();
            for (Map.Entry<PipelineStateKey, List<RenderPacket>> stateEntry : pipelineEntry.getValue().entrySet()) {
                if (stateEntry.getKey() == null || stateEntry.getValue() == null || stateEntry.getValue().isEmpty()) {
                    continue;
                }
                states.put(stateEntry.getKey(), List.copyOf(stateEntry.getValue()));
            }
            if (!states.isEmpty()) {
                normalized.put(pipelineEntry.getKey(), Collections.unmodifiableMap(states));
            }
        }
        return Collections.unmodifiableMap(normalized);
    }

    private static SnapshotScope deriveSnapshotScope(
            Map<PipelineType, Map<PipelineStateKey, List<RenderPacket>>> packets) {
        if (packets == null || packets.isEmpty()) {
            return SnapshotScope.empty();
        }

        SnapshotScope.Builder builder = SnapshotScope.builder();
        boolean hasPackets = false;
        boolean touchesFramebuffer = false;
        boolean touchesVertexArrays = false;

        for (Map.Entry<PipelineType, Map<PipelineStateKey, List<RenderPacket>>> pipelineEntry : packets.entrySet()) {
            PipelineType pipelineType = pipelineEntry.getKey();
            for (Map.Entry<PipelineStateKey, List<RenderPacket>> stateEntry : pipelineEntry.getValue().entrySet()) {
                PipelineStateKey stateKey = stateEntry.getKey();
                if (stateKey != null) {
                    if (stateKey.renderState() != null) {
                        builder.addStatesFromRenderState(stateKey.renderState());
                    }
                    if (stateKey.shaderId() != null) {
                        builder.addState(SnapshotScope.StateType.SHADER_PROGRAM);
                    }
                    if (stateKey.bindingPlan() != null && !stateKey.bindingPlan().isEmpty()) {
                        addBindingPlanScope(builder);
                    }
                }

                for (RenderPacket packet : stateEntry.getValue()) {
                    if (packet == null) {
                        continue;
                    }
                    hasPackets = true;
                    if (packet.bindingPlan() != null && !packet.bindingPlan().isEmpty()) {
                        addBindingPlanScope(builder);
                    }
                    if (packet instanceof DrawPacket) {
                        touchesFramebuffer = true;
                        touchesVertexArrays = true;
                        continue;
                    }
                    if (packet instanceof ClearPacket clearPacket) {
                        touchesFramebuffer = true;
                        if (clearPacket.colorMask() != null && clearPacket.colorMask().length >= 4) {
                            builder.addState(SnapshotScope.StateType.COLOR_MASK);
                        }
                        if (clearPacket.clearDepth()) {
                            builder.addState(SnapshotScope.StateType.DEPTH_MASK);
                        }
                        continue;
                    }
                    if (packet instanceof BindRenderTargetPacket || packet instanceof DrawBuffersPacket) {
                        touchesFramebuffer = true;
                        continue;
                    }
                    if (packet instanceof DispatchPacket || PipelineType.COMPUTE.equals(pipelineType)) {
                        builder.addState(SnapshotScope.StateType.SHADER_PROGRAM);
                        continue;
                    }
                    if (packet instanceof GenerateMipmapPacket) {
                        builder.addState(SnapshotScope.StateType.SHADER_PROGRAM);
                    }
                }
            }
        }

        if (touchesFramebuffer || hasPackets) {
            builder.addState(SnapshotScope.StateType.FBO);
        }
        if (touchesVertexArrays) {
            builder.addState(SnapshotScope.StateType.VAO);
        }
        return builder.build();
    }

    private static void addBindingPlanScope(SnapshotScope.Builder builder) {
        builder.addState(SnapshotScope.StateType.SHADER_PROGRAM);
    }
}
