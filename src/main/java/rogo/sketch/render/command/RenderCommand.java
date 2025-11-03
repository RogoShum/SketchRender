package rogo.sketch.render.command;

import rogo.sketch.render.data.PrimitiveType;
import rogo.sketch.render.pipeline.RenderSetting;
import rogo.sketch.render.pipeline.UniformBatchGroup;
import rogo.sketch.render.pipeline.information.GraphicsInformation;
import rogo.sketch.render.pipeline.information.RenderList;
import rogo.sketch.render.resource.ResourceBinding;
import rogo.sketch.render.resource.buffer.VertexResource;
import rogo.sketch.render.vertex.VertexRenderer;
import rogo.sketch.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * A render command that encapsulates all necessary GL parameters for efficient batched rendering
 * Uses glDrawElementsInstancedBaseVertexBaseInstance for maximum flexibility
 */
public class RenderCommand {
    private final VertexResource vertexResource;
    private final RenderSetting renderSetting;
    private final ResourceBinding resourceBinding;
    private final Identifier stageId;

    // GL rendering parameters
    private final PrimitiveType primitiveType;
    private final VertexDataShard vertexDataShard;    // vertex offset in VBO
    private final int instanceCount; // number of instances
    private final int baseInstance;  // instance offset in InstanceVBO

    // Instance data for debugging/reference
    private final List<InstanceData> instances;

    // Uniform batch groups for new pipeline
    private final List<UniformBatchGroup> uniformBatches;

    public RenderCommand(VertexResource vertexResource,
                         RenderSetting renderSetting,
                         ResourceBinding resourceBinding,
                         Identifier stageId,
                         PrimitiveType primitiveType,
                         VertexDataShard vertexDataShard,
                         int instanceCount,
                         int baseInstance,
                         List<InstanceData> instances) {
        this(vertexResource, renderSetting, resourceBinding, stageId, primitiveType,
                vertexDataShard, instanceCount, baseInstance, instances, new ArrayList<>());
    }

    public RenderCommand(VertexResource vertexResource,
                         RenderSetting renderSetting,
                         ResourceBinding resourceBinding,
                         Identifier stageId,
                         PrimitiveType primitiveType,
                         VertexDataShard vertexDataShard,
                         int instanceCount,
                         int baseInstance,
                         List<InstanceData> instances,
                         List<UniformBatchGroup> uniformBatches) {
        this.vertexResource = vertexResource;
        this.renderSetting = renderSetting;
        this.resourceBinding = resourceBinding;
        this.stageId = stageId;
        this.primitiveType = primitiveType;
        this.vertexDataShard = vertexDataShard;
        this.instanceCount = instanceCount;
        this.baseInstance = baseInstance;
        this.instances = List.copyOf(instances);
        this.uniformBatches = List.copyOf(uniformBatches);
    }

    /**
     * Execute this render command using the unified GL call
     */
    public void execute() {
        if (vertexDataShard.indexCount() == 0 || instanceCount == 0) {
            return;
        }

        // Use the unified drawing call with all offsets
        VertexRenderer.renderWithOffsets(
                vertexResource,
                primitiveType,
                vertexDataShard.indexCount(),
                vertexDataShard.indicesOffset(),
                vertexDataShard.vertexOffset(),
                instanceCount,
                baseInstance
        );
    }

    /**
     * Execute this render command as a single draw (no instancing)
     * Useful for debugging or when instancing is not desired
     */
    public void executeSingleDraw() {
        if (vertexDataShard.indexCount() == 0) {
            return;
        }

        VertexRenderer.renderElements(
                vertexResource,
                primitiveType,
                vertexDataShard.indexCount(),
                vertexDataShard.indicesOffset(),
                vertexDataShard.vertexOffset()
        );
    }

    // Getters for GL parameters
    public VertexResource getVertexResource() {
        return vertexResource;
    }

    public RenderSetting getRenderSetting() {
        return renderSetting;
    }

    public ResourceBinding getResourceBinding() {
        return resourceBinding;
    }

    public Identifier getStageId() {
        return stageId;
    }

    public PrimitiveType getPrimitiveType() {
        return primitiveType;
    }

