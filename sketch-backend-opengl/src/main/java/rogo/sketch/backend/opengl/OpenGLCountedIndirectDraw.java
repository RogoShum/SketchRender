package rogo.sketch.backend.opengl;

import org.lwjgl.opengl.ARBIndirectParameters;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL46C;
import rogo.sketch.backend.opengl.util.GLFeatureChecker;
import rogo.sketch.core.backend.IndirectDrawService;

final class OpenGLCountedIndirectDraw implements IndirectDrawService {
    @Override
    public boolean isSupported() {
        return GLFeatureChecker.supportsIndirectDrawCount();
    }

    @Override
    public void multiDrawElementsIndirectCount(
            long indirectOffsetBytes,
            long countBufferOffsetBytes,
            int maxDrawCount,
            int strideBytes) {
        if (GLFeatureChecker.supportsIndirectDrawCount46()) {
            GL46C.nglMultiDrawElementsIndirectCount(
                    GL11.GL_TRIANGLES,
                    GL11.GL_UNSIGNED_INT,
                    indirectOffsetBytes,
                    countBufferOffsetBytes,
                    maxDrawCount,
                    strideBytes);
            return;
        }
        if (GLFeatureChecker.supportsIndirectDrawCountARB()) {
            ARBIndirectParameters.nglMultiDrawElementsIndirectCountARB(
                    GL11.GL_TRIANGLES,
                    GL11.GL_UNSIGNED_INT,
                    indirectOffsetBytes,
                    countBufferOffsetBytes,
                    maxDrawCount,
                    strideBytes);
            return;
        }
        throw new UnsupportedOperationException("OpenGL counted indirect draw is unavailable");
    }
}
