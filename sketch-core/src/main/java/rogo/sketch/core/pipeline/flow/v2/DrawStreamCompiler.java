package rogo.sketch.core.pipeline.flow.v2;

import rogo.sketch.core.api.model.BakedTypeMesh;
import rogo.sketch.core.api.model.PreparedMesh;
import rogo.sketch.core.api.model.SharedGeometrySourceSnapshot;
import rogo.sketch.core.backend.BackendGeometryBinding;
import rogo.sketch.core.backend.BackendIndirectBuffer;
import rogo.sketch.core.data.MeshIndexMode;
import rogo.sketch.core.data.PrimitiveType;
import rogo.sketch.core.data.TopologyIndexGenerator;
import rogo.sketch.core.data.format.VertexBufferKey;
import rogo.sketch.core.graphics.ecs.GraphicsEntityId;
import rogo.sketch.core.graphics.ecs.GraphicsUniformSubject;
import rogo.sketch.core.packet.DrawPacket;
import rogo.sketch.core.packet.DrawPlan;
import rogo.sketch.core.packet.ExecutionKey;
import rogo.sketch.core.packet.GeometryHandleKey;
import rogo.sketch.core.packet.PacketBuildContext;
import rogo.sketch.core.packet.RasterPipelineKey;
import rogo.sketch.core.packet.ResourceBindingPlan;
import rogo.sketch.core.packet.ResourceSetKey;
import rogo.sketch.core.packet.draw.IndexedDrawSlice;
import rogo.sketch.core.packet.draw.IndirectCommandRange;
import rogo.sketch.core.pipeline.CompiledRenderSetting;
import rogo.sketch.core.pipeline.PipelineType;
import rogo.sketch.core.pipeline.data.IndirectBufferData;
import rogo.sketch.core.pipeline.data.IndirectPlanData;
import rogo.sketch.core.pipeline.flow.impl.RasterizationPostProcessor;
import rogo.sketch.core.pipeline.flow.plan.DrawPlanCompiler;
import rogo.sketch.core.pipeline.geometry.RasterGeometryEncoder;
import rogo.sketch.core.pipeline.indirect.GpuIndirectCompileInput;
import rogo.sketch.core.pipeline.indirect.GpuIndirectCompileResult;
import rogo.sketch.core.pipeline.indirect.GpuIndirectCompiler;
import rogo.sketch.core.pipeline.indirect.IndirectCommandBatch;
import rogo.sketch.core.pipeline.indirect.IndirectPlanRequest;
import rogo.sketch.core.pipeline.indirect.IndirectStreamKey;
import rogo.sketch.core.pipeline.indirect.IndirectRewriteResult;
import rogo.sketch.core.pipeline.parmeter.RasterizationParameter;
import rogo.sketch.core.shader.uniform.UniformGroupSet;
import rogo.sketch.core.vertex.GeometryResourceCoordinator;
import rogo.sketch.core.util.KeyId;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

final class DrawStreamCompiler {
    interface TraceHooks {
        void drop(GraphicsUniformSubject subject, String reason);

        void drop(List<GraphicsUniformSubject> subjects, String reason);

        void packetBuilt(List<GraphicsUniformSubject> subjects, ExecutionKey stateKey);

        void stagePlanned(List<GraphicsUniformSubject> subjects);
    }

    private final KeyId stageId;
    private final PipelineType pipelineType;
    private final GeometryResourceCoordinator resourceManager;
    private final RasterGeometryEncoder geometryEncoder;
    private final TraceHooks traceHooks;
    private final GpuIndirectCompiler gpuIndirectCompiler;

    DrawStreamCompiler(
            KeyId stageId,
            PipelineType pipelineType,
            GeometryResourceCoordinator resourceManager,
            RasterGeometryEncoder geometryEncoder,
            TraceHooks traceHooks) {
        this.stageId = stageId;
        this.pipelineType = pipelineType;
        this.resourceManager = resourceManager;
        this.geometryEncoder = geometryEncoder;
        this.traceHooks = traceHooks;
        this.gpuIndirectCompiler = GpuIndirectCompiler.NO_OP;
    }

