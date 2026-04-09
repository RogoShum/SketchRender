package rogo.sketch.backend.vulkan;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkClearAttachment;
import org.lwjgl.vulkan.VkClearColorValue;
import org.lwjgl.vulkan.VkClearDepthStencilValue;
import org.lwjgl.vulkan.VkClearRect;
import org.lwjgl.vulkan.VkClearValue;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDebugUtilsLabelEXT;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkImageBlit;
import org.lwjgl.vulkan.VkImageMemoryBarrier;
import org.lwjgl.vulkan.VkImageSubresourceRange;
import org.lwjgl.vulkan.VkRenderPassBeginInfo;
import rogo.sketch.core.packet.ClearPacket;
import rogo.sketch.core.packet.DispatchPacket;
import rogo.sketch.core.packet.DrawPacket;
import rogo.sketch.core.packet.DrawPlan;
import rogo.sketch.core.packet.GenerateMipmapPacket;
import rogo.sketch.core.packet.PipelineStateKey;
import rogo.sketch.core.packet.RenderPacket;
import rogo.sketch.core.packet.RenderPacketKind;
import rogo.sketch.core.packet.ResourceSetKey;
import rogo.sketch.core.backend.BackendPacketHandlerRegistry;
import rogo.sketch.core.pipeline.PipelineType;
import rogo.sketch.core.pipeline.TargetBinding;
import rogo.sketch.core.pipeline.kernel.FrameExecutionPlan;
import rogo.sketch.core.pipeline.module.diagnostic.SketchDiagnostics;
import rogo.sketch.core.util.KeyId;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.lwjgl.vulkan.EXTDebugUtils.VK_STRUCTURE_TYPE_DEBUG_UTILS_LABEL_EXT;
import static org.lwjgl.vulkan.EXTDebugUtils.vkCmdBeginDebugUtilsLabelEXT;
import static org.lwjgl.vulkan.EXTDebugUtils.vkCmdEndDebugUtilsLabelEXT;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_COLOR_ATTACHMENT_READ_BIT;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_SHADER_READ_BIT;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_TRANSFER_READ_BIT;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_TRANSFER_WRITE_BIT;
import static org.lwjgl.vulkan.VK10.VK_FILTER_NEAREST;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_COLOR_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_UNDEFINED;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_TRANSFER_DST_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_TRANSFER_SRC_BIT;
import static org.lwjgl.vulkan.VK10.VK_INDEX_TYPE_UINT32;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_BIND_POINT_COMPUTE;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_BIND_POINT_GRAPHICS;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_ALL_COMMANDS_BIT;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_TRANSFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO;
import static org.lwjgl.vulkan.VK10.VK_SUBPASS_CONTENTS_INLINE;
import static org.lwjgl.vulkan.VK10.vkCmdBeginRenderPass;
import static org.lwjgl.vulkan.VK10.vkCmdBindIndexBuffer;
import static org.lwjgl.vulkan.VK10.vkCmdBindPipeline;
import static org.lwjgl.vulkan.VK10.vkCmdBindVertexBuffers;
import static org.lwjgl.vulkan.VK10.vkCmdBlitImage;
import static org.lwjgl.vulkan.VK10.vkCmdClearAttachments;
import static org.lwjgl.vulkan.VK10.vkCmdClearColorImage;
import static org.lwjgl.vulkan.VK10.vkCmdClearDepthStencilImage;
import static org.lwjgl.vulkan.VK10.vkCmdDispatch;
import static org.lwjgl.vulkan.VK10.vkCmdDraw;
import static org.lwjgl.vulkan.VK10.vkCmdDrawIndexed;
import static org.lwjgl.vulkan.VK10.vkCmdDrawIndexedIndirect;
import static org.lwjgl.vulkan.VK10.vkCmdDrawIndirect;
import static org.lwjgl.vulkan.VK10.vkCmdEndRenderPass;
import static org.lwjgl.vulkan.VK10.vkCmdPipelineBarrier;

final class VulkanPacketExecutor {
    private static final String DIAG_MODULE = "vulkan-packet-executor";

