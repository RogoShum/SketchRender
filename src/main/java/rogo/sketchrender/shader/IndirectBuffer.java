package rogo.sketchrender.shader;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL46C;

import java.nio.ByteBuffer;

public class IndirectBuffer {
    private final int bufferId;
    private final int commandSize; // 每个间接绘制命令的大小（字节）
    private final int maxCommands; // 最大支持的绘制命令数量
    private int numCommands; // 当前存储的绘制命令数量

    // 每个间接绘制命令的结构（5个整数）
    private static final int COMMAND_SIZE_BYTES = 5 * Integer.BYTES;

    public IndirectBuffer(int maxCommands) {
        this.maxCommands = maxCommands;
        this.commandSize = COMMAND_SIZE_BYTES;
        this.numCommands = 0;

        // 生成并绑定缓冲区
        this.bufferId = GL46C.glGenBuffers();
        GL46C.glBindBuffer(GL46C.GL_DRAW_INDIRECT_BUFFER, bufferId);
        GL46C.glBufferData(GL46C.GL_DRAW_INDIRECT_BUFFER, maxCommands * commandSize, GL46C.GL_DYNAMIC_DRAW);
        GL46C.glBindBuffer(GL46C.GL_DRAW_INDIRECT_BUFFER, 0);
    }

    /**
     * 添加一个间接绘制命令
     *
     * @param count         索引数量
     * @param instanceCount 实例数量（通常为1）
     * @param firstIndex    第一个索引的偏移量
     * @param baseVertex    基顶点
     * @param baseInstance  基实例（通常为0）
     */
    public void addCommand(int count, int instanceCount, int firstIndex, int baseVertex, int baseInstance) {
        if (numCommands >= maxCommands) {
            throw new IllegalStateException("IndirectBuffer is full");
        }

        // 将命令数据打包到 ByteBuffer
        ByteBuffer buffer = BufferUtils.createByteBuffer(commandSize);
        buffer.putInt(count);
        buffer.putInt(instanceCount);
        buffer.putInt(firstIndex);
        buffer.putInt(baseVertex);
        buffer.putInt(baseInstance);
        buffer.flip();

        // 上传命令数据到缓冲区
        GL46C.glBindBuffer(GL46C.GL_DRAW_INDIRECT_BUFFER, bufferId);
        GL46C.glBufferSubData(GL46C.GL_DRAW_INDIRECT_BUFFER, numCommands * commandSize, buffer);
        GL46C.glBindBuffer(GL46C.GL_DRAW_INDIRECT_BUFFER, 0);

        numCommands++;
    }

    public void refresh() {
        numCommands = 0;
    }

    /**
     * 绑定间接缓冲区
     */
    public void bind() {
        GL46C.glBindBuffer(GL46C.GL_DRAW_INDIRECT_BUFFER, bufferId);
    }

    /**
     * 解绑间接缓冲区
     */
    public void unbind() {
        GL46C.glBindBuffer(GL46C.GL_DRAW_INDIRECT_BUFFER, 0);
    }

    /**
     * 获取当前存储的绘制命令数量
     */
    public int getNumCommands() {
        return numCommands;
    }

    /**
     * 清理资源
     */
    public void cleanup() {
        GL46C.glDeleteBuffers(bufferId);
    }
}