    Map<ExecutionKey, List<rogo.sketch.core.packet.RenderPacket>> compile(
            StageGeometryView geometryView,
            PacketBuildContext packetBuildContext,
            RasterizationPostProcessor processor) {
        if (geometryView == null || geometryView.isEmpty()) {
            return Map.of();
        }
        CompiledDrawStream compiledDrawStream = new CompiledDrawStream();
        for (StageGeometryView.VisibleBatch visibleBatch : geometryView.visibleBatches()) {
            compileVisibleBatch(visibleBatch, packetBuildContext, processor, compiledDrawStream);
        }
        return compiledDrawStream.asPacketMap();
    }

    private void compileVisibleBatch(
            StageGeometryView.VisibleBatch visibleBatch,
            PacketBuildContext packetBuildContext,
            RasterizationPostProcessor processor,
            CompiledDrawStream compiledDrawStream) {
        if (visibleBatch == null || visibleBatch.visibleEntries().isEmpty()) {
            return;
        }
        if (visibleBatch.rasterParameter() == null) {
            traceHooks.drop(sourceGraphicsOf(visibleBatch.visibleEntries()), "missing_reference_record");
            return;
        }
        if (visibleBatch.geometryTraits() == null) {
            traceHooks.drop(sourceGraphicsOf(visibleBatch.visibleEntries()), "missing_geometry_traits");
            return;
        }

        GeometryResourceCoordinator.BuilderPair[] builders = resourceManager.createBuilder(visibleBatch.rasterParameter());
        resetBuilders(builders);
        try {
            AtomicInteger baseInstanceCounter = new AtomicInteger(0);
            GeometryHandleKey geometryHandle = GeometryHandleKey.from(visibleBatch.vertexBufferKey());
            Map<PacketGroupKey, PacketAccumulator> groupedPackets = new LinkedHashMap<>();

            for (StageGeometryView.CompiledSettingSlice compiledSettingSlice : visibleBatch.compiledSettingSlices()) {
                if (compiledSettingSlice == null || compiledSettingSlice.compiledRenderSetting() == null || compiledSettingSlice.entries().isEmpty()) {
                    continue;
                }
                List<ResourceGroupSlice> resourceGroups = compiledSettingSlice.resourceGroups();
                if (resourceGroups.isEmpty()) {
                    traceHooks.drop(sourceGraphicsOf(compiledSettingSlice.entries()), "no_resource_group");
                }
                for (ResourceGroupSlice resourceGroup : resourceGroups) {
                    if (!(resourceGroup.stateKey() instanceof RasterPipelineKey rasterStateKey)) {
                        traceHooks.drop(sourceGraphicsOf(resourceGroup.entries()), "non_raster_state_key");
                        continue;
                    }
                    if (processor != null) {
                        processor.addResourceUpload(
                                stageId,
                                resourceGroup.resourceSetKey(),
                                resourceGroup.bindingPlan(),
                                resourceGroup.uniformGroups(),
                                rasterStateKey.shaderId());
                    }

                    PacketGroupKey packetGroupKey = new PacketGroupKey(
                            rasterStateKey,
                            resourceGroup.bindingPlan(),
                            resourceGroup.resourceSetKey(),
                            resourceGroup.uniformGroups(),
                            geometryHandle,
                            visibleBatch.rasterParameter().primitiveType(),
                            visibleBatch.firstVisibleOrder(),
                            packetTieBreaker(resourceGroup));
                    PacketAccumulator accumulator = groupedPackets.computeIfAbsent(packetGroupKey, ignored -> new PacketAccumulator());
                    boolean hasDrawItem = false;
                    for (StageGeometryView.PreparedMeshSlice preparedMeshSlice : selectPreparedMeshSlices(compiledSettingSlice, resourceGroup.entries())) {
                        DrawPlan.DirectDrawItem drawItem = encodePreparedMeshSlice(
                                visibleBatch.vertexBufferKey(),
                                preparedMeshSlice.preparedMesh(),
                                preparedMeshSlice.entries(),
                                builders,
                                baseInstanceCounter);
                        if (drawItem == null) {
                            traceHooks.drop(sourceGraphicsOf(preparedMeshSlice.entries()), "no_draw_item");
                            continue;
                        }
                        accumulator.add(drawItem, preparedMeshSlice.entries());
                        hasDrawItem = true;
                    }
                    if (!hasDrawItem) {
                        traceHooks.drop(sourceGraphicsOf(resourceGroup.entries()), "no_draw_item");
                    }
                }
            }

            if (groupedPackets.isEmpty()) {
                traceHooks.drop(sourceGraphicsOf(visibleBatch.visibleEntries()), "no_packet_groups");
                return;
            }

            IndirectBufferData indirectBufferData = packetBuildContext.indirectBufferData();
            IndirectPlanData indirectPlanData = packetBuildContext.indirectPlanData();
            BackendIndirectBuffer indirectBuffer = null;
            Map<PacketGroupKey, CompiledPacketPlan> compiledPlans = new LinkedHashMap<>();

            for (Map.Entry<PacketGroupKey, PacketAccumulator> packetEntry : groupedPackets.entrySet()) {
                DrawPlan drawPlan = DrawPlanCompiler.compileDirectBatch(
                        packetEntry.getKey().primitiveType(),
                        packetEntry.getValue().drawItems());
                if (drawPlan == null) {
                    traceHooks.drop(packetEntry.getValue().completionGraphics(), "no_draw_plan");
                    continue;
                }

                IndirectCompileOutcome indirectOutcome = tryCompileIndirect(
                        visibleBatch.rasterParameter(),
                        packetEntry.getKey(),
                        packetEntry.getKey().primitiveType(),
                        packetEntry.getValue(),
                        indirectPlanData,
                        indirectBufferData,
                        packetBuildContext);
                if (indirectOutcome != null && indirectOutcome.drawPlan() != null) {
                    drawPlan = indirectOutcome.drawPlan();
                    indirectBuffer = indirectOutcome.indirectBuffer();
                }
                compiledPlans.put(packetEntry.getKey(), new CompiledPacketPlan(drawPlan, packetEntry.getValue().completionGraphics()));
            }

            if (compiledPlans.isEmpty()) {
                traceHooks.drop(sourceGraphicsOf(visibleBatch.visibleEntries()), "no_compiled_packet_plan");
                return;
            }

            int totalVertexCount = resolveTotalVertexCount(builders, visibleBatch.vertexBufferKey(), visibleBatch.geometryTraits());
            int totalIndexCount = visibleBatch.rasterParameter().indexMode().usesIndexBuffer()
                    ? resolveTotalIndexCount(totalVertexCount, visibleBatch.vertexBufferKey(), visibleBatch.geometryTraits())
                    : 0;

            if (processor != null) {
                SharedGeometrySourceSnapshot sharedGeometrySourceSnapshot =
                        visibleBatch.geometryTraits().preparedMesh() instanceof BakedTypeMesh bakedTypeMesh
                                ? bakedTypeMesh.sharedGeometrySourceSnapshot()
                                : null;
                processor.addGeometryUpload(
                        geometryHandle,
                        visibleBatch.geometryTraits().geometryBatchKey().vertexLayoutKey(),
                        sharedGeometrySourceSnapshot,
                        visibleBatch.installedGeometryBinding(),
                        visibleBatch.sharedSourceGeometryBinding(),
                        builders,
                        indirectBuffer,
                        totalVertexCount,
                        totalIndexCount);
            }

            List<Map.Entry<PacketGroupKey, CompiledPacketPlan>> orderedPlans = new ArrayList<>(compiledPlans.entrySet());
            orderedPlans.sort(Map.Entry.comparingByKey((left, right) -> left.sortKey().compareTo(right.sortKey())));
            for (Map.Entry<PacketGroupKey, CompiledPacketPlan> packetEntry : orderedPlans) {
                DrawPlan drawPlan = packetEntry.getValue().drawPlan();
                traceHooks.packetBuilt(packetEntry.getValue().completionGraphics(), packetEntry.getKey().stateKey());
                RasterPipelineKey rasterStateKey = packetEntry.getKey().stateKey();
                compiledDrawStream.add(
                        packetEntry.getKey().sortKey(),
                        rasterStateKey,
                        new DrawPacket(
                                stageId,
                                pipelineType,
                                rasterStateKey,
                                packetEntry.getKey().bindingPlan(),
                                packetEntry.getKey().resourceSetKey(),
                                packetEntry.getKey().uniformGroups(),
                                packetEntry.getValue().completionGraphics(),
                                packetEntry.getKey().geometryHandle(),
                                drawPlan));
                traceHooks.stagePlanned(packetEntry.getValue().completionGraphics());
            }
        } finally {
            releaseBuilders(builders);
        }
    }

