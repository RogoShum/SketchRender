package rogo.sketch.core.pipeline.flow.impl;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.api.graphics.MeshBasedGraphics;
import rogo.sketch.core.api.graphics.SubmissionCapability;
import rogo.sketch.core.api.model.BakedTypeMesh;
import rogo.sketch.core.command.MultiDrawRenderCommand;
import rogo.sketch.core.command.RenderCommand;
import rogo.sketch.core.packet.DrawPacket;
import rogo.sketch.core.packet.DrawPlan;
import rogo.sketch.core.packet.GeometryHandleKey;
import rogo.sketch.core.packet.PacketBuildContext;
import rogo.sketch.core.packet.PipelineStateKey;
import rogo.sketch.core.packet.RenderPacket;
import rogo.sketch.core.packet.ResourceBindingPlan;
import rogo.sketch.core.packet.ResourceSetKey;
import rogo.sketch.core.command.prosessor.DrawRange;
import rogo.sketch.core.command.prosessor.ProcessorResult;
import rogo.sketch.core.data.PrimitiveType;
import rogo.sketch.core.data.format.ComponentSpec;
import rogo.sketch.core.data.format.VertexBufferKey;
import rogo.sketch.core.data.vertex.VertexDataShard;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.RenderSetting;
import rogo.sketch.core.pipeline.UniformBatchGroup;
import rogo.sketch.core.pipeline.data.IndirectBufferData;
import rogo.sketch.core.pipeline.data.InstancedOffsetData;
import rogo.sketch.core.pipeline.data.GeometryFrameData;
import rogo.sketch.core.pipeline.geometry.GeometryEncodeResult;
import rogo.sketch.core.pipeline.geometry.GeometryEncoder;
import rogo.sketch.core.pipeline.geometry.GeometrySourceKey;
import rogo.sketch.core.pipeline.geometry.LegacyMeshGeometryEncoder;
import rogo.sketch.core.pipeline.flow.*;
import rogo.sketch.core.pipeline.information.RasterizationInstanceInfo;
import rogo.sketch.core.pipeline.parmeter.RasterizationParameter;
import rogo.sketch.core.pipeline.parmeter.RenderParameter;
import rogo.sketch.core.pipeline.flow.plan.DrawPlanCompiler;
import rogo.sketch.core.pipeline.flow.plan.ResourceGroupCompiler;
import rogo.sketch.core.pipeline.flow.v2.BatchBucket;
import rogo.sketch.core.pipeline.flow.v2.ResourceGroupSlice;
import rogo.sketch.core.pipeline.flow.v2.VisibleBatchSlice;
import rogo.sketch.core.resource.buffer.IndirectCommandBuffer;
import rogo.sketch.core.resource.buffer.VertexResource;
import rogo.sketch.core.util.KeyId;
import rogo.sketch.core.vertex.VertexResourceManager;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Legacy rasterization flow retained as a compatibility bridge while the
 * {@code RasterStageFlowScene} path finishes replacing all
 * {@link BatchContainer}/{@link RenderBatch}-driven packet compilation.
 * <p>
 * New raster/translucent work should target the V2
 * {@code InstanceRecordStore -> VisibilityIndex -> GeometryBucketIndex ->
 * ResourceGroupCompiler -> DrawPlanCompiler} chain instead of extending this
 * strategy.
 * </p>
 */
@Deprecated(forRemoval = false)
public class RasterizationFlowStrategy implements RenderFlowStrategy<MeshBasedGraphics, RasterizationInstanceInfo> {
    private final GeometryEncoder<MeshBasedGraphics> geometryEncoder = new LegacyMeshGeometryEncoder();
    private final ResourceGroupCompiler resourceGroupCompiler = new ResourceGroupCompiler();

    @Override
    public RenderFlowType getFlowType() {
        return RenderFlowType.RASTERIZATION;
    }

    @Override
    public Class<MeshBasedGraphics> getGraphicsType() {
        return MeshBasedGraphics.class;
    }

    @Override
    public Class<RasterizationInstanceInfo> getInfoType() {
        return RasterizationInstanceInfo.class;
    }

