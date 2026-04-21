package rogo.sketch.backend.vulkan;

import rogo.sketch.core.backend.CommandRecorder;
import rogo.sketch.core.backend.CommandRecorderFactory;

final class VulkanCommandRecorderFactory implements CommandRecorderFactory {
    private final VulkanRenderDevice renderDevice;

    VulkanCommandRecorderFactory(VulkanRenderDevice renderDevice) {
        this.renderDevice = renderDevice;
    }

    @Override
    public CommandRecorder create(String label) {
        return new VulkanCommandRecorder(renderDevice, label);
    }
}