    @SuppressWarnings("unused")
    private final VkDevice device;
    private final VulkanDescriptorArena descriptorArena;
    private final VulkanGeometryArena geometryArena;
    private final VulkanResourceResolver resourceResolver;
    private final Set<String> warnedMipmap = ConcurrentHashMap.newKeySet();
    private final Set<String> warnedUnsupportedClearTargets = ConcurrentHashMap.newKeySet();
    private final Set<String> warnedUnsupportedClearDepth = ConcurrentHashMap.newKeySet();
    private final boolean debugUtilsEnabled;
    private final BackendPacketHandlerRegistry<VulkanPacketHandler> packetHandlers = new BackendPacketHandlerRegistry<>();

    VulkanPacketExecutor(
            VkDevice device,
            VulkanDescriptorArena descriptorArena,
            VulkanGeometryArena geometryArena,
            VulkanResourceResolver resourceResolver,
            boolean debugUtilsEnabled) {
        this.device = device;
        this.descriptorArena = descriptorArena;
        this.geometryArena = geometryArena;
        this.resourceResolver = resourceResolver;
        this.debugUtilsEnabled = debugUtilsEnabled;
        registerBuiltInPacketHandlers();
    }

    BackendPacketHandlerRegistry<VulkanPacketHandler> packetHandlerRegistry() {
        return packetHandlers;
    }

    void record(
            VkCommandBuffer commandBuffer,
            VulkanRasterPipelineCache pipelineCache,
            VulkanComputePipelineCache computePipelineCache,
            FrameExecutionPlan executionPlan,
            List<RenderPacket> immediatePackets,
            int imageIndex) {
        List<RenderPacket> packets = flattenPackets(executionPlan);
        if (immediatePackets != null && !immediatePackets.isEmpty()) {
            packets.addAll(immediatePackets);
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VulkanPacketExecutionContext executionContext = new VulkanPacketExecutionContext(
                    commandBuffer,
                    pipelineCache,
                    computePipelineCache,
                    imageIndex,
                    stack,
                    this);
            for (RenderPacket packet : packets) {
                pushDebugLabel(commandBuffer, debugLabel(packet));
                try {
                    if (packet.packetKind() == RenderPacketKind.CLEAR && packet instanceof ClearPacket clearPacket) {
                        if (targetsCurrentRenderPass(clearPacket.renderTargetId())) {
                            executionContext.ensureRenderPassOpen();
                        } else {
                            executionContext.ensureRenderPassClosed();
                        }
                    } else if (packet.packetKind() == RenderPacketKind.DRAW) {
                        executionContext.ensureRenderPassOpen();
                    } else {
                        executionContext.ensureRenderPassClosed();
                    }

                    VulkanPacketHandler handler = packetHandlers.handlerFor(packet);
                    if (handler == null) {
                        throw new IllegalArgumentException(
                                "No Vulkan packet handler registered for " + packet.packetType().id()
                                        + " (" + packet.getClass().getName() + ")");
                    }
                    handler.record(executionContext, packet);
                } finally {
                    popDebugLabel(commandBuffer);
                }
            }
            executionContext.closeRenderPassIfOpen();
        }
    }

    private void registerBuiltInPacketHandlers() {
        packetHandlers.register(rogo.sketch.core.packet.RenderPacketType.DRAW,
                (context, packet) -> recordDrawPacket(
                        context.commandBuffer(),
                        context.rasterPipelineCache(),
                        (DrawPacket) packet));
        packetHandlers.register(rogo.sketch.core.packet.RenderPacketType.DISPATCH,
                (context, packet) -> recordDispatchPacket(
                        context.commandBuffer(),
                        context.computePipelineCache(),
                        (DispatchPacket) packet));
        packetHandlers.register(rogo.sketch.core.packet.RenderPacketType.CLEAR,
                (context, packet) -> {
                    ClearPacket clearPacket = (ClearPacket) packet;
                    if (targetsCurrentRenderPass(clearPacket.renderTargetId())) {
                        clearCurrentAttachments(
                                context.commandBuffer(),
                                clearPacket,
                                context.rasterPipelineCache(),
                                context.stack());
                    } else {
                        recordClearPacket(context.commandBuffer(), clearPacket);
                    }
                });
        packetHandlers.register(rogo.sketch.core.packet.RenderPacketType.GENERATE_MIPMAP,
                (context, packet) -> recordGenerateMipmapPacket(
                        context.commandBuffer(),
                        (GenerateMipmapPacket) packet));
    }

