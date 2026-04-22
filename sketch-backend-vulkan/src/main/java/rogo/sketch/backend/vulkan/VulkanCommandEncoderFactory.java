package rogo.sketch.backend.vulkan;

import rogo.sketch.core.backend.CommandEncoder;
import rogo.sketch.core.backend.CommandEncoderFactory;

final class VulkanCommandEncoderFactory implements CommandEncoderFactory {
    private final VulkanRenderDevice renderDevice;

    VulkanCommandEncoderFactory(VulkanRenderDevice renderDevice) {
        this.renderDevice = renderDevice;
    }

    @Override
    public CommandEncoder create(String label) {
        return new VulkanCommandEncoder(renderDevice, label);
    }
}
