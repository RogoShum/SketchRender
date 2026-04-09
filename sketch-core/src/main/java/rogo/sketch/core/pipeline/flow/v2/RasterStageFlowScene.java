package rogo.sketch.core.pipeline.flow.v2;

import org.joml.primitives.AABBf;
import rogo.sketch.core.api.graphics.AABBGraphics;
import rogo.sketch.core.api.graphics.AsyncTickable;
import rogo.sketch.core.api.graphics.BoundsVersionProvider;
import rogo.sketch.core.api.graphics.DescriptorStability;
import rogo.sketch.core.api.graphics.GeometryVersionProvider;
import rogo.sketch.core.api.graphics.Graphics;
import rogo.sketch.core.api.graphics.RasterGraphics;
import rogo.sketch.core.api.graphics.RenderDescriptorProvider;
import rogo.sketch.core.api.graphics.Tickable;
import rogo.sketch.core.api.graphics.SubmissionCapability;
import rogo.sketch.core.api.model.BakedTypeMesh;
import rogo.sketch.core.api.model.PreparedMesh;
import rogo.sketch.core.api.model.SharedGeometrySourceSnapshot;
import rogo.sketch.core.backend.BackendGeometryBinding;
import rogo.sketch.core.backend.BackendIndirectBuffer;
import rogo.sketch.core.data.MeshIndexMode;
import rogo.sketch.core.data.PrimitiveType;
import rogo.sketch.core.data.TopologyIndexGenerator;
import rogo.sketch.core.data.format.VertexBufferKey;
import rogo.sketch.core.packet.DrawPacket;
import rogo.sketch.core.packet.DrawPlan;
import rogo.sketch.core.packet.GeometryHandleKey;
import rogo.sketch.core.packet.PacketBuildContext;
import rogo.sketch.core.packet.PipelineStateKey;
import rogo.sketch.core.packet.RenderPacket;
import rogo.sketch.core.packet.ResourceBindingPlan;
import rogo.sketch.core.packet.ResourceSetKey;
import rogo.sketch.core.packet.draw.IndexedDrawSlice;
import rogo.sketch.core.packet.draw.IndirectCommandRange;
import rogo.sketch.core.pipeline.CompiledRenderSetting;
import rogo.sketch.core.pipeline.PipelineType;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.container.GraphicsContainer;
import rogo.sketch.core.pipeline.data.IndirectBufferData;
import rogo.sketch.core.pipeline.data.IndirectPlanData;
import rogo.sketch.core.pipeline.data.PipelineDataStore;
import rogo.sketch.core.pipeline.flow.RenderFlowType;
import rogo.sketch.core.pipeline.flow.RenderPostProcessors;
import rogo.sketch.core.pipeline.flow.container.DefaultBatchContainers;
import rogo.sketch.core.pipeline.flow.impl.RasterizationPostProcessor;
import rogo.sketch.core.pipeline.flow.plan.DrawPlanCompiler;
import rogo.sketch.core.pipeline.flow.plan.ResourceGroupCompiler;
import rogo.sketch.core.pipeline.indirect.IndirectPlanRequest;
import rogo.sketch.core.pipeline.indirect.IndirectRewriteResult;
import rogo.sketch.core.pipeline.module.diagnostic.RenderTraceRecorder;
import rogo.sketch.core.pipeline.module.diagnostic.SketchDiagnostics;
import rogo.sketch.core.pipeline.geometry.GeometryEncodeResult;
import rogo.sketch.core.pipeline.geometry.GeometryEncoder;
import rogo.sketch.core.pipeline.geometry.GeometrySourceKey;
import rogo.sketch.core.pipeline.geometry.RasterGeometryEncoder;
import rogo.sketch.core.pipeline.parmeter.RasterizationParameter;
import rogo.sketch.core.pipeline.parmeter.RenderParameter;
import rogo.sketch.core.shader.uniform.UniformGroupSet;
import rogo.sketch.core.util.KeyId;
import rogo.sketch.core.vertex.GeometryResourceCoordinator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public final class RasterStageFlowScene<C extends RenderContext> implements StageFlowScene<C> {
    private final KeyId stageId;
    private final PipelineType pipelineType;
    private final GeometryResourceCoordinator resourceManager;
    private final Supplier<PipelineDataStore> dataStoreSupplier;
    private final InstanceRecordStore<RasterGraphics> instanceStore = new InstanceRecordStore<>();
    private final VisibilityContainerRegistry<C> visibilityContainerRegistry = new VisibilityContainerRegistry<>(instanceStore);
    private final GeometryBucketIndex geometryBucketIndex = new GeometryBucketIndex();
    private final GeometryEncoder<RasterGraphics> geometryEncoder = new RasterGeometryEncoder();
    private final ResourceGroupCompiler resourceGroupCompiler = new ResourceGroupCompiler();
    private final Map<GeometryBatchKey, VisibleInstanceSlice> visibleSlices = new LinkedHashMap<>();
    private final RenderTraceRecorder renderTraceRecorder;
    private final Set<String> emittedDropDiagnostics = new HashSet<>();
    private long instanceOrderCounter = 0L;
    private long visibilityRevision = 0L;

    public RasterStageFlowScene(
            KeyId stageId,
            PipelineType pipelineType,
            GeometryResourceCoordinator resourceManager,
            Supplier<PipelineDataStore> dataStoreSupplier,
            RenderTraceRecorder renderTraceRecorder) {
        this.stageId = stageId;
        this.pipelineType = pipelineType;
        this.resourceManager = resourceManager;
        this.dataStoreSupplier = dataStoreSupplier;
        this.renderTraceRecorder = renderTraceRecorder;
    }

    @Override
    public PipelineType pipelineType() {
        return pipelineType;
    }

    @Override
    public void registerGraphicsInstance(
            Graphics graphics,
            RenderParameter renderParameter,
            KeyId containerId,
            Supplier<? extends GraphicsContainer<? extends RenderContext>> supplier) {
        if (!(graphics instanceof RasterGraphics rasterGraphics)) {
            throw new IllegalArgumentException("Raster stage requires RasterGraphics, got: " +
                    (graphics != null ? graphics.getClass().getName() : "null"));
        }

        removeGraphicsInstance(graphics);

        KeyId resolvedContainer = resolveContainerType(containerId, rasterGraphics);
        InstanceRecord<RasterGraphics> record = instanceStore.register(
                rasterGraphics,
                stageId,
                pipelineType,
                renderParameter,
                resolvedContainer);
        if (record == null) {
            return;
        }

        refreshRecord(record, true);
        record.clearAllDirty();
    }

    @Override
    public void tick(C context) {
        for (InstanceRecord<RasterGraphics> record : instanceStore.records()) {
            if (record.graphics() instanceof Tickable tickable) {
                tickable.tick();
            }
        }
    }

    @Override
    public void asyncTick(C context) {
        for (InstanceRecord<RasterGraphics> record : instanceStore.records()) {
            if (record.graphics() instanceof AsyncTickable asyncTickable) {
                asyncTickable.asyncTick();
            }
        }
    }

    @Override
    public void swapData() {
        for (InstanceRecord<RasterGraphics> record : instanceStore.records()) {
            if (record.graphics() instanceof AsyncTickable asyncTickable) {
                asyncTickable.swapData();
            }
        }
    }

    @Override
    public void prepareForFrame() {
        for (InstanceRecord<RasterGraphics> record : instanceStore.records()) {
            if (record.graphics().shouldDiscard()) {
                record.markDiscarded();
                traceDrop(record.graphics(), "prepare_discarded");
                continue;
            }
            RefreshOutcome refreshOutcome = refreshRecord(record, false);
            if (refreshOutcome != null) {
                tracePrepare(record.graphics(), refreshOutcome);
            }
            record.clearAllDirty();
        }
    }

    @Override
    public void cleanupDiscardedInstances() {
        List<RasterGraphics> toRemove = new ArrayList<>();
        for (InstanceRecord<RasterGraphics> record : instanceStore.records()) {
            if (record.discarded() || record.graphics().shouldDiscard()) {
                toRemove.add(record.graphics());
            }
        }
        for (RasterGraphics graphics : toRemove) {
            removeGraphicsInstance(graphics);
        }
    }

    @Override
    public Map<PipelineStateKey, List<RenderPacket>> createRenderPackets(
            KeyId stageId,
            RenderFlowType flowType,
            RenderPostProcessors postProcessors,
            C context) {
        prepareVisibility(context);
        if (visibleSlices.isEmpty()) {
            return Collections.emptyMap();
        }

        PipelineDataStore dataStore = dataStoreSupplier.get();
        PacketBuildContext packetBuildContext = new PacketBuildContext(pipelineType, resourceManager, dataStore);
        RasterizationPostProcessor processor = postProcessors.get(flowType);
        Map<PipelineStateKey, List<RenderPacket>> packets = new LinkedHashMap<>();

        for (VisibleInstanceSlice visibleSlice : visibleSlices.values()) {
            compileVisibleSlice(stageId, visibleSlice, packetBuildContext, processor, packets);
        }
        return packets;
    }

    @Override
    public void clear() {
        visibleSlices.clear();
        geometryBucketIndex.clear();
        visibilityContainerRegistry.clear();
        instanceStore.clear();
    }

    @Override
    public void removeGraphicsInstance(Graphics graphics) {
        if (!(graphics instanceof RasterGraphics rasterGraphics)) {
            return;
        }
        InstanceRecord<RasterGraphics> removed = instanceStore.get(rasterGraphics);
        if (removed == null) {
            return;
        }
        visibilityContainerRegistry.remove(removed);
        geometryBucketIndex.remove(removed.handle());
        instanceStore.remove(rasterGraphics);
    }

    @Override
    public int instanceCount() {
        return instanceStore.size();
    }

    @Override
    public boolean hasInstances() {
        return !instanceStore.isEmpty();
    }

    private void prepareVisibility(C context) {
        visibleSlices.clear();
        if (instanceStore.isEmpty()) {
            return;
        }

        long currentRevision = ++visibilityRevision;
        Map<GeometryBatchKey, List<InstanceHandle>> visibleByBucket = new LinkedHashMap<>();
        Map<GeometryBatchKey, Long> firstVisibleOrder = new LinkedHashMap<>();
        long[] orderCounter = {0L};

        for (Map.Entry<KeyId, VisibilityIndex<C>> entry : visibilityContainerRegistry.orderedEntries()) {
            entry.getValue().collectVisible(context, handle -> {
                InstanceRecord<RasterGraphics> record = instanceStore.get(handle);
                if (record == null || record.graphics() == null) {
                    return;
                }
                if (record.graphics().shouldDiscard()) {
                    traceDrop(record.graphics(), "visibility_should_discard");
                    return;
                }
                if (!record.graphics().shouldRender()) {
                    traceDrop(record.graphics(), "visibility_should_render_false");
                    return;
                }
                GeometryBatchKey geometryBatchKey = geometryBucketIndex.geometryBucketOf(handle);
                if (geometryBatchKey == null) {
                    traceDrop(record.graphics(), "missing_geometry_bucket");
                    return;
                }

                traceVisible(record.graphics());
                visibleByBucket.computeIfAbsent(geometryBatchKey, ignored -> new ArrayList<>()).add(handle);
                firstVisibleOrder.putIfAbsent(geometryBatchKey, orderCounter[0]++);
            });
        }

        for (Map.Entry<GeometryBatchKey, List<InstanceHandle>> entry : visibleByBucket.entrySet()) {
            visibleSlices.put(entry.getKey(), new VisibleInstanceSlice(
                    entry.getKey(),
                    entry.getValue(),
                    currentRevision,
                    firstVisibleOrder.getOrDefault(entry.getKey(), Long.MAX_VALUE)));
        }
    }

    private void compileVisibleSlice(
            KeyId stageId,
            VisibleInstanceSlice visibleSlice,
            PacketBuildContext packetBuildContext,
            RasterizationPostProcessor processor,
            Map<PipelineStateKey, List<RenderPacket>> packets) {
        if (visibleSlice == null || visibleSlice.visibleHandles().isEmpty()) {
            return;
        }

        List<RasterGraphics> sliceGraphics = graphicsForHandles(visibleSlice.visibleHandles());
        InstanceRecord<RasterGraphics> referenceRecord = resolveReferenceRecord(visibleSlice.visibleHandles());
        if (referenceRecord == null || !(referenceRecord.renderParameter() instanceof RasterizationParameter rasterParameter)) {
            traceDrop(sliceGraphics, "missing_reference_record");
            return;
        }

        GeometryTraitsRef geometryTraits = referenceRecord.geometryTraitsRef();
        if (geometryTraits == null) {
            traceDrop(sliceGraphics, "missing_geometry_traits");
            return;
        }

        long sourceId = geometryTraits.sourceKey().sharedSourceId();
        long bindingToken = geometryBindingToken(visibleSlice.geometryBatchKey());
        VertexBufferKey vertexBufferKey = VertexBufferKey.fromParameter(rasterParameter, sourceId, bindingToken);
        BackendGeometryBinding sharedSourceGeometryBinding = geometryTraits.preparedMesh() instanceof BakedTypeMesh bakedTypeMesh
                ? bakedTypeMesh.sourceGeometryBinding()
                : null;
        BackendGeometryBinding installedGeometryBinding = resourceManager.getIfPresent(vertexBufferKey);

        GeometryResourceCoordinator.BuilderPair[] builders = resourceManager.createBuilder(rasterParameter);
        resetBuilders(builders);
        try {
            AtomicInteger baseInstanceCounter = new AtomicInteger(0);

            GeometryHandleKey geometryHandle = GeometryHandleKey.from(vertexBufferKey);
            Map<PacketGroupKey, PacketAccumulator> groupedPackets = new LinkedHashMap<>();

            for (Map.Entry<CompiledRenderSetting, List<RasterGraphics>> entry : groupByCompiledSetting(visibleSlice.visibleHandles()).entrySet()) {
                CompiledRenderSetting compiledRenderSetting = entry.getKey();
                List<ResourceGroupSlice> resourceGroups = resourceGroupCompiler.compile(compiledRenderSetting, visibleSlice, entry.getValue());
                if (resourceGroups.isEmpty()) {
                    traceDrop(entry.getValue(), "no_resource_group");
                }
                for (ResourceGroupSlice resourceGroup : resourceGroups) {
                    if (processor != null) {
                        processor.addResourceUpload(
                                stageId,
                                resourceGroup.resourceSetKey(),
                                resourceGroup.bindingPlan(),
                                resourceGroup.uniformGroups(),
                                resourceGroup.stateKey().shaderId());
                    }

                    PacketGroupKey packetGroupKey = new PacketGroupKey(
                            resourceGroup.stateKey(),
                            resourceGroup.bindingPlan(),
                            resourceGroup.resourceSetKey(),
                            resourceGroup.uniformGroups(),
                            geometryHandle,
                            rasterParameter.primitiveType());
                    PacketAccumulator accumulator = groupedPackets.computeIfAbsent(packetGroupKey, ignored -> new PacketAccumulator());
                    boolean hasDrawItem = false;
                    for (PreparedMeshGroup preparedMeshGroup : groupByPreparedMesh(resourceGroup.graphics())) {
                        DrawPlan.DirectDrawItem drawItem = encodePreparedMeshGroup(
                                vertexBufferKey,
                                preparedMeshGroup.preparedMesh(),
                                preparedMeshGroup.graphics(),
                                builders,
                                baseInstanceCounter);
                        if (drawItem == null) {
                            traceDrop(preparedMeshGroup.graphics(), "no_draw_item");
                            continue;
                        }
                        accumulator.add(drawItem, preparedMeshGroup.graphics());
                        hasDrawItem = true;
                    }
                    if (!hasDrawItem) {
                        traceDrop(resourceGroup.graphics(), "no_draw_item");
                    }
                }
            }

            if (groupedPackets.isEmpty()) {
                traceDrop(sliceGraphics, "no_packet_groups");
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
                    traceDrop(packetEntry.getValue().completionGraphics(), "no_draw_plan");
                    continue;
                }

                IndirectCompileOutcome indirectOutcome = tryCompileIndirect(
                        stageId,
                        rasterParameter,
                        packetEntry.getKey().primitiveType(),
                        packetEntry.getValue(),
                        indirectPlanData,
                        indirectBufferData);
                if (indirectOutcome != null && indirectOutcome.drawPlan() != null) {
                    drawPlan = indirectOutcome.drawPlan();
                    indirectBuffer = indirectOutcome.indirectBuffer();
                }
                compiledPlans.put(
                        packetEntry.getKey(),
                        new CompiledPacketPlan(drawPlan, packetEntry.getValue().completionGraphics()));
            }

            if (compiledPlans.isEmpty()) {
                traceDrop(sliceGraphics, "no_compiled_packet_plan");
                return;
            }

            int totalVertexCount = resolveTotalVertexCount(builders, vertexBufferKey, geometryTraits);
            int totalIndexCount = rasterParameter.indexMode().usesIndexBuffer()
                    ? resolveTotalIndexCount(totalVertexCount, vertexBufferKey, geometryTraits)
                    : 0;

            if (processor != null) {
                SharedGeometrySourceSnapshot sharedGeometrySourceSnapshot =
                        geometryTraits.preparedMesh() instanceof BakedTypeMesh bakedTypeMesh
                                ? bakedTypeMesh.sharedGeometrySourceSnapshot()
                                : null;
                processor.addGeometryUpload(
                        geometryHandle,
                        geometryTraits.geometryBatchKey().vertexLayoutKey(),
                        sharedGeometrySourceSnapshot,
                        installedGeometryBinding,
                        sharedSourceGeometryBinding,
                        builders,
                        indirectBuffer,
                        totalVertexCount,
                        totalIndexCount);
            }

            for (Map.Entry<PacketGroupKey, CompiledPacketPlan> packetEntry : compiledPlans.entrySet()) {
                DrawPlan drawPlan = packetEntry.getValue().drawPlan();
                tracePacketBuilt(stageId, packetEntry.getValue().completionGraphics(), packetEntry.getKey().stateKey());
                packets.computeIfAbsent(packetEntry.getKey().stateKey(), ignored -> new ArrayList<>())
                        .add(new DrawPacket(
                                stageId,
                                pipelineType,
                                packetEntry.getKey().stateKey(),
                                packetEntry.getKey().bindingPlan(),
                                packetEntry.getKey().resourceSetKey(),
                                packetEntry.getKey().uniformGroups(),
                                packetEntry.getValue().completionGraphics(),
                                packetEntry.getKey().geometryHandle(),
                                drawPlan));
                traceStagePlanned(stageId, packetEntry.getValue().completionGraphics());
            }
        } finally {
            releaseBuilders(builders);
        }
    }

    private Map<CompiledRenderSetting, List<RasterGraphics>> groupByCompiledSetting(List<InstanceHandle> handles) {
        Map<CompiledRenderSetting, List<RasterGraphics>> grouped = new LinkedHashMap<>();
        for (InstanceHandle handle : handles) {
            InstanceRecord<RasterGraphics> record = instanceStore.get(handle);
            if (record == null || record.graphics() == null) {
                continue;
            }
            if (record.compiledRenderSetting() == null) {
                traceDrop(record.graphics(), "missing_compiled_render_setting");
                continue;
            }
            grouped.computeIfAbsent(record.compiledRenderSetting(), ignored -> new ArrayList<>()).add(record.graphics());
        }
        return grouped;
    }

    private DrawPlan.DirectDrawItem encodePreparedMeshGroup(
            VertexBufferKey vertexBufferKey,
            PreparedMesh preparedMesh,
            List<RasterGraphics> graphics,
            GeometryResourceCoordinator.BuilderPair[] builders,
            AtomicInteger baseInstanceCounter) {
        if (vertexBufferKey == null || graphics == null || graphics.isEmpty()) {
            return null;
        }

        PrimitiveType primitiveType = vertexBufferKey.renderParameter().primitiveType();
        MeshIndexMode indexMode = vertexBufferKey.renderParameter().indexMode();
        int batchStartVertex = currentNonInstancedVertexCount(builders, vertexBufferKey);
        int batchStartIndex = indexMode != null && indexMode.isGenerated()
                ? TopologyIndexGenerator.calculateIndexCount(primitiveType, batchStartVertex)
                : 0;

        for (RasterGraphics rasterGraphics : graphics) {
            geometryEncoder.encodeDynamicComponents(rasterGraphics, vertexBufferKey, builders);
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
                graphics.size(),
                batchStartVertex,
                batchVertexCount,
                batchStartIndex,
                batchIndexCount,
                baseInstanceCounter);
    }

    private List<PreparedMeshGroup> groupByPreparedMesh(List<? extends Graphics> graphics) {
        if (graphics == null || graphics.isEmpty()) {
            return List.of();
        }
        Map<GeometrySourceKey, PreparedMeshGroupBuilder> grouped = new LinkedHashMap<>();
        for (Graphics graphic : graphics) {
            if (!(graphic instanceof RasterGraphics rasterGraphics)) {
                continue;
            }
            PreparedMesh preparedMesh = rasterGraphics.getPreparedMesh();
            GeometrySourceKey submeshKey = GeometrySourceKey.fromPreparedMesh(preparedMesh);
            grouped.computeIfAbsent(submeshKey, ignored -> new PreparedMeshGroupBuilder(preparedMesh, new ArrayList<>()))
                    .graphics()
                    .add(rasterGraphics);
        }
        if (grouped.isEmpty()) {
            return List.of();
        }
        List<PreparedMeshGroup> preparedMeshGroups = new ArrayList<>(grouped.size());
        for (PreparedMeshGroupBuilder group : grouped.values()) {
            preparedMeshGroups.add(new PreparedMeshGroup(group.preparedMesh(), List.copyOf(group.graphics())));
        }
        return preparedMeshGroups;
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
                return DrawPlan.DirectDrawItem.indexed(
                        generatedIndexedShard(preparedMesh, primitiveType),
                        1,
                        0);
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
            return DrawPlan.DirectDrawItem.nonIndexed(
                    preparedMesh.getVertexCount(),
                    preparedMesh.getVertexOffset(),
                    1,
                    0);
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

    private long geometryBindingToken(GeometryBatchKey geometryBatchKey) {
        long stageHash = Integer.toUnsignedLong(stageId.hashCode());
        long batchHash = Integer.toUnsignedLong(Objects.hash(pipelineType, geometryBatchKey));
        return (stageHash << 32) ^ batchHash;
    }

    private IndirectCompileOutcome tryCompileIndirect(
            KeyId packetStageId,
            RasterizationParameter rasterParameter,
            PrimitiveType primitiveType,
            PacketAccumulator accumulator,
            IndirectPlanData indirectPlanData,
            IndirectBufferData indirectBufferData) {
        if (packetStageId == null
                || rasterParameter == null
                || primitiveType == null
                || accumulator == null
                || accumulator.drawItems().isEmpty()
                || indirectPlanData == null) {
            return null;
        }

        IndirectPlanRequest request = indirectPlanData.firstRequest(packetStageId, accumulator.completionGraphics());
        if (request == null) {
            return null;
        }
        if (request.requestMode() == IndirectPlanRequest.RequestMode.GPU_CULL) {
            recordIndirectResults(
                    indirectPlanData,
                    packetStageId,
                    accumulator.completionGraphics(),
                    request,
                    false,
                    DrawPlan.DrawSubmission.DIRECT_BATCH,
                    "gpu_cull_request_pending_module_rewrite");
            return null;
        }
        if (!allSupportIndirect(accumulator.completionGraphics())) {
            recordIndirectResults(
                    indirectPlanData,
                    packetStageId,
                    accumulator.completionGraphics(),
                    request,
                    false,
                    DrawPlan.DrawSubmission.DIRECT_BATCH,
                    "graphics_capability_direct_only");
            return null;
        }
        if (indirectBufferData == null) {
            recordIndirectResults(
                    indirectPlanData,
                    packetStageId,
                    accumulator.completionGraphics(),
                    request,
                    false,
                    DrawPlan.DrawSubmission.DIRECT_BATCH,
                    "missing_indirect_buffer_data");
            return null;
        }

        BackendIndirectBuffer indirectBuffer = indirectBufferData.get(rasterParameter);
        if (indirectBuffer == null) {
            indirectBufferData.planCreate(rasterParameter);
            recordIndirectResults(
                    indirectPlanData,
                    packetStageId,
                    accumulator.completionGraphics(),
                    request,
                    false,
                    DrawPlan.DrawSubmission.DIRECT_BATCH,
                    "indirect_buffer_pending_materialization");
            return null;
        }

        int startCommandIndex = indirectBuffer.commandCount();
        for (DrawPlan.DirectDrawItem drawItem : accumulator.drawItems()) {
            appendIndirectCommand(indirectBuffer, drawItem);
        }
        IndirectCommandRange range = new IndirectCommandRange(startCommandIndex, indirectBuffer.commandCount() - startCommandIndex);
        boolean indexed = !accumulator.drawItems().isEmpty() && accumulator.drawItems().get(0).indexed();
        DrawPlan drawPlan = DrawPlanCompiler.compileIndirect(primitiveType, indexed, range, indirectBuffer);
        if (drawPlan == null) {
            recordIndirectResults(
                    indirectPlanData,
                    packetStageId,
                    accumulator.completionGraphics(),
                    request,
                    false,
                    DrawPlan.DrawSubmission.DIRECT_BATCH,
                    "indirect_draw_plan_compile_failed");
            return null;
        }
        recordIndirectResults(
                indirectPlanData,
                packetStageId,
                accumulator.completionGraphics(),
                request,
                true,
                DrawPlan.DrawSubmission.MULTI_DRAW_INDIRECT,
                "rewritten_to_multi_draw_indirect");
        return new IndirectCompileOutcome(drawPlan, indirectBuffer);
    }

    private void appendIndirectCommand(BackendIndirectBuffer indirectBuffer, DrawPlan.DirectDrawItem drawItem) {
        if (indirectBuffer == null || drawItem == null) {
            return;
        }
        if (drawItem.indexed() && drawItem.indexedSlice() != null) {
            indirectBuffer.addDrawElementsCommand(
                    drawItem.indexedSlice().indexCount(),
                    drawItem.instanceCount(),
                    (int) (drawItem.indexedSlice().firstIndexByteOffset() / Integer.BYTES),
                    drawItem.indexedSlice().baseVertex(),
                    drawItem.baseInstance());
            return;
        }
        indirectBuffer.addDrawArraysCommand(
                drawItem.vertexCount(),
                drawItem.instanceCount(),
                drawItem.firstVertex(),
                drawItem.baseInstance());
    }

    private boolean allSupportIndirect(List<RasterGraphics> graphics) {
        if (graphics == null || graphics.isEmpty()) {
            return false;
        }
        for (RasterGraphics graphic : graphics) {
            if (graphic == null || !graphic.submissionCapability().supportsIndirect()) {
                return false;
            }
        }
        return true;
    }

    private void recordIndirectResults(
            IndirectPlanData indirectPlanData,
            KeyId packetStageId,
            List<RasterGraphics> graphics,
            IndirectPlanRequest request,
            boolean honored,
            DrawPlan.DrawSubmission submission,
            String reason) {
        if (indirectPlanData == null || graphics == null || graphics.isEmpty() || request == null) {
            return;
        }
        for (RasterGraphics graphic : graphics) {
            if (graphic == null || graphic.getIdentifier() == null) {
                continue;
            }
            indirectPlanData.recordResult(new IndirectRewriteResult(
                    packetStageId,
                    graphic.getIdentifier(),
                    request.requestMode(),
                    submission,
                    honored,
                    reason));
        }
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

    private InstanceRecord<RasterGraphics> resolveReferenceRecord(List<InstanceHandle> handles) {
        for (InstanceHandle handle : handles) {
            InstanceRecord<RasterGraphics> record = instanceStore.get(handle);
            if (record != null && record.geometryTraitsRef() != null) {
                return record;
            }
        }
        return null;
    }

    private RefreshOutcome refreshRecord(
            InstanceRecord<RasterGraphics> record,
            boolean forceFullRefresh) {
        if (record == null || record.graphics() == null) {
            return null;
        }

        RasterGraphics graphics = record.graphics();
        SubmissionCapability submissionCapability = graphics.submissionCapability();
        boolean submissionCapabilityDirty = record.submissionCapability() != submissionCapability;
        if (submissionCapabilityDirty) {
            record.setSubmissionCapability(submissionCapability);
            record.markDirty(InstanceDirtyMask.GEOMETRY);
        }

        long descriptorVersion = resolveDescriptorVersion(graphics, record.renderParameter());
        boolean descriptorDirty = forceFullRefresh
                || record.compiledRenderSetting() == null
                || (DescriptorStability.DYNAMIC.equals(graphics.descriptorStability())
                && record.descriptorVersion() != descriptorVersion);
        if (descriptorDirty) {
            record.setCompiledRenderSetting(resolveCompiledRenderSetting(graphics, record.renderParameter()));
            record.setDescriptorVersion(descriptorVersion);
            record.markDirty(InstanceDirtyMask.DESCRIPTOR);
        }

        long geometryVersion = resolveGeometryVersion(graphics);
        boolean geometryDirty = forceFullRefresh
                || descriptorDirty
                || submissionCapabilityDirty
                || record.geometryTraitsRef() == null
                || record.geometryVersion() != geometryVersion;
        if (geometryDirty) {
            GeometryTraitsRef geometryTraits = resolveGeometryTraits(graphics, record.renderParameter(), record.compiledRenderSetting());
            record.setGeometryTraitsRef(geometryTraits);
            record.setGeometryVersion(geometryVersion);
            geometryBucketIndex.assign(record.handle(), geometryTraits.geometryBatchKey());
            record.markDirty(InstanceDirtyMask.GEOMETRY);
        }

        long boundsVersion = resolveBoundsVersion(graphics);
        boolean boundsDirty = forceFullRefresh
                || record.visibilityMetadata() == null
                || record.boundsVersion() != boundsVersion;
        if (boundsDirty) {
            record.setVisibilityMetadata(resolveVisibilityMetadata(graphics, resolveOrderHint(record)));
            record.setBoundsVersion(boundsVersion);
            record.markDirty(InstanceDirtyMask.BOUNDS);
        }

        visibilityContainerRegistry.upsert(record);
        return new RefreshOutcome(descriptorDirty, geometryDirty, boundsDirty);
    }

    private List<RasterGraphics> graphicsForHandles(List<InstanceHandle> handles) {
        if (handles == null || handles.isEmpty()) {
            return List.of();
        }
        List<RasterGraphics> graphics = new ArrayList<>(handles.size());
        for (InstanceHandle handle : handles) {
            InstanceRecord<RasterGraphics> record = instanceStore.get(handle);
            if (record != null && record.graphics() != null) {
                graphics.add(record.graphics());
            }
        }
        return graphics;
    }

    private void tracePrepare(Graphics graphics, RefreshOutcome refreshOutcome) {
        if (renderTraceRecorder == null || graphics == null || refreshOutcome == null) {
            return;
        }
        renderTraceRecorder.recordPrepare(
                stageId,
                graphics,
                refreshOutcome.descriptorDirty(),
                refreshOutcome.geometryDirty(),
                refreshOutcome.boundsDirty());
    }

    private void traceVisible(Graphics graphics) {
        if (renderTraceRecorder != null && graphics != null) {
            renderTraceRecorder.recordVisible(stageId, graphics);
        }
    }

    private void tracePacketBuilt(KeyId packetStageId, List<? extends Graphics> graphics, PipelineStateKey stateKey) {
        if (renderTraceRecorder == null || graphics == null) {
            return;
        }
        for (Graphics graphic : graphics) {
            if (graphic != null) {
                renderTraceRecorder.recordPacketBuilt(packetStageId, graphic, stateKey);
            }
        }
    }

    private void traceStagePlanned(KeyId packetStageId, List<? extends Graphics> graphics) {
        if (renderTraceRecorder == null || graphics == null) {
            return;
        }
        for (Graphics graphic : graphics) {
            if (graphic != null) {
                renderTraceRecorder.recordStagePlanned(packetStageId, graphic);
            }
        }
    }

    private void traceDrop(Graphics graphics, String reason) {
        if (renderTraceRecorder != null && graphics != null) {
            renderTraceRecorder.recordDrop(stageId, graphics, reason);
        }
        emitDropDiagnostic(graphics, reason);
    }

    private void traceDrop(List<? extends Graphics> graphics, String reason) {
        if (graphics == null) {
            return;
        }
        for (Graphics graphic : graphics) {
            if (graphic != null) {
                if (renderTraceRecorder != null) {
                    renderTraceRecorder.recordDrop(stageId, graphic, reason);
                }
                emitDropDiagnostic(graphic, reason);
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

    private void emitDropDiagnostic(Graphics graphics, String reason) {
        if (graphics == null || reason == null || reason.isBlank()) {
            return;
        }
        String dedupeKey = stageId + "|" + graphics.getIdentifier() + "|" + reason;
        if (!emittedDropDiagnostics.add(dedupeKey)) {
            return;
        }
        SketchDiagnostics.get().warn(
                "raster-stage-flow",
                "Dropped raster graphics " + graphics.getIdentifier()
                        + " in stage " + stageId
                        + " because " + reason);
    }

    private long resolveOrderHint(InstanceRecord<RasterGraphics> record) {
        if (record != null && record.visibilityMetadata() != null) {
            return record.visibilityMetadata().orderHint();
        }
        return instanceOrderCounter++;
    }

    private KeyId resolveContainerType(
            KeyId requestedContainer,
            Graphics graphics) {
        KeyId resolved = requestedContainer != null ? requestedContainer : DefaultBatchContainers.DEFAULT;
        if ((DefaultBatchContainers.AABB_TREE.equals(resolved) || DefaultBatchContainers.OCTREE.equals(resolved))
                && !(graphics instanceof AABBGraphics)) {
            return DefaultBatchContainers.DEFAULT;
        }
        return resolved;
    }

    private CompiledRenderSetting resolveCompiledRenderSetting(RasterGraphics graphics, RenderParameter renderParameter) {
        CompiledRenderSetting compiledRenderSetting = graphics.buildRenderDescriptor(renderParameter);
        if (compiledRenderSetting == null) {
            throw new IllegalStateException("Raster graphics must provide a non-null CompiledRenderSetting: "
                    + graphics.getIdentifier());
        }
        return compiledRenderSetting;
    }

    private long resolveDescriptorVersion(RasterGraphics graphics, RenderParameter renderParameter) {
        if (DescriptorStability.STABLE.equals(graphics.descriptorStability())) {
            return 0L;
        }
        return graphics.descriptorVersion();
    }

    private long resolveGeometryVersion(RasterGraphics graphics) {
        if (graphics instanceof GeometryVersionProvider geometryVersionProvider) {
            return geometryVersionProvider.geometryVersion();
        }
        PreparedMesh mesh = graphics.getPreparedMesh();
        GeometrySourceKey sourceKey = GeometrySourceKey.fromPreparedMesh(mesh);
        return Objects.hash(sourceKey, graphics.submissionCapability());
    }

    private long resolveBoundsVersion(Graphics graphics) {
        if (graphics instanceof BoundsVersionProvider boundsVersionProvider) {
            return boundsVersionProvider.boundsVersion();
        }
        if (graphics instanceof AABBGraphics aabbGraphics) {
            AABBf bounds = aabbGraphics.getAABB();
            if (bounds == null) {
                return 0L;
            }
            return Objects.hash(
                    bounds.minX,
                    bounds.minY,
                    bounds.minZ,
                    bounds.maxX,
                    bounds.maxY,
                    bounds.maxZ);
        }
        return 0L;
    }

    private VisibilityMetadata resolveVisibilityMetadata(Graphics graphics, long orderHint) {
        AABBf bounds = null;
        if (graphics instanceof AABBGraphics aabbGraphics && aabbGraphics.getAABB() != null) {
            bounds = new AABBf(aabbGraphics.getAABB());
        }
        Object sortKey = graphics instanceof Comparable<?> ? graphics : Long.valueOf(orderHint);
        return new VisibilityMetadata(bounds, sortKey, orderHint, 0);
    }

    private GeometryTraitsRef resolveGeometryTraits(
            RasterGraphics graphics,
            RenderParameter renderParameter,
            CompiledRenderSetting compiledRenderSetting) {
        GeometryEncodeResult encodeResult = geometryEncoder.inspect(graphics);
        KeyId vertexLayoutKey = compiledRenderSetting != null
                ? compiledRenderSetting.pipelineStateDescriptor().vertexLayoutKey()
                : KeyId.of("sketch:empty_vertex_layout");
        GeometryBatchKey geometryBatchKey = new GeometryBatchKey(
                encodeResult.sourceKey().sharedBatchKey(),
                vertexLayoutKey,
                renderParameter != null ? renderParameter.primitiveType() : PrimitiveType.TRIANGLES,
                GeometryBatchKey.submissionClassOf(graphics.submissionCapability()));
        return new GeometryTraitsRef(
                graphics.getPreparedMesh(),
                encodeResult.sourceKey(),
                geometryBatchKey,
                encodeResult.vertexCount(),
                encodeResult.indexCount(),
                encodeResult.indexed());
    }

    private record PacketGroupKey(
            PipelineStateKey stateKey,
            ResourceBindingPlan bindingPlan,
            ResourceSetKey resourceSetKey,
            UniformGroupSet uniformGroups,
            GeometryHandleKey geometryHandle,
            PrimitiveType primitiveType
    ) {
    }

    private record RefreshOutcome(
            boolean descriptorDirty,
            boolean geometryDirty,
            boolean boundsDirty
    ) {
    }

    private static final class PacketAccumulator {
        private final List<DrawPlan.DirectDrawItem> drawItems = new ArrayList<>();
        private final List<RasterGraphics> completionGraphics = new ArrayList<>();

        void add(DrawPlan.DirectDrawItem drawItem, List<? extends Graphics> graphics) {
            if (drawItem != null) {
                drawItems.add(drawItem);
            }
            if (graphics == null) {
                return;
            }
            for (Graphics graphic : graphics) {
                if (graphic instanceof RasterGraphics rasterGraphics) {
                    completionGraphics.add(rasterGraphics);
                }
            }
        }

        List<DrawPlan.DirectDrawItem> drawItems() {
            return drawItems;
        }

        List<RasterGraphics> completionGraphics() {
            return completionGraphics;
        }
    }

    private record IndirectCompileOutcome(
            DrawPlan drawPlan,
            BackendIndirectBuffer indirectBuffer
    ) {
    }

    private record CompiledPacketPlan(
            DrawPlan drawPlan,
            List<RasterGraphics> completionGraphics
    ) {
    }

    private record PreparedMeshGroup(
            PreparedMesh preparedMesh,
            List<RasterGraphics> graphics
    ) {
    }

    private record PreparedMeshGroupBuilder(
            PreparedMesh preparedMesh,
            List<RasterGraphics> graphics
    ) {
    }
}