    private List<StageGeometryView.PreparedMeshSlice> selectPreparedMeshSlices(
            StageGeometryView.CompiledSettingSlice compiledSettingSlice,
            List<StageEntityView.Entry> resourceGroupEntries) {
        if (compiledSettingSlice == null || compiledSettingSlice.preparedMeshSlices().isEmpty() || resourceGroupEntries == null || resourceGroupEntries.isEmpty()) {
            return List.of();
        }
        List<StageGeometryView.PreparedMeshSlice> slices = new ArrayList<>();
        for (StageGeometryView.PreparedMeshSlice preparedMeshSlice : compiledSettingSlice.preparedMeshSlices()) {
            List<StageEntityView.Entry> selected = selectMatchingEntries(preparedMeshSlice.entries(), resourceGroupEntries);
            if (!selected.isEmpty()) {
                slices.add(new StageGeometryView.PreparedMeshSlice(preparedMeshSlice.preparedMesh(), selected));
            }
        }
        return slices;
    }

    private List<StageEntityView.Entry> selectMatchingEntries(
            List<StageEntityView.Entry> candidates,
            List<StageEntityView.Entry> resourceGroupEntries) {
        if (candidates == null || candidates.isEmpty()
                || resourceGroupEntries == null || resourceGroupEntries.isEmpty()) {
            return List.of();
        }
        Map<StageEntityView.Entry, Boolean> candidateIdentities = new IdentityHashMap<>();
        Set<GraphicsEntityId> candidateEntityIds = new HashSet<>();
        for (StageEntityView.Entry entry : candidates) {
            if (entry != null) {
                candidateIdentities.put(entry, Boolean.TRUE);
                candidateEntityIds.add(entry.entityId());
            }
        }
        if (candidateIdentities.isEmpty()) {
            return List.of();
        }
        List<StageEntityView.Entry> selected = new ArrayList<>();
        for (StageEntityView.Entry entry : resourceGroupEntries) {
            if (entry != null && (candidateIdentities.containsKey(entry) || candidateEntityIds.contains(entry.entityId()))) {
                selected.add(entry);
            }
        }
        return selected;
    }

