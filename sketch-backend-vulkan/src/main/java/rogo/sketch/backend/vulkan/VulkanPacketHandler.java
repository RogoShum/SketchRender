package rogo.sketch.backend.vulkan;

import rogo.sketch.core.packet.RenderPacket;

@FunctionalInterface
public interface VulkanPacketHandler {
    void record(VulkanPacketExecutionContext context, RenderPacket packet);
}
