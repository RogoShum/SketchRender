package rogo.sketch.backend.vulkan;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkClearValue;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkRenderPassBeginInfo;
import rogo.sketch.core.packet.ClearPacket;
import rogo.sketch.core.packet.DrawPacket;
import rogo.sketch.core.packet.DrawPlan;
import rogo.sketch.core.packet.PipelineStateKey;
import rogo.sketch.core.packet.RenderPacket;
import rogo.sketch.core.pipeline.PipelineType;
import rogo.sketch.core.pipeline.kernel.FrameExecutionPlan;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.lwjgl.vulkan.VK10.VK_INDEX_TYPE_UINT32;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_BIND_POINT_GRAPHICS;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO;
import static org.lwjgl.vulkan.VK10.VK_SUBPASS_CONTENTS_INLINE;
import static org.lwjgl.vulkan.VK10.vkCmdBeginRenderPass;
import static org.lwjgl.vulkan.VK10.vkCmdBindIndexBuffer;
import static org.lwjgl.vulkan.VK10.vkCmdBindPipeline;
import static org.lwjgl.vulkan.VK10.vkCmdBindVertexBuffers;
import static org.lwjgl.vulkan.VK10.vkCmdDraw;
import static org.lwjgl.vulkan.VK10.vkCmdDrawIndexed;
import static org.lwjgl.vulkan.VK10.vkCmdDrawIndexedIndirect;
import static org.lwjgl.vulkan.VK10.vkCmdDrawIndirect;
import static org.lwjgl.vulkan.VK10.vkCmdEndRenderPass;

final class VulkanPacketExecutor {
    @SuppressWarnings("unused")
    private final VkDevice device;
    private final VulkanDescriptorArena descriptorArena;
    private final VulkanGeometryArena geometryArena;

    VulkanPacketExecutor(VkDevice device, VulkanDescriptorArena descriptorArena, VulkanGeometryArena geometryArena) {
        this.device = device;
        this.descriptorArena = descriptorArena;
        this.geometryArena = geometryArena;
    }

    void record(
            VkCommandBuffer commandBuffer,
            VulkanRasterPipelineCache pipelineCache,
            FrameExecutionPlan executionPlan,
            int imageIndex,
            float[] fallbackClearColor) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkClearValue.Buffer clearValues = VkClearValue.calloc(1, stack);
            float[] clearColor = resolveClearColor(executionPlan, fallbackClearColor);
            clearValues.get(0).color()
                    .float32(0, clearColor[0])
                    .float32(1, clearColor[1])
                    .float32(2, clearColor[2])
                    .float32(3, clearColor[3]);

