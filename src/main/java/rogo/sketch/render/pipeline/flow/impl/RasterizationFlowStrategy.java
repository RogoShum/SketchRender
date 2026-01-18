package rogo.sketch.render.pipeline.flow.impl;

import org.joml.Matrix4f;
import rogo.sketch.api.graphics.*;
import rogo.sketch.api.model.BakedTypeMesh;
import rogo.sketch.api.model.DynamicTypeMesh;
import rogo.sketch.api.model.PreparedMesh;
import rogo.sketch.render.command.MultiDrawRenderCommand;
import rogo.sketch.render.command.RenderCommand;
import rogo.sketch.render.command.prosessor.DrawRange;
import rogo.sketch.render.command.prosessor.ProcessorResult;
import rogo.sketch.render.data.builder.VertexDataBuilder;
import rogo.sketch.render.data.format.ComponentSpec;
import rogo.sketch.render.data.format.VertexLayoutSpec;
import rogo.sketch.render.data.format.VertexBufferKey;
import rogo.sketch.render.pipeline.*;
import rogo.sketch.render.pipeline.flow.RenderFlowContext;
import rogo.sketch.render.pipeline.flow.RenderFlowStrategy;
import rogo.sketch.render.pipeline.flow.RenderFlowType;
import rogo.sketch.render.pipeline.flow.RenderPostProcessor;
import rogo.sketch.render.pipeline.information.InstanceInfo;
import rogo.sketch.render.pipeline.information.RasterizationInstanceInfo;
import rogo.sketch.render.resource.ResourceBinding;
import rogo.sketch.render.resource.buffer.IndirectCommandBuffer;
import rogo.sketch.render.resource.buffer.VertexResource;
import rogo.sketch.render.vertex.VertexResourceManager;
import rogo.sketch.util.Identifier;
import javax.annotation.Nullable;
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
    public <C extends RenderContext> InstanceInfo collectInstanceInfo(
            Graphics instance,
            RenderSetting renderSetting,
            C context) {
        if (!instance.shouldRender()) {
            return null;
        }

        ResourceBinding resourceBinding = renderSetting.resourceBinding();
        PreparedMesh mesh = extractMesh(instance);
        Matrix4f meshMatrix = extractMeshMatrix(instance);

        int vertexCount = (mesh != null) ? mesh.getVertexCount() : 0;
        if (vertexCount == 0 && instance instanceof VertexCountProvider provider) {
            vertexCount = provider.getVertexCount();
        }

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
            Identifier stageId,
            RenderFlowContext flowContext,
            rogo.sketch.render.pipeline.flow.RenderPostProcessors postProcessors) {
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
        Map<VertexBufferKey, List<RenderBatch<RasterizationInstanceInfo>>> keyGroups = groupByVertexBufferKey(batches);

        // Process each group and create commands
        Map<RenderSetting, List<RenderCommand>> commandsMap = new LinkedHashMap<>();
        Map<RenderParameter, IndirectCommandBuffer> indirectBuffers = flowContext.indirectBuffers();

        RasterizationPostProcessor processor = postProcessors.get(getFlowType());

        for (Map.Entry<VertexBufferKey, List<RenderBatch<RasterizationInstanceInfo>>> entry : keyGroups.entrySet()) {
            VertexBufferKey key = entry.getKey();
            List<RenderBatch<RasterizationInstanceInfo>> keyBatches = entry.getValue();

            ProcessorResult result = processBatches(key, keyBatches, flowContext, processor);
            if (result == null)
                continue;

            VertexResource resource = result.resource();
            IndirectCommandBuffer indirectBuffer = indirectBuffers.get(key.renderParameter());

            for (RenderBatch<? extends RasterizationInstanceInfo> batch : keyBatches) {
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

        indirectBuffers.values().forEach(buffer -> {
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
        // Group by Composite Key (RenderSetting + SourceID)
        // Since we don't have a Pair/Tuple class, we can use a custom key or just
        // nested maps.
        // Let's use a custom record-like class or just assumption that RenderSetting is
        // unique enough?
        // No, RenderSetting doesn't include Mesh.
        // We must split by Source ID (for BakedMeshes).

        Map<RenderSetting, Map<Long, List<RasterizationInstanceInfo>>> grouped = new LinkedHashMap<>();

        for (RasterizationInstanceInfo info : infos) {
            RenderSetting setting = info.getRenderSetting();
            long sourceId = getSourceId(info);

            grouped.computeIfAbsent(setting, k -> new LinkedHashMap<>())
                    .computeIfAbsent(sourceId, k -> new ArrayList<>())
                    .add(info);
        }

        List<RenderBatch<RasterizationInstanceInfo>> batches = new ArrayList<>();
        for (Map.Entry<RenderSetting, Map<Long, List<RasterizationInstanceInfo>>> settingEntry : grouped.entrySet()) {
            RenderSetting setting = settingEntry.getKey();
            for (Map.Entry<Long, List<RasterizationInstanceInfo>> sourceEntry : settingEntry.getValue().entrySet()) {
                batches.add(new RenderBatch<>(setting, sourceEntry.getValue()));
            }
        }
        return batches;
    }

    private long getSourceId(RasterizationInstanceInfo info) {
        if (info.hasMesh()) {
            PreparedMesh mesh = info.getMesh();
            if (mesh instanceof BakedTypeMesh baked) {
                // Use the handle of the source VAO/VBO container as the ID
                return baked.getVAOHandle();
            }
        }
        return 0; // Dynamic or no mesh
    }

    /**
     * Group render batches by their VertexBufferKey.
     */
    private Map<VertexBufferKey, List<RenderBatch<RasterizationInstanceInfo>>> groupByVertexBufferKey(
            List<RenderBatch<RasterizationInstanceInfo>> batches) {
        Map<VertexBufferKey, List<RenderBatch<RasterizationInstanceInfo>>> keyGroups = new HashMap<>();

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
            long sourceId = 0;
            if (!batch.getInstances().isEmpty()) {
                sourceId = getSourceId(batch.getInstances().get(0));
            }

            // Build key with source ID
            VertexBufferKey key = VertexBufferKey.fromParameter(rasterParam, sourceId);

            keyGroups.computeIfAbsent(key, k -> new ArrayList<>()).add(batch);
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
    private ProcessorResult processBatches(
            VertexBufferKey key,
            List<RenderBatch<RasterizationInstanceInfo>> batches,
            RenderFlowContext context,
            RasterizationPostProcessor accumulator) {
        if (batches.isEmpty())
            return null;

        VertexResourceManager resourceManager = context.getResourceManager();
        Map<RenderParameter, IndirectCommandBuffer> indirectBuffers = context.indirectBuffers();
        Map<RenderParameter, AtomicInteger> instancedOffsets = context.instancedOffsets();

        // 1. Calculate totals for builders
        int totalInstances = 0;
        int totalVertices = 0;
        for (RenderBatch<RasterizationInstanceInfo> batch : batches) {
            totalInstances += batch.getInstances().size();
            for (RasterizationInstanceInfo info : batch.getInstances()) {
                if (info.getInstance().shouldRender()) {
                    totalVertices += info.getVertexCount(); // Accumulate for dynamic vertex buffers
                }
            }
        }

        if (totalInstances == 0)
            return null;

        // 2. Prepare Builders and Source Resource
        VertexResource source = null;
        if (key.sourceResourceID() != 0) {
            // Attempt to get source from first baked mesh in batches
            if (!batches.isEmpty() && !batches.get(0).getInstances().isEmpty()) {
                RasterizationInstanceInfo first = batches.get(0).getInstances().get(0);
                if (first.getMesh() instanceof BakedTypeMesh baked) {
                    source = baked.getSourceResource();
                }
            }
        }
        VertexResource resource = resourceManager.get(key, source);
        IndirectCommandBuffer indirectBuffer = indirectBuffers.computeIfAbsent(key.renderParameter(), k -> new IndirectCommandBuffer(1280));
        AtomicInteger batchCurrentInstancedCount = instancedOffsets.computeIfAbsent(key.renderParameter(), k -> new AtomicInteger(0));

        // Create builders for MUTABLE components
        int builderCapacity = Math.max(totalVertices, totalInstances);
        Map<Integer, VertexDataBuilder> builders = resourceManager.createBuilder(key.renderParameter(), builderCapacity);

        Map<RenderBatch<?>, DrawRange> ranges = new HashMap<>();
        boolean isInstancedDraw = key.hasInstancing();
        int currentVertexOffset = 0; // For Standard/Dynamic Vertex Buffer accumulation

        // Use the current vertex count from the first non-instanced builder as the
        // start offset
        // This ensures correct offset when builders are reused across batches
        for (ComponentSpec spec : key.components()) {
            if (!spec.isInstanced() && builders.containsKey(spec.getBindingPoint())) {
                currentVertexOffset = builders.get(spec.getBindingPoint()).getVertexCount();
                RenderParameter param = key.renderParameter();
                if (param instanceof RasterizationParameter rasterParam
                        && rasterParam.primitiveType().requiresIndexBuffer()) {
                    currentVertexOffset = rasterParam.primitiveType().calculateIndexCount(currentVertexOffset);
                }
                break;
            }
        }

        // 3. Process Batches
        for (RenderBatch<RasterizationInstanceInfo> batch : batches) {
            int batchStartCommand = indirectBuffer.getCommandCount();
            int batchInstanceCount = 0;
            int validInstanceCount = 0;

            for (RasterizationInstanceInfo info : batch.getInstances()) {
                Graphics instance = info.getInstance();
                if (!instance.shouldRender())
                    continue;

                validInstanceCount++;
                batchInstanceCount++;

                // Fill Data
                for (Map.Entry<Integer, VertexDataBuilder> entry : builders
                        .entrySet()) {
                    int bindingPoint = entry.getKey();
                    // Identify if this component is instanced or vertex-based
                    boolean isCompInstanced = key.components().stream()
                            .filter(c -> c.getBindingPoint() == bindingPoint)
                            .findFirst().map(ComponentSpec::isInstanced).orElse(false);

                    if (isCompInstanced) {
                        // Instanced Data
                        if (instance instanceof InstanceDataProvider provider) {
                            provider.fillInstanceData(entry.getKey(), entry.getValue());
                        }
                    } else {
                        // Standard Vertex Data (Dynamic Mesh)
                        // Assuming this is Binding 0 or similar main vertex buffer
                        if (info.hasMesh()) {
                            PreparedMesh mesh = info.getMesh();
                            // Only fill if it's dynamic? Baked mesh usually immutable component.
                            // But if we have a mutable vertex component, we MUST fill it.
                            if (mesh instanceof DynamicTypeMesh dynamicMesh) {
                                dynamicMesh.fill(entry.getKey(), entry.getValue(), info.getMeshMatrix());
                            }
                        }
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
            if (isInstancedDraw && validInstanceCount > 0) {
                // Determine vertex count from mesh (Shared)
                int vCount = 0;
                if (!batch.getInstances().isEmpty() && batch.getInstances().get(0).hasMesh()) {
                    vCount = key.renderParameter().primitiveType().requiresIndexBuffer()
                            ? batch.getInstances().get(0).getMesh().getIndicesCount()
                            : batch.getInstances().get(0).getMesh().getVertexCount();
                }

                indirectBuffer.addDrawCommand(
                        vCount,
                        batchInstanceCount, // Draw all valid instances in batch
                        key.renderParameter().primitiveType().requiresIndexBuffer()
                                ? batch.getInstances().get(0).getMesh().getIndexOffset()
                                : batch.getInstances().get(0).getMesh().getVertexOffset(), // Vertex Offset?
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
}