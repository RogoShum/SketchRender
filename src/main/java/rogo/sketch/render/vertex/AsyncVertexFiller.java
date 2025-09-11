package rogo.sketch.render.vertex;

import rogo.sketch.api.graphics.InstancedLayoutProvider;
import rogo.sketch.api.graphics.VertexDataProvider;
import rogo.sketch.render.data.filler.VertexFiller;
import rogo.sketch.render.information.GraphicsInformation;
import rogo.sketch.render.information.RenderList;
import rogo.sketch.render.resource.buffer.VertexResource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

/**
 * Handles async vertex buffer filling with offset-based attribute setting
 */
public class AsyncVertexFiller {
    private static final AsyncVertexFiller INSTANCE = new AsyncVertexFiller();
    private final ExecutorService executor;
    private final VertexResourceManager vertexResourceManager;

    private final Map<BatchResourceKey, PreallocatedResources> preallocatedResources = new ConcurrentHashMap<>();

    private int asyncThreshold = 32;
    private int chunkSize = 128;

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

    public void preallocateResources(RenderList renderList) {
        for (RenderList.RenderBatch batch : renderList.getBatches()) {
            preallocateBatchResources(batch);
        }
    }

    public CompletableFuture<List<FilledVertexResource>> fillVertexBuffersAsync(RenderList renderList) {
        List<RenderList.RenderBatch> batches = renderList.getBatches();
        preallocateResources(renderList);

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
        BatchResourceKey resourceKey = new BatchResourceKey(key, false);
        PreallocatedResources resources = preallocatedResources.get(resourceKey);

        if (resources == null) {
            throw new IllegalStateException("Resources not preallocated for batch: " + key +
                    ". Call preallocateResources() first in the render thread.");
        }

        VertexResource vertexResource = resources.vertexResource;
        VertexFiller filler = resources.vertexFiller;

        // Reset filler for new data
        filler.reset();

        // Fill vertex data for each instance with offset-based positioning
        for (GraphicsInformation info : batch.getSortedInstances()) {
            fillInstanceVertexData(filler, info);
        }

//        if (filler.getVertexCount() > 0) {
//            vertexResource.uploadFromVertexFiller(filler);
//        }

        return new FilledVertexResource(vertexResource, resources, batch);
    }

    /**
     * Fill vertex buffer for instanced batch
     */
    private FilledVertexResource fillInstancedBatch(RenderList.RenderBatch batch, RenderList.BatchKey key) {
        BatchResourceKey resourceKey = new BatchResourceKey(key, true);
        PreallocatedResources resources = preallocatedResources.get(resourceKey);

        if (resources == null) {
            throw new IllegalStateException("Instanced resources not preallocated for batch: " + key +
                    ". Call preallocateResources() first in the render thread.");
        }

        VertexResource vertexResource = resources.vertexResource;

        // Fill static vertex data (mesh geometry)
        fillStaticVertexData(vertexResource, batch, resources);

        // Fill dynamic vertex data (instance attributes)
        fillDynamicVertexData(vertexResource, batch, resources);

        return new FilledVertexResource(vertexResource, resources, batch);
    }

    /**
     * Fill vertex data for a single graphics instance
     */
    private void fillInstanceVertexData(VertexFiller filler, GraphicsInformation info) {
        int vertexOffset = info.getVertexOffset();

        // Set the filler position to the instance's vertex offset
        // Use vertex() method to position at the desired offset
        filler.vertex(vertexOffset);

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
        // Fill vertex data from model mesh
        info.getModelMesh().fillVertexData(filler);
    }

