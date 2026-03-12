package rogo.sketch.core.resource.buffer;

import org.lwjgl.opengl.GL45;
import rogo.sketch.core.driver.GLRuntimeFlags;

/**
 * Persistent mapped write-only buffer.
 */
public class PersistentWriteBuffer extends AbstractPersistentMappedBuffer {
    public PersistentWriteBuffer(int target, long dataCount, long stride) {
        this(target, dataCount, stride, GLRuntimeFlags.VBO_PERSISTENT_COHERENT);
    }

    public PersistentWriteBuffer(int target, long dataCount, long stride, boolean coherent) {
        super(target, dataCount, stride,
                coherent
                        ? (GL45.GL_MAP_PERSISTENT_BIT | GL45.GL_MAP_WRITE_BIT | GL45.GL_MAP_COHERENT_BIT)
                        : (GL45.GL_MAP_PERSISTENT_BIT | GL45.GL_MAP_WRITE_BIT),
                coherent
                        ? (GL45.GL_MAP_PERSISTENT_BIT | GL45.GL_MAP_WRITE_BIT | GL45.GL_MAP_COHERENT_BIT)
                        : (GL45.GL_MAP_PERSISTENT_BIT | GL45.GL_MAP_WRITE_BIT | GL45.GL_MAP_FLUSH_EXPLICIT_BIT));
    }
}


