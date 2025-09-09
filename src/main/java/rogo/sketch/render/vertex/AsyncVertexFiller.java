package rogo.sketch.render.vertex;

import rogo.sketch.api.graphics.InstancedLayoutProvider;
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

    // 预分配的资源缓存 - 在主线程中创建，在异步线程中安全访问
    private final Map<BatchResourceKey, PreallocatedResources> preallocatedResources = new ConcurrentHashMap<>();

    // 性能配置参数
    private int asyncThreshold = 32;  // 异步处理阈值
    private int chunkSize = 128;      // 分块处理大小

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
     * 预分配渲染列表所需的所有OpenGL资源（必须在渲染线程中调用）
     */
    public void preallocateResources(RenderList renderList) {
        for (RenderList.RenderBatch batch : renderList.getBatches()) {
            preallocateBatchResources(batch);
        }
    }

    /**
     * Fill vertex buffers asynchronously for all batches in a render list
     */
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
        // 获取预分配的资源
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

        // Upload data to GPU (这个操作是线程安全的，因为只是更新缓冲区数据)
        vertexResource.uploadFromVertexFiller(filler);

        return new FilledVertexResource(vertexResource, batch);
    }

    /**
     * Fill vertex buffer for instanced batch
     */
    private FilledVertexResource fillInstancedBatch(RenderList.RenderBatch batch, RenderList.BatchKey key) {
        // 获取预分配的实例化资源
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

        return new FilledVertexResource(vertexResource, batch);
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
        if (info.getModelMesh() instanceof VertexDataProvider provider) {
            provider.fillVertexData(filler);
        }
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
            if (firstInfo.getModelMesh() instanceof VertexDataProvider provider) {
                provider.fillVertexData(staticFiller);
            }
        } else if (firstInfo.hasMesh()) {
            if (firstInfo.getMesh() instanceof VertexDataProvider provider) {
                provider.fillVertexData(staticFiller);
            }
        }

        // Upload static data to GPU
        vertexResource.uploadStaticFromVertexFiller(staticFiller);
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
            // 小批次：直接同步处理，使用顺序模式
            // Use sequential filling mode - no mode setting needed
            fillInstanceDataSync(dynamicFiller, instances);
        } else {
            // 大批次：使用索引模式进行异步处理
            // Use random access methods directly - no mode setting needed
            if (instances.size() < chunkSize * 4) {
                // 中等批次：使用并行流
                fillInstanceDataParallel(dynamicFiller, instances);
            } else {
                // 超大批次：使用分块处理
                fillInstanceDataChunked(dynamicFiller, instances);
            }
        }

        // Upload dynamic data to GPU
        vertexResource.uploadDynamicFromVertexFiller(dynamicFiller);
    }

    /**
     * 同步填充实例数据 - 用于小批次，使用顺序模式避免开销
     */
    private void fillInstanceDataSync(VertexFiller dynamicFiller, List<GraphicsInformation> instances) {
        for (int i = 0; i < instances.size(); i++) {
            GraphicsInformation info = instances.get(i);
            if (info.getInstance() instanceof InstancedLayoutProvider provider) {
                // 顺序模式：直接调用provider，filler会按顺序填充
                provider.fillInstanceVertexData(dynamicFiller, i);
                dynamicFiller.nextVertex();
            }
        }
    }

    /**
     * 并行填充实例数据 - 用于中等批次，使用索引模式无锁并行
     */
    private void fillInstanceDataParallel(VertexFiller dynamicFiller, List<GraphicsInformation> instances) {
        // 使用索引并行流，每个线程直接写入指定位置，无需同步
        IntStream.range(0, instances.size())
                .parallel()
                .forEach(i -> {
                    GraphicsInformation info = instances.get(i);
                    if (info.getInstance() instanceof InstancedLayoutProvider provider) {
                        // 索引模式：每个线程写入不同位置，无冲突
                        // Direct random access filling - switch to vertex and fill
                        dynamicFiller.vertex(i);
                        provider.fillInstanceVertexData(dynamicFiller, i);
                        dynamicFiller.nextVertex();
                    }
                });
    }

    /**
     * 分块填充实例数据 - 用于超大批次，避免线程池过载
     */
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
                        // 索引模式：每个chunk处理不同范围的vertex，无冲突
                        // Direct random access filling - switch to vertex and fill
                        dynamicFiller.vertex(index);
                        provider.fillInstanceVertexData(dynamicFiller, index);
                        dynamicFiller.nextVertex();
                    }
                }
            }, executor);

            chunkFutures.add(chunkFuture);
        }

        // 等待所有块完成
        CompletableFuture.allOf(chunkFutures.toArray(new CompletableFuture[0])).join();
    }

    /**
     * 设置异步处理阈值
     */
    public void setAsyncThreshold(int threshold) {
        this.asyncThreshold = Math.max(1, threshold);
    }

    /**
     * 设置分块大小
     */
    public void setChunkSize(int size) {
        this.chunkSize = Math.max(1, size);
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
     * 预分配批次资源（必须在渲染线程中调用）
     */
    private void preallocateBatchResources(RenderList.RenderBatch batch) {
        RenderList.BatchKey key = batch.getKey();
        
        if (key.isInstanced()) {
            preallocateInstancedBatchResources(batch, key);
        } else {
            preallocateRegularBatchResources(batch, key);
        }
    }

    /**
     * 预分配常规批次资源
     */
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

    /**
     * 预分配实例化批次资源
     */
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

    /**
     * 清理预分配的资源
     */
    public void clearPreallocatedResources() {
        preallocatedResources.clear();
    }

    /**
     * 获取当前预分配资源的统计信息
     */
    public String getPreallocationStats() {
        return String.format("AsyncVertexFiller: %d preallocated resource sets", 
                preallocatedResources.size());
    }

    /**
     * 使用示例：正确的调用顺序
     * 
     * // 在渲染线程中：
     * AsyncVertexFiller filler = AsyncVertexFiller.getInstance();
     * filler.preallocateResources(renderList);  // 预分配OpenGL资源
     * 
     * // 然后可以在异步线程中：
     * CompletableFuture<List<FilledVertexResource>> future = 
     *     filler.fillVertexBuffersAsync(renderList);  // 异步填充数据
     * 
     * // 处理结果...
     * future.thenAccept(resources -> {
     *     // 在这里处理填充好的vertex资源
     * });
     */
    public static void usageExample() {
        // This is a documentation method - do not call
    }

    /**
     * 批次资源键，用于标识不同的批次资源
     */
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

    /**
     * 预分配的资源容器
     */
    private static class PreallocatedResources {
        final VertexResource vertexResource;
        final VertexFiller vertexFiller;      // 用于常规渲染
        final VertexFiller staticFiller;      // 用于实例化渲染的静态数据
        final VertexFiller dynamicFiller;     // 用于实例化渲染的动态数据

        public PreallocatedResources(VertexResource vertexResource,
                                   VertexFiller vertexFiller,
                                   VertexFiller staticFiller,
                                   VertexFiller dynamicFiller) {
            this.vertexResource = vertexResource;
            this.vertexFiller = vertexFiller;
            this.staticFiller = staticFiller;
            this.dynamicFiller = dynamicFiller;
        }
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

        public VertexResource getVertexResource() {
            return vertexResource;
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
                    ", vertexResource=" + vertexResource +
                    '}';
        }
    }
}