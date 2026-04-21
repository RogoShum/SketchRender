package rogo.sketch.backend.vulkan;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkCommandBufferAllocateInfo;
import org.lwjgl.vulkan.VkCommandBufferBeginInfo;
import org.lwjgl.vulkan.VkSubmitInfo;
import rogo.sketch.core.backend.CommandRecorder;

import static org.lwjgl.vulkan.VK10.VK_COMMAND_BUFFER_LEVEL_PRIMARY;
import static org.lwjgl.vulkan.VK10.VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_ALL_COMMANDS_BIT;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_SUBMIT_INFO;
import static org.lwjgl.vulkan.VK10.vkAllocateCommandBuffers;
import static org.lwjgl.vulkan.VK10.vkBeginCommandBuffer;
import static org.lwjgl.vulkan.VK10.vkCmdDispatch;
import static org.lwjgl.vulkan.VK10.vkCmdPipelineBarrier;
import static org.lwjgl.vulkan.VK10.vkEndCommandBuffer;
import static org.lwjgl.vulkan.VK10.vkFreeCommandBuffers;
import static org.lwjgl.vulkan.VK10.vkQueueSubmit;
import static org.lwjgl.vulkan.VK10.vkQueueWaitIdle;

final class VulkanCommandRecorder implements CommandRecorder {
    private final VulkanRenderDevice renderDevice;
    @SuppressWarnings("unused")
    private final String label;
    private VkCommandBuffer commandBuffer;
    private boolean recording;
    private boolean submitted;

    VulkanCommandRecorder(VulkanRenderDevice renderDevice, String label) {
        this.renderDevice = renderDevice;
        this.label = label != null ? label : "unnamed";
    }

    @Override
    public void bufferBarrier() {
        VkCommandBuffer commandBuffer = ensureRecording();
        vkCmdPipelineBarrier(
                commandBuffer,
                VK_PIPELINE_STAGE_ALL_COMMANDS_BIT,
                VK_PIPELINE_STAGE_ALL_COMMANDS_BIT,
                0,
                null,
                null,
                null);
    }

    @Override
    public void imageBarrier() {
        bufferBarrier();
    }

    @Override
    public void dispatch(int groupCountX, int groupCountY, int groupCountZ) {
        VkCommandBuffer commandBuffer = ensureRecording();
        vkCmdDispatch(commandBuffer, groupCountX, groupCountY, groupCountZ);
    }

    @Override
    public void submit() {
        if (!recording || submitted || commandBuffer == null) {
            return;
        }
        VulkanDeviceBootstrapper.checkVkResult(vkEndCommandBuffer(commandBuffer), "vkEndCommandBuffer");
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkSubmitInfo submitInfo = VkSubmitInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
                    .pCommandBuffers(stack.pointers(commandBuffer.address()));
            VulkanDeviceBootstrapper.checkVkResult(
                    vkQueueSubmit(renderDevice.graphicsQueue(), submitInfo, VK_NULL_HANDLE),
                    "vkQueueSubmit");
            VulkanDeviceBootstrapper.checkVkResult(
                    vkQueueWaitIdle(renderDevice.graphicsQueue()),
                    "vkQueueWaitIdle");
        }
        submitted = true;
        recording = false;
        freeCommandBuffer();
    }

    @Override
    public void close() {
        try {
            submit();
        } finally {
            freeCommandBuffer();
        }
    }

    private VkCommandBuffer ensureRecording() {
        if (recording && commandBuffer != null) {
            return commandBuffer;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkCommandBufferAllocateInfo allocInfo = VkCommandBufferAllocateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
                    .commandPool(renderDevice.commandPoolHandle())
                    .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                    .commandBufferCount(1);
            PointerBuffer pointerBuffer = stack.mallocPointer(1);
            VulkanDeviceBootstrapper.checkVkResult(
                    vkAllocateCommandBuffers(renderDevice.device(), allocInfo, pointerBuffer),
                    "vkAllocateCommandBuffers");
            commandBuffer = new VkCommandBuffer(pointerBuffer.get(0), renderDevice.device());

            VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
                    .flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);
            VulkanDeviceBootstrapper.checkVkResult(
                    vkBeginCommandBuffer(commandBuffer, beginInfo),
                    "vkBeginCommandBuffer");
        }
        recording = true;
        submitted = false;
        return commandBuffer;
    }

    private void freeCommandBuffer() {
        if (commandBuffer == null) {
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            vkFreeCommandBuffers(
                    renderDevice.device(),
                    renderDevice.commandPoolHandle(),
                    stack.pointers(commandBuffer.address()));
        }
        commandBuffer = null;
    }
}