    @Override
    public RenderPostProcessor createPostProcessor() {
        return new RasterizationPostProcessor();
    }

    @Override
    public Map<PipelineStateKey, List<RenderPacket>> buildPackets(
            BatchContainer<MeshBasedGraphics, RasterizationInstanceInfo> batchContainer,
            KeyId stageId,
            PacketBuildContext flowContext,
            RenderPostProcessors postProcessors,
            RenderContext context) {

        // Get active batches from container
        Collection<RenderBatch<RasterizationInstanceInfo>> activeBatches = batchContainer.getActiveBatches();
        if (activeBatches.isEmpty()) {
            return Collections.emptyMap();
        }

        // Filter visible instances and update uniforms for each batch
        List<RenderBatch<RasterizationInstanceInfo>> batchesWithVisible = new ArrayList<>();
        for (RenderBatch<RasterizationInstanceInfo> batch : activeBatches) {
            List<RasterizationInstanceInfo> visibleInfos = batch.getVisibleInstances();
            if (!visibleInfos.isEmpty()) {
                batch.updateUniformsForVisible();
                batchesWithVisible.add(batch);
            }
        }

        if (batchesWithVisible.isEmpty()) {
            return Collections.emptyMap();
        }

        // Group by VertexBufferKey
        Map<VertexBufferKey, RenderBatchList> keyGroups = groupByVertexBufferKey(stageId, batchesWithVisible);

        Map<PipelineStateKey, List<RenderPacket>> packets = new LinkedHashMap<>();
        GeometryFrameData geometryFrameData = flowContext.geometryFrameData();
        RasterizationPostProcessor processor = postProcessors.get(getFlowType());

        for (Map.Entry<VertexBufferKey, RenderBatchList> entry : keyGroups.entrySet()) {
            VertexBufferKey key = entry.getKey();
            RenderBatchList keyBatches = entry.getValue();

            DirectBuildResult result = processBatchesDirect(key, keyBatches, flowContext, processor);
            if (result == null || result.drawItems().isEmpty()) {
                continue;
            }

            if (geometryFrameData != null) {
                geometryFrameData.register(result.geometryHandle(), result.resource(), null);
            }

            Map<PacketGroupKey, PacketAccumulator> groupedPackets = new LinkedHashMap<>();
            for (RenderBatch<RasterizationInstanceInfo> batch : keyBatches.meshBatches()) {
                DrawPlan.DirectDrawItem drawItem = result.drawItems().get(batch);
                if (drawItem == null) {
                    continue;
                }

                VisibleBatchSlice<RasterizationInstanceInfo> visibleSlice = new VisibleBatchSlice<>(
                        BatchBucket.from(batch),
                        List.copyOf(batch.getVisibleInstances()),
                        batch.getVisibleRevision(),
                        batch.getFirstVisibleOrderKey());

                for (ResourceGroupSlice resourceGroup : resourceGroupCompiler.compile(visibleSlice)) {
                    if (resourceGroup.graphics().isEmpty()) {
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
                            result.geometryHandle(),
                            key.renderParameter().primitiveType());
                    groupedPackets.computeIfAbsent(packetGroupKey, ignored -> new PacketAccumulator())
                            .add(drawItem, resourceGroup.graphics());
                }
            }

            for (Map.Entry<PacketGroupKey, PacketAccumulator> packetEntry : groupedPackets.entrySet()) {
                DrawPlan drawPlan = DrawPlanCompiler.compileDirectBatch(
                        packetEntry.getKey().primitiveType(),
                        packetEntry.getValue().drawItems());
                if (drawPlan == null) {
                    continue;
                }
                packets.computeIfAbsent(packetEntry.getKey().stateKey(), ignored -> new ArrayList<>())
                        .add(new DrawPacket(
                                stageId,
                                flowContext.pipelineType(),
                                packetEntry.getKey().stateKey(),
                                packetEntry.getKey().bindingPlan(),
                                packetEntry.getKey().resourceSetKey(),
                                packetEntry.getKey().uniformGroups(),
                                packetEntry.getValue().completionGraphics(),
                                packetEntry.getKey().geometryHandle(),
                                drawPlan));
            }
        }

        return packets;
    }

