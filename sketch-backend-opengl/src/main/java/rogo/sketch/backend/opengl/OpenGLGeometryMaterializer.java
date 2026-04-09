package rogo.sketch.backend.opengl;

import rogo.sketch.core.packet.DrawPacket;
import rogo.sketch.core.packet.GeometryHandleKey;
import rogo.sketch.core.pipeline.GraphicsPipeline;
import rogo.sketch.core.pipeline.PipelineType;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.data.FrameDataDomain;
import rogo.sketch.core.pipeline.data.GeometryFrameData;
import rogo.sketch.core.pipeline.data.GeometryFrameData.BufferSlice;
import rogo.sketch.core.pipeline.data.GeometryFrameData.IndirectSlice;
import rogo.sketch.core.pipeline.flow.RenderPostProcessors;
import rogo.sketch.core.pipeline.flow.RenderFlowType;
import rogo.sketch.core.pipeline.flow.impl.RasterizationPostProcessor;
import rogo.sketch.core.pipeline.kernel.FrameExecutionPlan;
import rogo.sketch.core.api.model.BakedTypeMesh;
import rogo.sketch.core.api.model.SharedGeometrySourceSnapshot;
import rogo.sketch.core.backend.BackendGeometryBinding;
import rogo.sketch.core.data.MeshIndexMode;
import rogo.sketch.core.data.builder.VertexRecordWriter;
import rogo.sketch.core.data.format.ComponentSpec;
import rogo.sketch.core.resource.descriptor.BufferUpdatePolicy;
import rogo.sketch.core.vertex.GeometryResourceCoordinator;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class OpenGLGeometryMaterializer {
    private static final Map<Long, OpenGLGeometryBinding> sharedSourceBindings = new ConcurrentHashMap<>();

    private OpenGLGeometryMaterializer() {
    }

    static <C extends RenderContext> void materializePendingGeometryResources(GraphicsPipeline<C> pipeline) {
        if (pipeline == null) {
            return;
        }
        for (var pipelineType : pipeline.getPipelineTypes()) {
            GeometryResourceCoordinator manager = pipeline.getGeometryResourceCoordinator(pipelineType);
            for (GeometryResourceCoordinator.PendingGeometryBindingRequest request : manager.drainPendingMaterializationRequests()) {
                if (request == null || request.key() == null || manager.getIfPresent(request.key()) != null) {
                    continue;
                }
                OpenGLGeometryBinding sourceBinding = request.sourceProvider() instanceof OpenGLGeometryBinding openGLGeometryBinding
                        ? openGLGeometryBinding
                        : null;
                OpenGLGeometryBinding geometryBinding = OpenGLGeometryBinding.materialize(request.key(), sourceBinding);
                if (geometryBinding != null) {
                    manager.registerInstalledBinding(request.key(), geometryBinding);
                }
            }
        }
    }

    static <C extends RenderContext> void installImmediateGeometryBindings(
            GraphicsPipeline<C> pipeline,
            PipelineType pipelineType,
            RenderPostProcessors postProcessors) {
        if (pipeline == null || postProcessors == null) {
            return;
        }
        RasterizationPostProcessor rasterizationPostProcessor = postProcessors.get(RenderFlowType.RASTERIZATION);
        if (rasterizationPostProcessor == null || rasterizationPostProcessor.geometryUploadPlans().isEmpty()) {
            return;
        }

        GeometryFrameData geometryFrameData = pipeline.getPipelineDataStore(pipelineType, FrameDataDomain.SYNC_READ)
                .get(GeometryFrameData.KEY);
        if (geometryFrameData == null) {
            return;
        }

        GeometryResourceCoordinator manager = pipeline.getGeometryResourceCoordinator(pipelineType);
        for (FrameExecutionPlan.GeometryUploadPlan geometryUploadPlan : rasterizationPostProcessor.geometryUploadPlans()) {
            if (geometryUploadPlan == null || geometryUploadPlan.geometryHandle() == null) {
                continue;
            }
            try {
                OpenGLGeometryBinding geometryBinding = resolveOrCreateBinding(manager, geometryUploadPlan);
                if (geometryBinding == null) {
                    continue;
                }
                geometryUploadPlan.uploadTo(geometryBinding);
                geometryUploadPlan.uploadIndirect();
                geometryFrameData.registerNeutral(
                        geometryUploadPlan.geometryHandle(),
                        new BufferSlice(geometryBinding.getStaticVBO(), 0L, 0L, 0),
                        geometryBinding.hasIndices()
                                ? new BufferSlice(geometryBinding.getIndexBuffer().getHandle(), 0L, 0L, 0)
                                : null,
                        geometryUploadPlan.indirectBuffer() instanceof rogo.sketch.core.backend.BackendIndirectBuffer indirectBuffer
                                ? new IndirectSlice(0L, 0L, indirectBuffer.commandCount(), (int) indirectBuffer.strideBytes())
                                : null,
                        geometryUploadPlan.sharedSourceRef() > 0L
                                ? GeometryFrameData.SourceKind.SHARED_SOURCE
                                : GeometryFrameData.SourceKind.BACKEND_NATIVE,
                        geometryBinding,
                        geometryUploadPlan.indirectBuffer());
            } finally {
                geometryUploadPlan.releaseBuilderSnapshots();
            }
        }
    }

    static <C extends RenderContext> void installExecutionGeometryBindings(
            GraphicsPipeline<C> pipeline,
            FrameExecutionPlan executionPlan,
            boolean uploadGeometryData) {
        if (pipeline == null || executionPlan == null || executionPlan.geometryUploadPlans().isEmpty()) {
            return;
        }

        Map<GeometryHandleKey, Set<PipelineType>> consumersByHandle = collectGeometryConsumers(executionPlan);
        for (FrameExecutionPlan.GeometryUploadPlan geometryUploadPlan : executionPlan.geometryUploadPlans()) {
            if (geometryUploadPlan == null || geometryUploadPlan.geometryHandle() == null) {
                continue;
            }
            Set<PipelineType> consumers = consumersByHandle.get(geometryUploadPlan.geometryHandle());
            if (consumers == null || consumers.isEmpty()) {
                geometryUploadPlan.releaseBuilderSnapshots();
                continue;
            }

            boolean indirectUploaded = false;
            try {
                for (PipelineType pipelineType : consumers) {
                    if (pipelineType != PipelineType.RASTERIZATION && pipelineType != PipelineType.TRANSLUCENT) {
                        continue;
                    }
                    GeometryResourceCoordinator manager = pipeline.getGeometryResourceCoordinator(pipelineType);
                    OpenGLGeometryBinding geometryBinding = resolveOrCreateBinding(manager, geometryUploadPlan);
                    if (geometryBinding == null) {
                        continue;
                    }
                    if (uploadGeometryData) {
                        geometryUploadPlan.uploadTo(geometryBinding);
                        if (!indirectUploaded) {
                            geometryUploadPlan.uploadIndirect();
                            indirectUploaded = true;
                        }
                    }
                    GeometryFrameData geometryFrameData = pipeline
                            .getPipelineDataStore(pipelineType, FrameDataDomain.ASYNC_BUILD)
                            .get(GeometryFrameData.KEY);
                    if (geometryFrameData != null) {
                        geometryFrameData.registerNeutral(
                                geometryUploadPlan.geometryHandle(),
                                new BufferSlice(geometryBinding.getStaticVBO(), 0L, 0L, 0),
                                geometryBinding.hasIndices()
                                        ? new BufferSlice(geometryBinding.getIndexBuffer().getHandle(), 0L, 0L, 0)
                                        : null,
                                geometryUploadPlan.indirectBuffer() instanceof rogo.sketch.core.backend.BackendIndirectBuffer indirectBuffer
                                        ? new IndirectSlice(0L, 0L, indirectBuffer.commandCount(), (int) indirectBuffer.strideBytes())
                                        : null,
                                geometryUploadPlan.sharedSourceRef() > 0L
                                        ? GeometryFrameData.SourceKind.SHARED_SOURCE
                                        : GeometryFrameData.SourceKind.BACKEND_NATIVE,
                                geometryBinding,
                                geometryUploadPlan.indirectBuffer());
                    }
                }
            } finally {
                geometryUploadPlan.releaseBuilderSnapshots();
            }
        }
    }

    private static Map<GeometryHandleKey, Set<PipelineType>> collectGeometryConsumers(FrameExecutionPlan executionPlan) {
        Map<GeometryHandleKey, Set<PipelineType>> consumers = new LinkedHashMap<>();
        for (var stagePlan : executionPlan.stagePlans().values()) {
            if (stagePlan == null) {
                continue;
            }
            for (Map.Entry<PipelineType, Map<rogo.sketch.core.packet.PipelineStateKey, java.util.List<rogo.sketch.core.packet.RenderPacket>>> pipelineEntry : stagePlan.packets().entrySet()) {
                PipelineType pipelineType = pipelineEntry.getKey();
                for (var packetList : pipelineEntry.getValue().values()) {
                    for (var packet : packetList) {
                        if (packet instanceof DrawPacket drawPacket && drawPacket.geometryHandle() != null) {
                            consumers.computeIfAbsent(drawPacket.geometryHandle(), ignored -> new LinkedHashSet<>())
                                    .add(pipelineType);
                        }
                    }
                }
            }
        }
        return consumers;
    }

    private static OpenGLGeometryBinding resolveOrCreateBinding(
            GeometryResourceCoordinator manager,
            FrameExecutionPlan.GeometryUploadPlan geometryUploadPlan) {
        if (manager == null || geometryUploadPlan == null || geometryUploadPlan.geometryHandle() == null) {
            return null;
        }
        BackendGeometryBinding existing = manager.getIfPresent(geometryUploadPlan.geometryHandle().vertexBufferKey());
        if (existing instanceof OpenGLGeometryBinding openGLGeometryBinding) {
            return openGLGeometryBinding;
        }
        OpenGLGeometryBinding sourceBinding = geometryUploadPlan.sourceGeometryBinding() instanceof OpenGLGeometryBinding openGLGeometryBinding
                ? openGLGeometryBinding
                : resolveOrCreateSharedSourceBinding(geometryUploadPlan.optionalSharedSourceSnapshot());
        OpenGLGeometryBinding geometryBinding = OpenGLGeometryBinding.materialize(
                geometryUploadPlan.geometryHandle().vertexBufferKey(),
                sourceBinding);
        if (geometryBinding != null) {
            manager.registerInstalledBinding(geometryUploadPlan.geometryHandle().vertexBufferKey(), geometryBinding);
        }
        return geometryBinding;
    }

    private static OpenGLGeometryBinding resolveOrCreateSharedSourceBinding(SharedGeometrySourceSnapshot snapshot) {
        if (snapshot == null || snapshot.sharedSourceRef() <= 0L) {
            return null;
        }
        return sharedSourceBindings.computeIfAbsent(snapshot.sharedSourceRef(), ignored -> createSharedSourceBinding(snapshot));
    }

    private static OpenGLGeometryBinding createSharedSourceBinding(SharedGeometrySourceSnapshot snapshot) {
        if (snapshot == null || !snapshot.hasVertexData()) {
            return null;
        }
        OpenGLGeometryBinding geometryBinding = new OpenGLGeometryBinding(
                snapshot.primitiveType(),
                snapshot.hasIndexData() ? MeshIndexMode.EXPLICIT_LOCAL : MeshIndexMode.NONE,
                snapshot.hasIndexData());
        geometryBinding.attachVBO(
                ComponentSpec.immutable(BakedTypeMesh.BAKED_MESH, 0, snapshot.format(), false),
                new OpenGLVertexBufferObject(BufferUpdatePolicy.IMMUTABLE));
        geometryBinding.uploadVertexComponent(BakedTypeMesh.BAKED_MESH, snapshot.vertexData());
        if (snapshot.hasIndexData()) {
            geometryBinding.uploadIndices(decodeIndices(snapshot.indexData()));
        }
        return geometryBinding;
    }

    private static int[] decodeIndices(byte[] data) {
        if (data == null || data.length == 0) {
            return new int[0];
        }
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        int[] indices = new int[data.length / Integer.BYTES];
        buffer.asIntBuffer().get(indices);
        return indices;
    }
}

