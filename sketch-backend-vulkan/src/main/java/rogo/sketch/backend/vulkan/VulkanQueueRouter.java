package rogo.sketch.backend.vulkan;

import org.lwjgl.vulkan.VkQueue;
import rogo.sketch.core.backend.GpuHandle;
import rogo.sketch.core.backend.QueueAffinity;
import rogo.sketch.core.backend.QueueRouter;
import rogo.sketch.core.packet.ExecutionDomain;

final class VulkanQueueRouter implements QueueRouter {
    private final GpuHandle graphicsQueueHandle;
    private final GpuHandle computeQueueHandle;
    private final GpuHandle transferQueueHandle;
    private final boolean dedicatedComputeQueue;
    private final boolean dedicatedTransferQueue;

    VulkanQueueRouter(
            VkQueue graphicsQueue,
            VkQueue computeQueue,
            VkQueue transferQueue,
            boolean dedicatedComputeQueue,
            boolean dedicatedTransferQueue) {
        this.graphicsQueueHandle = GpuHandle.of(graphicsQueue != null ? graphicsQueue.address() : GpuHandle.NULL);
        this.computeQueueHandle = GpuHandle.of(computeQueue != null ? computeQueue.address() : GpuHandle.NULL);
        this.transferQueueHandle = GpuHandle.of(transferQueue != null ? transferQueue.address() : GpuHandle.NULL);
        this.dedicatedComputeQueue = dedicatedComputeQueue;
        this.dedicatedTransferQueue = dedicatedTransferQueue;
    }

    @Override
    public GpuHandle resolveQueue(QueueAffinity affinity) {
        if (affinity == null || affinity.domain() == null) {
            return graphicsQueueHandle;
        }
        return switch (affinity.domain()) {
            case RASTER, OFFSCREEN_GRAPHICS -> graphicsQueueHandle;
            case COMPUTE -> affinity.preferDedicated() && dedicatedComputeQueue
                    ? computeQueueHandle
                    : graphicsQueueHandle;
            case TRANSFER -> affinity.preferDedicated() && dedicatedTransferQueue
                    ? transferQueueHandle
                    : graphicsQueueHandle;
        };
    }

    @Override
    public boolean isDedicatedQueue(ExecutionDomain domain) {
        if (domain == null) {
            return false;
        }
        return switch (domain) {
            case COMPUTE -> dedicatedComputeQueue;
            case TRANSFER -> dedicatedTransferQueue;
            case RASTER, OFFSCREEN_GRAPHICS -> false;
        };
    }
}
