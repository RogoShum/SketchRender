package rogo.sketch.render.pipeline.information;

import rogo.sketch.render.data.PrimitiveType;
import rogo.sketch.render.data.format.DataFormat;
import rogo.sketch.render.pipeline.UniformBatchGroup;
import rogo.sketch.render.shader.uniform.UniformValueSnapshot;
import rogo.sketch.api.ShaderProvider;
import rogo.sketch.api.graphics.GraphicsInstance;
import rogo.sketch.render.resource.ResourceReference;
import rogo.sketch.render.resource.ResourceTypes;
import rogo.sketch.render.state.gl.ShaderState;

import java.util.*;

/**
 * Organizes graphics information into a render queue with calculated vertex offsets
 */
public class RenderList {
    private final List<RenderBatch> batches;
    private final Map<BatchKey, RenderBatch> batchMap;
    
    public RenderList() {
        this.batches = new ArrayList<>();
        this.batchMap = new HashMap<>();
    }
    
    /**
     * Organize a list of graphics information into batched render queue
     */
    public static RenderList organize(List<GraphicsInstanceInformation> graphicsInfoList) {
        RenderList renderList = new RenderList();
        
        // Group by batch key (same primitive type, same vertex attributes)
        Map<BatchKey, List<GraphicsInstanceInformation>> groups = new HashMap<>();
        
        for (GraphicsInstanceInformation info : graphicsInfoList) {
            BatchKey key = createBatchKey(info);
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(info);
        }
        
        // Create batches and calculate offsets
        for (Map.Entry<BatchKey, List<GraphicsInstanceInformation>> entry : groups.entrySet()) {
            BatchKey key = entry.getKey();
            List<GraphicsInstanceInformation> infos = entry.getValue();
            
            RenderBatch batch = new RenderBatch(key, infos);
            // Collect uniform batches for this render batch
            batch.collectUniformBatches();
            renderList.addBatch(batch);
        }
        
        return renderList;
    }
    
    /**
     * Create a batch key for grouping compatible graphics instances
     * Compatible instances must have the same primitive type, data format, instanced flag, and mesh
     */
    private static BatchKey createBatchKey(GraphicsInstanceInformation info) {
        PrimitiveType primitiveType = info.getRenderSetting().renderParameter().primitiveType();
        DataFormat dataFormat = info.getRenderSetting().renderParameter().dataFormat();
        boolean isInstanced = info.isInstancedRendering();

        return new BatchKey(primitiveType, dataFormat, isInstanced);
    }
    
    private void addBatch(RenderBatch batch) {
        batches.add(batch);
        batchMap.put(batch.getKey(), batch);
    }
    
    public List<RenderBatch> getBatches() {
        return new ArrayList<>(batches);
    }
    
    public RenderBatch getBatch(BatchKey key) {
        return batchMap.get(key);
    }
    
    public int getBatchCount() {
        return batches.size();
    }
    
    public int getTotalVertexCount() {
        return batches.stream().mapToInt(RenderBatch::getTotalVertexCount).sum();
    }
    
    /**
     * Key for grouping compatible render instances
     * Instances with the same key can be batched together for efficient rendering
     */
    public static class BatchKey {
        private final PrimitiveType primitiveType;
        private final DataFormat dataFormat;
        private final boolean isInstanced;
        private final int hashCode;
        
        public BatchKey(PrimitiveType primitiveType, DataFormat dataFormat, boolean isInstanced) {
            this.primitiveType = primitiveType;
            this.dataFormat = dataFormat;
            this.isInstanced = isInstanced;
            this.hashCode = Objects.hash(primitiveType, dataFormat, isInstanced);
        }
        
        public PrimitiveType getPrimitiveType() { return primitiveType; }
        public DataFormat getDataFormat() { return dataFormat; }
        public boolean isInstanced() { return isInstanced; }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            BatchKey batchKey = (BatchKey) o;
            return isInstanced == batchKey.isInstanced &&
                   Objects.equals(primitiveType, batchKey.primitiveType) &&
                   Objects.equals(dataFormat, batchKey.dataFormat);
        }
        
