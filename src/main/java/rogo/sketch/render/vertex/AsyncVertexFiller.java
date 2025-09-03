package rogo.sketch.render.vertex;

import rogo.sketch.render.data.filler.VertexFiller;
import rogo.sketch.render.information.GraphicsInformation;
import rogo.sketch.render.information.RenderList;
import rogo.sketch.render.resource.buffer.VertexResource;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.List;

/**
 * Handles async vertex buffer filling with offset-based attribute setting
 */
public class AsyncVertexFiller {
    private static final AsyncVertexFiller INSTANCE = new AsyncVertexFiller();
    private final ExecutorService executor;
    private final VertexResourceManager vertexResourceManager;
    
    private AsyncVertexFiller() {
        this.executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "AsyncVertexFiller");
            t.setDaemon(true);
            return t;
        });
        this.vertexResourceManager = VertexResourceManager.getInstance();
    }
    
    public static AsyncVertexFiller getInstance() {
        return INSTANCE;
    }
    
    /**
     * Fill vertex buffers asynchronously for all batches in a render list
     */
    public CompletableFuture<List<FilledVertexResource>> fillVertexBuffersAsync(RenderList renderList) {
        List<RenderList.RenderBatch> batches = renderList.getBatches();
        
        @SuppressWarnings("unchecked")
        CompletableFuture<FilledVertexResource>[] futures = batches.stream()
                .map(this::fillBatchAsync)
                .toArray(CompletableFuture[]::new);
        
        return CompletableFuture.allOf(futures)
                .thenApply(v -> List.of(futures).stream()
                        .map(CompletableFuture::join)
                        .toList());
    }
    
    /**
     * Fill vertex buffer asynchronously for a single batch
     */
    private CompletableFuture<FilledVertexResource> fillBatchAsync(RenderList.RenderBatch batch) {
        return CompletableFuture.supplyAsync(() -> fillBatch(batch), executor);
    }
    
    /**
     * Fill vertex buffer for a single batch
     */
    private FilledVertexResource fillBatch(RenderList.RenderBatch batch) {
        RenderList.BatchKey key = batch.getKey();
        
        if (key.isInstanced()) {
            return fillInstancedBatch(batch, key);
        } else {
            return fillRegularBatch(batch, key);
        }
    }
    
    /**
     * Fill vertex buffer for regular (non-instanced) batch
     */
    private FilledVertexResource fillRegularBatch(RenderList.RenderBatch batch, RenderList.BatchKey key) {
        // Create or get vertex resource for this batch
        VertexResource vertexResource = vertexResourceManager.getOrCreateVertexResource(
                key.getPrimitiveType(),
                key.getDataFormat(),
                batch.getTotalVertexCount()
        );
        
        // Get vertex filler for this batch
        VertexFiller filler = vertexResourceManager.getOrCreateVertexFiller(
                key.getPrimitiveType(),
                key.getDataFormat()
        );
        
        // Reset filler for new data
        filler.reset();
        
        // Fill vertex data for each instance with offset-based positioning
        for (GraphicsInformation info : batch.getSortedInstances()) {
            fillInstanceVertexData(filler, info);
        }
        
        // Upload data to GPU
        vertexResource.uploadFromVertexFiller(filler);
        
        return new FilledVertexResource(vertexResource, batch);
    }
    
    /**
     * Fill vertex buffer for instanced batch
     */
    private FilledVertexResource fillInstancedBatch(RenderList.RenderBatch batch, RenderList.BatchKey key) {
        // For instanced rendering, we need to handle static and dynamic vertex data separately
        
        // Create vertex resource with both static and dynamic layouts
        VertexResource vertexResource = vertexResourceManager.getOrCreateInstancedVertexResource(
                key.getPrimitiveType(),
                key.getDataFormat(),
                batch
        );
        
        // Fill static vertex data (mesh geometry)
        fillStaticVertexData(vertexResource, batch);
        
        // Fill dynamic vertex data (instance attributes)
        fillDynamicVertexData(vertexResource, batch);
        
        return new FilledVertexResource(vertexResource, batch);
    }
    
    /**
     * Fill vertex data for a single graphics instance
     */
    private void fillInstanceVertexData(VertexFiller filler, GraphicsInformation info) {
        int vertexOffset = info.getVertexOffset();
        
        // Set the filler position to the instance's vertex offset
        filler.setVertexOffset(vertexOffset);
        
        // Fill vertex data based on the instance type
        if (info.hasModelMesh()) {
            fillFromModelMesh(filler, info);
        } else if (info.hasMesh()) {
            fillFromMesh(filler, info);
        } else {
            fillFromInstance(filler, info);
        }
    }
    
    /**
     * Fill vertex data from a model mesh
     */
    private void fillFromModelMesh(VertexFiller filler, GraphicsInformation info) {
        // Apply transformation matrix
        float[] matrix = info.getMeshMatrix();
        filler.pushMatrix(matrix);
        
        // Fill vertex data from model mesh
        if (info.getModelMesh() instanceof VertexDataProvider provider) {
            provider.fillVertexData(filler);
        }
        
        filler.popMatrix();
    }
    
    /**
     * Fill vertex data from a mesh
     */
    private void fillFromMesh(VertexFiller filler, GraphicsInformation info) {
        // Apply transformation matrix
        float[] matrix = info.getMeshMatrix();
        filler.pushMatrix(matrix);
        
        // Fill vertex data from mesh
        if (info.getMesh() instanceof VertexDataProvider provider) {
            provider.fillVertexData(filler);
        }
        
        filler.popMatrix();
    }
    
    /**
     * Fill vertex data directly from the graphics instance
     */
    private void fillFromInstance(VertexFiller filler, GraphicsInformation info) {
        if (info.getInstance() instanceof VertexDataProvider provider) {
            provider.fillVertexData(filler);
        }
    }
    
    /**
     * Fill static vertex data for instanced rendering (mesh geometry)
     */
    private void fillStaticVertexData(VertexResource vertexResource, RenderList.RenderBatch batch) {
        // Get static vertex filler
        VertexFiller staticFiller = vertexResourceManager.getOrCreateVertexFiller(
                batch.getKey().getPrimitiveType(),
                batch.getKey().getDataFormat()
        );
        
        staticFiller.reset();
        
        // For instanced rendering, we typically only need one copy of the mesh geometry
        // Use the first instance to get the mesh data
        GraphicsInformation firstInfo = batch.getInstances().get(0);
        
        // Apply transformation matrix
        float[] matrix = firstInfo.getMeshMatrix();
        staticFiller.pushMatrix(matrix);
        
        // Fill mesh geometry
        if (firstInfo.hasModelMesh()) {
            if (firstInfo.getModelMesh() instanceof VertexDataProvider provider) {
                provider.fillVertexData(staticFiller);
            }
        } else if (firstInfo.hasMesh()) {
            if (firstInfo.getMesh() instanceof VertexDataProvider provider) {
                provider.fillVertexData(staticFiller);
            }
        }
        
        staticFiller.popMatrix();
        
        // Upload static data to GPU
        vertexResource.uploadStaticFromVertexFiller(staticFiller);
    }
    
    /**
     * Fill dynamic vertex data for instanced rendering (instance attributes)
     */
    private void fillDynamicVertexData(VertexResource vertexResource, RenderList.RenderBatch batch) {
        // Get the instanced vertex layout from the first instance
        GraphicsInformation firstInfo = batch.getInstances().get(0);
        if (!firstInfo.hasInstancedData()) {
            return;
        }
        
        // Create dynamic vertex filler for instance data
        VertexFiller dynamicFiller = vertexResourceManager.getOrCreateDynamicVertexFiller(
                firstInfo.getInstancedVertexLayout()
        );
        
        dynamicFiller.reset();
        
        // Fill instance data for each graphics instance
        int instanceIndex = 0;
        for (GraphicsInformation info : batch.getInstances()) {
            if (info.getInstance() instanceof rogo.sketch.render.information.InfoCollector.InstancedLayoutProvider provider) {
                // Fill instance-specific data for each instance count
                for (int i = 0; i < info.getInstanceCount(); i++) {
                    provider.fillInstanceVertexData(dynamicFiller, instanceIndex++);
                    dynamicFiller.nextVertex();
                }
            }
        }
        
        // Upload dynamic data to GPU
        vertexResource.uploadDynamicFromVertexFiller(dynamicFiller);
    }
    
    /**
     * Shutdown the executor service
     */
    public void shutdown() {
        executor.shutdown();
    }
    
    /**
     * Interface for objects that can provide vertex data
     */
    public interface VertexDataProvider {
        void fillVertexData(VertexFiller filler);
    }
    
    /**
     * Result of filling a vertex resource
     */
    public static class FilledVertexResource {
        private final VertexResource vertexResource;
        private final RenderList.RenderBatch batch;
        
        public FilledVertexResource(VertexResource vertexResource, RenderList.RenderBatch batch) {
            this.vertexResource = vertexResource;
            this.batch = batch;
        }
        
        public VertexResource getVertexResource() { return vertexResource; }
        public RenderList.RenderBatch getBatch() { return batch; }
        
        public int getTotalVertexCount() { return batch.getTotalVertexCount(); }
        public int getInstanceCount() { return batch.getInstanceCount(); }
        
        @Override
        public String toString() {
            return "FilledVertexResource{" +
                    "batch=" + batch +
                    ", vertexResource=" + vertexResource +
                    '}';
        }
    }
}
