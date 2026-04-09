package rogo.sketch.core.pipeline.kernel;

import rogo.sketch.core.packet.GeometryHandleKey;
import rogo.sketch.core.packet.PipelineStateKey;
import rogo.sketch.core.packet.RenderPacket;
import rogo.sketch.core.packet.ResourceBindingPlan;
import rogo.sketch.core.packet.ResourceSetKey;
import rogo.sketch.core.api.model.SharedGeometrySourceSnapshot;
import rogo.sketch.core.backend.BackendIndirectBuffer;
import rogo.sketch.core.backend.BackendGeometryBinding;
import rogo.sketch.core.backend.BackendMutableGeometryBinding;
import rogo.sketch.core.backend.BackendInstalledBuffer;
import rogo.sketch.core.pipeline.PipelineType;
import rogo.sketch.core.resource.ResourceTypes;
import rogo.sketch.core.shader.uniform.UniformGroupSet;
import rogo.sketch.core.util.KeyId;
import rogo.sketch.core.vertex.GeometryResourceCoordinator;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
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
            long sharedSourceRef,
            SharedGeometrySourceSnapshot optionalSharedSourceSnapshot,
            BackendGeometryBinding targetGeometryBinding,
            BackendGeometryBinding sourceGeometryBinding,
            GeometryResourceCoordinator.BuilderPair[] builders,
            BackendInstalledBuffer indirectBuffer,
            List<VertexUploadSnapshot> dynamicVertexUploads,
            IndexUploadSnapshot optionalIndexUpload,
            IndirectUploadSnapshot optionalIndirectUpload,
            int vertexCount,
            int indexCount
    ) {
        public GeometryUploadPlan {
            sharedSourceRef = Math.max(sharedSourceRef, 0L);
            builders = builders != null ? builders.clone() : new GeometryResourceCoordinator.BuilderPair[0];
            vertexLayoutKey = vertexLayoutKey != null ? vertexLayoutKey : KeyId.of("sketch:empty_vertex_layout");
            dynamicVertexUploads = dynamicVertexUploads != null ? List.copyOf(dynamicVertexUploads) : List.of();
        }

        public static GeometryUploadPlan capture(
                GeometryHandleKey geometryHandle,
                KeyId vertexLayoutKey,
                SharedGeometrySourceSnapshot sharedGeometrySourceSnapshot,
                BackendGeometryBinding targetGeometryBinding,
                BackendGeometryBinding sourceGeometryBinding,
                GeometryResourceCoordinator.BuilderPair[] builders,
                BackendInstalledBuffer indirectBuffer,
                int vertexCount,
                int indexCount) {
            GeometryResourceCoordinator.BuilderPair[] snapshotBuilders = GeometryResourceCoordinator.snapshotBuilders(builders);
            return new GeometryUploadPlan(
                geometryHandle,
                vertexLayoutKey,
                resolveSharedSourceRef(geometryHandle, sharedGeometrySourceSnapshot),
                sharedGeometrySourceSnapshot,
                targetGeometryBinding,
                    sourceGeometryBinding,
                    snapshotBuilders,
                    indirectBuffer,
                    captureVertexUploads(geometryHandle, snapshotBuilders),
                    captureIndexUpload(geometryHandle, sharedGeometrySourceSnapshot, vertexCount, indexCount),
                    captureIndirectUpload(indirectBuffer),
                    vertexCount,
                    indexCount);
        }

        public void uploadTo(BackendGeometryBinding geometryBinding) {
            try {
                if (!(geometryBinding instanceof BackendMutableGeometryBinding mutableGeometryBinding)) {
                    return;
                }
                for (VertexUploadSnapshot vertexUpload : dynamicVertexUploads) {
                    if (vertexUpload == null || vertexUpload.data().length == 0) {
                        continue;
                    }
                    mutableGeometryBinding.uploadVertexComponent(vertexUpload.componentId(), vertexUpload.data());
                }
                if (optionalIndexUpload != null && optionalIndexUpload.data().length > 0 && mutableGeometryBinding.hasIndices()) {
                    mutableGeometryBinding.uploadIndices(decodeIndices(optionalIndexUpload));
                }
            } finally {
                // no-op: builder snapshots are released after all backend uploads finish
            }
        }

        public void uploadIndirect() {
            if (!(indirectBuffer instanceof BackendIndirectBuffer indirectCommandBuffer)) {
                return;
            }
            indirectCommandBuffer.bind();
            try {
                indirectCommandBuffer.upload();
            } finally {
                indirectCommandBuffer.unbind();
            }
        }

        public void releaseBuilderSnapshots() {
            for (GeometryResourceCoordinator.BuilderPair builder : builders) {
                if (builder != null && builder.builder() != null) {
                    builder.builder().close();
                }
            }
        }

        public boolean indexed() {
            return indexCount > 0;
        }

        private static long resolveSharedSourceRef(
                GeometryHandleKey geometryHandle,
                SharedGeometrySourceSnapshot sharedGeometrySourceSnapshot) {
            if (sharedGeometrySourceSnapshot != null && sharedGeometrySourceSnapshot.sharedSourceRef() > 0L) {
                return sharedGeometrySourceSnapshot.sharedSourceRef();
            }
            return 0L;
        }

        private static List<VertexUploadSnapshot> captureVertexUploads(
                GeometryHandleKey geometryHandle,
                GeometryResourceCoordinator.BuilderPair[] builders) {
            if (geometryHandle == null || builders == null || builders.length == 0) {
                return List.of();
            }
            Map<KeyId, rogo.sketch.core.data.format.ComponentSpec> specs = new LinkedHashMap<>();
            for (rogo.sketch.core.data.format.ComponentSpec componentSpec : geometryHandle.vertexBufferKey().components()) {
                specs.put(componentSpec.getId(), componentSpec);
            }

            java.util.ArrayList<VertexUploadSnapshot> uploads = new java.util.ArrayList<>();
            for (GeometryResourceCoordinator.BuilderPair builderPair : builders) {
                if (builderPair == null || builderPair.builder() == null || builderPair.key() == null) {
                    continue;
                }
                rogo.sketch.core.data.format.ComponentSpec componentSpec = specs.get(builderPair.key());
                if (componentSpec == null) {
                    continue;
                }
                byte[] bytes = copyBuilderBytes(builderPair.builder());
                if (bytes.length == 0) {
                    continue;
                }
                uploads.add(new VertexUploadSnapshot(
                        builderPair.key(),
                        componentSpec.getBindingPoint(),
                        componentSpec.getFormat().getStride(),
                        componentSpec.isInstanced(),
                        bytes,
                        builderPair.builder().getVertexCount()));
            }
            uploads.sort(java.util.Comparator
                    .comparingInt(VertexUploadSnapshot::bindingPoint)
                    .thenComparing(upload -> upload.componentId().toString()));
            return uploads;
        }

        private static IndexUploadSnapshot captureIndexUpload(
                GeometryHandleKey geometryHandle,
                SharedGeometrySourceSnapshot sharedGeometrySourceSnapshot,
                int vertexCount,
                int indexCount) {
            if (geometryHandle == null || geometryHandle.vertexBufferKey() == null || indexCount <= 0) {
                return null;
            }
            if (resolveSharedSourceRef(geometryHandle, sharedGeometrySourceSnapshot) > 0L) {
                return null;
            }
            rogo.sketch.core.pipeline.parmeter.RenderParameter renderParameter = geometryHandle.vertexBufferKey().renderParameter();
            if (renderParameter == null || renderParameter.indexMode() == null || !renderParameter.indexMode().isGenerated()) {
                return null;
            }
            rogo.sketch.core.data.PrimitiveType primitiveType = renderParameter.primitiveType();
            int[] indices = rogo.sketch.core.data.TopologyIndexGenerator.generateIndices(primitiveType, vertexCount);
            if (indices.length == 0) {
                return null;
            }
            ByteBuffer buffer = ByteBuffer.allocate(indices.length * Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN);
            for (int index : indices) {
                buffer.putInt(index);
            }
            return new IndexUploadSnapshot(buffer.array(), indices.length);
        }

        private static IndirectUploadSnapshot captureIndirectUpload(BackendInstalledBuffer indirectBuffer) {
            if (!(indirectBuffer instanceof BackendIndirectBuffer indirectCommandBuffer)
                    || indirectCommandBuffer.writePositionBytes() <= 0L
                    || indirectCommandBuffer.memoryAddress() == 0L) {
                return null;
            }
            int size = Math.toIntExact(indirectCommandBuffer.writePositionBytes());
            ByteBuffer source = MemoryUtil.memByteBuffer(indirectCommandBuffer.memoryAddress(), size);
            byte[] bytes = new byte[size];
            source.get(bytes);
            return new IndirectUploadSnapshot(
                    bytes,
                    indirectCommandBuffer.commandCount(),
                    (int) indirectCommandBuffer.strideBytes());
        }

        private static byte[] copyBuilderBytes(rogo.sketch.core.data.builder.VertexRecordWriter builder) {
            if (builder == null || builder.getWriteOffset() <= 0L) {
                return new byte[0];
            }
            ByteBuffer source = builder.asReadOnlyBuffer();
            byte[] bytes = new byte[source.remaining()];
            source.get(bytes);
            return bytes;
        }

        private static int[] decodeIndices(IndexUploadSnapshot snapshot) {
            ByteBuffer buffer = ByteBuffer.wrap(snapshot.data()).order(ByteOrder.LITTLE_ENDIAN);
            int[] indices = new int[snapshot.indexCount()];
            buffer.asIntBuffer().get(indices);
            return indices;
        }
    }

    public record VertexUploadSnapshot(
            KeyId componentId,
            int bindingPoint,
            int stride,
            boolean instanced,
            byte[] data,
            int vertexCount
    ) {
        public VertexUploadSnapshot {
            componentId = componentId != null ? componentId : ResourceTypes.VERTEX_BUFFER;
            data = data != null ? data.clone() : new byte[0];
        }
    }

    public record IndexUploadSnapshot(byte[] data, int indexCount) {
        public IndexUploadSnapshot {
            data = data != null ? data.clone() : new byte[0];
        }
    }

    public record IndirectUploadSnapshot(byte[] data, int drawCount, int stride) {
        public IndirectUploadSnapshot {
            data = data != null ? data.clone() : new byte[0];
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