    private DrawPlan.DirectDrawItem encodePreparedMeshSlice(
            VertexBufferKey vertexBufferKey,
            PreparedMesh preparedMesh,
            List<StageEntityView.Entry> entries,
            GeometryResourceCoordinator.BuilderPair[] builders,
            AtomicInteger baseInstanceCounter) {
        if (vertexBufferKey == null || entries == null || entries.isEmpty()) {
            return null;
        }

        PrimitiveType primitiveType = vertexBufferKey.renderParameter().primitiveType();
        MeshIndexMode indexMode = vertexBufferKey.renderParameter().indexMode();
        int batchStartVertex = currentNonInstancedVertexCount(builders, vertexBufferKey);
        int batchStartIndex = indexMode != null && indexMode.isGenerated()
                ? TopologyIndexGenerator.calculateIndexCount(primitiveType, batchStartVertex)
                : 0;

        for (StageEntityView.Entry entry : entries) {
            geometryEncoder.encodeDynamicComponents(entry, vertexBufferKey, builders);
        }

        int batchEndVertex = currentNonInstancedVertexCount(builders, vertexBufferKey);
        int batchVertexCount = batchEndVertex - batchStartVertex;
        int batchEndIndex = indexMode != null && indexMode.isGenerated()
                ? TopologyIndexGenerator.calculateIndexCount(primitiveType, batchEndVertex)
                : 0;
        int batchIndexCount = batchEndIndex - batchStartIndex;

        return compileDirectDrawItem(
                vertexBufferKey,
                preparedMesh,
                entries.size(),
                batchStartVertex,
                batchVertexCount,
                batchStartIndex,
                batchIndexCount,
                baseInstanceCounter);
    }