            VkRenderPassBeginInfo renderPassInfo = VkRenderPassBeginInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO)
                    .renderPass(pipelineCache.renderPass())
                    .framebuffer(pipelineCache.framebuffer(imageIndex))
                    .pClearValues(clearValues);
            renderPassInfo.renderArea()
                    .offset(it -> it.set(0, 0))
                    .extent(it -> it.set(pipelineCache.extentWidth(), pipelineCache.extentHeight()));

            vkCmdBeginRenderPass(commandBuffer, renderPassInfo, VK_SUBPASS_CONTENTS_INLINE);
            for (RenderPacket packet : flattenPackets(executionPlan)) {
                if (packet instanceof DrawPacket drawPacket) {
                    recordDrawPacket(commandBuffer, pipelineCache, drawPacket);
                }
            }
            vkCmdEndRenderPass(commandBuffer);
        }
    }

    private void recordDrawPacket(
            VkCommandBuffer commandBuffer,
            VulkanRasterPipelineCache pipelineCache,
            DrawPacket packet) {
        DrawPlan drawPlan = packet.drawPlan();
        if (drawPlan == null) {
            return;
        }

        long pipeline = pipelineCache.pipelineFor(packet.stateKey());
        if (pipeline == VK_NULL_HANDLE) {
            return;
        }

        VulkanGeometryArena.GeometrySlice geometrySlice = geometryArena.resolve(packet.geometryHandle());
        if (geometrySlice == null) {
            return;
        }

        vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline);
        descriptorArena.bindResources(commandBuffer, pipelineCache.pipelineLayout(), packet.resourceSetKey());
        vkCmdBindVertexBuffers(commandBuffer, 0, new long[]{geometrySlice.vertexBuffer()}, new long[]{0L});

        if (drawPlan.submission() != DrawPlan.DrawSubmission.MULTI_DRAW_INDIRECT) {
            for (DrawPlan.DirectDrawItem item : drawPlan.directItems()) {
                if (!item.indexed()) {
                    int vertexCount = item.vertexCount() > 0 ? item.vertexCount() : geometrySlice.vertexCount();
                    vkCmdDraw(
                            commandBuffer,
                            vertexCount,
                            item.instanceCount(),
                            item.firstVertex(),
                            item.baseInstance());
                    continue;
                }

                if (geometrySlice.indexBuffer() == VK_NULL_HANDLE || item.indexedShard() == null) {
                    continue;
                }
                vkCmdBindIndexBuffer(commandBuffer, geometrySlice.indexBuffer(), 0L, VK_INDEX_TYPE_UINT32);
                vkCmdDrawIndexed(
                        commandBuffer,
                        item.indexedShard().indexCount(),
                        item.instanceCount(),
                        (int) (item.indexedShard().indicesOffset() / Integer.BYTES),
                        (int) item.indexedShard().vertexOffset(),
                        item.baseInstance());
            }
            return;
        }

        if (drawPlan.submission() == DrawPlan.DrawSubmission.MULTI_DRAW_INDIRECT && geometrySlice.indirectBuffer() != VK_NULL_HANDLE) {
            if (drawPlan.primitiveType().requiresIndexBuffer() && geometrySlice.indexBuffer() != VK_NULL_HANDLE) {
                vkCmdBindIndexBuffer(commandBuffer, geometrySlice.indexBuffer(), 0L, VK_INDEX_TYPE_UINT32);
                vkCmdDrawIndexedIndirect(
                        commandBuffer,
                        geometrySlice.indirectBuffer(),
                        drawPlan.indirectOffset(),
                        drawPlan.drawCount(),
                        drawPlan.indirectStride());
            } else {
                vkCmdDrawIndirect(
                        commandBuffer,
                        geometrySlice.indirectBuffer(),
                        drawPlan.indirectOffset(),
                        drawPlan.drawCount(),
                        drawPlan.indirectStride());
            }
        }
    }

    private float[] resolveClearColor(FrameExecutionPlan executionPlan, float[] fallbackClearColor) {
        float[] clearColor = fallbackClearColor != null ? fallbackClearColor.clone() : new float[]{0f, 0f, 0f, 1f};
        if (executionPlan == null) {
            return clearColor;
        }
        for (RenderPacket packet : flattenPackets(executionPlan)) {
            if (packet instanceof ClearPacket clearPacket && clearPacket.clearColor() && clearPacket.colorValue() != null) {
                return clearPacket.colorValue().clone();
            }
        }
        return clearColor;
    }

    private List<RenderPacket> flattenPackets(FrameExecutionPlan executionPlan) {
        List<RenderPacket> packets = new ArrayList<>();
        if (executionPlan == null || executionPlan.stagePlans().isEmpty()) {
            return packets;
        }
        for (rogo.sketch.core.pipeline.kernel.StageExecutionPlan stagePlan : executionPlan.stagePlans().values()) {
            for (Map.Entry<PipelineType, Map<PipelineStateKey, List<RenderPacket>>> pipelineEntry : stagePlan.packets().entrySet()) {
                for (List<RenderPacket> statePackets : pipelineEntry.getValue().values()) {
                    packets.addAll(statePackets);
                }
            }
        }
        return packets;
    }
}
