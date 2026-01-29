package rogo.sketch.core.data.builder;

import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL43;

import static org.lwjgl.opengl.GL32.*;

/**
 * 持久化映射上下文管理器。
 * 管理 Persistent Mapped Buffer (PMB) 的生命周期、同步和多缓冲机制。
 * 
 * 用于确保 CPU 和 GPU 不会同时访问同一块内存区域，防止读写冲突。
 */
public class PersistentMappingContext {

    private final int bufferCount; // Triple buffering (通常是 3)
    private final long[] fences; // 每个 Buffer 对应的 Fence
    private int currentBuffer = 0;

    private final boolean coherent; // 是否使用 Coherent 映射

    /**
     * 创建持久化映射上下文。
     * 
     * @param bufferCount 缓冲区数量（建议至少 2，Triple Buffering 使用 3）
     * @param coherent    是否使用 GL_MAP_COHERENT_BIT（无需显式 flush）
     */
    public PersistentMappingContext(int bufferCount, boolean coherent) {
        if (bufferCount < 2) {
            throw new IllegalArgumentException("bufferCount must be at least 2");
        }
        this.bufferCount = bufferCount;
        this.fences = new long[bufferCount];
        this.coherent = coherent;

        // 初始化所有 Fence 为 NULL
        for (int i = 0; i < bufferCount; i++) {
            fences[i] = 0; // GL_NONE
        }
    }

    /**
     * 获取下一个可用的缓冲区索引。
     * 会自动等待上一轮使用的 Buffer 的 GPU 完成。
     * 
     * @return 可用的缓冲区索引
     */
    public int nextBuffer() {
        currentBuffer = (currentBuffer + 1) % bufferCount;

        // 等待此 Buffer 的上一次 GPU 使用完成
        waitForFence(currentBuffer);

        return currentBuffer;
    }

    /**
     * 等待指定缓冲区的 Fence。
     * 
     * @param bufferIndex 缓冲区索引
     */
    public void waitForFence(int bufferIndex) {
        if (bufferIndex < 0 || bufferIndex >= bufferCount) {
            throw new IllegalArgumentException("Invalid buffer index: " + bufferIndex);
        }

        long fence = fences[bufferIndex];
        if (fence != 0) {
            // 等待 Fence（超时 1 秒）
            int result = GL32.glClientWaitSync(fence, GL_SYNC_FLUSH_COMMANDS_BIT, 1_000_000_000L);

            if (result == GL32.GL_WAIT_FAILED) {
                throw new RuntimeException("Fence wait failed for buffer " + bufferIndex);
            }

            // 删除旧 Fence
            GL32.glDeleteSync(fence);
            fences[bufferIndex] = 0;
        }
    }

    /**
     * 为当前缓冲区创建 Fence，标记 GPU 命令提交点。
     * 
     * @return Fence 句柄
     */
    public long createFence() {
        long fence = GL32.glFenceSync(GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
        if (fence == 0) {
            throw new RuntimeException("Failed to create fence");
        }
        fences[currentBuffer] = fence;
        return fence;
    }

    /**
     * Flush 映射的内存范围（仅用于非 Coherent 映射）。
     * 
     * @param bufferId OpenGL Buffer 对象 ID
     * @param offset   刷新起始偏移（字节）
     * @param length   刷新长度（字节）
     */
    public void flush(int bufferId, long offset, long length) {
        if (!coherent) {
            GL43.glFlushMappedBufferRange(GL_ARRAY_BUFFER, offset, length);
        }
    }

    /**
     * 获取当前缓冲区索引。
     */
    public int getCurrentBuffer() {
        return currentBuffer;
    }

    /**
     * 获取缓冲区总数。
     */
    public int getBufferCount() {
        return bufferCount;
    }

    /**
     * 清理所有 Fence。
     * 通常在资源释放时调用。
     */
    public void cleanup() {
        for (int i = 0; i < bufferCount; i++) {
            if (fences[i] != 0) {
                GL32.glDeleteSync(fences[i]);
                fences[i] = 0;
            }
        }
    }

    /**
     * 等待所有 Fence 完成（阻塞直到 GPU 空闲）。
     */
    public void waitAll() {
        for (int i = 0; i < bufferCount; i++) {
            waitForFence(i);
        }
    }
}