    private DrawPlan.DirectDrawItem compileDirectDrawItem(
            VertexBufferKey key,
            PreparedMesh preparedMesh,
            int batchInstanceCount,
            int batchStartVertex,
            int batchVertexCount,
            int batchStartIndex,
            int batchIndexCount,
            AtomicInteger instancedBaseOffset) {
        if (batchInstanceCount <= 0) {
            return null;
        }

        PrimitiveType primitiveType = key.renderParameter().primitiveType();
        MeshIndexMode indexMode = key.renderParameter().indexMode();
        if (key.hasInstancing()) {
            int baseInstance = instancedBaseOffset.getAndAdd(batchInstanceCount);
            if (preparedMesh != null) {
                if (preparedMesh.getIndicesCount() > 0) {
                    return DrawPlan.DirectDrawItem.indexed(
                            IndexedDrawSlice.indexed(
                                    preparedMesh.getVertexOffset(),
                                    preparedMesh.getIndicesCount(),
                                    (long) preparedMesh.getIndexOffset() * Integer.BYTES),
                            batchInstanceCount,
                            baseInstance);
                }
                if (indexMode != null && indexMode.isGenerated() && preparedMesh.getVertexCount() > 0) {
                    return DrawPlan.DirectDrawItem.indexed(
                            generatedIndexedShard(preparedMesh, primitiveType),
                            batchInstanceCount,
                            baseInstance);
                }
                if (preparedMesh.getVertexCount() > 0) {
                    return DrawPlan.DirectDrawItem.nonIndexed(
                            preparedMesh.getVertexCount(),
                            preparedMesh.getVertexOffset(),
                            batchInstanceCount,
                            baseInstance);
                }
            }

            if (indexMode != null && indexMode.isGenerated() && batchIndexCount > 0) {
                return DrawPlan.DirectDrawItem.indexed(
                        IndexedDrawSlice.indexed(0, batchIndexCount, (long) batchStartIndex * Integer.BYTES),
                        batchInstanceCount,
                        baseInstance);
            }
            if (batchVertexCount > 0) {
                return DrawPlan.DirectDrawItem.nonIndexed(batchVertexCount, batchStartVertex, batchInstanceCount, baseInstance);
            }
            return null;
        }

        if (preparedMesh != null && preparedMesh.getIndicesCount() > 0) {
            return DrawPlan.DirectDrawItem.indexed(
                    IndexedDrawSlice.indexed(
                            preparedMesh.getVertexOffset(),
                            preparedMesh.getIndicesCount(),
                            (long) preparedMesh.getIndexOffset() * Integer.BYTES),
                    1,
                    0);
        }
        if (indexMode != null && indexMode.isGenerated()) {
            if (preparedMesh != null && preparedMesh.getVertexCount() > 0) {
                return DrawPlan.DirectDrawItem.indexed(generatedIndexedShard(preparedMesh, primitiveType), 1, 0);
            }
            if (batchIndexCount <= 0) {
                return null;
            }
            return DrawPlan.DirectDrawItem.indexed(
                    IndexedDrawSlice.indexed(0, batchIndexCount, (long) batchStartIndex * Integer.BYTES),
                    1,
                    0);
        }
        if (preparedMesh != null && preparedMesh.getVertexCount() > 0) {
            return DrawPlan.DirectDrawItem.nonIndexed(preparedMesh.getVertexCount(), preparedMesh.getVertexOffset(), 1, 0);
        }
        if (batchVertexCount <= 0) {
            return null;
        }
        return DrawPlan.DirectDrawItem.nonIndexed(batchVertexCount, batchStartVertex, 1, 0);
    }

