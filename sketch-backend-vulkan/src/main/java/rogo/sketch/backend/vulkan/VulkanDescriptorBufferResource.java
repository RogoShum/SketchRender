package rogo.sketch.backend.vulkan;

import rogo.sketch.core.backend.BackendInstalledBuffer;

interface VulkanDescriptorBufferResource extends BackendInstalledBuffer {
    long descriptorBuffer();

    long descriptorRange();
}