    public VertexDataShard getVertexDataShard() {
        return vertexDataShard;
    }

    public int getInstanceCount() {
        return instanceCount;
    }

    public int getBaseInstance() {
        return baseInstance;
    }

    public List<InstanceData> getInstances() {
        return instances;
    }

    public List<UniformBatchGroup> getUniformBatches() {
        return uniformBatches;
    }

    public int getGraphicsInstanceCount() {
        return instances.size();
    }

    public int getTotalVertexCount() {
        return instances.stream().mapToInt(InstanceData::getVertexCount).sum();
    }

    @Override
    public String toString() {
        return "RenderCommand{" +
                "stageId=" + stageId +
                ", primitiveType=" + primitiveType +
                ", vertexDataShard=" + vertexDataShard +
                ", instanceCount=" + instanceCount +
                ", baseInstance=" + baseInstance +
                ", graphicsInstances=" + instances.size() +
                '}';
    }

    /**
     * Data for a single instance within a render command
     * Simplified to track offsets for debugging purposes
     */
    public static class InstanceData {
        private final GraphicsInformation graphicsInfo;
        private final int vertexOffset;
        private final int vertexCount;
        private final int instanceIndex; // Index within the batch

        public InstanceData(GraphicsInformation graphicsInfo,
                            int vertexOffset,
                            int vertexCount,
                            int instanceIndex) {
            this.graphicsInfo = graphicsInfo;
            this.vertexOffset = vertexOffset;
            this.vertexCount = vertexCount;
            this.instanceIndex = instanceIndex;
        }

        public GraphicsInformation getGraphicsInfo() {
            return graphicsInfo;
        }

        public int getVertexOffset() {
            return vertexOffset;
        }

        public int getVertexCount() {
            return vertexCount;
        }

        public int getInstanceIndex() {
            return instanceIndex;
        }

        @Override
        public String toString() {
            return "InstanceData{" +
                    "instance=" + graphicsInfo.getInstance().getIdentifier() +
                    ", vertexOffset=" + vertexOffset +
                    ", vertexCount=" + vertexCount +
                    ", instanceIndex=" + instanceIndex +
                    '}';
        }
    }

    /**
     * Builder class for creating render commands with proper offset calculations
     */
    public static class Builder {
        private VertexResource vertexResource;
        private RenderSetting renderSetting;
        private ResourceBinding resourceBinding;
        private Identifier stageId;
        private PrimitiveType primitiveType;
        private final List<GraphicsInformation> graphicsInfos = new ArrayList<>();
        private final List<UniformBatchGroup> uniformBatches = new ArrayList<>();

        public Builder vertexResource(VertexResource vertexResource) {
            this.vertexResource = vertexResource;
            return this;
        }

        public Builder renderSetting(RenderSetting renderSetting) {
            this.renderSetting = renderSetting;
            this.resourceBinding = renderSetting.resourceBinding();
            this.primitiveType = renderSetting.renderParameter().primitiveType();
            return this;
        }

        public Builder stageId(Identifier stageId) {
            this.stageId = stageId;
            return this;
        }

        public Builder addGraphicsInfo(GraphicsInformation info) {
            this.graphicsInfos.add(info);
            return this;
        }

        public Builder addGraphicsInfos(List<GraphicsInformation> infos) {
            this.graphicsInfos.addAll(infos);
            return this;
        }

        public Builder addUniformBatch(UniformBatchGroup batch) {
            this.uniformBatches.add(batch);
            return this;
        }

        public Builder addUniformBatches(List<UniformBatchGroup> batches) {
            this.uniformBatches.addAll(batches);
            return this;
        }