    private IndexedDrawSlice generatedIndexedShard(PreparedMesh preparedMesh, PrimitiveType primitiveType) {
        int vertexOffset = preparedMesh.getVertexOffset();
        int indexCount = TopologyIndexGenerator.calculateIndexCount(primitiveType, preparedMesh.getVertexCount());
        long indexOffsetBytes = (long) preparedMesh.getIndexOffset() * Integer.BYTES;
        return IndexedDrawSlice.indexed(vertexOffset, indexCount, indexOffsetBytes);
    }

    private int currentNonInstancedVertexCount(GeometryResourceCoordinator.BuilderPair[] builders, VertexBufferKey key) {
        for (int i = 0; i < key.dynamicComponents().length; ++i) {
            GeometryResourceCoordinator.BuilderPair pair = builders[i];
            if (!key.dynamicComponents()[i].isInstanced() && pair != null && pair.builder() != null) {
                return pair.builder().getVertexCount();
            }
        }
        return 0;
    }

    private int resolveTotalVertexCount(
            GeometryResourceCoordinator.BuilderPair[] builders,
            VertexBufferKey key,
            GeometryTraitsRef geometryTraits) {
        int dynamicVertexCount = currentNonInstancedVertexCount(builders, key);
        if (dynamicVertexCount > 0) {
            return dynamicVertexCount;
        }
        return geometryTraits != null ? geometryTraits.vertexCount() : 0;
    }

    private int resolveTotalIndexCount(int totalVertexCount, VertexBufferKey key, GeometryTraitsRef geometryTraits) {
        if (geometryTraits != null && geometryTraits.indexCount() > 0) {
            return geometryTraits.indexCount();
        }
        MeshIndexMode indexMode = key.renderParameter().indexMode();
        if (indexMode == null || !indexMode.isGenerated()) {
            return 0;
        }
        return TopologyIndexGenerator.calculateIndexCount(key.renderParameter().primitiveType(), totalVertexCount);
    }

