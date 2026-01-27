package rogo.sketch.core.pipeline.flow.impl;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import rogo.sketch.core.api.graphics.Graphics;
import rogo.sketch.core.api.graphics.MatrixProvider;
import rogo.sketch.core.api.graphics.MeshProvider;
import rogo.sketch.core.api.model.BakedTypeMesh;
import rogo.sketch.core.api.model.DynamicTypeMesh;
import rogo.sketch.core.api.model.PreparedMesh;
import rogo.sketch.core.command.MultiDrawRenderCommand;
import rogo.sketch.core.command.RenderCommand;
import rogo.sketch.core.command.prosessor.DrawRange;
import rogo.sketch.core.command.prosessor.ProcessorResult;
import rogo.sketch.core.data.builder.VertexDataBuilder;
import rogo.sketch.core.data.format.ComponentSpec;
import rogo.sketch.core.data.format.VertexBufferKey;
import rogo.sketch.core.data.format.VertexLayoutSpec;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.RenderSetting;
import rogo.sketch.core.pipeline.data.IndirectBufferData;
import rogo.sketch.core.pipeline.data.InstancedOffsetData;
import rogo.sketch.core.pipeline.flow.*;
import rogo.sketch.core.pipeline.information.InstanceInfo;
import rogo.sketch.core.pipeline.information.RasterizationInstanceInfo;
import rogo.sketch.core.pipeline.parmeter.RasterizationParameter;
import rogo.sketch.core.pipeline.parmeter.RenderParameter;
import rogo.sketch.core.resource.ResourceBinding;
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
public class RasterizationFlowStrategy implements RenderFlowStrategy {

    @Override
    public RenderFlowType getFlowType() {
        return RenderFlowType.RASTERIZATION;
    }

    @Override
    public RenderPostProcessor createPostProcessor() {
        return new RasterizationPostProcessor();
    }

    @Override
    @Nullable
    public <C extends RenderContext> InstanceInfo collectInstanceInfo(Graphics instance, RenderParameter renderParameter, C context) {
        RenderSetting renderSetting = RenderSetting.fromPartial(renderParameter, instance.getPartialRenderSetting());
        ResourceBinding resourceBinding = renderSetting.resourceBinding();
        PreparedMesh mesh = extractMesh(instance);
        Matrix4f meshMatrix = extractMeshMatrix(instance);

        int vertexCount = (mesh != null) ? mesh.getVertexCount() : 0;

        if (vertexCount == 0 && mesh == null) {
            return null; // No geometry to render
        }

        // Determine if this is instanced rendering based on RenderParameter
        RenderParameter param = renderSetting.renderParameter();
        VertexLayoutSpec layout = param.getLayout();
        boolean isInstancedRendering = layout != null && !layout.getDynamicSpecs().isEmpty();
        // Or check specifically for instanced components?
        // Actually, if layout has ANY instanced component, we flag it.
        // But the user might want explicitly "instanced" vs "dynamic but not
        // instanced"?
        // New design: If RenderParameter has instanced components, we treat it as
        // instanced draw.

        return new RasterizationInstanceInfo(
                instance,
                renderSetting,
                resourceBinding,
                mesh,
                meshMatrix,
                vertexCount,
                isInstancedRendering);
    }

