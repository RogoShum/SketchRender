package rogo.sketch.core.pipeline.kernel;

import rogo.sketch.core.packet.GeometryHandleKey;
import rogo.sketch.core.packet.PipelineStateKey;
import rogo.sketch.core.packet.RenderPacket;
import rogo.sketch.core.packet.ResourceBindingPlan;
import rogo.sketch.core.packet.ResourceSetKey;
import rogo.sketch.core.pipeline.PipelineType;
import rogo.sketch.core.resource.buffer.IndirectCommandBuffer;
import rogo.sketch.core.resource.buffer.VertexResource;
import rogo.sketch.core.shader.uniform.UniformGroupSet;
import rogo.sketch.core.util.KeyId;
import rogo.sketch.core.vertex.VertexResourceManager;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record FrameExecutionPlan(
        Map<KeyId, StageExecutionPlan> stagePlans,
        List<GeometryUploadPlan> geometryUploadPlans,
        List<ResourceUploadPlan> resourceUploadPlans,
        FrameCaptureSnapshot frameCaptureSnapshot
) {
    public FrameExecutionPlan {
        stagePlans = stagePlans != null ? normalizeStagePlans(stagePlans) : Map.of();
        geometryUploadPlans = geometryUploadPlans != null ? List.copyOf(geometryUploadPlans) : List.of();
        resourceUploadPlans = resourceUploadPlans != null ? List.copyOf(resourceUploadPlans) : List.of();
        frameCaptureSnapshot = frameCaptureSnapshot != null
                ? frameCaptureSnapshot
                : FrameCaptureSnapshot.fromStagePlans(stagePlans);
    }

    public static FrameExecutionPlan empty() {
        return new FrameExecutionPlan(Map.of(), List.of(), List.of(), FrameCaptureSnapshot.empty());
    }

    public static FrameExecutionPlan fromPackets(Map<PipelineType, Map<PipelineStateKey, List<RenderPacket>>> packets) {
        Map<KeyId, StageExecutionPlan> stagePlans = new LinkedHashMap<>();
        if (packets != null) {
            for (Map.Entry<PipelineType, Map<PipelineStateKey, List<RenderPacket>>> pipelineEntry : packets.entrySet()) {
                PipelineType pipelineType = pipelineEntry.getKey();
                for (Map.Entry<PipelineStateKey, List<RenderPacket>> stateEntry : pipelineEntry.getValue().entrySet()) {
                    PipelineStateKey stateKey = stateEntry.getKey();
                    for (RenderPacket packet : stateEntry.getValue()) {
                        if (packet == null) {
                            continue;
                        }
                        KeyId stageId = packet.stageId();
                        StageExecutionPlan existing = stagePlans.get(stageId);
                        Map<PipelineType, Map<PipelineStateKey, List<RenderPacket>>> stagePackets =
                                existing != null ? new LinkedHashMap<>(existing.packets()) : new LinkedHashMap<>();
                        Map<PipelineStateKey, List<RenderPacket>> states =
                                new LinkedHashMap<>(stagePackets.getOrDefault(pipelineType, Map.of()));
                        List<RenderPacket> packetList = new java.util.ArrayList<>(states.getOrDefault(stateKey, List.of()));
                        packetList.add(packet);
                        states.put(stateKey, List.copyOf(packetList));
                        stagePackets.put(pipelineType, Collections.unmodifiableMap(states));
                        stagePlans.put(stageId, StageExecutionPlan.fromPackets(stageId, stagePackets));
                    }
                }
            }
        }
        return new FrameExecutionPlan(
                stagePlans,
                List.of(),
                List.of(),
                FrameCaptureSnapshot.fromStagePlans(stagePlans));
    }

    public boolean isEmpty() {
        return stagePlans.isEmpty();
    }

    public Map<PipelineType, Map<PipelineStateKey, List<RenderPacket>>> stagePackets() {
        Map<PipelineType, Map<PipelineStateKey, List<RenderPacket>>> aggregated = new LinkedHashMap<>();
        for (StageExecutionPlan stagePlan : stagePlans.values()) {
            for (Map.Entry<PipelineType, Map<PipelineStateKey, List<RenderPacket>>> pipelineEntry : stagePlan.packets().entrySet()) {
                Map<PipelineStateKey, List<RenderPacket>> states = aggregated.computeIfAbsent(
                        pipelineEntry.getKey(),
                        ignored -> new LinkedHashMap<>());
                for (Map.Entry<PipelineStateKey, List<RenderPacket>> stateEntry : pipelineEntry.getValue().entrySet()) {
                    List<RenderPacket> packets = new java.util.ArrayList<>(states.getOrDefault(stateEntry.getKey(), List.of()));
                    packets.addAll(stateEntry.getValue());
                    states.put(stateEntry.getKey(), List.copyOf(packets));
                }
            }
        }
        return Collections.unmodifiableMap(aggregated);
    }

    public StageExecutionPlan stagePlan(KeyId stageId) {
        return stagePlans.get(stageId);
    }

    private static Map<KeyId, StageExecutionPlan> normalizeStagePlans(Map<KeyId, StageExecutionPlan> stagePlans) {
        Map<KeyId, StageExecutionPlan> normalized = new LinkedHashMap<>();
        for (Map.Entry<KeyId, StageExecutionPlan> entry : stagePlans.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            normalized.put(entry.getKey(), entry.getValue());
        }
        return Collections.unmodifiableMap(normalized);
    }

    public record GeometryUploadPlan(
            GeometryHandleKey geometryHandle,
            KeyId vertexLayoutKey,
            VertexResource vertexResource,
            VertexResourceManager.BuilderPair[] builders,
            IndirectCommandBuffer indirectBuffer,
            int vertexCount,
            int indexCount
    ) {
        public GeometryUploadPlan {
            builders = VertexResourceManager.snapshotBuilders(builders);
            vertexLayoutKey = vertexLayoutKey != null ? vertexLayoutKey : KeyId.of("sketch:empty_vertex_layout");
        }

        public void apply() {
            try {
                if (vertexResource != null) {
                    for (VertexResourceManager.BuilderPair builder : builders) {
                        if (builder == null || builder.builder() == null) {
                            continue;
                        }
                        vertexResource.upload(builder.key(), builder.builder());
                    }
                }
                if (indirectBuffer != null) {
                    indirectBuffer.bind();
                    try {
                        indirectBuffer.upload();
                    } finally {
                        IndirectCommandBuffer.unBind();
                    }
                }
            } finally {
                for (VertexResourceManager.BuilderPair builder : builders) {
                    if (builder != null && builder.builder() != null) {
                        builder.builder().close();
                    }
                }
            }
        }

        public boolean indexed() {
            return indexCount > 0;
        }
    }

    public record ResourceUploadPlan(
            KeyId stageId,
            ResourceSetKey resourceSetKey,
            ResourceBindingPlan bindingPlan,
            UniformGroupSet uniformGroups,
            KeyId shaderId,
            KeyId resourceLayoutKey
    ) {
        public ResourceUploadPlan {
            stageId = stageId != null ? stageId : KeyId.of("sketch:unknown_stage");
            resourceSetKey = resourceSetKey != null ? resourceSetKey : ResourceSetKey.empty();
            bindingPlan = bindingPlan != null ? bindingPlan : ResourceBindingPlan.empty();
            uniformGroups = uniformGroups != null ? uniformGroups : UniformGroupSet.empty();
            shaderId = shaderId != null ? shaderId : KeyId.of("sketch:unbound_shader");
            resourceLayoutKey = resourceLayoutKey != null ? resourceLayoutKey : bindingPlan.layoutKey();
        }
    }
}