    private void recordDrawPacket(
            VkCommandBuffer commandBuffer,
            VulkanRasterPipelineCache pipelineCache,
            DrawPacket packet) {
        DrawPlan drawPlan = packet.drawPlan();
        if (drawPlan == null) {
            return;
        }

        ResourceSetKey resourceSetKey = packet.resourceSetKey();
        long descriptorSetLayout = descriptorArena.layoutHandle(resourceSetKey.resourceLayoutKey());
        long pipeline = pipelineCache.pipelineFor(packet.stateKey(), resourceSetKey.resourceLayoutKey(), descriptorSetLayout);
        if (pipeline == VK_NULL_HANDLE) {
            return;
        }
        long pipelineLayout = pipelineCache.pipelineLayout(resourceSetKey.resourceLayoutKey(), descriptorSetLayout);

        VulkanGeometryArena.GeometrySlice geometrySlice = geometryArena.resolve(packet.geometryHandle());
        if (geometrySlice == null || geometrySlice.vertexBindings().length == 0) {
            return;
        }

        vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline);
        descriptorArena.bindResources(commandBuffer, pipelineLayout, resourceSetKey);
        long[] vertexBuffers = new long[geometrySlice.vertexBindings().length];
        long[] vertexOffsets = new long[geometrySlice.vertexBindings().length];
        for (int i = 0; i < geometrySlice.vertexBindings().length; i++) {
            VulkanGeometryArena.VertexBindingSlice bindingSlice = geometrySlice.vertexBindings()[i];
            vertexBuffers[i] = bindingSlice.buffer();
            vertexOffsets[i] = bindingSlice.offset();
        }
        vkCmdBindVertexBuffers(commandBuffer, geometrySlice.vertexBindings()[0].binding(), vertexBuffers, vertexOffsets);

        if (drawPlan.submission() != DrawPlan.DrawSubmission.MULTI_DRAW_INDIRECT) {
            if (drawPlan.indexed() && geometrySlice.indexSlice() != null) {
                vkCmdBindIndexBuffer(
                        commandBuffer,
                        geometrySlice.indexSlice().buffer(),
                        geometrySlice.indexSlice().offset(),
                        VK_INDEX_TYPE_UINT32);
            }
            for (DrawPlan.DirectDrawItem item : drawPlan.directItems()) {
                if (!item.indexed()) {
                    int vertexCount = item.vertexCount() > 0 ? item.vertexCount() : geometrySlice.vertexCount();
                    vkCmdDraw(commandBuffer, vertexCount, item.instanceCount(), item.firstVertex(), item.baseInstance());
                    continue;
                }
                if (geometrySlice.indexSlice() == null || item.indexedSlice() == null) {
                    continue;
                }
                vkCmdDrawIndexed(
                        commandBuffer,
                        item.indexedSlice().indexCount(),
                        item.instanceCount(),
                        (int) (item.indexedSlice().firstIndexByteOffset() / Integer.BYTES),
                        item.indexedSlice().baseVertex(),
                        item.baseInstance());
            }
            return;
        }

