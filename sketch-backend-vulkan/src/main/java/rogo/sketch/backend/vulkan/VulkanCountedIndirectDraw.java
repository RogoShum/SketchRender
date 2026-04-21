package rogo.sketch.backend.vulkan;

import rogo.sketch.core.backend.BackendCountedIndirectDraw;

final class VulkanCountedIndirectDraw implements BackendCountedIndirectDraw {
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
        throw new UnsupportedOperationException("Terrain counted indirect draw is not wired for Vulkan yet");
    }
}
