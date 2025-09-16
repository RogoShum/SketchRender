package rogo.sketch.render.vertex;

import rogo.sketch.render.pipeline.information.RenderList;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 使用示例：展示如何正确使用重构后的AsyncVertexFiller
 * 
 * 重要原则：
 * 1. 所有OpenGL资源必须在渲染线程中预分配
 * 2. 数据填充可以在异步线程中进行
 * 3. 预分配和异步填充之间需要正确的顺序
 */
public class AsyncVertexFillerUsage {

    /**
     * 示例：在渲染循环中正确使用AsyncVertexFiller
     */
    public void renderLoopExample(RenderList renderList) {
        AsyncVertexFiller filler = AsyncVertexFiller.getInstance();
        
        // 步骤1：在渲染线程中预分配所有必需的OpenGL资源
        // 这一步必须在主渲染线程中完成，因为会创建VAO、VBO等OpenGL对象
        filler.preallocateResources(renderList);
        
        // 步骤2：异步填充vertex数据
        // 这一步可以在后台线程中执行，只进行CPU数据处理，不涉及OpenGL资源创建
        CompletableFuture<List<AsyncVertexFiller.FilledVertexResource>> future = 
            filler.fillVertexBuffersAsync(renderList);
            
        // 步骤3：等待异步处理完成并使用结果
        future.thenAccept(filledResources -> {
            // 在这里可以使用填充好的vertex资源进行渲染
            for (AsyncVertexFiller.FilledVertexResource resource : filledResources) {
                // 渲染这个resource
                renderVertexResource(resource);
            }
        }).exceptionally(throwable -> {
            System.err.println("Async vertex filling failed: " + throwable.getMessage());
            return null;
        });
    }

    /**
     * 示例：批处理多个渲染列表
     */
    public void batchProcessingExample(List<RenderList> renderLists) {
        AsyncVertexFiller filler = AsyncVertexFiller.getInstance();
        
        // 步骤1：批量预分配所有渲染列表的资源
        for (RenderList renderList : renderLists) {
            filler.preallocateResources(renderList);
        }
        
        // 步骤2：并行处理所有渲染列表
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
            renderLists.stream()
                .map(filler::fillVertexBuffersAsync)
                .map(future -> future.thenAccept(this::processFilledResources))
                .toArray(CompletableFuture[]::new)
        );
        
        // 步骤3：等待所有处理完成
        allFutures.join();
        
        // 步骤4：清理预分配的资源（可选，在合适的时机）
        // filler.clearPreallocatedResources();
    }

    /**
     * 示例：错误的使用方式（不要这样做）
     */
    @Deprecated
    public void incorrectUsageExample(RenderList renderList) {
        AsyncVertexFiller filler = AsyncVertexFiller.getInstance();
        
        // 错误：直接调用异步填充而不预分配资源
        // 这会导致IllegalStateException，因为没有预分配OpenGL资源
        try {
            filler.fillVertexBuffersAsync(renderList).join();
            // 这会失败！
        } catch (Exception e) {
            System.err.println("Expected error: " + e.getMessage());
        }
    }

    /**
     * 渲染单个vertex资源
     */
    private void renderVertexResource(AsyncVertexFiller.FilledVertexResource resource) {
        // 这里实现实际的渲染逻辑
        // 例如：绑定VAO，设置shader参数，调用glDraw等
        System.out.println("Rendering " + resource);
    }

    /**
     * 处理填充完成的资源列表
     */
    private void processFilledResources(List<AsyncVertexFiller.FilledVertexResource> resources) {
        for (AsyncVertexFiller.FilledVertexResource resource : resources) {
            renderVertexResource(resource);
        }
    }

    /**
     * 性能监控示例
     */
    public void performanceMonitoringExample() {
        AsyncVertexFiller filler = AsyncVertexFiller.getInstance();
        
        // 打印预分配统计信息
        System.out.println(filler.getPreallocationStats());
        
        // 调整性能参数
        filler.setAsyncThreshold(64);  // 更高的异步阈值
        filler.setChunkSize(256);      // 更大的分块大小
        
        // 在应用程序关闭时清理资源
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            filler.clearPreallocatedResources();
            filler.shutdown();
        }));
    }
}