        if (geometrySlice.indirectSlice() == null) {
            return;
        }
        if (drawPlan.indexed() && geometrySlice.indexSlice() != null) {
            vkCmdBindIndexBuffer(
                    commandBuffer,
                    geometrySlice.indexSlice().buffer(),
                    geometrySlice.indexSlice().offset(),
                    VK_INDEX_TYPE_UINT32);
            vkCmdDrawIndexedIndirect(
                    commandBuffer,
                    geometrySlice.indirectSlice().buffer(),
                    geometrySlice.indirectSlice().offset() + drawPlan.indirectOffset(),
                    drawPlan.drawCount(),
                    drawPlan.indirectStride());
        } else {
            vkCmdDrawIndirect(
                    commandBuffer,
                    geometrySlice.indirectSlice().buffer(),
                    geometrySlice.indirectSlice().offset() + drawPlan.indirectOffset(),
                    drawPlan.drawCount(),
                    drawPlan.indirectStride());
        }
    }

    private void recordDispatchPacket(
            VkCommandBuffer commandBuffer,
            VulkanComputePipelineCache computePipelineCache,
            DispatchPacket packet) {
        if (packet == null || computePipelineCache == null) {
            return;
        }
        ResourceSetKey resourceSetKey = packet.resourceSetKey();
        long descriptorSetLayout = descriptorArena.layoutHandle(resourceSetKey.resourceLayoutKey());
        long pipeline = computePipelineCache.pipelineFor(packet.stateKey(), resourceSetKey.resourceLayoutKey(), descriptorSetLayout);
        if (pipeline == VK_NULL_HANDLE) {
            return;
        }
        long pipelineLayout = computePipelineCache.pipelineLayout(resourceSetKey.resourceLayoutKey(), descriptorSetLayout);

        vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, pipeline);
        descriptorArena.bindResources(commandBuffer, pipelineLayout, resourceSetKey, VK_PIPELINE_BIND_POINT_COMPUTE);
        vkCmdDispatch(
                commandBuffer,
                Math.max(1, packet.workGroupsX()),
                Math.max(1, packet.workGroupsY()),
                Math.max(1, packet.workGroupsZ()));
    }

    private void recordClearPacket(VkCommandBuffer commandBuffer, ClearPacket packet) {
        if (packet == null) {
            return;
        }

        VulkanResourceResolver.ResolvedRenderTarget renderTarget = resourceResolver.resolveRenderTargetResource(packet.renderTargetId());
        if (renderTarget == null) {
            warnMissingNamedClearTarget(packet.renderTargetId());
            return;
        }

        if (packet.clearColor() && packet.colorValue() != null) {
            for (Integer attachmentIndex : resolveNamedColorAttachments(packet, renderTarget)) {
                VulkanTextureResource textureResource = renderTarget.colorAttachments().get(attachmentIndex);
                if (textureResource == null) {
                    warnMissingNamedClearAttachment(packet.renderTargetId(), attachmentIndex);
                    continue;
                }
                clearColorImage(commandBuffer, textureResource, packet.colorValue());
            }
        }

        if (packet.clearDepth()) {
            VulkanTextureResource depthAttachment = renderTarget.depthAttachment();
            if (depthAttachment == null) {
                warnMissingNamedDepthAttachment(packet.renderTargetId());
                return;
            }
            clearDepthImage(commandBuffer, depthAttachment, packet.depthValue());
        }
    }

    private void recordGenerateMipmapPacket(VkCommandBuffer commandBuffer, GenerateMipmapPacket packet) {
        if (packet == null || packet.textureId() == null) {
            return;
        }
        VulkanTextureResource textureResource = resourceResolver != null
                ? resourceResolver.resolveTextureResource(packet.textureId())
                : null;
        if (textureResource == null || textureResource.isDisposed()) {
            return;
        }
        if (textureResource.mipLevels() <= 1) {
            return;
        }
        if ((textureResource.aspectMask() & VK_IMAGE_ASPECT_COLOR_BIT) == 0
                || (textureResource.usageFlags() & VK_IMAGE_USAGE_TRANSFER_SRC_BIT) == 0
                || (textureResource.usageFlags() & VK_IMAGE_USAGE_TRANSFER_DST_BIT) == 0) {
            warnUnsupportedMipmap(packet.textureId(), "missing color/transfer usage requirements");
            return;
        }

        int finalLayout = textureResource.imageLayout() != 0
                ? textureResource.imageLayout()
                : VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
        transitionImageLayout(
                commandBuffer,
                textureResource.image(),
                textureResource.aspectMask(),
                textureResource.currentImageLayout(),
                VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                0,
                textureResource.mipLevels());

        int mipWidth = Math.max(1, textureResource.width());
        int mipHeight = Math.max(1, textureResource.height());
        try (MemoryStack stack = MemoryStack.stackPush()) {
            for (int level = 1; level < textureResource.mipLevels(); level++) {
                transitionImageLayout(
                        commandBuffer,
                        textureResource.image(),
                        textureResource.aspectMask(),
                        VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                        VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                        level - 1,
                        1);

                int nextMipWidth = Math.max(1, mipWidth / 2);
                int nextMipHeight = Math.max(1, mipHeight / 2);

                VkImageBlit.Buffer blit = VkImageBlit.calloc(1, stack);
                blit.srcSubresource()
                        .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                        .mipLevel(level - 1)
                        .baseArrayLayer(0)
                        .layerCount(1);
                blit.srcOffsets(0).set(0, 0, 0);
                blit.srcOffsets(1).set(mipWidth, mipHeight, 1);
                blit.dstSubresource()
                        .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                        .mipLevel(level)
                        .baseArrayLayer(0)
                        .layerCount(1);
                blit.dstOffsets(0).set(0, 0, 0);
                blit.dstOffsets(1).set(nextMipWidth, nextMipHeight, 1);

                vkCmdBlitImage(
                        commandBuffer,
                        textureResource.image(),
                        VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                        textureResource.image(),
                        VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                        blit,
                        VK_FILTER_NEAREST);

                transitionImageLayout(
                        commandBuffer,
                        textureResource.image(),
                        textureResource.aspectMask(),
                        VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                        finalLayout,
                        level - 1,
                        1);

                mipWidth = nextMipWidth;
                mipHeight = nextMipHeight;
            }

            transitionImageLayout(
                    commandBuffer,
                    textureResource.image(),
                    textureResource.aspectMask(),
                    VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                    finalLayout,
                    textureResource.mipLevels() - 1,
                    1);
        }
        textureResource.setCurrentImageLayout(finalLayout);
    }

    private boolean targetsCurrentRenderPass(KeyId renderTargetId) {
        return renderTargetId == null || TargetBinding.DEFAULT_RENDER_TARGET.equals(renderTargetId);
    }

    private void warnMissingNamedClearTarget(KeyId renderTargetId) {
        String warnKey = String.valueOf(renderTargetId);
        if (!warnedUnsupportedClearTargets.add(warnKey)) {
            return;
        }
        SketchDiagnostics.get().warn(
                DIAG_MODULE,
                "Vulkan ClearPacket could not resolve a backend-native named render target for " + renderTargetId);
    }

    private void warnMissingNamedClearAttachment(KeyId renderTargetId, int attachmentIndex) {
        String warnKey = renderTargetId + "#color" + attachmentIndex;
        if (!warnedUnsupportedClearTargets.add(warnKey)) {
            return;
        }
        SketchDiagnostics.get().warn(
                DIAG_MODULE,
                "Vulkan ClearPacket could not resolve color attachment " + attachmentIndex
                        + " for render target " + renderTargetId);
    }

    private void warnMissingNamedDepthAttachment(KeyId renderTargetId) {
        String warnKey = renderTargetId + "#depth";
        if (!warnedUnsupportedClearDepth.add(warnKey)) {
            return;
        }
        SketchDiagnostics.get().warn(
                DIAG_MODULE,
                "Vulkan ClearPacket could not resolve a depth attachment for render target " + renderTargetId);
    }

    private void warnUnsupportedMipmap(KeyId textureId, String reason) {
        String warnKey = textureId + ":" + reason;
        if (!warnedMipmap.add(warnKey)) {
            return;
        }
        SketchDiagnostics.get().warn(
                DIAG_MODULE,
                "Vulkan GenerateMipmapPacket is unsupported for texture " + textureId + ": " + reason);
    }

    void beginRenderPass(
            VkCommandBuffer commandBuffer,
            VulkanRasterPipelineCache pipelineCache,
            int imageIndex,
            MemoryStack stack) {
        VkClearValue.Buffer clearValues = VkClearValue.calloc(2, stack);
        clearValues.get(0).color().float32(0, 0.0f).float32(1, 0.0f).float32(2, 0.0f).float32(3, 0.0f);
        clearValues.get(1).depthStencil().depth(1.0f).stencil(0);

        VkRenderPassBeginInfo renderPassInfo = VkRenderPassBeginInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO)
                .renderPass(pipelineCache.renderPass())
                .framebuffer(pipelineCache.framebuffer(imageIndex))
                .pClearValues(clearValues);
        renderPassInfo.renderArea()
                .offset(it -> it.set(0, 0))
                .extent(it -> it.set(pipelineCache.extentWidth(), pipelineCache.extentHeight()));
        vkCmdBeginRenderPass(commandBuffer, renderPassInfo, VK_SUBPASS_CONTENTS_INLINE);
    }

    private void pushDebugLabel(VkCommandBuffer commandBuffer, String label) {
        if (!debugUtilsEnabled || commandBuffer == null || label == null || label.isBlank()) {
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDebugUtilsLabelEXT labelInfo = VkDebugUtilsLabelEXT.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_DEBUG_UTILS_LABEL_EXT)
                    .pLabelName(stack.UTF8(label));
            labelInfo.color().put(0, 0.20f).put(1, 0.60f).put(2, 0.95f).put(3, 1.0f);
            vkCmdBeginDebugUtilsLabelEXT(commandBuffer, labelInfo);
        }
    }

    private void popDebugLabel(VkCommandBuffer commandBuffer) {
        if (!debugUtilsEnabled || commandBuffer == null) {
            return;
        }
        vkCmdEndDebugUtilsLabelEXT(commandBuffer);
    }

    private String debugLabel(RenderPacket packet) {
        if (packet == null) {
            return "sketch:packet:null";
        }
        StringBuilder builder = new StringBuilder("sketch:");
        builder.append(packet.getClass().getSimpleName());
        if (packet.stageId() != null) {
            builder.append(" stage=").append(packet.stageId());
        }
        List<? extends rogo.sketch.core.api.graphics.Graphics> completionGraphics = packet.completionGraphics();
        if (completionGraphics != null && !completionGraphics.isEmpty()) {
            rogo.sketch.core.api.graphics.Graphics graphics = completionGraphics.get(0);
            if (graphics != null && graphics.getIdentifier() != null) {
                builder.append(" graphics=").append(graphics.getIdentifier());
            }
        }
        return builder.toString();
    }

    private void clearCurrentAttachments(
            VkCommandBuffer commandBuffer,
            ClearPacket packet,
            VulkanRasterPipelineCache pipelineCache,
            MemoryStack stack) {
        int colorAttachmentCount = packet.clearColor() && packet.colorValue() != null
                ? resolveColorAttachments(packet).length
                : 0;
        int depthAttachmentCount = packet.clearDepth() ? 1 : 0;
        if (colorAttachmentCount == 0 && depthAttachmentCount == 0) {
            return;
        }

        int[] colorAttachments = colorAttachmentCount > 0 ? resolveColorAttachments(packet) : new int[0];
        VkClearAttachment.Buffer attachments = VkClearAttachment.calloc(colorAttachmentCount + depthAttachmentCount, stack);
        for (int i = 0; i < colorAttachmentCount; i++) {
            attachments.get(i)
                    .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .colorAttachment(colorAttachments[i]);
            attachments.get(i).clearValue().color()
                    .float32(0, packet.colorValue()[0])
                    .float32(1, packet.colorValue()[1])
                    .float32(2, packet.colorValue()[2])
                    .float32(3, packet.colorValue()[3]);
        }
        if (depthAttachmentCount > 0) {
            attachments.get(colorAttachmentCount)
                    .aspectMask(org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_DEPTH_BIT)
                    .colorAttachment(0);
            attachments.get(colorAttachmentCount)
                    .clearValue()
                    .depthStencil()
                    .depth(packet.depthValue())
                    .stencil(0);
        }

        VkClearRect.Buffer rects = VkClearRect.calloc(1, stack);
        rects.get(0).rect()
                .offset(it -> it.set(0, 0))
                .extent(it -> it.set(pipelineCache.extentWidth(), pipelineCache.extentHeight()));
        rects.get(0)
                .baseArrayLayer(0)
                .layerCount(1);

        vkCmdClearAttachments(commandBuffer, attachments, rects);
    }

    private List<Integer> resolveNamedColorAttachments(
            ClearPacket packet,
            VulkanResourceResolver.ResolvedRenderTarget renderTarget) {
        if (renderTarget == null || renderTarget.colorAttachments().isEmpty()) {
            return List.of();
        }
        if (packet == null || packet.colorAttachments() == null || packet.colorAttachments().isEmpty()) {
            return new ArrayList<>(renderTarget.colorAttachments().keySet());
        }
        List<Integer> resolved = new ArrayList<>();
        for (Object attachment : packet.colorAttachments()) {
            if (attachment instanceof Integer index && index >= 0) {
                resolved.add(index);
            }
        }
        return resolved;
    }

    private int[] resolveColorAttachments(ClearPacket packet) {
        if (packet == null || packet.colorAttachments() == null || packet.colorAttachments().isEmpty()) {
            return new int[]{0};
        }
        List<Integer> resolved = new ArrayList<>();
        for (Object attachment : packet.colorAttachments()) {
            if (attachment instanceof Integer index && index >= 0) {
                resolved.add(index);
            }
        }
        if (resolved.isEmpty()) {
            return new int[]{0};
        }
        return resolved.stream().mapToInt(Integer::intValue).toArray();
    }

    private void clearColorImage(
            VkCommandBuffer commandBuffer,
            VulkanTextureResource textureResource,
            float[] colorValue) {
        if (textureResource == null || colorValue == null) {
            return;
        }
        int finalLayout = textureResource.imageLayout() != 0
                ? textureResource.imageLayout()
                : VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
        transitionImageLayout(
                commandBuffer,
                textureResource.image(),
                textureResource.aspectMask(),
                textureResource.currentImageLayout(),
                VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                0,
                1);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkClearColorValue clearColorValue = VkClearColorValue.calloc(stack);
            clearColorValue.float32(0, colorValue[0]);
            clearColorValue.float32(1, colorValue[1]);
            clearColorValue.float32(2, colorValue[2]);
            clearColorValue.float32(3, colorValue[3]);

            VkImageSubresourceRange.Buffer range = VkImageSubresourceRange.calloc(1, stack);
            range.get(0)
                    .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .baseMipLevel(0)
                    .levelCount(1)
                    .baseArrayLayer(0)
                    .layerCount(1);

            vkCmdClearColorImage(
                    commandBuffer,
                    textureResource.image(),
                    VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                    clearColorValue,
                    range);
        }

        transitionImageLayout(
                commandBuffer,
                textureResource.image(),
                textureResource.aspectMask(),
                VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                finalLayout,
                0,
                1);
        textureResource.setCurrentImageLayout(finalLayout);
    }

    private void clearDepthImage(
            VkCommandBuffer commandBuffer,
            VulkanTextureResource textureResource,
            float depthValue) {
        if (textureResource == null) {
            return;
        }
        int finalLayout = textureResource.imageLayout() != 0
                ? textureResource.imageLayout()
                : VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL;
        transitionImageLayout(
                commandBuffer,
                textureResource.image(),
                textureResource.aspectMask(),
                textureResource.currentImageLayout(),
                VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                0,
                1);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkClearDepthStencilValue clearDepthStencilValue = VkClearDepthStencilValue.calloc(stack)
                    .depth(depthValue)
                    .stencil(0);
            VkImageSubresourceRange.Buffer range = VkImageSubresourceRange.calloc(1, stack);
            range.get(0)
                    .aspectMask(textureResource.aspectMask())
                    .baseMipLevel(0)
                    .levelCount(1)
                    .baseArrayLayer(0)
                    .layerCount(1);
            vkCmdClearDepthStencilImage(
                    commandBuffer,
                    textureResource.image(),
                    VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                    clearDepthStencilValue,
                    range);
        }

        transitionImageLayout(
                commandBuffer,
                textureResource.image(),
                textureResource.aspectMask(),
                VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                finalLayout,
                0,
                1);
        textureResource.setCurrentImageLayout(finalLayout);
    }

    private void transitionImageLayout(
            VkCommandBuffer commandBuffer,
            long image,
            int aspectMask,
            int oldLayout,
            int newLayout,
            int baseMipLevel,
            int levelCount) {
        if (image == VK_NULL_HANDLE || oldLayout == newLayout) {
            return;
        }
        LayoutTransition transition = resolveTransition(oldLayout, newLayout);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.calloc(1, stack);
            barrier.get(0)
                    .sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                    .oldLayout(oldLayout)
                    .newLayout(newLayout)
                    .srcQueueFamilyIndex(-1)
                    .dstQueueFamilyIndex(-1)
                    .image(image)
                    .srcAccessMask(transition.srcAccessMask())
                    .dstAccessMask(transition.dstAccessMask());
            barrier.get(0).subresourceRange()
                    .aspectMask(aspectMask != 0 ? aspectMask : VK_IMAGE_ASPECT_COLOR_BIT)
                    .baseMipLevel(Math.max(0, baseMipLevel))
                    .levelCount(Math.max(1, levelCount))
                    .baseArrayLayer(0)
                    .layerCount(1);
            vkCmdPipelineBarrier(
                    commandBuffer,
                    transition.srcStageMask(),
                    transition.dstStageMask(),
                    0,
                    null,
                    null,
                    barrier);
        }
    }

    private LayoutTransition resolveTransition(int oldLayout, int newLayout) {
        int srcStageMask = VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
        int srcAccessMask = 0;
        if (oldLayout == VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL) {
            srcStageMask = VK_PIPELINE_STAGE_TRANSFER_BIT;
            srcAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT;
        } else if (oldLayout == VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL) {
            srcStageMask = VK_PIPELINE_STAGE_TRANSFER_BIT;
            srcAccessMask = VK_ACCESS_TRANSFER_READ_BIT;
        } else if (oldLayout == VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL) {
            srcStageMask = VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT | VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT;
            srcAccessMask = VK_ACCESS_SHADER_READ_BIT;
        } else if (oldLayout == VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL) {
            srcStageMask = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
            srcAccessMask = VK_ACCESS_COLOR_ATTACHMENT_READ_BIT | VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
        } else if (oldLayout == VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL) {
            srcStageMask = VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT | VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT;
            srcAccessMask = VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT | VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT;
        } else if (oldLayout != VK_IMAGE_LAYOUT_UNDEFINED) {
            srcStageMask = VK_PIPELINE_STAGE_ALL_COMMANDS_BIT;
        }

        int dstStageMask = VK_PIPELINE_STAGE_ALL_COMMANDS_BIT;
        int dstAccessMask = 0;
        if (newLayout == VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL) {
            dstStageMask = VK_PIPELINE_STAGE_TRANSFER_BIT;
            dstAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT;
        } else if (newLayout == VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL) {
            dstStageMask = VK_PIPELINE_STAGE_TRANSFER_BIT;
            dstAccessMask = VK_ACCESS_TRANSFER_READ_BIT;
        } else if (newLayout == VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL) {
            dstStageMask = VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT | VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT;
            dstAccessMask = VK_ACCESS_SHADER_READ_BIT;
        } else if (newLayout == VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL) {
            dstStageMask = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
            dstAccessMask = VK_ACCESS_COLOR_ATTACHMENT_READ_BIT | VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
        } else if (newLayout == VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL) {
            dstStageMask = VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT | VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT;
            dstAccessMask = VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT | VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT;
        }
        return new LayoutTransition(srcStageMask, srcAccessMask, dstStageMask, dstAccessMask);
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

    private record LayoutTransition(
            int srcStageMask,
            int srcAccessMask,
            int dstStageMask,
            int dstAccessMask) {
    }
}

