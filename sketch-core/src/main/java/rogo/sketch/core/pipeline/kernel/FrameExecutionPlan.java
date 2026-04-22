package rogo.sketch.core.pipeline.kernel;

import rogo.sketch.core.packet.GeometryHandleKey;
import rogo.sketch.core.packet.ExecutionKey;
import rogo.sketch.core.packet.RenderPacket;
import rogo.sketch.core.packet.ResourceBindingPlan;
import rogo.sketch.core.packet.ResourceSetKey;
import rogo.sketch.core.api.model.SharedGeometrySourceSnapshot;
import rogo.sketch.core.backend.BackendIndirectBuffer;
import rogo.sketch.core.backend.BackendGeometryBinding;
import rogo.sketch.core.backend.BackendMutableGeometryBinding;
import rogo.sketch.core.backend.BackendInstalledBuffer;
import rogo.sketch.core.pipeline.PipelineType;
import rogo.sketch.core.resource.GraphicsResourceManager;
import rogo.sketch.core.resource.ResourceTypes;
import rogo.sketch.core.shader.uniform.UniformGroupSet;
import rogo.sketch.core.util.KeyId;
import rogo.sketch.core.vertex.GeometryResourceCoordinator;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record FrameExecutionPlan(
        Map<KeyId, StageExecutionPlan> stagePlans,
        List<GeometryUploadPlan> geometryUploadPlans,
        List<ResourceUploadPlan> resourceUploadPlans,
        Map<GeometryHandleKey, Set<PipelineType>> geometryConsumers,
        FrameCaptureSnapshot frameCaptureSnapshot
) {
    public FrameExecutionPlan {
        stagePlans = stagePlans != null ? normalizeStagePlans(stagePlans) : Map.of();
        geometryUploadPlans = geometryUploadPlans != null ? List.copyOf(geometryUploadPlans) : List.of();
        resourceUploadPlans = resourceUploadPlans != null ? List.copyOf(resourceUploadPlans) : List.of();
        geometryConsumers = geometryConsumers != null
                ? normalizeGeometryConsumers(geometryConsumers)
                : deriveGeometryConsumers(stagePlans);
        frameCaptureSnapshot = frameCaptureSnapshot != null
                ? frameCaptureSnapshot
                : FrameCaptureSnapshot.fromStagePlans(stagePlans);
    }

    public static FrameExecutionPlan empty() {
        return new FrameExecutionPlan(Map.of(), List.of(), List.of(), Map.of(), FrameCaptureSnapshot.empty());
    }

    public static FrameExecutionPlan fromPackets(Map<PipelineType, Map<ExecutionKey, List<RenderPacket>>> packets) {
        return fromPackets(packets, null);
    }

    public static FrameExecutionPlan fromPackets(
            Map<PipelineType, Map<ExecutionKey, List<RenderPacket>>> packets,
            GraphicsResourceManager resourceManager) {
        Map<KeyId, Map<PipelineType, Map<ExecutionKey, java.util.ArrayList<RenderPacket>>>> packetBuilder = new LinkedHashMap<>();
        if (packets != null) {
            for (Map.Entry<PipelineType, Map<ExecutionKey, List<RenderPacket>>> pipelineEntry : packets.entrySet()) {
                PipelineType pipelineType = pipelineEntry.getKey();
                if (pipelineType == null || pipelineEntry.getValue() == null) {
                    continue;
                }
                for (Map.Entry<ExecutionKey, List<RenderPacket>> stateEntry : pipelineEntry.getValue().entrySet()) {
                    ExecutionKey stateKey = stateEntry.getKey();
                    if (stateKey == null || stateEntry.getValue() == null) {
                        continue;
                    }
                    for (RenderPacket packet : stateEntry.getValue()) {
                        if (packet == null) {
                            continue;
                        }
                        packetBuilder
                                .computeIfAbsent(packet.stageId(), ignored -> new LinkedHashMap<>())
                                .computeIfAbsent(pipelineType, ignored -> new LinkedHashMap<>())
                                .computeIfAbsent(stateKey, ignored -> new java.util.ArrayList<>())
                                .add(packet);
                    }
                }
            }
        }

        Map<KeyId, StageExecutionPlan> stagePlans = new LinkedHashMap<>();
        for (Map.Entry<KeyId, Map<PipelineType, Map<ExecutionKey, java.util.ArrayList<RenderPacket>>>> stageEntry : packetBuilder.entrySet()) {
            Map<PipelineType, Map<ExecutionKey, List<RenderPacket>>> stagePackets = new LinkedHashMap<>();
            for (Map.Entry<PipelineType, Map<ExecutionKey, java.util.ArrayList<RenderPacket>>> pipelineEntry : stageEntry.getValue().entrySet()) {
                Map<ExecutionKey, List<RenderPacket>> statePackets = new LinkedHashMap<>();
                for (Map.Entry<ExecutionKey, java.util.ArrayList<RenderPacket>> stateEntry : pipelineEntry.getValue().entrySet()) {
                    statePackets.put(stateEntry.getKey(), List.copyOf(stateEntry.getValue()));
                }
                stagePackets.put(pipelineEntry.getKey(), Collections.unmodifiableMap(statePackets));
            }
            stagePlans.put(stageEntry.getKey(), StageExecutionPlan.fromPackets(stageEntry.getKey(), stagePackets, resourceManager));
        }
        return new FrameExecutionPlan(
                stagePlans,
                List.of(),
                List.of(),
                null,
                FrameCaptureSnapshot.fromStagePlans(stagePlans));
    }

    public boolean isEmpty() {
        return stagePlans.isEmpty();
    }

    public Map<PipelineType, Map<ExecutionKey, List<RenderPacket>>> stagePackets() {
        Map<PipelineType, Map<ExecutionKey, List<RenderPacket>>> aggregated = new LinkedHashMap<>();
        for (StageExecutionPlan stagePlan : stagePlans.values()) {
            for (PipelineExecutionSlice pipelineSlice : stagePlan.pipelineSlices()) {
                Map<ExecutionKey, List<RenderPacket>> states = aggregated.computeIfAbsent(
                        pipelineSlice.pipelineType(),
                        ignored -> new LinkedHashMap<>());
                for (int i = 0; i < pipelineSlice.groupCount(); i++) {
                    PacketGroup group = pipelineSlice.groupAt(i);
                    List<RenderPacket> packets = new java.util.ArrayList<>(states.getOrDefault(group.stateKey(), List.of()));
                    packets.addAll(group.packetView());
                    states.put(group.stateKey(), List.copyOf(packets));
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

    private static Map<GeometryHandleKey, Set<PipelineType>> deriveGeometryConsumers(
            Map<KeyId, StageExecutionPlan> stagePlans) {
        if (stagePlans == null || stagePlans.isEmpty()) {
            return Map.of();
        }
        Map<GeometryHandleKey, Set<PipelineType>> consumers = new LinkedHashMap<>();
        for (StageExecutionPlan stagePlan : stagePlans.values()) {
            if (stagePlan == null || stagePlan.isEmpty()) {
                continue;
            }
            for (PipelineExecutionSlice pipelineSlice : stagePlan.pipelineSlices()) {
                PipelineType pipelineType = pipelineSlice.pipelineType();
                if (pipelineType == null) {
                    continue;
                }
                for (int i = 0; i < pipelineSlice.groupCount(); i++) {
                    RenderPacket[] packets = pipelineSlice.groupAt(i).packets();
                    for (RenderPacket packet : packets) {
                        if (packet instanceof rogo.sketch.core.packet.DrawPacket drawPacket
                                && drawPacket.geometryHandle() != null) {
                            consumers.computeIfAbsent(drawPacket.geometryHandle(), ignored -> new LinkedHashSet<>())
                                    .add(pipelineType);
                        }
                    }
                }
            }
        }
        return normalizeGeometryConsumers(consumers);
    }

    private static Map<GeometryHandleKey, Set<PipelineType>> normalizeGeometryConsumers(
            Map<GeometryHandleKey, Set<PipelineType>> geometryConsumers) {
        Map<GeometryHandleKey, Set<PipelineType>> normalized = new LinkedHashMap<>();
        for (Map.Entry<GeometryHandleKey, Set<PipelineType>> entry : geometryConsumers.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue().isEmpty()) {
                continue;
            }
            normalized.put(entry.getKey(), Set.copyOf(entry.getValue()));
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
            int indexCount,
            int installVersion
    ) {
        public GeometryUploadPlan {
            sharedSourceRef = Math.max(sharedSourceRef, 0L);
            builders = builders != null ? builders.clone() : new GeometryResourceCoordinator.BuilderPair[0];
            vertexLayoutKey = vertexLayoutKey != null ? vertexLayoutKey : KeyId.of("sketch:empty_vertex_layout");
            dynamicVertexUploads = dynamicVertexUploads != null ? List.copyOf(dynamicVertexUploads) : List.of();
            installVersion = installVersion != 0 ? installVersion : deriveInstallVersion(
                    geometryHandle,
                    vertexLayoutKey,
                    sharedSourceRef,
                    dynamicVertexUploads,
                    optionalIndexUpload,
                    optionalIndirectUpload,
                    vertexCount,
                    indexCount);
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
                    indexCount,
                    0);
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

        private static int deriveInstallVersion(
                GeometryHandleKey geometryHandle,
                KeyId vertexLayoutKey,
                long sharedSourceRef,
                List<VertexUploadSnapshot> dynamicVertexUploads,
                IndexUploadSnapshot optionalIndexUpload,
                IndirectUploadSnapshot optionalIndirectUpload,
                int vertexCount,
                int indexCount) {
            int hash = Objects.hash(geometryHandle, vertexLayoutKey, sharedSourceRef, vertexCount, indexCount);
            if (dynamicVertexUploads != null) {
                for (VertexUploadSnapshot upload : dynamicVertexUploads) {
                    if (upload != null) {
                        hash = 31 * hash + upload.hashCode();
                    }
                }
            }
            if (optionalIndexUpload != null) {
                hash = 31 * hash + optionalIndexUpload.hashCode();
            }
            if (optionalIndirectUpload != null) {
                hash = 31 * hash + optionalIndirectUpload.hashCode();
            }
            return hash;
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

        @Override
        public int hashCode() {
            return Objects.hash(componentId, bindingPoint, stride, instanced, vertexCount, Arrays.hashCode(data));
        }
    }

    public record IndexUploadSnapshot(byte[] data, int indexCount) {
        public IndexUploadSnapshot {
            data = data != null ? data.clone() : new byte[0];
        }

        @Override
        public int hashCode() {
            return Objects.hash(indexCount, Arrays.hashCode(data));
        }
    }

    public record IndirectUploadSnapshot(byte[] data, int drawCount, int stride) {
        public IndirectUploadSnapshot {
            data = data != null ? data.clone() : new byte[0];
        }

        @Override
        public int hashCode() {
            return Objects.hash(drawCount, stride, Arrays.hashCode(data));
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