    private IndirectCompileOutcome tryCompileIndirect(
            RasterizationParameter rasterParameter,
            PacketGroupKey packetGroupKey,
            PrimitiveType primitiveType,
            PacketAccumulator accumulator,
            IndirectPlanData indirectPlanData,
            IndirectBufferData indirectBufferData,
            PacketBuildContext packetBuildContext) {
        if (stageId == null
                || rasterParameter == null
                || packetGroupKey == null
                || primitiveType == null
                || accumulator == null
                || accumulator.drawItems().isEmpty()
                || indirectPlanData == null) {
            return null;
        }

        IndirectPlanRequest request = indirectPlanData.firstRequest(stageId, accumulator.completionGraphics());
        if (request == null) {
            return null;
        }
        if (request.requestMode() == IndirectPlanRequest.RequestMode.GPU_CULL) {
            GpuIndirectCompileResult gpuResult = gpuIndirectCompiler.compile(new GpuIndirectCompileInput(
                    stageId,
                    pipelineType,
                    rasterParameter,
                    packetGroupKey.stateKey(),
                    packetGroupKey.resourceSetKey(),
                    packetGroupKey.geometryHandle(),
                    entityIdsOf(accumulator.completionEntries()),
                    packetBuildContext));
            recordIndirectResults(indirectPlanData, accumulator.completionGraphics(), request, false, DrawPlan.DrawSubmission.DIRECT_BATCH, "gpu_cull_request_pending_module_rewrite");
            if (gpuResult != null && gpuResult.handled() && gpuResult.indirectCommandRange() != null) {
                BackendIndirectBuffer gpuBuffer = indirectBufferData != null ? indirectBufferData.get(rasterParameter) : null;
                if (gpuBuffer != null) {
                    DrawPlan gpuDrawPlan = DrawPlanCompiler.compileIndirect(
                            primitiveType,
                            true,
                            gpuResult.indirectCommandRange(),
                            gpuBuffer);
                    if (gpuDrawPlan != null) {
                        return new IndirectCompileOutcome(gpuDrawPlan, gpuBuffer);
                    }
                }
            }
            return null;
        }
        if (!allSupportIndirectEntries(accumulator.completionEntries())) {
            recordIndirectResults(indirectPlanData, accumulator.completionGraphics(), request, false, DrawPlan.DrawSubmission.DIRECT_BATCH, "graphics_capability_direct_only");
            return null;
        }
        if (indirectBufferData == null) {
            recordIndirectResults(indirectPlanData, accumulator.completionGraphics(), request, false, DrawPlan.DrawSubmission.DIRECT_BATCH, "missing_indirect_buffer_data");
            return null;
        }
        if (indirectBufferData.get(rasterParameter) == null) {
            indirectBufferData.planCreate(rasterParameter);
            recordIndirectResults(indirectPlanData, accumulator.completionGraphics(), request, false, DrawPlan.DrawSubmission.DIRECT_BATCH, "indirect_buffer_pending_materialization");
            return null;
        }

        IndirectCommandBatch commandBatch = IndirectCommandBatch.from(accumulator.drawItems());
        var writeResult = indirectBufferData.pool().writeStream(
                rasterParameter,
                packetGroupKey.streamKey(stageId, pipelineType),
                commandBatch);
        BackendIndirectBuffer indirectBuffer = writeResult.buffer();
        IndirectCommandRange range = writeResult.range();
        boolean indexed = commandBatch.indexed();
        DrawPlan drawPlan = DrawPlanCompiler.compileIndirect(primitiveType, indexed, range, indirectBuffer);
        if (drawPlan == null) {
            recordIndirectResults(indirectPlanData, accumulator.completionGraphics(), request, false, DrawPlan.DrawSubmission.DIRECT_BATCH, "indirect_draw_plan_compile_failed");
            return null;
        }
        recordIndirectResults(indirectPlanData, accumulator.completionGraphics(), request, true, DrawPlan.DrawSubmission.MULTI_DRAW_INDIRECT, "rewritten_to_multi_draw_indirect");
        return new IndirectCompileOutcome(drawPlan, indirectBuffer);
    }

    private void recordIndirectResults(
            IndirectPlanData indirectPlanData,
            List<GraphicsUniformSubject> subjects,
            IndirectPlanRequest request,
            boolean honored,
            DrawPlan.DrawSubmission submission,
            String reason) {
        if (indirectPlanData == null || subjects == null || subjects.isEmpty() || request == null) {
            return;
        }
        for (GraphicsUniformSubject subject : subjects) {
            if (subject == null || subject.identifier() == null) {
                continue;
            }
            indirectPlanData.recordResult(new IndirectRewriteResult(
                    stageId,
                    subject.identifier(),
                    request.requestMode(),
                    submission,
                    honored,
                    reason));
        }
    }

    private boolean allSupportIndirectEntries(List<StageEntityView.Entry> entries) {
        if (entries == null || entries.isEmpty()) {
            return false;
        }
        for (StageEntityView.Entry entry : entries) {
            if (entry == null || !entry.submissionCapability().supportsIndirect()) {
                return false;
            }
        }
        return true;
    }

    private void resetBuilders(GeometryResourceCoordinator.BuilderPair[] builders) {
        if (builders == null) {
            return;
        }
        for (GeometryResourceCoordinator.BuilderPair builder : builders) {
            if (builder != null && builder.builder() != null) {
                builder.builder().reset();
            }
        }
    }

    private void releaseBuilders(GeometryResourceCoordinator.BuilderPair[] builders) {
        if (builders == null) {
            return;
        }
        for (GeometryResourceCoordinator.BuilderPair builder : builders) {
            if (builder != null && builder.builder() != null) {
                builder.builder().close();
            }
        }
    }

    private String packetTieBreaker(ResourceGroupSlice resourceGroup) {
        if (resourceGroup == null) {
            return "";
        }
        return (resourceGroup.stateKey() != null ? resourceGroup.stateKey().hashCode() : 0)
                + "|" + (resourceGroup.resourceSetKey() != null ? resourceGroup.resourceSetKey().hashCode() : 0)
                + "|" + resourceGroup.uniformGroups().hashCode();
    }