    @Override
    public Map<RenderSetting, List<RenderCommand>> createRenderCommands(
            Collection<InstanceInfo> infos,
            KeyId stageId,
            RenderFlowContext flowContext,
            RenderPostProcessors postProcessors) {
        // Filter to only RasterizationInstanceInfo
        List<RasterizationInstanceInfo> rasterInfos = infos.stream()
                .filter(info -> info instanceof RasterizationInstanceInfo)
                .map(info -> (RasterizationInstanceInfo) info)
                .toList();

        if (rasterInfos.isEmpty()) {
            return java.util.Collections.emptyMap();
        }

        // Calculate vertex offsets for dynamic meshes
        int currentVertexOffset = 0;
        int currentIndexOffset = 0;

        for (RasterizationInstanceInfo info : rasterInfos) {
            if (info.hasMesh()) {
                PreparedMesh mesh = info.getMesh();
                int vertexCount = mesh.getVertexCount();
                int indexCount = mesh.getIndicesCount();

                if (mesh instanceof DynamicTypeMesh) {
                    info.setVertexOffset(currentVertexOffset);
                    if (mesh.isIndexed()) {
                        info.setIndexOffset(currentIndexOffset);
                    }
                    currentVertexOffset += vertexCount;
                    currentIndexOffset += indexCount;
                }
            }
        }

        // Organize into batches
        List<RenderBatch<RasterizationInstanceInfo>> batches = organizeToBatches(rasterInfos);

        // Group by VertexBufferKey
        Map<VertexBufferKey, RenderBatchList> keyGroups = groupByVertexBufferKey(batches);

        // Process each group and create commands
        Map<RenderSetting, List<RenderCommand>> commandsMap = new LinkedHashMap<>();
        IndirectBufferData indirectBufferData = flowContext.getPipelineData(IndirectBufferData.KEY);
        Map<RenderParameter, IndirectCommandBuffer> indirectBuffers = indirectBufferData.getAll();

        RasterizationPostProcessor processor = postProcessors.get(getFlowType());

        for (Map.Entry<VertexBufferKey, RenderBatchList> entry : keyGroups.entrySet()) {
            VertexBufferKey key = entry.getKey();
            RenderBatchList keyBatches = entry.getValue();

            ProcessorResult result = processBatches(key, keyBatches, flowContext, processor);
            if (result == null)
                continue;

            VertexResource resource = result.resource();
            IndirectCommandBuffer indirectBuffer = indirectBuffers.get(key.renderParameter());

            for (RenderBatch<? extends RasterizationInstanceInfo> batch : keyBatches.meshBatches()) {
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

    // ===== Helper Methods (Moved from GeometryBatchProcessor) =====

    /**
     * Organize rasterization infos into render batches grouped by RenderSetting and
     * Source (Mesh) ID.
     */
    private List<RenderBatch<RasterizationInstanceInfo>> organizeToBatches(List<RasterizationInstanceInfo> infos) {
        Map<RenderSetting, Map<MeshHolder, List<RasterizationInstanceInfo>>> grouped = new LinkedHashMap<>();

        for (RasterizationInstanceInfo info : infos) {
            RenderSetting setting = info.getRenderSetting();
            MeshHolder sourceId = getSourceId(info);

            grouped.computeIfAbsent(setting, k -> new LinkedHashMap<>())
                    .computeIfAbsent(sourceId, k -> new ArrayList<>())
                    .add(info);
        }

        List<RenderBatch<RasterizationInstanceInfo>> batches = new ArrayList<>();
        for (Map.Entry<RenderSetting, Map<MeshHolder, List<RasterizationInstanceInfo>>> settingEntry : grouped.entrySet()) {
            RenderSetting setting = settingEntry.getKey();
            for (Map.Entry<MeshHolder, List<RasterizationInstanceInfo>> sourceEntry : settingEntry.getValue().entrySet()) {
                if (sourceEntry.getKey().bakedTypeMesh() == null) {
                    batches.add(new RenderBatch<>(setting, sourceEntry.getValue()));
                } else {
                    batches.add(new MeshRenderBatch(setting, sourceEntry.getKey().bakedTypeMesh(), sourceEntry.getValue()));
                }

            }
        }

        return batches;
    }

    private MeshHolder getSourceId(RasterizationInstanceInfo info) {
        if (info.hasMesh()) {
            PreparedMesh mesh = info.getMesh();
            return new MeshHolder(mesh);
        }

        return MeshHolder.EMPTY;
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
            // Since organizeToBatches guarantees all instances in a batch share the same
            // SourceID
            long sourceId = -1;
            BakedTypeMesh mesh;
            if (!batch.getInstances().isEmpty() && batch instanceof MeshRenderBatch meshRenderBatch) {
                sourceId = meshRenderBatch.mesh().getVAOHandle();
                mesh = meshRenderBatch.mesh();
            } else {
                mesh = null;
            }

            // Build key with source ID
            VertexBufferKey key = VertexBufferKey.fromParameter(rasterParam, sourceId);
            keyGroups.computeIfAbsent(key, k -> new RenderBatchList((RasterizationParameter) param, mesh)).add(batch);
        }

        return keyGroups;
    }

    /**
     * Process a group of batches that share the same VertexBufferKey.
     * Uses the batch processing strategy registry.
     */
    /**
     * Process a group of batches that share the same VertexBufferKey.
     * Implements unified batch processing logic.
     */
    private ProcessorResult processBatches(VertexBufferKey key, RenderBatchList batches, RenderFlowContext context, RasterizationPostProcessor accumulator) {
        VertexResourceManager resourceManager = context.getResourceManager();
        IndirectBufferData indirectBufferData = context.getPipelineData(IndirectBufferData.KEY);
        InstancedOffsetData instancedOffsetData = context.getPipelineData(InstancedOffsetData.KEY);
        Map<RenderParameter, IndirectCommandBuffer> indirectBuffers = indirectBufferData.getAll();
        Map<RenderParameter, AtomicInteger> instancedOffsets = instancedOffsetData.getAll();

        // 1. Calculate totals for builders
        int totalInstances = 0;
        int totalVertices = 0;
        for (RenderBatch<RasterizationInstanceInfo> batch : batches.meshBatches()) {
            totalInstances += batch.getInstances().size();
            for (RasterizationInstanceInfo info : batch.getInstances()) {
                totalVertices += info.getVertexCount(); // Accumulate for dynamic vertex buffers
            }
        }

        if (totalInstances == 0)
            return null;

        // 2. Prepare Builders and Source Resource
        VertexResource source = null;
        if (key.sourceResourceID() > 0) {
            // Attempt to get source from first baked mesh in batches
            if (!batches.empty() && batches.mesh() != null) {
                source = batches.mesh().getSourceResource();
            }
        }

        VertexResource resource = resourceManager.get(key, source);
        IndirectCommandBuffer indirectBuffer = indirectBuffers.computeIfAbsent(key.renderParameter(), k -> new IndirectCommandBuffer(1280));
        AtomicInteger batchCurrentInstancedCount = instancedOffsets.computeIfAbsent(key.renderParameter(), k -> new AtomicInteger(0));

        // Create builders for MUTABLE components
        int builderCapacity = Math.max(totalVertices, totalInstances);
        Map<KeyId, VertexDataBuilder> builders = resourceManager.createBuilder(key.renderParameter(), builderCapacity);

        Map<RenderBatch<?>, DrawRange> ranges = new HashMap<>();
        boolean isInstancedDraw = key.hasInstancing();
        int currentVertexOffset = 0; // For Standard/Dynamic Vertex Buffer accumulation

        // Use the current vertex count from the first non-instanced builder as the
        // start offset
        // This ensures correct offset when builders are reused across batches
        for (ComponentSpec spec : key.components()) {
            if (!spec.isInstanced() && builders.containsKey(spec.getId())) {
                currentVertexOffset = builders.get(spec.getId()).getVertexCount();
                RenderParameter param = key.renderParameter();
                if (param instanceof RasterizationParameter rasterParam && rasterParam.primitiveType().requiresIndexBuffer()) {
                    currentVertexOffset = rasterParam.primitiveType().calculateIndexCount(currentVertexOffset);
                }
                break;
            }
        }

        // 3. Process Batches
        for (RenderBatch<RasterizationInstanceInfo> batch : batches.meshBatches()) {
            int batchStartCommand = indirectBuffer.getCommandCount();
            int batchInstanceCount = 0;
            int validInstanceCount = 0;

            for (RasterizationInstanceInfo info : batch.getInstances()) {
                Graphics instance = info.getInstance();
                validInstanceCount++;
                batchInstanceCount++;

                // Fill Data
                if (instance instanceof MeshProvider provider) {
                    for (Map.Entry<KeyId, VertexDataBuilder> entry : builders.entrySet()) {
                        provider.fillVertex(entry.getKey(), entry.getValue());
                    }
                }

                // Generate Commands (Per Instance if Standard)
                if (!isInstancedDraw) {
                    int vCount = info.getVertexCount();
                    RenderParameter param = info.getRenderSetting().renderParameter();
                    if (param instanceof RasterizationParameter rasterParam
                            && rasterParam.primitiveType().requiresIndexBuffer()) {
                        vCount = rasterParam.primitiveType().calculateIndexCount(vCount);
                    }

                    indirectBuffer.addDrawCommand(
                            vCount,
                            1,
                            currentVertexOffset, // Start relative to this buffer
                            0); // BaseInstance always 0 for non-instanced?

                    currentVertexOffset += vCount;
                }
            }

            // Generate Commands (Per Batch if Instanced)
            if (isInstancedDraw && validInstanceCount > 0 && batch instanceof MeshRenderBatch meshBatch) {
                // Determine vertex count from mesh (Shared)
                int vCount = 0;
                if (!batch.getInstances().isEmpty()) {
                    vCount = key.renderParameter().primitiveType().requiresIndexBuffer()
                            ? meshBatch.mesh().getIndicesCount()
                            : meshBatch.mesh().getVertexCount();
                }

                indirectBuffer.addDrawCommand(
                        vCount,
                        batchInstanceCount, // Draw all valid instances in batch
                        key.renderParameter().primitiveType().requiresIndexBuffer()
                                ? meshBatch.mesh().getIndexOffset()
                                : meshBatch.mesh().getVertexOffset(), // Vertex Offset?
                        // If BakedMesh, offset is 0 relative to VBO start (handled by VAO binding
                        // usually?
                        // Or Resource handles sharing).
                        // If VertexResource.share from source, the VBO is bound.
                        // Does Source have offset?
                        // BakedTypeMesh usually is its own VBO or sub-allocated.
                        // If sub-allocated, we need baseVertex.
                        // But currently VBOComponent assumes 0 offset or handled in VertexResource?
                        // Let's assume 0 for shared/baked VBOs for now.
                        batchCurrentInstancedCount.getAndAdd(batchInstanceCount)); // BaseInstance
            }

            ranges.put(batch, new DrawRange(
                    batchStartCommand,
                    indirectBuffer.getCommandCount() - batchStartCommand));
        }

        // 4. Deferred Upload Data (Handled by createRenderCommands registration)
        // accumulator.uploadAll() will be called once per frame.
        if (accumulator != null) {
            accumulator.add(resource, builders);
        }

        return new ProcessorResult(resource, ranges);
    }

    @Override
    public boolean supportsParallel() {
        return true;
    }

    @Nullable
    private static PreparedMesh extractMesh(Graphics instance) {
        if (instance instanceof MeshProvider provider) {
            return provider.getPreparedMesh();
        }
        return null;
    }

    private static Matrix4f extractMeshMatrix(Graphics instance) {
        if (instance instanceof MatrixProvider provider) {
            return provider.getMeshMatrix();
        }
        return new Matrix4f();
    }

    protected static class MeshHolder {
        protected static final MeshHolder EMPTY = new MeshHolder(null);
        protected final BakedTypeMesh mesh;
        protected final long meshHandle;
        protected final KeyId meshId;

        public MeshHolder(@Nullable PreparedMesh mesh) {
            if (mesh instanceof BakedTypeMesh baked) {
                meshHandle = baked.getVAOHandle();
                meshId = baked.getKetId();
                this.mesh = (BakedTypeMesh) mesh;
            } else {
                meshHandle = -1;
                this.mesh = null;
                this.meshId = null;
            }
        }

        @Nullable
        public BakedTypeMesh bakedTypeMesh() {
            return mesh;
        }

        @Nullable
        public KeyId meshId() {
            return meshId;
        }

        public long getMeshHandle() {
            return meshHandle;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            MeshHolder that = (MeshHolder) o;
            return meshHandle == that.meshHandle && Objects.equals(meshId, that.meshId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(meshHandle, meshId);
        }
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
            if (empty && batch.getInstanceCount() > 0) {
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