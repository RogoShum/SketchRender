package rogo.sketch.backend.vulkan;

import rogo.sketch.core.backend.IndirectDrawService;

final class VulkanCountedIndirectDraw implements IndirectDrawService {
    @Override
    public boolean isSupported() {
        return false;
    }

    @Override
    public void multiDrawElementsIndirectCount(
            long indirectOffsetBytes,
            long countBufferOffsetBytes,
            int maxDrawCount,
            int strideBytes) {
        throw new UnsupportedOperationException("Terrain counted indirect draw is not wired for Vulkan yet");
    }
}
