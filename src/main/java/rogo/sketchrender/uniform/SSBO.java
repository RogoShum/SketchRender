package rogo.sketchrender.uniform;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL43;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

public class SSBO {
    private final int id;
    private int size;

    public SSBO(int size) {
        this.size = size;
        id = GL15.glGenBuffers();
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, id);
        GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, size, GL15.GL_DYNAMIC_COPY);
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
    }

    public void bind(int bindingPoint) {
        GL43.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, bindingPoint, id);
    }

    public void setData(float[] data) {
        FloatBuffer buffer = BufferUtils.createFloatBuffer(data.length);
        buffer.put(data).flip();
        GL43.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, id);
        GL43.glBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, 0, buffer);
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
    }

    public float[] getData(int count) {
        FloatBuffer buffer = BufferUtils.createFloatBuffer(count);
        GL43.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, id);
        GL43.glGetBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, 0, buffer);
        float[] data = new float[count];
        buffer.get(data);
        return data;
    }

    public void discard() {
        GL15.glDeleteBuffers(id);
    }
}
