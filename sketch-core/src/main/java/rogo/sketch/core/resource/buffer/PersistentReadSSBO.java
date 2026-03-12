package rogo.sketch.core.resource.buffer;

import org.lwjgl.opengl.GL43;
import rogo.sketch.core.api.BindingResource;
import rogo.sketch.core.util.KeyId;
 
public class PersistentReadSSBO extends PersistentReadBuffer implements BindingResource {
    public int position;

    public PersistentReadSSBO(long dataCount, long stride) {
        super(GL43.GL_SHADER_STORAGE_BUFFER, dataCount, stride);
    }

    public void ensureCapacity(int requiredCount, boolean force) {
        super.ensureCapacity(requiredCount, force);
    }

    public void ensureCapacity(int requiredCount) {
        ensureCapacity(requiredCount, false);
    }

    public void discardBufferId() {
        dispose();
    }

    public void discardMemory() {
        // No-op: backing memory is owned by GL persistent mapping lifecycle.
    }

    public int getInt(long index) {
        checkDisposed();
        if (getMappedBuffer() == null) {
            throw new IllegalStateException("Buffer is not mapped");
        }
        return getMappedBuffer().asIntBuffer().get((int) index);
    }

    public void sync() {
        barrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT);
    }

    @Override
    public void bind(KeyId resourceType, int binding) {
        checkDisposed();
        GL43.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, binding, getHandle());
    }
}