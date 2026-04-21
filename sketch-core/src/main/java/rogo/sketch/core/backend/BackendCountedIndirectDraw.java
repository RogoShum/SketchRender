package rogo.sketch.core.backend;

/**
 * Backend-owned counted indirect draw contract used by host renderers that
 * still own their final drawcall submission.
 */
public interface BackendCountedIndirectDraw {
    BackendCountedIndirectDraw UNSUPPORTED = new BackendCountedIndirectDraw() {
        @Override
        public boolean isSupported() {
            return false;
        }

        @Override
        public void multiDrawElementsIndirectCount(
                int primitiveType,
                int indexType,
                long indirectOffsetBytes,
                long countBufferOffsetBytes,
                int maxDrawCount,
                int strideBytes) {
            throw new UnsupportedOperationException("Counted indirect draw is not supported by this backend");
        }
    };

    boolean isSupported();

    void multiDrawElementsIndirectCount(
            int primitiveType,
            int indexType,
            long indirectOffsetBytes,
            long countBufferOffsetBytes,
            int maxDrawCount,
            int strideBytes);
}