        @Override
        public int hashCode() {
            return hashCode;
        }
        
        @Override
        public String toString() {
            return "BatchKey{" +
                    "primitiveType=" + primitiveType +
                    ", dataFormat=" + dataFormat +
                    ", isInstanced=" + isInstanced +
                    '}';
        }
    }
    
    /**
     * A batch of graphics instances that can be rendered together
     */
    public static class RenderBatch {
        private final BatchKey key;
        private final List<GraphicsInstanceInformation> instances;
        private final int totalVertexCount;
        private final List<UniformBatchGroup> uniformBatches;
        
        public RenderBatch(BatchKey key, List<GraphicsInstanceInformation> instances) {
            this.key = key;
            this.instances = new ArrayList<>(instances);
            this.uniformBatches = new ArrayList<>();
            
            // Calculate vertex offsets for each instance
            int currentOffset = 0;
            for (GraphicsInstanceInformation info : this.instances) {
                info.setVertexOffset(currentOffset);
                currentOffset += info.getVertexCount();
            }
            
            this.totalVertexCount = currentOffset;
        }
        
        public BatchKey getKey() { return key; }
        public List<GraphicsInstanceInformation> getInstances() { return new ArrayList<>(instances); }
        public int getTotalVertexCount() { return totalVertexCount; }
        public int getGraphicsInstanceCount() { return instances.size(); }
        public List<UniformBatchGroup> getUniformBatches() { return new ArrayList<>(uniformBatches); }
        
        /**
         * Set uniform batches for this render batch
         */
        public void setUniformBatches(List<UniformBatchGroup> uniformBatches) {
            this.uniformBatches.clear();
            this.uniformBatches.addAll(uniformBatches);
        }
        
        /**
         * Collect uniform batches for this render batch based on graphics instances
         */
        public void collectUniformBatches() {
            if (instances.isEmpty()) {
                return;
            }
            
            // Get render setting and shader provider from the first instance
            GraphicsInstanceInformation firstInfo = instances.get(0);
            ShaderProvider shaderProvider = extractShaderProvider(firstInfo);
            
            if (shaderProvider == null) {
                return;
            }
            
            // Collect uniform batches using the same logic as the old pipeline
            Map<UniformValueSnapshot, UniformBatchGroup> batches = new HashMap<>();
            
            for (GraphicsInstanceInformation info : instances) {
                GraphicsInstance instance = info.getInstance();
                if (instance.shouldRender()) {
                    UniformValueSnapshot snapshot = UniformValueSnapshot.captureFrom(
                            shaderProvider.getUniformHookGroup(), instance);
                    
                    batches.computeIfAbsent(snapshot, UniformBatchGroup::new).addInstance(instance);
                }
            }
            
            this.uniformBatches.clear();
            this.uniformBatches.addAll(batches.values());
        }
        
        /**
         * Extract shader provider from GraphicsInformation
         */
        private ShaderProvider extractShaderProvider(GraphicsInstanceInformation info) {
            try {
                ResourceReference<ShaderProvider> reference = 
                    ((ShaderState) info.getRenderSetting().renderState().get(ResourceTypes.SHADER_PROGRAM)).shader();
                if (reference.isAvailable()) {
                    return reference.get();
                }
            } catch (Exception e) {
                // Ignore and return null
            }
            return null;
        }
        
        /**
         * Get instances sorted by vertex offset
         */
        public List<GraphicsInstanceInformation> getSortedInstances() {
            List<GraphicsInstanceInformation> sorted = new ArrayList<>(instances);
            sorted.sort(Comparator.comparingInt(GraphicsInstanceInformation::getVertexOffset));
            return sorted;
        }
        
        @Override
        public String toString() {
            return "RenderBatch{" +
                    "key=" + key +
                    ", graphicsInstanceCount=" + instances.size() +
                    ", totalVertexCount=" + totalVertexCount +
                    '}';
        }
    }
}