    private record PacketGroupKey(
            RasterPipelineKey stateKey,
            ResourceBindingPlan bindingPlan,
            ResourceSetKey resourceSetKey,
            UniformGroupSet uniformGroups,
            GeometryHandleKey geometryHandle,
            PrimitiveType primitiveType,
            long firstVisibleOrder,
            String tieBreaker
    ) {
        private DrawSortKey sortKey() {
            return DrawSortKey.of(stateKey, resourceSetKey, geometryHandle, firstVisibleOrder, tieBreaker);
        }

        private IndirectStreamKey streamKey(KeyId stageId, PipelineType pipelineType) {
            return new IndirectStreamKey(stageId, pipelineType, stateKey, resourceSetKey, geometryHandle, tieBreaker);
        }
    }

    private static final class PacketAccumulator {
        private final List<DrawPlan.DirectDrawItem> drawItems = new ArrayList<>();
        private final List<StageEntityView.Entry> completionEntries = new ArrayList<>();

        void add(DrawPlan.DirectDrawItem drawItem, List<StageEntityView.Entry> entries) {
            if (drawItem != null) {
                drawItems.add(drawItem);
            }
            if (entries == null) {
                return;
            }
            completionEntries.addAll(entries);
        }

        List<DrawPlan.DirectDrawItem> drawItems() {
            return drawItems;
        }

        List<StageEntityView.Entry> completionEntries() {
            return completionEntries;
        }

        List<GraphicsUniformSubject> completionGraphics() {
            List<GraphicsUniformSubject> subjects = new ArrayList<>();
            for (StageEntityView.Entry entry : completionEntries) {
                if (entry != null && entry.uniformSubject() != null) {
                    subjects.add(entry.uniformSubject());
                }
            }
            return List.copyOf(subjects);
        }
    }

    private record IndirectCompileOutcome(
            DrawPlan drawPlan,
            BackendIndirectBuffer indirectBuffer
    ) {
    }

    private record CompiledPacketPlan(
            DrawPlan drawPlan,
            List<GraphicsUniformSubject> completionGraphics
    ) {
    }

    private static final class CompiledDrawStream {
        private final List<OrderedPacket> orderedPackets = new ArrayList<>();

        void add(DrawSortKey sortKey, ExecutionKey stateKey, rogo.sketch.core.packet.RenderPacket packet) {
            orderedPackets.add(new OrderedPacket(sortKey, stateKey, packet));
        }

        Map<ExecutionKey, List<rogo.sketch.core.packet.RenderPacket>> asPacketMap() {
            orderedPackets.sort((left, right) -> left.sortKey().compareTo(right.sortKey()));
            Map<ExecutionKey, List<rogo.sketch.core.packet.RenderPacket>> packets = new LinkedHashMap<>();
            for (OrderedPacket orderedPacket : orderedPackets) {
                packets.computeIfAbsent(orderedPacket.stateKey(), ignored -> new ArrayList<>()).add(orderedPacket.packet());
            }
            return packets;
        }
    }

    private record OrderedPacket(
            DrawSortKey sortKey,
            ExecutionKey stateKey,
            rogo.sketch.core.packet.RenderPacket packet
    ) {
    }

    private List<KeyId> entityIdsOf(List<StageEntityView.Entry> entries) {
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }
        List<KeyId> ids = new ArrayList<>(entries.size());
        for (StageEntityView.Entry entry : entries) {
            if (entry != null && entry.identifier() != null) {
                ids.add(entry.identifier());
            }
        }
        return List.copyOf(ids);
    }

    private List<GraphicsUniformSubject> sourceGraphicsOf(List<StageEntityView.Entry> entries) {
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }
        List<GraphicsUniformSubject> subjects = new ArrayList<>(entries.size());
        for (StageEntityView.Entry entry : entries) {
            GraphicsUniformSubject subject = entry != null ? entry.uniformSubject() : null;
            if (subject != null) {
                subjects.add(subject);
            }
        }
        return List.copyOf(subjects);
    }
}