    /**
     * Fill vertex data from a mesh
     */
    private void fillFromMesh(VertexFiller filler, GraphicsInformation info) {
        // Fill vertex data from mesh
        if (info.getMesh() instanceof VertexDataProvider provider) {
            provider.fillVertexData(filler);
        }
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
    private void fillStaticVertexData(VertexResource vertexResource, RenderList.RenderBatch batch, PreallocatedResources resources) {
        VertexFiller staticFiller = resources.staticFiller;
        staticFiller.reset();

        // For instanced rendering, we typically only need one copy of the mesh geometry
        // Use the first instance to get the mesh data
        GraphicsInformation firstInfo = batch.getInstances().get(0);

        // Fill mesh geometry
        if (firstInfo.hasModelMesh()) {
            firstInfo.getModelMesh().fillVertexData(staticFiller);
        } else if (firstInfo.hasMesh()) {
            if (firstInfo.getMesh() instanceof VertexDataProvider provider) {
                provider.fillVertexData(staticFiller);
            }
        }
    }

    /**
     * Fill dynamic vertex data for instanced rendering (instance attributes)
     */
    private void fillDynamicVertexData(VertexResource vertexResource, RenderList.RenderBatch batch, PreallocatedResources resources) {
        // Get the instanced vertex layout from the first instance
        GraphicsInformation firstInfo = batch.getInstances().get(0);
        if (!firstInfo.hasInstancedData()) {
            return;
        }

        VertexFiller dynamicFiller = resources.dynamicFiller;
        dynamicFiller.reset();

        // Fill instance data - use efficient async strategy based on batch size
        List<GraphicsInformation> instances = batch.getInstances();

        if (instances.size() < asyncThreshold) {
            // Use sequential filling mode - no mode setting needed
            fillInstanceDataSync(dynamicFiller, instances);
        } else {
            // Use random access methods directly - no mode setting needed
            if (instances.size() < chunkSize * 4) {
                fillInstanceDataParallel(dynamicFiller, instances);
            } else {
                fillInstanceDataChunked(dynamicFiller, instances);
            }
        }
    }

    private void fillInstanceDataSync(VertexFiller dynamicFiller, List<GraphicsInformation> instances) {
        for (int i = 0; i < instances.size(); i++) {
            GraphicsInformation info = instances.get(i);
            if (info.getInstance() instanceof InstancedLayoutProvider provider) {
                provider.fillInstanceVertexData(dynamicFiller, i);
            }
        }
    }

    private void fillInstanceDataParallel(VertexFiller dynamicFiller, List<GraphicsInformation> instances) {
        IntStream.range(0, instances.size())
                .parallel()
                .forEach(i -> {
                    GraphicsInformation info = instances.get(i);
                    if (info.getInstance() instanceof InstancedLayoutProvider provider) {
                        // Direct random access filling - switch to vertex and fill
                        dynamicFiller.vertex(i);
                        provider.fillInstanceVertexData(dynamicFiller, i);
                    }
                });
    }

    private void fillInstanceDataChunked(VertexFiller dynamicFiller, List<GraphicsInformation> instances) {
        int totalInstances = instances.size();
        List<CompletableFuture<Void>> chunkFutures = new ArrayList<>((totalInstances + chunkSize - 1) / chunkSize);

        for (int start = 0; start < totalInstances; start += chunkSize) {
            final int chunkStart = start;
            final int chunkEnd = Math.min(start + chunkSize, totalInstances);

            CompletableFuture<Void> chunkFuture = CompletableFuture.runAsync(() -> {
                for (int i = chunkStart; i < chunkEnd; i++) {
                    final int index = i; // Make effectively final
                    GraphicsInformation info = instances.get(i);
                    if (info.getInstance() instanceof InstancedLayoutProvider provider) {
                        // Direct random access filling - switch to vertex and fill
                        dynamicFiller.vertex(index);
                        provider.fillInstanceVertexData(dynamicFiller, index);
                    }
                }
            }, executor);

            chunkFutures.add(chunkFuture);
        }

        // 等待所有块完成
        CompletableFuture.allOf(chunkFutures.toArray(new CompletableFuture[0])).join();
    }

    public void setAsyncThreshold(int threshold) {
        this.asyncThreshold = Math.max(1, threshold);
    }

    public void setChunkSize(int size) {
        this.chunkSize = Math.max(1, size);
    }

    public void shutdown() {
        executor.shutdown();
    }

    private void preallocateBatchResources(RenderList.RenderBatch batch) {
        RenderList.BatchKey key = batch.getKey();

        if (key.isInstanced()) {
            preallocateInstancedBatchResources(batch, key);
        } else {
            preallocateRegularBatchResources(batch, key);
        }
    }

    private void preallocateRegularBatchResources(RenderList.RenderBatch batch, RenderList.BatchKey key) {
        BatchResourceKey resourceKey = new BatchResourceKey(key, false);

        if (!preallocatedResources.containsKey(resourceKey)) {
            // 在渲染线程中创建OpenGL资源
            VertexResource vertexResource = vertexResourceManager.getOrCreateVertexResource(
                    key.getPrimitiveType(),
                    key.getDataFormat(),
                    batch.getTotalVertexCount()
            );

            VertexFiller vertexFiller = vertexResourceManager.getOrCreateVertexFiller(
                    key.getPrimitiveType(),
                    key.getDataFormat()
            );

            PreallocatedResources resources = new PreallocatedResources(
                    vertexResource, vertexFiller, null, null
            );

            preallocatedResources.put(resourceKey, resources);
        }
    }

    private void preallocateInstancedBatchResources(RenderList.RenderBatch batch, RenderList.BatchKey key) {
        BatchResourceKey resourceKey = new BatchResourceKey(key, true);

        if (!preallocatedResources.containsKey(resourceKey)) {
            // 在渲染线程中创建OpenGL资源
            VertexResource vertexResource = vertexResourceManager.getOrCreateInstancedVertexResource(
                    key.getPrimitiveType(),
                    key.getDataFormat(),
                    batch
            );

            VertexFiller staticFiller = vertexResourceManager.getOrCreateVertexFiller(
                    key.getPrimitiveType(),
                    key.getDataFormat()
            );

            VertexFiller dynamicFiller = null;
            if (!batch.getInstances().isEmpty()) {
                GraphicsInformation firstInfo = batch.getInstances().get(0);
                if (firstInfo.hasInstancedData() &&
                        firstInfo.getInstancedVertexLayout() != null) {
                    var mesh = firstInfo.getMesh();
                    if (mesh != null) {
                        dynamicFiller = vertexResourceManager.getOrCreateDynamicVertexFiller(
                                firstInfo.getInstancedVertexLayout(),
                                mesh.getPrimitiveType()
                        );
                    }
                }
            }

            PreallocatedResources resources = new PreallocatedResources(
                    vertexResource, null, staticFiller, dynamicFiller
            );

            preallocatedResources.put(resourceKey, resources);
        }
    }

    public void clearPreallocatedResources() {
        preallocatedResources.clear();
    }

    public String getPreallocationStats() {
        return String.format("AsyncVertexFiller: %d preallocated resource sets",
                preallocatedResources.size());
    }


    public static void usageExample() {
        // This is a documentation method - do not call
    }

    private static class BatchResourceKey {
        private final RenderList.BatchKey batchKey;
        private final boolean isInstanced;

        public BatchResourceKey(RenderList.BatchKey batchKey, boolean isInstanced) {
            this.batchKey = batchKey;
            this.isInstanced = isInstanced;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            BatchResourceKey that = (BatchResourceKey) o;
            return isInstanced == that.isInstanced &&
                    batchKey.equals(that.batchKey);
        }

        @Override
        public int hashCode() {
            int result = batchKey.hashCode();
            result = 31 * result + (isInstanced ? 1 : 0);
            return result;
        }

        @Override
        public String toString() {
            return "BatchResourceKey{" +
                    "batchKey=" + batchKey +
                    ", isInstanced=" + isInstanced +
                    '}';
        }
    }

    public record PreallocatedResources(VertexResource vertexResource, VertexFiller vertexFiller, VertexFiller staticFiller, VertexFiller dynamicFiller) {

    }

    /**
     * Result of filling a vertex resource
     */
    public static class FilledVertexResource {
        private final VertexResource vertexResource;
        private final PreallocatedResources resources;
        private final RenderList.RenderBatch batch;

        public FilledVertexResource(VertexResource vertexResource, PreallocatedResources resources, RenderList.RenderBatch batch) {
            this.vertexResource = vertexResource;
            this.resources = resources;
            this.batch = batch;
        }

        public VertexResource getVertexResource() {
            return vertexResource;
        }

        public PreallocatedResources getResources() {
            return resources;
        }

        public RenderList.RenderBatch getBatch() {
            return batch;
        }

        public int getTotalVertexCount() {
            return batch.getTotalVertexCount();
        }

        public int getDynamicVertexCount() {
            return batch.getGraphicsInstanceCount();
        }

        @Override
        public String toString() {
            return "FilledVertexResource{" +
                    "batch=" + batch +
                    ", resources=" + resources +
                    ", vertexResource=" + vertexResource +
                    '}';
        }
    }
}