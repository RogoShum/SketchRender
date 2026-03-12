package rogo.sketch.core.resource.buffer;

import org.lwjgl.opengl.GL45;

/**
 * Persistent mapped read-only buffer.
 */
public class PersistentReadBuffer extends AbstractPersistentMappedBuffer {
    public PersistentReadBuffer(int target, long dataCount, long stride) {
        super(target, dataCount, stride,
                GL45.GL_MAP_PERSISTENT_BIT | GL45.GL_MAP_READ_BIT,
                GL45.GL_MAP_PERSISTENT_BIT | GL45.GL_MAP_READ_BIT);
    }
}


