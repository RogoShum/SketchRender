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
import rogo.sketch.core.vertex.MeshResidencyPool;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class OpenGLGeometryMaterializer {
    private static final Map<Long, OpenGLGeometryBinding> sharedSourceBindings = new ConcurrentHashMap<>();
    private static final Map<InstallBindingKey, Integer> installedGeometryVersions = new ConcurrentHashMap<>();

    private OpenGLGeometryMaterializer() {
    }

    static <C extends RenderContext> void materializePendingGeometryResources(GraphicsPipeline<C> pipeline) {
        if (pipeline == null) {
            return;
        }
        for (var pipelineType : pipeline.getPipelineTypes()) {
            MeshResidencyPool residencyPool = pipeline.getMeshResidencyPool(pipelineType);
            for (MeshResidencyPool.PendingResidencyRequest request : residencyPool.drainPendingMaterializationRequests()) {
                if (request == null || request.vertexBufferKey() == null || residencyPool.getIfPresent(request.vertexBufferKey()) != null) {
                    continue;
                }
                OpenGLGeometryBinding sourceBinding = request.sourceProvider() instanceof OpenGLGeometryBinding openGLGeometryBinding
                        ? openGLGeometryBinding
                        : null;
                OpenGLGeometryBinding geometryBinding = OpenGLGeometryBinding.materialize(request.vertexBufferKey(), sourceBinding);
                if (geometryBinding != null) {
                    residencyPool.registerInstalledBinding(request.vertexBufferKey(), geometryBinding);
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

        for (FrameExecutionPlan.GeometryUploadPlan geometryUploadPlan : rasterizationPostProcessor.geometryUploadPlans()) {
            if (geometryUploadPlan == null || geometryUploadPlan.geometryHandle() == null) {
                continue;
            }
            try {
                OpenGLGeometryBinding geometryBinding = resolveOrCreateBinding(
                        pipeline.getMeshResidencyPool(pipelineType),
                        geometryUploadPlan);
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

        Map<GeometryHandleKey, Set<PipelineType>> consumersByHandle = executionPlan.geometryConsumers();
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
                    OpenGLGeometryBinding geometryBinding = resolveOrCreateBinding(
                            pipeline.getMeshResidencyPool(pipelineType),
                            geometryUploadPlan);
                    if (geometryBinding == null) {
                        continue;
                    }
                    InstallBindingKey installBindingKey = new InstallBindingKey(pipelineType, geometryUploadPlan.geometryHandle());
                    Integer installedVersion = installedGeometryVersions.get(installBindingKey);
                    boolean versionChanged = installedVersion == null || installedVersion != geometryUploadPlan.installVersion();
                    if (uploadGeometryData) {
                        if (versionChanged) {
                            geometryUploadPlan.uploadTo(geometryBinding);
                            installedGeometryVersions.put(installBindingKey, geometryUploadPlan.installVersion());
                        }
                        if (!indirectUploaded && versionChanged) {
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

    private record InstallBindingKey(PipelineType pipelineType, GeometryHandleKey geometryHandle) {
    }

    private static OpenGLGeometryBinding resolveOrCreateBinding(
            MeshResidencyPool residencyPool,
            FrameExecutionPlan.GeometryUploadPlan geometryUploadPlan) {
        if (residencyPool == null || geometryUploadPlan == null || geometryUploadPlan.geometryHandle() == null) {
            return null;
        }
        BackendGeometryBinding existing = residencyPool.getIfPresent(geometryUploadPlan.geometryHandle().vertexBufferKey());
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
            residencyPool.registerInstalledBinding(geometryUploadPlan.geometryHandle().vertexBufferKey(), geometryBinding);
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

