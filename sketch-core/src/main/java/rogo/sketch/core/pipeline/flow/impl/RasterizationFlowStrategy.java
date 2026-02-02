package rogo.sketch.core.pipeline.flow.impl;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.api.graphics.MeshBasedGraphics;
import rogo.sketch.core.api.model.BakedTypeMesh;
import rogo.sketch.core.command.MultiDrawRenderCommand;
import rogo.sketch.core.command.RenderCommand;
import rogo.sketch.core.command.prosessor.DrawRange;
import rogo.sketch.core.command.prosessor.ProcessorResult;
import rogo.sketch.core.data.PrimitiveType;
import rogo.sketch.core.data.format.ComponentSpec;
import rogo.sketch.core.data.format.VertexBufferKey;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.RenderSetting;
import rogo.sketch.core.pipeline.data.IndirectBufferData;
import rogo.sketch.core.pipeline.data.InstancedOffsetData;
import rogo.sketch.core.pipeline.flow.*;
import rogo.sketch.core.pipeline.information.RasterizationInstanceInfo;
import rogo.sketch.core.pipeline.parmeter.RasterizationParameter;
import rogo.sketch.core.pipeline.parmeter.RenderParameter;
import rogo.sketch.core.resource.buffer.IndirectCommandBuffer;
import rogo.sketch.core.resource.buffer.VertexResource;
import rogo.sketch.core.util.KeyId;
import rogo.sketch.core.vertex.VertexResourceManager;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Flow strategy for rasterization rendering pipeline.
 * <p>
 * Handles traditional vertex/fragment shader rendering with geometry batching.
 * Collects mesh data, transformation matrices, and vertex layouts from graphics
 * instances.
 * </p>
 */
public class RasterizationFlowStrategy implements RenderFlowStrategy<MeshBasedGraphics, RasterizationInstanceInfo> {

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
    public Map<RenderSetting, List<RenderCommand>> createRenderCommands(
            BatchContainer<MeshBasedGraphics, RasterizationInstanceInfo> batchContainer,
            KeyId stageId,
            RenderFlowContext flowContext,
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
            List<RasterizationInstanceInfo> visibleInfos = filterVisible(batch.getInstances());
            if (!visibleInfos.isEmpty()) {
                batch.setVisibleInstances(visibleInfos);
                batch.updateUniformsForVisible();
                batchesWithVisible.add(batch);
            }
        }

        if (batchesWithVisible.isEmpty()) {
            return Collections.emptyMap();
        }

        // Group by VertexBufferKey
        Map<VertexBufferKey, RenderBatchList> keyGroups = groupByVertexBufferKey(batchesWithVisible);

        // Process each group and create commands
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

        indirectBufferData.getAll().values().forEach(buffer -> {
            buffer.bind();
            buffer.upload();
        });
        IndirectCommandBuffer.unBind();

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
     * Filter visible instances from a batch.
     */
    private List<RasterizationInstanceInfo> filterVisible(List<RasterizationInstanceInfo> infos) {
        List<RasterizationInstanceInfo> visible = new ArrayList<>();
        for (RasterizationInstanceInfo info : infos) {
            if (info.getInstance().shouldRender()) {
                visible.add(info);
            }
        }
        return visible;
    }

    /**
     * Group render batches by their VertexBufferKey.
     */
    private Map<VertexBufferKey, RenderBatchList> groupByVertexBufferKey(List<RenderBatch<RasterizationInstanceInfo>> batches) {
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
                sourceId = meshRenderBatch.mesh().getVAOHandle();
                mesh = meshRenderBatch.mesh();
            } else {
                mesh = null;
            }

            // Build key with source ID
            VertexBufferKey key = VertexBufferKey.fromParameter(rasterParam, sourceId);
            keyGroups.computeIfAbsent(key, k -> new RenderBatchList(rasterParam, mesh)).add(batch);
        }

        return keyGroups;
    }

    /**
     * Process a group of batches that share the same VertexBufferKey.
     */
    private ProcessorResult processBatches(VertexBufferKey key, RenderBatchList batches, RenderFlowContext context, RasterizationPostProcessor accumulator, boolean nextTick) {
        VertexResourceManager resourceManager = context.getResourceManager();
        IndirectBufferData indirectBufferData = context.getPipelineData(IndirectBufferData.KEY);
        InstancedOffsetData instancedOffsetData = context.getPipelineData(InstancedOffsetData.KEY);
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
        IndirectCommandBuffer indirectBuffer = indirectBuffers.computeIfAbsent(key.renderParameter(), k -> new IndirectCommandBuffer(1280));
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
                // Fill Data
                for (int b = 0; b < builders.length; ++b) {
                    VertexResourceManager.BuilderPair pair = builders[b];
                    instance.fillVertex(pair.key(), pair.builder());
                }

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
}