        public RenderCommand build() {
            if (graphicsInfos.isEmpty()) {
                throw new IllegalArgumentException("Cannot create render command with no graphics instances");
            }

            // Calculate offsets and parameters
            List<InstanceData> instances = new ArrayList<>();
            int totalIndexCount = 0;
            int firstVertexOffset = graphicsInfos.get(0).getVertexOffset();

            for (int i = 0; i < graphicsInfos.size(); i++) {
                GraphicsInformation info = graphicsInfos.get(i);
                instances.add(new InstanceData(
                        info,
                        info.getVertexOffset(),
                        info.getVertexCount(),
                        i  // instance index
                ));

                // For batched meshes, we assume they share the same mesh structure
                // The actual index count should come from the mesh data
//                if (info.hasMesh()) {
//                    totalIndexCount = Math.max(totalIndexCount, info.getMesh().getIndexCount());
//                } else
                if (info.hasModelMesh()) {
                    totalIndexCount = Math.max(totalIndexCount, info.getModelMesh().getTotalIndexCount());
                } else {
                    // Estimate index count from vertex count (assuming triangles)
                    totalIndexCount = Math.max(totalIndexCount, info.getVertexCount() * 3 / 2);
                }
            }

            // Calculate GL parameters
            long indexOffset = 0; // Would need to be calculated based on mesh positioning in EBO
            int baseVertex = firstVertexOffset;
            int instanceCount = graphicsInfos.size();
            int baseInstance = 0; // Would need to be calculated based on instance positioning in InstanceVBO

            VertexDataShard dataShard = new VertexDataShard(0L, baseVertex, totalIndexCount, indexOffset);

            return new RenderCommand(
                    vertexResource,
                    renderSetting,
                    resourceBinding,
                    stageId,
                    primitiveType,
                    dataShard,
                    instanceCount,
                    baseInstance,
                    instances,
                    uniformBatches
            );
        }
    }

    /**
     * Factory method to create render commands from filled vertex resources
     */
    public static RenderCommand createFromFilledResource(
            VertexResource vertexResource,
            List<GraphicsInformation> instances,
            Identifier stageId) {

        if (instances.isEmpty()) {
            throw new IllegalArgumentException("Cannot create render command with no instances");
        }

        return new Builder()
                .vertexResource(vertexResource)
                .renderSetting(instances.get(0).getRenderSetting())
                .stageId(stageId)
                .addGraphicsInfos(instances)
                .build();
    }

    /**
     * Enhanced factory method to create render commands from RenderBatch
     * This provides access to UniformBatchGroup information
     */
    public static RenderCommand createFromRenderBatch(
            VertexResource vertexResource,
            RenderList.RenderBatch renderBatch,
            Identifier stageId) {

        List<GraphicsInformation> instances = renderBatch.getInstances();
        if (instances.isEmpty()) {
            throw new IllegalArgumentException("Cannot create render command with no instances");
        }

        return new Builder()
                .vertexResource(vertexResource)
                .renderSetting(instances.get(0).getRenderSetting())
                .stageId(stageId)
                .addGraphicsInfos(instances)
                .addUniformBatches(renderBatch.getUniformBatches())
                .build();
    }

    /**
     * Enhanced factory method that creates render commands with proper offset calculations
     * based on mesh and instance positioning in batched buffers
     */
    public static RenderCommand createWithCalculatedOffsets(
            VertexResource vertexResource,
            List<GraphicsInformation> instances,
            Identifier stageId,
            int meshBaseVertex,
            long meshIndexOffset,
            int meshIndexCount,
            int batchBaseInstance) {

        if (instances.isEmpty()) {
            throw new IllegalArgumentException("Cannot create render command with no instances");
        }

        RenderSetting renderSetting = instances.get(0).getRenderSetting();
        ResourceBinding resourceBinding = renderSetting.resourceBinding();
        PrimitiveType primitiveType = renderSetting.renderParameter().primitiveType();

        // Create instance data
        List<InstanceData> instanceDataList = new ArrayList<>();
        for (int i = 0; i < instances.size(); i++) {
            GraphicsInformation info = instances.get(i);
            instanceDataList.add(new InstanceData(
                    info,
                    info.getVertexOffset(),
                    info.getVertexCount(),
                    i  // instance index within this batch
            ));
        }

        VertexDataShard dataShard = new VertexDataShard(0L, meshBaseVertex, meshIndexCount, meshIndexOffset);


        return new RenderCommand(
                vertexResource,
                renderSetting,
                resourceBinding,
                stageId,
                primitiveType,
                dataShard,
                instances.size(), // instanceCount
                batchBaseInstance,
                instanceDataList,
                new ArrayList<>() // Empty uniform batches for this method
        );
    }
}