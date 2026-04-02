package rogo.sketch.core.pipeline.flow.v2;

import org.joml.primitives.AABBf;
import rogo.sketch.core.api.graphics.AABBGraphics;
import rogo.sketch.core.api.graphics.AsyncTickable;
import rogo.sketch.core.api.graphics.BoundsVersionProvider;
import rogo.sketch.core.api.graphics.GeometryVersionProvider;
import rogo.sketch.core.api.graphics.Graphics;
import rogo.sketch.core.api.graphics.MeshBasedGraphics;
import rogo.sketch.core.api.graphics.RenderDescriptorProvider;
import rogo.sketch.core.api.graphics.Tickable;
import rogo.sketch.core.api.graphics.SubmissionCapability;
import rogo.sketch.core.api.model.BakedTypeMesh;
import rogo.sketch.core.api.model.PreparedMesh;
import rogo.sketch.core.data.PrimitiveType;
import rogo.sketch.core.data.builder.VertexStreamBuilder;
import rogo.sketch.core.data.format.VertexBufferKey;
import rogo.sketch.core.data.vertex.VertexDataShard;
import rogo.sketch.core.packet.DrawPacket;
import rogo.sketch.core.packet.DrawPlan;
import rogo.sketch.core.packet.GeometryHandleKey;
import rogo.sketch.core.packet.PacketBuildContext;
import rogo.sketch.core.packet.PipelineStateKey;
import rogo.sketch.core.packet.RenderPacket;
import rogo.sketch.core.packet.ResourceBindingPlan;
import rogo.sketch.core.packet.ResourceSetKey;
import rogo.sketch.core.pipeline.CompiledRenderSetting;
import rogo.sketch.core.pipeline.PartialRenderSetting;
import rogo.sketch.core.pipeline.PipelineType;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.RenderSetting;
import rogo.sketch.core.pipeline.RenderSettingCompiler;
import rogo.sketch.core.pipeline.container.GraphicsContainer;
import rogo.sketch.core.pipeline.data.GeometryFrameData;
import rogo.sketch.core.pipeline.data.InstancedOffsetData;
import rogo.sketch.core.pipeline.data.PipelineDataStore;
import rogo.sketch.core.pipeline.flow.RenderFlowType;
import rogo.sketch.core.pipeline.flow.RenderPostProcessors;
import rogo.sketch.core.pipeline.flow.container.DefaultBatchContainers;
import rogo.sketch.core.pipeline.flow.impl.RasterizationPostProcessor;
import rogo.sketch.core.pipeline.flow.plan.DrawPlanCompiler;
import rogo.sketch.core.pipeline.flow.plan.ResourceGroupCompiler;
import rogo.sketch.core.pipeline.module.diagnostic.RenderTraceRecorder;
import rogo.sketch.core.pipeline.geometry.GeometryEncodeResult;
import rogo.sketch.core.pipeline.geometry.GeometryEncoder;
import rogo.sketch.core.pipeline.geometry.GeometrySourceKey;
import rogo.sketch.core.pipeline.geometry.LegacyMeshGeometryEncoder;
import rogo.sketch.core.pipeline.parmeter.RasterizationParameter;
import rogo.sketch.core.pipeline.parmeter.RenderParameter;
import rogo.sketch.core.resource.buffer.VertexResource;
import rogo.sketch.core.shader.uniform.UniformGroupSet;
import rogo.sketch.core.util.KeyId;
import rogo.sketch.core.vertex.VertexResourceManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public final class RasterStageFlowScene<C extends RenderContext> implements StageFlowScene<C> {
    private final KeyId stageId;
    private final PipelineType pipelineType;
    private final VertexResourceManager resourceManager;
    private final PipelineDataStore dataStore;
    private final InstanceRecordStore<MeshBasedGraphics> instanceStore = new InstanceRecordStore<>();
    private final VisibilityContainerRegistry<C> visibilityContainerRegistry = new VisibilityContainerRegistry<>(instanceStore);
    private final GeometryBucketIndex geometryBucketIndex = new GeometryBucketIndex();
    private final GeometryEncoder<MeshBasedGraphics> geometryEncoder = new LegacyMeshGeometryEncoder();
    private final ResourceGroupCompiler resourceGroupCompiler = new ResourceGroupCompiler();
    private final Map<GeometryBatchKey, VisibleInstanceSlice> visibleSlices = new LinkedHashMap<>();
    private final RenderTraceRecorder renderTraceRecorder;
    private long instanceOrderCounter = 0L;
    private long visibilityRevision = 0L;

    public RasterStageFlowScene(
            KeyId stageId,
            PipelineType pipelineType,
            VertexResourceManager resourceManager,
            PipelineDataStore dataStore,
            RenderTraceRecorder renderTraceRecorder) {
        this.stageId = stageId;
        this.pipelineType = pipelineType;
        this.resourceManager = resourceManager;
        this.dataStore = dataStore;
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
        if (!(graphics instanceof MeshBasedGraphics meshBasedGraphics)) {
            throw new IllegalArgumentException("Raster stage requires MeshBasedGraphics, got: " +
                    (graphics != null ? graphics.getClass().getName() : "null"));
        }

        removeGraphicsInstance(graphics);

        KeyId resolvedContainer = resolveContainerType(containerId, supplier, meshBasedGraphics);
        InstanceRecord<MeshBasedGraphics> record = instanceStore.register(
                meshBasedGraphics,
                stageId,
                pipelineType,
                renderParameter,
                resolvedContainer);
        if (record == null) {
            return;
        }

        refreshRecord(record, supplier, true);
        record.clearAllDirty();
    }

    @Override
    public void tick(C context) {
        for (InstanceRecord<MeshBasedGraphics> record : instanceStore.records()) {
            if (record.graphics() instanceof Tickable tickable) {
                tickable.tick();
            }
        }
    }

    @Override
    public void asyncTick(C context) {
        for (InstanceRecord<MeshBasedGraphics> record : instanceStore.records()) {
            if (record.graphics() instanceof AsyncTickable asyncTickable) {
                asyncTickable.asyncTick();
            }
        }
    }

    @Override
    public void swapData() {
        for (InstanceRecord<MeshBasedGraphics> record : instanceStore.records()) {
            if (record.graphics() instanceof AsyncTickable asyncTickable) {
                asyncTickable.swapData();
            }
        }
    }

    @Override
    public void prepareForFrame() {
        for (InstanceRecord<MeshBasedGraphics> record : instanceStore.records()) {
            if (record.graphics().shouldDiscard()) {
                record.markDiscarded();
                traceDrop(record.graphics(), "prepare_discarded");
                continue;
            }
            RefreshOutcome refreshOutcome = refreshRecord(record, null, false);
            if (refreshOutcome != null) {
                tracePrepare(record.graphics(), refreshOutcome);
            }
            record.clearAllDirty();
        }
    }

    @Override
    public void cleanupDiscardedInstances() {
        List<MeshBasedGraphics> toRemove = new ArrayList<>();
        for (InstanceRecord<MeshBasedGraphics> record : instanceStore.records()) {
            if (record.discarded() || record.graphics().shouldDiscard()) {
                toRemove.add(record.graphics());
            }
        }
        for (MeshBasedGraphics graphics : toRemove) {
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

        PacketBuildContext packetBuildContext = new PacketBuildContext(pipelineType, resourceManager, dataStore);
        GeometryFrameData geometryFrameData = packetBuildContext.geometryFrameData();
        RasterizationPostProcessor processor = postProcessors.get(flowType);
        Map<PipelineStateKey, List<RenderPacket>> packets = new LinkedHashMap<>();

        for (VisibleInstanceSlice visibleSlice : visibleSlices.values()) {
            compileVisibleSlice(stageId, visibleSlice, packetBuildContext, processor, geometryFrameData, packets);
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
        if (!(graphics instanceof MeshBasedGraphics meshBasedGraphics)) {
            return;
        }
        InstanceRecord<MeshBasedGraphics> removed = instanceStore.get(meshBasedGraphics);
        if (removed == null) {
            return;
        }
        visibilityContainerRegistry.remove(removed);
        geometryBucketIndex.remove(removed.handle());
        instanceStore.remove(meshBasedGraphics);
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
                InstanceRecord<MeshBasedGraphics> record = instanceStore.get(handle);
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
            GeometryFrameData geometryFrameData,
            Map<PipelineStateKey, List<RenderPacket>> packets) {
        if (visibleSlice == null || visibleSlice.visibleHandles().isEmpty()) {
            return;
        }

        List<MeshBasedGraphics> sliceGraphics = graphicsForHandles(visibleSlice.visibleHandles());
        InstanceRecord<MeshBasedGraphics> referenceRecord = resolveReferenceRecord(visibleSlice.visibleHandles());
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
        VertexResource sourceResource = geometryTraits.preparedMesh() instanceof BakedTypeMesh bakedTypeMesh
                ? bakedTypeMesh.getSourceResource()
                : null;
        VertexResource resource = resourceManager.get(vertexBufferKey, sourceResource);
        if (resource == null) {
            traceDrop(sliceGraphics, "geometry_resource_unavailable");
            return;
        }

        VertexResourceManager.BuilderPair[] builders = resourceManager.createBuilder(rasterParameter);
        resetBuilders(builders);

        InstancedOffsetData instancedOffsetData = packetBuildContext.getPipelineData(InstancedOffsetData.KEY);
        Map<RenderParameter, AtomicInteger> instancedOffsets = instancedOffsetData != null
                ? instancedOffsetData.getAll()
                : new HashMap<>();
        AtomicInteger baseInstanceCounter = instancedOffsets.computeIfAbsent(rasterParameter, ignored -> new AtomicInteger(0));

        GeometryHandleKey geometryHandle = GeometryHandleKey.from(vertexBufferKey);
        Map<PacketGroupKey, PacketAccumulator> groupedPackets = new LinkedHashMap<>();

        for (Map.Entry<CompiledRenderSetting, List<MeshBasedGraphics>> entry : groupByCompiledSetting(visibleSlice.visibleHandles()).entrySet()) {
            CompiledRenderSetting compiledRenderSetting = entry.getKey();
            List<ResourceGroupSlice> resourceGroups = resourceGroupCompiler.compile(compiledRenderSetting, visibleSlice, entry.getValue());
            if (resourceGroups.isEmpty()) {
                traceDrop(entry.getValue(), "no_resource_group");
            }
            for (ResourceGroupSlice resourceGroup : resourceGroups) {
                DrawPlan.DirectDrawItem drawItem = encodeResourceGroup(
                        vertexBufferKey,
                        resourceGroup.graphics(),
                        geometryTraits.preparedMesh(),
                        builders,
                        baseInstanceCounter);
                if (drawItem == null) {
                    traceDrop(resourceGroup.graphics(), "no_draw_item");
                    continue;
                }

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
                groupedPackets.computeIfAbsent(packetGroupKey, ignored -> new PacketAccumulator())
                        .add(drawItem, resourceGroup.graphics());
            }
        }

        if (groupedPackets.isEmpty()) {
            traceDrop(sliceGraphics, "no_packet_groups");
            return;
        }

        int totalVertexCount = resolveTotalVertexCount(builders, vertexBufferKey, geometryTraits);
        int totalIndexCount = rasterParameter.primitiveType().requiresIndexBuffer()
                ? resolveTotalIndexCount(totalVertexCount, vertexBufferKey, geometryTraits)
                : 0;

        if (processor != null) {
            processor.addGeometryUpload(
                    geometryHandle,
                    geometryTraits.geometryBatchKey().vertexLayoutKey(),
                    resource,
                    builders,
                    null,
                    totalVertexCount,
                    totalIndexCount);
        }
        if (geometryFrameData != null) {
            geometryFrameData.register(geometryHandle, resource, null);
        }

        for (Map.Entry<PacketGroupKey, PacketAccumulator> packetEntry : groupedPackets.entrySet()) {
            DrawPlan drawPlan = DrawPlanCompiler.compileDirectBatch(
                    packetEntry.getKey().primitiveType(),
                    packetEntry.getValue().drawItems());
            if (drawPlan == null) {
                traceDrop(packetEntry.getValue().completionGraphics(), "no_draw_plan");
                continue;
            }
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
    }

    private Map<CompiledRenderSetting, List<MeshBasedGraphics>> groupByCompiledSetting(List<InstanceHandle> handles) {
        Map<CompiledRenderSetting, List<MeshBasedGraphics>> grouped = new LinkedHashMap<>();
        for (InstanceHandle handle : handles) {
            InstanceRecord<MeshBasedGraphics> record = instanceStore.get(handle);
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

    private DrawPlan.DirectDrawItem encodeResourceGroup(
            VertexBufferKey vertexBufferKey,
            List<? extends Graphics> graphics,
            PreparedMesh preparedMesh,
            VertexResourceManager.BuilderPair[] builders,
            AtomicInteger baseInstanceCounter) {
        if (vertexBufferKey == null || graphics == null || graphics.isEmpty()) {
            return null;
        }

        PrimitiveType primitiveType = vertexBufferKey.renderParameter().primitiveType();
        int batchStartVertex = currentNonInstancedVertexCount(builders, vertexBufferKey);
        int batchStartIndex = primitiveType.requiresIndexBuffer()
                ? primitiveType.calculateIndexCount(batchStartVertex)
                : 0;

        for (Graphics graphic : graphics) {
            if (graphic instanceof MeshBasedGraphics meshBasedGraphics) {
                geometryEncoder.encodeInstance(meshBasedGraphics, builders);
            }
        }

        int batchEndVertex = currentNonInstancedVertexCount(builders, vertexBufferKey);
        int batchVertexCount = batchEndVertex - batchStartVertex;
        int batchEndIndex = primitiveType.requiresIndexBuffer()
                ? primitiveType.calculateIndexCount(batchEndVertex)
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
        if (key.hasInstancing()) {
            int baseInstance = instancedBaseOffset.getAndAdd(batchInstanceCount);
            if (preparedMesh != null) {
                if (primitiveType.requiresIndexBuffer() && preparedMesh.getIndicesCount() > 0) {
                    return DrawPlan.DirectDrawItem.indexed(
                            new VertexDataShard(
                                    0L,
                                    preparedMesh.getVertexOffset(),
                                    preparedMesh.getIndicesCount(),
                                    (long) preparedMesh.getIndexOffset() * Integer.BYTES),
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

            if (primitiveType.requiresIndexBuffer() && batchIndexCount > 0) {
                return DrawPlan.DirectDrawItem.indexed(
                        new VertexDataShard(0L, 0L, batchIndexCount, (long) batchStartIndex * Integer.BYTES),
                        batchInstanceCount,
                        baseInstance);
            }
            if (batchVertexCount > 0) {
                return DrawPlan.DirectDrawItem.nonIndexed(batchVertexCount, batchStartVertex, batchInstanceCount, baseInstance);
            }
            return null;
        }

        if (primitiveType.requiresIndexBuffer()) {
            if (batchIndexCount <= 0) {
                return null;
            }
            return DrawPlan.DirectDrawItem.indexed(
                    new VertexDataShard(0L, 0L, batchIndexCount, (long) batchStartIndex * Integer.BYTES),
                    1,
                    0);
        }

        if (batchVertexCount <= 0) {
            return null;
        }
        return DrawPlan.DirectDrawItem.nonIndexed(batchVertexCount, batchStartVertex, 1, 0);
    }

    private int currentNonInstancedVertexCount(VertexResourceManager.BuilderPair[] builders, VertexBufferKey key) {
        for (int i = 0; i < key.dynamicComponents().length; ++i) {
            VertexResourceManager.BuilderPair pair = builders[i];
            if (!key.dynamicComponents()[i].isInstanced() && pair != null && pair.builder() != null) {
                return pair.builder().getVertexCount();
            }
        }
        return 0;
    }

    private int resolveTotalVertexCount(
            VertexResourceManager.BuilderPair[] builders,
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
        return key.renderParameter().primitiveType().calculateIndexCount(totalVertexCount);
    }

    private long geometryBindingToken(GeometryBatchKey geometryBatchKey) {
        long stageHash = Integer.toUnsignedLong(stageId.hashCode());
        long batchHash = Integer.toUnsignedLong(Objects.hash(pipelineType, geometryBatchKey));
        return (stageHash << 32) ^ batchHash;
    }

    private void resetBuilders(VertexResourceManager.BuilderPair[] builders) {
        if (builders == null) {
            return;
        }
        for (VertexResourceManager.BuilderPair builder : builders) {
            if (builder != null && builder.builder() != null) {
                builder.builder().reset();
            }
        }
    }

    private InstanceRecord<MeshBasedGraphics> resolveReferenceRecord(List<InstanceHandle> handles) {
        for (InstanceHandle handle : handles) {
            InstanceRecord<MeshBasedGraphics> record = instanceStore.get(handle);
            if (record != null && record.geometryTraitsRef() != null) {
                return record;
            }
        }
        return null;
    }

    private RefreshOutcome refreshRecord(
            InstanceRecord<MeshBasedGraphics> record,
            Supplier<? extends GraphicsContainer<? extends RenderContext>> supplier,
            boolean forceFullRefresh) {
        if (record == null || record.graphics() == null) {
            return null;
        }

        MeshBasedGraphics graphics = record.graphics();
        SubmissionCapability submissionCapability = graphics.submissionCapability();
        boolean submissionCapabilityDirty = record.submissionCapability() != submissionCapability;
        if (submissionCapabilityDirty) {
            record.setSubmissionCapability(submissionCapability);
            record.markDirty(InstanceDirtyMask.GEOMETRY);
        }

        long descriptorVersion = resolveDescriptorVersion(graphics, record.renderParameter());
        boolean descriptorDirty = forceFullRefresh || record.compiledRenderSetting() == null || record.descriptorVersion() != descriptorVersion;
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

        visibilityContainerRegistry.upsert(record, supplier);
        return new RefreshOutcome(descriptorDirty, geometryDirty, boundsDirty);
    }

    private List<MeshBasedGraphics> graphicsForHandles(List<InstanceHandle> handles) {
        if (handles == null || handles.isEmpty()) {
            return List.of();
        }
        List<MeshBasedGraphics> graphics = new ArrayList<>(handles.size());
        for (InstanceHandle handle : handles) {
            InstanceRecord<MeshBasedGraphics> record = instanceStore.get(handle);
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
    }

    private void traceDrop(List<? extends Graphics> graphics, String reason) {
        if (renderTraceRecorder == null || graphics == null) {
            return;
        }
        for (Graphics graphic : graphics) {
            if (graphic != null) {
                renderTraceRecorder.recordDrop(stageId, graphic, reason);
            }
        }
    }

    private long resolveOrderHint(InstanceRecord<MeshBasedGraphics> record) {
        if (record != null && record.visibilityMetadata() != null) {
            return record.visibilityMetadata().orderHint();
        }
        return instanceOrderCounter++;
    }

    private KeyId resolveContainerType(
            KeyId requestedContainer,
            Supplier<? extends GraphicsContainer<? extends RenderContext>> supplier,
            Graphics graphics) {
        if (supplier != null) {
            return requestedContainer != null ? requestedContainer : DefaultBatchContainers.DEFAULT;
        }
        KeyId resolved = requestedContainer != null ? requestedContainer : DefaultBatchContainers.DEFAULT;
        if ((DefaultBatchContainers.AABB_TREE.equals(resolved) || DefaultBatchContainers.OCTREE.equals(resolved))
                && !(graphics instanceof AABBGraphics)) {
            return DefaultBatchContainers.DEFAULT;
        }
        return resolved;
    }

    private CompiledRenderSetting resolveCompiledRenderSetting(MeshBasedGraphics graphics, RenderParameter renderParameter) {
        if (graphics instanceof RenderDescriptorProvider provider) {
            CompiledRenderSetting compiledRenderSetting = provider.buildRenderDescriptor(renderParameter);
            if (compiledRenderSetting != null) {
                return compiledRenderSetting;
            }
        }
        PartialRenderSetting partialRenderSetting = graphics.getPartialRenderSetting();
        RenderSetting renderSetting = RenderSetting.fromPartial(
                renderParameter,
                partialRenderSetting != null ? partialRenderSetting : PartialRenderSetting.EMPTY);
        return RenderSettingCompiler.compile(renderSetting);
    }

    private long resolveDescriptorVersion(Graphics graphics, RenderParameter renderParameter) {
        if (graphics instanceof RenderDescriptorProvider provider) {
            return provider.descriptorVersion();
        }
        return Objects.hash(renderParameter, graphics.getPartialRenderSetting());
    }

    private long resolveGeometryVersion(MeshBasedGraphics graphics) {
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
            MeshBasedGraphics graphics,
            RenderParameter renderParameter,
            CompiledRenderSetting compiledRenderSetting) {
        GeometryEncodeResult encodeResult = geometryEncoder.inspect(graphics);
        KeyId vertexLayoutKey = compiledRenderSetting != null
                ? compiledRenderSetting.pipelineStateDescriptor().vertexLayoutKey()
                : KeyId.of("sketch:empty_vertex_layout");
        GeometryBatchKey geometryBatchKey = new GeometryBatchKey(
                encodeResult.sourceKey(),
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
        private final List<MeshBasedGraphics> completionGraphics = new ArrayList<>();

        void add(DrawPlan.DirectDrawItem drawItem, List<? extends Graphics> graphics) {
            if (drawItem != null) {
                drawItems.add(drawItem);
            }
            if (graphics == null) {
                return;
            }
            for (Graphics graphic : graphics) {
                if (graphic instanceof MeshBasedGraphics meshBasedGraphics) {
                    completionGraphics.add(meshBasedGraphics);
                }
            }
        }

        List<DrawPlan.DirectDrawItem> drawItems() {
            return drawItems;
        }

        List<MeshBasedGraphics> completionGraphics() {
            return completionGraphics;
        }
    }
}
