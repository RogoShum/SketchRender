package rogo.sketch.backend.opengl;

import org.lwjgl.opengl.ARBIndirectParameters;
import org.lwjgl.opengl.GL46C;
import rogo.sketch.backend.opengl.util.GLFeatureChecker;
import rogo.sketch.core.backend.BackendCountedIndirectDraw;

final class OpenGLCountedIndirectDraw implements BackendCountedIndirectDraw {
    @Override
    public boolean isSupported() {
        return GLFeatureChecker.supportsIndirectDrawCount();
    }

    @Override
    public void multiDrawElementsIndirectCount(
            int primitiveType,
            int indexType,
            long indirectOffsetBytes,
            long countBufferOffsetBytes,
            int maxDrawCount,
            int strideBytes) {
        if (GLFeatureChecker.supportsIndirectDrawCount46()) {
            GL46C.nglMultiDrawElementsIndirectCount(
                    primitiveType,
                    indexType,
                    indirectOffsetBytes,
                    countBufferOffsetBytes,
                    maxDrawCount,
                    strideBytes);
            return;
        }
        if (GLFeatureChecker.supportsIndirectDrawCountARB()) {
            ARBIndirectParameters.nglMultiDrawElementsIndirectCountARB(
                    primitiveType,
                    indexType,
                    indirectOffsetBytes,
                    countBufferOffsetBytes,
                    maxDrawCount,
                    strideBytes);
            return;
        }
        throw new UnsupportedOperationException("OpenGL counted indirect draw is unavailable");
    }
}