    @Deprecated
    @Override
    public Map<RenderSetting, List<RenderCommand>> createRenderCommands(
            BatchContainer<MeshBasedGraphics, RasterizationInstanceInfo> batchContainer,
            KeyId stageId,
            rogo.sketch.core.pipeline.flow.RenderFlowContext flowContext,
            RenderPostProcessors postProcessors,
            RenderContext context) {

        Collection<RenderBatch<RasterizationInstanceInfo>> activeBatches = batchContainer.getActiveBatches();
        if (activeBatches.isEmpty()) {
            return Collections.emptyMap();
        }

        List<RenderBatch<RasterizationInstanceInfo>> batchesWithVisible = new ArrayList<>();
        for (RenderBatch<RasterizationInstanceInfo> batch : activeBatches) {
            List<RasterizationInstanceInfo> visibleInfos = batch.getVisibleInstances();
            if (!visibleInfos.isEmpty()) {
                batch.updateUniformsForVisible();
                batchesWithVisible.add(batch);
            }
        }

        if (batchesWithVisible.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<VertexBufferKey, RenderBatchList> keyGroups = groupByVertexBufferKey(stageId, batchesWithVisible);

        Map<RenderSetting, List<RenderCommand>> commandsMap = new LinkedHashMap<>();
        IndirectBufferData indirectBufferData = flowContext.getPipelineData(IndirectBufferData.KEY);
        Map<RenderParameter, IndirectCommandBuffer> indirectBuffers = indirectBufferData.getAll();

        RasterizationPostProcessor processor = postProcessors.get(getFlowType());
        boolean nextTick = context.nextTick();

        for (Map.Entry<VertexBufferKey, RenderBatchList> entry : keyGroups.entrySet()) {
            VertexBufferKey key = entry.getKey();
            RenderBatchList keyBatches = entry.getValue();

            ProcessorResult result = processBatches(key, keyBatches, flowContext, processor, nextTick);
            if (result == null)
                continue;

            VertexResource resource = result.resource();
            IndirectCommandBuffer indirectBuffer = indirectBuffers.get(key.renderParameter());

            for (RenderBatch<RasterizationInstanceInfo> batch : keyBatches.meshBatches()) {
                DrawRange range = result.ranges().get(batch);
                if (range != null && range.count() > 0) {
                    RenderCommand command = new MultiDrawRenderCommand(
                            resource,
                            indirectBuffer,
                            batch.getRenderSetting(),
                            stageId,
                            range.count(),
                            (long) range.startCommandIndex() * indirectBuffer.getStride(),
                            batch);

                    commandsMap.computeIfAbsent(batch.getRenderSetting(), k -> new ArrayList<>()).add(command);
                }
            }
        }

        indirectBufferData.getAll().values().forEach(processor::addIndirectUpload);

        return commandsMap;
    }

    @Override
    public boolean supportsBatching() {
        return true;
    }

    @Override
    public boolean supportsParallel() {
        return true;
    }

    // ===== Helper Methods =====

    /**
     * Group render batches by their VertexBufferKey.
     */
    private Map<VertexBufferKey, RenderBatchList> groupByVertexBufferKey(KeyId stageId, List<RenderBatch<RasterizationInstanceInfo>> batches) {
        Map<VertexBufferKey, RenderBatchList> keyGroups = new LinkedHashMap<>();

        for (RenderBatch<RasterizationInstanceInfo> batch : batches) {
            RenderSetting setting = batch.getRenderSetting();
            RenderParameter param = setting.renderParameter();

            // Skip non-rasterization parameters
            if (!(param instanceof RasterizationParameter rasterParam)) {
                continue;
            }

            // Determine Source ID from the first instance in the batch
            long sourceId = -1;
            BakedTypeMesh mesh;
            if (!batch.getVisibleInstances().isEmpty() && batch instanceof MeshRenderBatch meshRenderBatch) {
                GeometryEncodeResult encodeResult = geometryEncoder.inspect(batch.getVisibleInstances().get(0).getInstance());
                GeometrySourceKey sourceKey = encodeResult.sourceKey();
                sourceId = sourceKey.sharedSourceId();
                mesh = meshRenderBatch.mesh();
            } else {
                mesh = null;
            }

            // Build key with source ID
            long bindingToken = geometryBindingToken(stageId, rasterParam, batch, sourceId);
            VertexBufferKey key = VertexBufferKey.fromParameter(rasterParam, sourceId, bindingToken);
            keyGroups.computeIfAbsent(key, k -> new RenderBatchList(rasterParam, mesh)).add(batch);
        }

        return keyGroups;
    }

    /**
     * Process a group of batches that share the same VertexBufferKey.
     */
    private ProcessorResult processBatches(
            VertexBufferKey key,
            RenderBatchList batches,
            PacketBuildContext context,
            RasterizationPostProcessor accumulator,
            boolean nextTick) {
        return processBatches(
                key,
                batches,
                context.getResourceManager(),
                context.getPipelineData(IndirectBufferData.KEY),
                context.getPipelineData(InstancedOffsetData.KEY),
                accumulator,
                nextTick);
    }

    private ProcessorResult processBatches(
            VertexBufferKey key,
            RenderBatchList batches,
            RenderFlowContext context,
            RasterizationPostProcessor accumulator,
            boolean nextTick) {
        return processBatches(
                key,
                batches,
                context.getResourceManager(),
                context.getPipelineData(IndirectBufferData.KEY),
                context.getPipelineData(InstancedOffsetData.KEY),
                accumulator,
                nextTick);
    }

    private ProcessorResult processBatches(
            VertexBufferKey key,
            RenderBatchList batches,
            VertexResourceManager resourceManager,
            IndirectBufferData indirectBufferData,
            InstancedOffsetData instancedOffsetData,
            RasterizationPostProcessor accumulator,
            boolean nextTick) {
        Map<RenderParameter, IndirectCommandBuffer> indirectBuffers = indirectBufferData.getAll();
        Map<RenderParameter, AtomicInteger> instancedOffsets = instancedOffsetData.getAll();

        // 1. Calculate totals for builders (use visible instances)
        int totalInstances = 0;
        int batchCount = batches.meshBatches().size();
        for (int i = 0; i < batchCount; i++) {
            RenderBatch<RasterizationInstanceInfo> batch = batches.meshBatches().get(i);
            totalInstances += batch.getVisibleInstanceCount();
        }

        if (totalInstances == 0)
            return null;

        // 2. Prepare Builders and Source Resource
        VertexResource source = null;
        if (key.sourceResourceID() > 0) {
            if (!batches.empty() && batches.mesh() != null) {
                source = batches.mesh().getSourceResource();
            }
        }

        VertexResource resource = resourceManager.get(key, source);
        if (resource == null) {
            // Planned on async thread; will be materialized on sync thread and consumed next frame.
            return null;
        }

        IndirectCommandBuffer indirectBuffer = indirectBuffers.get(key.renderParameter());
        if (indirectBuffer == null) {
            indirectBufferData.planCreate(key.renderParameter());
            return null;
        }
        AtomicInteger batchCurrentInstancedCount = instancedOffsets.computeIfAbsent(key.renderParameter(), k -> new AtomicInteger(0));

        VertexResourceManager.BuilderPair[] builders = resourceManager.createBuilder(key.renderParameter());

        Map<RenderBatch<?>, DrawRange> ranges = new HashMap<>();
        boolean isInstancedDraw = key.hasInstancing();
        int currentVertexOffset = 0;

        for (int b = 0; b < key.dynamicComponents().length; ++b) {
            VertexResourceManager.BuilderPair pair = builders[b];
            ComponentSpec componentSpec = key.dynamicComponents()[b];
            if (pair.key().equals(componentSpec.getId()) && !componentSpec.isInstanced()) {
                currentVertexOffset = pair.builder().getVertexCount();
                RenderParameter param = key.renderParameter();
                if (param.primitiveType().requiresIndexBuffer()) {
                    currentVertexOffset = param.primitiveType().calculateIndexCount(currentVertexOffset);
                }
                break;
            }
        }

        // 3. Process Batches - use visible instances
        for (int i = 0; i < batchCount; i++) {
            RenderBatch<RasterizationInstanceInfo> batch = batches.meshBatches().get(i);
            List<RasterizationInstanceInfo> visibleInstances = batch.getVisibleInstances();
            int batchStartCommand = indirectBuffer.getCommandCount();
            int batchInstanceCount = visibleInstances.size();
            PrimitiveType primitiveType = key.renderParameter().primitiveType();

            for (int ins = 0; ins < batchInstanceCount; ++ins) {
                RasterizationInstanceInfo info = visibleInstances.get(ins);
                MeshBasedGraphics instance = info.getInstance();
                geometryEncoder.encodeInstance(instance, builders);

                // Generate Commands (Per Instance if Standard)
                if (!isInstancedDraw) {
                    int vCount = info.getVertexCount();
                    if (primitiveType.requiresIndexBuffer()) {
                        vCount = primitiveType.calculateIndexCount(vCount);
                    }

                    indirectBuffer.addDrawCommand(
                            vCount,
                            1,
                            currentVertexOffset,
                            0);

                    currentVertexOffset += vCount;
                }
            }

            // Generate Commands (Per Batch if Instanced)
            if (isInstancedDraw && batchInstanceCount > 0 && batch instanceof MeshRenderBatch meshBatch) {
                int vCount = key.renderParameter().primitiveType().requiresIndexBuffer()
                        ? meshBatch.mesh().getIndicesCount()
                        : meshBatch.mesh().getVertexCount();

                indirectBuffer.addDrawCommand(vCount, batchInstanceCount,
                        key.renderParameter().primitiveType().requiresIndexBuffer()
                                ? meshBatch.mesh().getIndexOffset()
                                : meshBatch.mesh().getVertexOffset(),
                        batchCurrentInstancedCount.getAndAdd(batchInstanceCount));
            }

            ranges.put(batch, new DrawRange(batchStartCommand, indirectBuffer.getCommandCount() - batchStartCommand));
        }

        if (accumulator != null) {
            accumulator.add(resource, builders);
        }

        return new ProcessorResult(resource, ranges);
    }

    private DirectBuildResult processBatchesDirect(
            VertexBufferKey key,
            RenderBatchList batches,
            PacketBuildContext context,
            RasterizationPostProcessor accumulator) {
        VertexResourceManager resourceManager = context.getResourceManager();
        InstancedOffsetData instancedOffsetData = context.getPipelineData(InstancedOffsetData.KEY);
        Map<RenderParameter, AtomicInteger> instancedOffsets = instancedOffsetData != null
                ? instancedOffsetData.getAll()
                : new HashMap<>();

        int totalInstances = 0;
        for (RenderBatch<RasterizationInstanceInfo> batch : batches.meshBatches()) {
            totalInstances += batch.getVisibleInstanceCount();
        }
        if (totalInstances == 0) {
            return null;
        }

        VertexResource source = null;
        if (key.sourceResourceID() > 0 && !batches.empty() && batches.mesh() != null) {
            source = batches.mesh().getSourceResource();
        }

        VertexResource resource = resourceManager.get(key, source);
        if (resource == null) {
            return null;
        }

        VertexResourceManager.BuilderPair[] builders = resourceManager.createBuilder(key.renderParameter());
        Map<RenderBatch<?>, DrawPlan.DirectDrawItem> drawItems = new HashMap<>();
        AtomicInteger batchCurrentInstancedCount = instancedOffsets.computeIfAbsent(key.renderParameter(), ignored -> new AtomicInteger(0));
        PrimitiveType primitiveType = key.renderParameter().primitiveType();

        for (RenderBatch<RasterizationInstanceInfo> batch : batches.meshBatches()) {
            List<RasterizationInstanceInfo> visibleInstances = batch.getVisibleInstances();
            if (visibleInstances.isEmpty()) {
                continue;
            }

            int batchStartVertex = currentNonInstancedVertexCount(builders, key);
            int batchStartIndex = primitiveType.requiresIndexBuffer()
                    ? primitiveType.calculateIndexCount(batchStartVertex)
                    : 0;

            SubmissionCapability submissionCapability = submissionCapabilityOf(visibleInstances);
            if (submissionCapability.supportsIndirect()) {
                // Direct-first path: indirect stays opt-in for a future module pass.
            }

            for (RasterizationInstanceInfo info : visibleInstances) {
                geometryEncoder.encodeInstance(info.getInstance(), builders);
            }

            int batchEndVertex = currentNonInstancedVertexCount(builders, key);
            int batchVertexCount = batchEndVertex - batchStartVertex;
            int batchEndIndex = primitiveType.requiresIndexBuffer()
                    ? primitiveType.calculateIndexCount(batchEndVertex)
                    : 0;
            int batchIndexCount = batchEndIndex - batchStartIndex;

            DrawPlan.DirectDrawItem drawItem = compileDirectDrawItem(
                    key,
                    batch,
                    batch.getVisibleInstanceCount(),
                    batchStartVertex,
                    batchVertexCount,
                    batchStartIndex,
                    batchIndexCount,
                    batchCurrentInstancedCount);
            if (drawItem != null) {
                drawItems.put(batch, drawItem);
            }
        }

        int totalVertexCount = resolveTotalVertexCount(builders, key, batches);
        int totalIndexCount = primitiveType.requiresIndexBuffer()
                ? resolveTotalIndexCount(totalVertexCount, key, batches)
                : 0;
        GeometryHandleKey geometryHandle = GeometryHandleKey.from(key);
        if (accumulator != null) {
            accumulator.addGeometryUpload(
                    geometryHandle,
                    deriveVertexLayoutKey(key),
                    resource,
                    builders,
                    null,
                    totalVertexCount,
                    totalIndexCount);
        }
        return new DirectBuildResult(resource, geometryHandle, drawItems);
    }

    private DrawPlan.DirectDrawItem compileDirectDrawItem(
            VertexBufferKey key,
            RenderBatch<RasterizationInstanceInfo> batch,
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
            if (batch instanceof MeshRenderBatch meshBatch && meshBatch.mesh() != null) {
                if (primitiveType.requiresIndexBuffer() && meshBatch.mesh().getIndicesCount() > 0) {
                    return DrawPlan.DirectDrawItem.indexed(
                            new VertexDataShard(
                                    0L,
                                    meshBatch.mesh().getVertexOffset(),
                                    meshBatch.mesh().getIndicesCount(),
                                    (long) meshBatch.mesh().getIndexOffset() * Integer.BYTES),
                            batchInstanceCount,
                            baseInstance);
                }
                if (meshBatch.mesh().getVertexCount() > 0) {
                    return DrawPlan.DirectDrawItem.nonIndexed(
                            meshBatch.mesh().getVertexCount(),
                            meshBatch.mesh().getVertexOffset(),
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
        for (int b = 0; b < key.dynamicComponents().length; ++b) {
            VertexResourceManager.BuilderPair pair = builders[b];
            ComponentSpec componentSpec = key.dynamicComponents()[b];
            if (pair.key().equals(componentSpec.getId()) && !componentSpec.isInstanced()) {
                return pair.builder().getVertexCount();
            }
        }
        return 0;
    }

    private int resolveTotalVertexCount(
            VertexResourceManager.BuilderPair[] builders,
            VertexBufferKey key,
            RenderBatchList batches) {
        int dynamicVertexCount = currentNonInstancedVertexCount(builders, key);
        if (dynamicVertexCount > 0) {
            return dynamicVertexCount;
        }
        if (batches.mesh() != null) {
            return batches.mesh().getVertexCount();
        }
        return 0;
    }

    private int resolveTotalIndexCount(int totalVertexCount, VertexBufferKey key, RenderBatchList batches) {
        if (batches.mesh() != null && batches.mesh().getIndicesCount() > 0) {
            return batches.mesh().getIndicesCount();
        }
        return key.renderParameter().primitiveType().calculateIndexCount(totalVertexCount);
    }

    private SubmissionCapability submissionCapabilityOf(List<RasterizationInstanceInfo> visibleInstances) {
        SubmissionCapability capability = SubmissionCapability.DIRECT_ONLY;
        for (RasterizationInstanceInfo info : visibleInstances) {
            SubmissionCapability current = info.getInstance().submissionCapability();
            if (current == SubmissionCapability.GPU_CULL_READY) {
                return current;
            }
            if (current == SubmissionCapability.INDIRECT_READY) {
                capability = current;
            } else if (current == SubmissionCapability.DIRECT_BATCHABLE && capability == SubmissionCapability.DIRECT_ONLY) {
                capability = current;
            }
        }
        return capability;
    }

    private KeyId deriveVertexLayoutKey(VertexBufferKey key) {
        return key != null && key.renderParameter() != null && key.renderParameter().getLayout() != null
                ? KeyId.of("sketch:vertex_layout_" + Integer.toHexString(key.renderParameter().getLayout().hashCode()))
                : KeyId.of("sketch:empty_vertex_layout");
    }

    private long geometryBindingToken(
            KeyId stageId,
            RasterizationParameter rasterParam,
            RenderBatch<RasterizationInstanceInfo> batch,
            long sourceId) {
        GeometrySourceKey sourceKey = GeometrySourceKey.empty();
        if (batch instanceof MeshRenderBatch meshRenderBatch && meshRenderBatch.mesh() != null) {
            sourceKey = GeometrySourceKey.fromPreparedMesh(meshRenderBatch.mesh());
        } else if (!batch.getVisibleInstances().isEmpty()) {
            sourceKey = geometryEncoder.inspect(batch.getVisibleInstances().get(0).getInstance()).sourceKey();
        }
        long stageHash = Integer.toUnsignedLong(stageId != null ? stageId.hashCode() : 0);
        long batchHash = Integer.toUnsignedLong(Objects.hash(rasterParam.getLayout(), rasterParam.primitiveType(), sourceId, sourceKey));
        return (stageHash << 32) ^ batchHash;
    }

    protected static class RenderBatchList {
        protected final List<RenderBatch<RasterizationInstanceInfo>> meshBatches = new ArrayList<>();
        protected final RasterizationParameter renderParameter;
        protected final BakedTypeMesh mesh;
        protected boolean empty = true;

        protected RenderBatchList(RasterizationParameter renderParameter, BakedTypeMesh mesh) {
            this.renderParameter = renderParameter;
            this.mesh = mesh;
        }

        public void add(RenderBatch<RasterizationInstanceInfo> batch) {
            meshBatches.add(batch);
            if (empty && batch.getVisibleInstanceCount() > 0) {
                empty = false;
            }
        }

        public List<RenderBatch<RasterizationInstanceInfo>> meshBatches() {
            return meshBatches;
        }

        public boolean empty() {
            return empty;
        }

        @Nullable
        public BakedTypeMesh mesh() {
            return mesh;
        }

        public RasterizationParameter renderParameter() {
            return renderParameter;
        }
    }

    private record DirectBuildResult(
            VertexResource resource,
            GeometryHandleKey geometryHandle,
            Map<RenderBatch<?>, DrawPlan.DirectDrawItem> drawItems
    ) {
    }

    private record PacketGroupKey(
            PipelineStateKey stateKey,
            ResourceBindingPlan bindingPlan,
            ResourceSetKey resourceSetKey,
            rogo.sketch.core.shader.uniform.UniformGroupSet uniformGroups,
            GeometryHandleKey geometryHandle,
            PrimitiveType primitiveType
    ) {
    }

    private static final class PacketAccumulator {
        private final List<DrawPlan.DirectDrawItem> drawItems = new ArrayList<>();
        private final List<MeshBasedGraphics> completionGraphics = new ArrayList<>();

        void add(DrawPlan.DirectDrawItem drawItem, List<? extends rogo.sketch.core.api.graphics.Graphics> graphics) {
            if (drawItem != null) {
                drawItems.add(drawItem);
            }
            if (graphics == null) {
                return;
            }
            for (rogo.sketch.core.api.graphics.Graphics graphic : graphics) {
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
