package rogo.sketch.render.information;

import rogo.sketch.render.data.PrimitiveType;
import rogo.sketch.render.data.format.DataFormat;

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
    public static RenderList organize(List<GraphicsInformation> graphicsInfoList) {
        RenderList renderList = new RenderList();
        
        // Group by batch key (same primitive type, same vertex attributes)
        Map<BatchKey, List<GraphicsInformation>> groups = new HashMap<>();
        
        for (GraphicsInformation info : graphicsInfoList) {
            BatchKey key = createBatchKey(info);
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(info);
        }
        
        // Create batches and calculate offsets
        for (Map.Entry<BatchKey, List<GraphicsInformation>> entry : groups.entrySet()) {
            BatchKey key = entry.getKey();
            List<GraphicsInformation> infos = entry.getValue();
            
            RenderBatch batch = new RenderBatch(key, infos);
            renderList.addBatch(batch);
        }
        
        return renderList;
    }
    
    /**
     * Create a batch key for grouping compatible graphics instances
     */
    private static BatchKey createBatchKey(GraphicsInformation info) {
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
        private final List<GraphicsInformation> instances;
        private final int totalVertexCount;
        
        public RenderBatch(BatchKey key, List<GraphicsInformation> instances) {
            this.key = key;
            this.instances = new ArrayList<>(instances);
            
            // Calculate vertex offsets for each instance
            int currentOffset = 0;
            for (GraphicsInformation info : this.instances) {
                info.setVertexOffset(currentOffset);
                currentOffset += info.getVertexCount();
            }
            
            this.totalVertexCount = currentOffset;
        }
        
        public BatchKey getKey() { return key; }
        public List<GraphicsInformation> getInstances() { return new ArrayList<>(instances); }
        public int getTotalVertexCount() { return totalVertexCount; }
        public int getInstanceCount() { return instances.size(); }
        
        /**
         * Get instances sorted by vertex offset
         */
        public List<GraphicsInformation> getSortedInstances() {
            List<GraphicsInformation> sorted = new ArrayList<>(instances);
            sorted.sort(Comparator.comparingInt(GraphicsInformation::getVertexOffset));
            return sorted;
        }
        
        @Override
        public String toString() {
            return "RenderBatch{" +
                    "key=" + key +
                    ", instanceCount=" + instances.size() +
                    ", totalVertexCount=" + totalVertexCount +
                    '}';
        }
    }
}