package rogo.sketch.backend.vulkan;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkAttachmentDescription;
import org.lwjgl.vulkan.VkAttachmentReference;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkExtent2D;
import org.lwjgl.vulkan.VkFramebufferCreateInfo;
import org.lwjgl.vulkan.VkGraphicsPipelineCreateInfo;
import org.lwjgl.vulkan.VkPipelineColorBlendAttachmentState;
import org.lwjgl.vulkan.VkPipelineColorBlendStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineDepthStencilStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineInputAssemblyStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineMultisampleStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineRasterizationStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo;
import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineViewportStateCreateInfo;
import org.lwjgl.vulkan.VkRect2D;
import org.lwjgl.vulkan.VkRenderPassCreateInfo;
import org.lwjgl.vulkan.VkSubpassDependency;
import org.lwjgl.vulkan.VkSubpassDescription;
import org.lwjgl.vulkan.VkVertexInputAttributeDescription;
import org.lwjgl.vulkan.VkVertexInputBindingDescription;
import org.lwjgl.vulkan.VkViewport;
import rogo.sketch.core.data.PrimitiveType;
import rogo.sketch.core.data.format.ComponentSpec;
import rogo.sketch.core.data.format.VertexLayoutSpec;
import rogo.sketch.core.data.layout.FieldSpec;
import rogo.sketch.core.data.type.ValueType;
import rogo.sketch.core.driver.state.BlendFactor;
import rogo.sketch.core.driver.state.BlendOp;
import rogo.sketch.core.driver.state.CompareOp;
import rogo.sketch.core.driver.state.CompiledRasterState;
import rogo.sketch.core.driver.state.DepthState;
import rogo.sketch.core.driver.state.component.BlendState;
import rogo.sketch.core.driver.state.component.CullState;
import rogo.sketch.core.packet.RasterPipelineKey;
import rogo.sketch.core.pipeline.module.diagnostic.SketchDiagnostics;
import rogo.sketch.core.shader.vertex.ActiveShaderVertexLayout;
import rogo.sketch.core.shader.vertex.VertexAttributeSpec;
import rogo.sketch.core.util.KeyId;

import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.lwjgl.vulkan.VK10.*;

final class VulkanRasterPipelineCache {
    private static final String DIAG_MODULE = "vulkan-raster-pipeline-cache";
    private static final int GL_ZERO = 0;
    private static final int GL_ONE = 1;
    private static final int GL_SRC_COLOR = 0x0300;
    private static final int GL_ONE_MINUS_SRC_COLOR = 0x0301;
    private static final int GL_SRC_ALPHA = 0x0302;
    private static final int GL_ONE_MINUS_SRC_ALPHA = 0x0303;
    private static final int GL_DST_ALPHA = 0x0304;
    private static final int GL_ONE_MINUS_DST_ALPHA = 0x0305;
    private static final int GL_DST_COLOR = 0x0306;
    private static final int GL_ONE_MINUS_DST_COLOR = 0x0307;
    private static final int GL_SRC_ALPHA_SATURATE = 0x0308;
    private static final int GL_FRONT = 0x0404;
    private static final int GL_FRONT_AND_BACK = 0x0408;
    private static final int GL_CW = 0x0900;
    private static final int GL_NEVER = 0x0200;
    private static final int GL_LESS = 0x0201;
    private static final int GL_EQUAL = 0x0202;
    private static final int GL_LEQUAL = 0x0203;
    private static final int GL_GREATER = 0x0204;
    private static final int GL_NOTEQUAL = 0x0205;
    private static final int GL_GEQUAL = 0x0206;
    private static final int GL_ALWAYS = 0x0207;

    private final VkDevice device;
    private final VulkanPipelineLayoutCache layoutCache;
    private final VulkanShaderVariantCache shaderVariantCache;
    private long renderPass = VK_NULL_HANDLE;
    private final Map<PipelineVariantKey, PipelineVariant> pipelines = new ConcurrentHashMap<>();
    private long[] framebuffers = new long[0];
    private int extentWidth;
    private int extentHeight;
    private int colorAttachmentCount;
    private int depthAttachmentFormat = VK_FORMAT_UNDEFINED;

    VulkanRasterPipelineCache(VkDevice device, VulkanPipelineLayoutCache layoutCache) {
        this.device = device;
        this.layoutCache = layoutCache;
        this.shaderVariantCache = new VulkanShaderVariantCache(device);
    }

    void recreate(int colorAttachmentFormat, int depthAttachmentFormat, int extentWidth, int extentHeight, long[] imageViews, long[] depthImageViews) {
        destroySwapchainResources();
        this.extentWidth = extentWidth;
        this.extentHeight = extentHeight;
        this.colorAttachmentCount = colorAttachmentFormat != VK_FORMAT_UNDEFINED ? 1 : 0;
        this.depthAttachmentFormat = depthAttachmentFormat;
        renderPass = createRenderPass(device, colorAttachmentFormat, depthAttachmentFormat);
        framebuffers = createFramebuffers(device, renderPass, extentWidth, extentHeight, imageViews, depthImageViews);
    }

    long renderPass() {
        return renderPass;
    }

    long framebuffer(int imageIndex) {
        return framebuffers[imageIndex];
    }

    long pipelineFor(RasterPipelineKey stateKey, KeyId resourceLayoutKey, long descriptorSetLayout) {
        PipelineVariantKey key = new PipelineVariantKey(
                stateKey,
                resourceLayoutKey != null ? resourceLayoutKey : KeyId.of("sketch:empty_resource_layout"));
        PipelineVariant variant = pipelines.computeIfAbsent(key, ignored -> createPipelineVariant(key, descriptorSetLayout));
        return variant != null ? variant.pipeline() : VK_NULL_HANDLE;
    }

    long pipelineLayout(KeyId resourceLayoutKey, long descriptorSetLayout) {
        return layoutCache.layoutFor(resourceLayoutKey, descriptorSetLayout);
    }

    int extentWidth() {
        return extentWidth;
    }

    int extentHeight() {
        return extentHeight;
    }

    int colorAttachmentCount() {
        return colorAttachmentCount;
    }

    boolean hasDepthAttachment() {
        return depthAttachmentFormat != VK_FORMAT_UNDEFINED;
    }

    void destroy() {
        destroySwapchainResources();
        shaderVariantCache.destroy();
    }

    private void destroySwapchainResources() {
        if (framebuffers != null) {
            for (long framebuffer : framebuffers) {
                if (framebuffer != VK_NULL_HANDLE) {
                    vkDestroyFramebuffer(device, framebuffer, null);
                }
            }
        }
        framebuffers = new long[0];

        for (PipelineVariant variant : pipelines.values()) {
            if (variant != null && variant.pipeline() != VK_NULL_HANDLE) {
                vkDestroyPipeline(device, variant.pipeline(), null);
            }
        }
        pipelines.clear();

        if (renderPass != VK_NULL_HANDLE) {
            vkDestroyRenderPass(device, renderPass, null);
            renderPass = VK_NULL_HANDLE;
        }
    }

    private PipelineVariant createPipelineVariant(PipelineVariantKey key, long descriptorSetLayout) {
        if (key == null || key.stateKey() == null) {
            return PipelineVariant.invalid();
        }

        VulkanShaderVariantCache.GraphicsVariantModules graphicsVariant = shaderVariantCache.resolveGraphicsVariant(
                key.stateKey().shaderId(),
                key.stateKey().shaderVariantKey(),
                key.resourceLayoutKey());
        if (graphicsVariant == null || !graphicsVariant.isValid()) {
            return PipelineVariant.invalid();
        }

        VertexInputDescriptions inputDescriptions = buildVertexInputDescriptions(
                key.stateKey(),
                graphicsVariant.spec().activeVertexLayout());
        if (inputDescriptions == null) {
            return PipelineVariant.invalid();
        }

        long pipelineLayout = layoutCache.layoutFor(key.resourceLayoutKey(), descriptorSetLayout);
        long pipeline = createGraphicsPipeline(
                device,
                renderPass,
                pipelineLayout,
                extentWidth,
                extentHeight,
                key.stateKey(),
                graphicsVariant,
                inputDescriptions,
                colorAttachmentCount);
        return new PipelineVariant(pipeline, pipelineLayout);
    }

    private VertexInputDescriptions buildVertexInputDescriptions(
            RasterPipelineKey stateKey,
            ActiveShaderVertexLayout activeVertexLayout) {
        if (stateKey == null || stateKey.renderParameter() == null || stateKey.renderParameter().getLayout() == null) {
            return VertexInputDescriptions.empty();
        }
        if (activeVertexLayout == null || activeVertexLayout.isEmpty()) {
            return VertexInputDescriptions.empty();
        }

        VertexLayoutSpec vertexLayout = stateKey.renderParameter().getLayout();
        Map<Integer, ComponentSpec> bindingsByPoint = new LinkedHashMap<>();
        List<ResolvedAttribute> attributes = new ArrayList<>();
        for (VertexAttributeSpec activeAttribute : activeVertexLayout.getAttributes()) {
            ResolvedAttribute resolvedAttribute = resolveAttribute(vertexLayout, activeAttribute);
            if (resolvedAttribute == null) {
                SketchDiagnostics.get().warn(
                        DIAG_MODULE,
                        "Skipping Vulkan pipeline for shader=" + stateKey.shaderId()
                                + " because active semantic " + activeAttribute.name() + " is unavailable");
                return null;
            }
            bindingsByPoint.putIfAbsent(resolvedAttribute.component().getBindingPoint(), resolvedAttribute.component());
            attributes.add(resolvedAttribute);
        }
        attributes.sort(Comparator.comparingInt(attribute -> attribute.activeAttribute().location()));
        List<ComponentSpec> bindings = new ArrayList<>(bindingsByPoint.values());
        bindings.sort(Comparator.comparingInt(ComponentSpec::getBindingPoint));
        return new VertexInputDescriptions(bindings, attributes);
    }

    private ResolvedAttribute resolveAttribute(VertexLayoutSpec vertexLayout, VertexAttributeSpec activeAttribute) {
        for (ComponentSpec component : vertexLayout.getComponents()) {
            if (component == null || component.getFormat() == null) {
                continue;
            }
            for (FieldSpec element : component.getFormat().getElements()) {
                if (!activeAttribute.name().equals(element.getName())) {
                    continue;
                }
                if (!activeAttribute.type().isCompatibleWith(element.getDataType())) {
                    SketchDiagnostics.get().warn(
                            DIAG_MODULE,
                            "Vulkan active semantic mismatch for " + activeAttribute.name()
                                    + ": shader expects " + activeAttribute.type()
                                    + ", geometry provides " + element.getDataType());
                    return null;
                }
                int vkFormat = mapDataTypeToVkFormat(element.getDataType());
                if (vkFormat == VK_FORMAT_UNDEFINED) {
                    SketchDiagnostics.get().warn(
                            DIAG_MODULE,
                            "Unsupported Vulkan vertex data type " + element.getDataType()
                                    + " for semantic " + activeAttribute.name());
                    return null;
                }
                return new ResolvedAttribute(activeAttribute, component, element, vkFormat);
            }
        }
        return null;
    }

    private static long createRenderPass(VkDevice device, int colorAttachmentFormat, int depthAttachmentFormat) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            boolean hasColor = colorAttachmentFormat != VK_FORMAT_UNDEFINED;
            boolean hasDepth = depthAttachmentFormat != VK_FORMAT_UNDEFINED;
            int attachmentCount = (hasColor ? 1 : 0) + (hasDepth ? 1 : 0);
            VkAttachmentDescription.Buffer attachments = VkAttachmentDescription.calloc(attachmentCount, stack);
            int attachmentIndex = 0;
            if (hasColor) {
                attachments.get(attachmentIndex++)
                        .format(colorAttachmentFormat)
                        .samples(VK_SAMPLE_COUNT_1_BIT)
                        .loadOp(VK_ATTACHMENT_LOAD_OP_LOAD)
                        .storeOp(VK_ATTACHMENT_STORE_OP_STORE)
                        .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                        .stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                        .initialLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
                        .finalLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
            }
            if (hasDepth) {
                attachments.get(attachmentIndex)
                        .format(depthAttachmentFormat)
                        .samples(VK_SAMPLE_COUNT_1_BIT)
                        .loadOp(VK_ATTACHMENT_LOAD_OP_LOAD)
                        .storeOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                        .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                        .stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                        .initialLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL)
                        .finalLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);
            }

            VkAttachmentReference.Buffer colorAttachmentRef = hasColor ? VkAttachmentReference.calloc(1, stack) : null;
            if (hasColor) {
                colorAttachmentRef.get(0).attachment(0).layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
            }
            VkAttachmentReference depthAttachmentRef = hasDepth
                    ? VkAttachmentReference.calloc(stack)
                    .attachment(hasColor ? 1 : 0)
                    .layout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL)
                    : null;

            VkSubpassDescription.Buffer subpasses = VkSubpassDescription.calloc(1, stack);
            subpasses.get(0)
                    .pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS)
                    .colorAttachmentCount(hasColor ? 1 : 0)
                    .pColorAttachments(colorAttachmentRef)
                    .pDepthStencilAttachment(depthAttachmentRef);

            VkSubpassDependency.Buffer dependencies = VkSubpassDependency.calloc(1, stack);
            dependencies.get(0)
                    .srcSubpass(VK_SUBPASS_EXTERNAL)
                    .dstSubpass(0)
                    .srcStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT
                            | VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT
                            | VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT)
                    .srcAccessMask(0)
                    .dstStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT
                            | VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT
                            | VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT)
                    .dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT
                            | VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT
                            | VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT);

            VkRenderPassCreateInfo createInfo = VkRenderPassCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO)
                    .pAttachments(attachments)
                    .pSubpasses(subpasses)
                    .pDependencies(dependencies);

            LongBuffer renderPassPointer = stack.mallocLong(1);
            VulkanDeviceBootstrapper.checkVkResult(
                    vkCreateRenderPass(device, createInfo, null, renderPassPointer),
                    "vkCreateRenderPass(packet)");
            return renderPassPointer.get(0);
        }
    }

    private static long createGraphicsPipeline(
            VkDevice device,
            long renderPass,
            long pipelineLayout,
            int extentWidth,
            int extentHeight,
            RasterPipelineKey stateKey,
            VulkanShaderVariantCache.GraphicsVariantModules graphicsVariant,
            VertexInputDescriptions inputDescriptions,
            int colorAttachmentCount) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPipelineShaderStageCreateInfo.Buffer shaderStages = VkPipelineShaderStageCreateInfo.calloc(2, stack);
            shaderStages.get(0)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                    .stage(VK_SHADER_STAGE_VERTEX_BIT)
                    .module(graphicsVariant.vertexShaderModule())
                    .pName(stack.UTF8("main"));
            shaderStages.get(1)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                    .stage(VK_SHADER_STAGE_FRAGMENT_BIT)
                    .module(graphicsVariant.fragmentShaderModule())
                    .pName(stack.UTF8("main"));

            VkVertexInputBindingDescription.Buffer bindingDescriptions = VkVertexInputBindingDescription.calloc(
                    inputDescriptions.bindings().size(),
                    stack);
            for (int i = 0; i < inputDescriptions.bindings().size(); i++) {
                ComponentSpec component = inputDescriptions.bindings().get(i);
                bindingDescriptions.get(i)
                        .binding(component.getBindingPoint())
                        .stride(component.getFormat().getStride())
                        .inputRate(component.isInstanced() ? VK_VERTEX_INPUT_RATE_INSTANCE : VK_VERTEX_INPUT_RATE_VERTEX);
            }

            VkVertexInputAttributeDescription.Buffer attributeDescriptions = VkVertexInputAttributeDescription.calloc(
                    inputDescriptions.attributes().size(),
                    stack);
            for (int i = 0; i < inputDescriptions.attributes().size(); i++) {
                ResolvedAttribute attribute = inputDescriptions.attributes().get(i);
                attributeDescriptions.get(i)
                        .location(attribute.activeAttribute().location())
                        .binding(attribute.component().getBindingPoint())
                        .format(attribute.vkFormat())
                        .offset(attribute.element().getOffset());
            }

            VkPipelineVertexInputStateCreateInfo vertexInput = VkPipelineVertexInputStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO)
                    .pVertexBindingDescriptions(bindingDescriptions)
                    .pVertexAttributeDescriptions(attributeDescriptions);

            VkPipelineInputAssemblyStateCreateInfo inputAssembly = VkPipelineInputAssemblyStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO)
                    .topology(mapPrimitiveTopology(stateKey.renderParameter() != null ? stateKey.renderParameter().primitiveType() : null))
                    .primitiveRestartEnable(false);

            VkViewport.Buffer viewport = VkViewport.calloc(1, stack);
            viewport.get(0)
                    .x(0.0f)
                    .y(0.0f)
                    .width((float) extentWidth)
                    .height((float) extentHeight)
                    .minDepth(0.0f)
                    .maxDepth(1.0f);

            VkRect2D.Buffer scissor = VkRect2D.calloc(1, stack);
            scissor.get(0)
                    .offset(it -> it.set(0, 0))
                    .extent(VkExtent2D.calloc(stack).set(extentWidth, extentHeight));

            VkPipelineViewportStateCreateInfo viewportState = VkPipelineViewportStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO)
                    .viewportCount(1)
                    .pViewports(viewport)
                    .scissorCount(1)
                    .pScissors(scissor);

            CompiledRasterState renderState = stateKey.compiledRasterState();
            CullState cullState = renderState != null && renderState.pipelineRasterState() != null
                    ? renderState.pipelineRasterState().cullState()
                    : null;
            DepthState depthState = renderState != null && renderState.pipelineRasterState() != null
                    ? renderState.pipelineRasterState().depthState()
                    : null;
            BlendState blendState = renderState != null && renderState.pipelineRasterState() != null
                    ? renderState.pipelineRasterState().blendState()
                    : null;

            VkPipelineRasterizationStateCreateInfo rasterizer = VkPipelineRasterizationStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO)
                    .depthClampEnable(false)
                    .rasterizerDiscardEnable(false)
                    .polygonMode(VK_POLYGON_MODE_FILL)
                    .lineWidth(1.0f)
                    .cullMode(mapCullMode(cullState))
                    .frontFace(mapFrontFace(cullState))
                    .depthBiasEnable(false);

            VkPipelineMultisampleStateCreateInfo multisampling = VkPipelineMultisampleStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO)
                    .rasterizationSamples(VK_SAMPLE_COUNT_1_BIT)
                    .sampleShadingEnable(false);

            VkPipelineDepthStencilStateCreateInfo depthStencil = VkPipelineDepthStencilStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_DEPTH_STENCIL_STATE_CREATE_INFO)
                    .depthTestEnable(depthState != null && depthState.testEnabled())
                    .depthWriteEnable(depthState == null || depthState.writeEnabled())
                    .depthCompareOp(mapDepthCompare(depthState))
                    .depthBoundsTestEnable(false)
                    .stencilTestEnable(false);

            boolean blendEnabled = blendState != null && blendState.enabled();
            boolean hasColorAttachment = colorAttachmentCount > 0;
            VkPipelineColorBlendAttachmentState.Buffer colorBlendAttachment = hasColorAttachment
                    ? VkPipelineColorBlendAttachmentState.calloc(1, stack)
                    : null;
            if (hasColorAttachment) {
                colorBlendAttachment.get(0)
                        .colorWriteMask(VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT | VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT)
                        .blendEnable(blendEnabled)
                        .srcColorBlendFactor(mapBlendFactor(blendEnabled ? blendState.colorSrcFactor() : BlendFactor.ONE))
                        .dstColorBlendFactor(mapBlendFactor(blendEnabled ? blendState.colorDstFactor() : BlendFactor.ZERO))
                        .colorBlendOp(mapBlendOp(blendEnabled ? blendState.colorOp() : BlendOp.ADD))
                        .srcAlphaBlendFactor(mapBlendFactor(blendEnabled ? blendState.alphaSrcFactor() : BlendFactor.ONE))
                        .dstAlphaBlendFactor(mapBlendFactor(blendEnabled ? blendState.alphaDstFactor() : BlendFactor.ZERO))
                        .alphaBlendOp(mapBlendOp(blendEnabled ? blendState.alphaOp() : BlendOp.ADD));
            }

            VkPipelineColorBlendStateCreateInfo colorBlending = VkPipelineColorBlendStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO)
                    .logicOpEnable(false)
                    .logicOp(VK_LOGIC_OP_COPY)
                    .pAttachments(colorBlendAttachment);

            VkGraphicsPipelineCreateInfo.Buffer pipelineInfo = VkGraphicsPipelineCreateInfo.calloc(1, stack);
            pipelineInfo.get(0)
                    .sType(VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO)
                    .pStages(shaderStages)
                    .pVertexInputState(vertexInput)
                    .pInputAssemblyState(inputAssembly)
                    .pViewportState(viewportState)
                    .pRasterizationState(rasterizer)
                    .pMultisampleState(multisampling)
                    .pDepthStencilState(depthStencil)
                    .pColorBlendState(colorBlending)
                    .layout(pipelineLayout)
                    .renderPass(renderPass)
                    .subpass(0);

            LongBuffer graphicsPipelinePointer = stack.mallocLong(1);
            VulkanDeviceBootstrapper.checkVkResult(
                    vkCreateGraphicsPipelines(device, VK_NULL_HANDLE, pipelineInfo, null, graphicsPipelinePointer),
                    "vkCreateGraphicsPipelines(packet)");
            return graphicsPipelinePointer.get(0);
        }
    }

    private static long[] createFramebuffers(
            VkDevice device,
            long renderPass,
            int extentWidth,
            int extentHeight,
            long[] imageViews,
            long[] depthImageViews) {
        boolean hasColor = imageViews != null && imageViews.length > 0;
        boolean hasDepth = depthImageViews != null && depthImageViews.length > 0;
        int framebufferCount = Math.max(hasColor ? imageViews.length : 0, hasDepth ? depthImageViews.length : 0);
        long[] framebuffers = new long[framebufferCount];
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkFramebufferCreateInfo createInfo = VkFramebufferCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO)
                    .renderPass(renderPass)
                    .width(extentWidth)
                    .height(extentHeight)
                    .layers(1);
            for (int i = 0; i < framebufferCount; i++) {
                long colorView = hasColor ? imageViews[Math.min(i, imageViews.length - 1)] : VK_NULL_HANDLE;
                long depthView = hasDepth ? depthImageViews[Math.min(i, depthImageViews.length - 1)] : VK_NULL_HANDLE;
                createInfo.pAttachments(hasColor && hasDepth
                        ? stack.longs(colorView, depthView)
                        : hasColor ? stack.longs(colorView) : stack.longs(depthView));
                LongBuffer framebufferPointer = stack.mallocLong(1);
                VulkanDeviceBootstrapper.checkVkResult(
                        vkCreateFramebuffer(device, createInfo, null, framebufferPointer),
                        "vkCreateFramebuffer(packet)");
                framebuffers[i] = framebufferPointer.get(0);
            }
        }
        return framebuffers;
    }

    private static int mapPrimitiveTopology(PrimitiveType primitiveType) {
        if (primitiveType == null) {
            return VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST;
        }
        switch (primitiveType) {
            case POINTS:
                return VK_PRIMITIVE_TOPOLOGY_POINT_LIST;
            case LINES:
                return VK_PRIMITIVE_TOPOLOGY_LINE_LIST;
            case LINE_STRIP:
            case LINE_LOOP:
                return VK_PRIMITIVE_TOPOLOGY_LINE_STRIP;
            case TRIANGLE_STRIP:
                return VK_PRIMITIVE_TOPOLOGY_TRIANGLE_STRIP;
            case TRIANGLE_FAN:
                return VK_PRIMITIVE_TOPOLOGY_TRIANGLE_FAN;
            case TRIANGLES:
            case QUADS:
            default:
                return VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST;
        }
    }

    private static int mapCullMode(CullState cullState) {
        if (cullState == null || !cullState.enabled()) {
            return VK_CULL_MODE_NONE;
        }
        switch (cullState.face()) {
            case FRONT:
                return VK_CULL_MODE_FRONT_BIT;
            case FRONT_AND_BACK:
                return VK_CULL_MODE_FRONT_AND_BACK;
            default:
                return VK_CULL_MODE_BACK_BIT;
        }
    }

    private static int mapFrontFace(CullState cullState) {
        if (cullState == null) {
            return VK_FRONT_FACE_COUNTER_CLOCKWISE;
        }
        return cullState.frontFace() == rogo.sketch.core.driver.state.FrontFaceMode.CW
                ? VK_FRONT_FACE_CLOCKWISE
                : VK_FRONT_FACE_COUNTER_CLOCKWISE;
    }

    private static int mapDepthCompare(DepthState depthState) {
        if (depthState == null || !depthState.testEnabled()) {
            return VK_COMPARE_OP_ALWAYS;
        }
        return mapCompareOp(depthState.compareOp());
    }

    private static int mapCompareOp(CompareOp compareOp) {
        if (compareOp == null) {
            return VK_COMPARE_OP_LESS;
        }
        return switch (compareOp) {
            case NEVER -> VK_COMPARE_OP_NEVER;
            case LESS -> VK_COMPARE_OP_LESS;
            case EQUAL -> VK_COMPARE_OP_EQUAL;
            case LEQUAL -> VK_COMPARE_OP_LESS_OR_EQUAL;
            case GREATER -> VK_COMPARE_OP_GREATER;
            case NOTEQUAL -> VK_COMPARE_OP_NOT_EQUAL;
            case GEQUAL -> VK_COMPARE_OP_GREATER_OR_EQUAL;
            case ALWAYS -> VK_COMPARE_OP_ALWAYS;
        };
    }

    private static int mapBlendFactor(BlendFactor blendFactor) {
        if (blendFactor == null) {
            return VK_BLEND_FACTOR_ONE;
        }
        return switch (blendFactor) {
            case ZERO -> VK_BLEND_FACTOR_ZERO;
            case ONE -> VK_BLEND_FACTOR_ONE;
            case SRC_COLOR -> VK_BLEND_FACTOR_SRC_COLOR;
            case ONE_MINUS_SRC_COLOR -> VK_BLEND_FACTOR_ONE_MINUS_SRC_COLOR;
            case DST_COLOR -> VK_BLEND_FACTOR_DST_COLOR;
            case ONE_MINUS_DST_COLOR -> VK_BLEND_FACTOR_ONE_MINUS_DST_COLOR;
            case SRC_ALPHA -> VK_BLEND_FACTOR_SRC_ALPHA;
            case ONE_MINUS_SRC_ALPHA -> VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA;
            case DST_ALPHA -> VK_BLEND_FACTOR_DST_ALPHA;
            case ONE_MINUS_DST_ALPHA -> VK_BLEND_FACTOR_ONE_MINUS_DST_ALPHA;
            case SRC_ALPHA_SATURATE -> VK_BLEND_FACTOR_SRC_ALPHA_SATURATE;
        };
    }

    private static int mapBlendOp(BlendOp blendOp) {
        if (blendOp == null) {
            return VK_BLEND_OP_ADD;
        }
        return switch (blendOp) {
            case ADD -> VK_BLEND_OP_ADD;
            case SUBTRACT -> VK_BLEND_OP_SUBTRACT;
            case REVERSE_SUBTRACT -> VK_BLEND_OP_REVERSE_SUBTRACT;
            case MIN -> VK_BLEND_OP_MIN;
            case MAX -> VK_BLEND_OP_MAX;
        };
    }

    private static int mapDataTypeToVkFormat(ValueType dataType) {
        if (dataType == null) {
            return VK_FORMAT_UNDEFINED;
        }
        switch (dataType) {
            case FLOAT:
                return VK_FORMAT_R32_SFLOAT;
            case VEC2F:
                return VK_FORMAT_R32G32_SFLOAT;
            case VEC3F:
                return VK_FORMAT_R32G32B32_SFLOAT;
            case VEC4F:
                return VK_FORMAT_R32G32B32A32_SFLOAT;
            case INT:
                return VK_FORMAT_R32_SINT;
            case VEC2I:
                return VK_FORMAT_R32G32_SINT;
            case VEC3I:
                return VK_FORMAT_R32G32B32_SINT;
            case VEC4I:
                return VK_FORMAT_R32G32B32A32_SINT;
            case UINT:
                return VK_FORMAT_R32_UINT;
            case VEC2UI:
                return VK_FORMAT_R32G32_UINT;
            case VEC3UI:
                return VK_FORMAT_R32G32B32_UINT;
            case VEC4UI:
                return VK_FORMAT_R32G32B32A32_UINT;
            default:
                return VK_FORMAT_UNDEFINED;
        }
    }

    private record PipelineVariantKey(RasterPipelineKey stateKey, KeyId resourceLayoutKey) {
    }

    private record PipelineVariant(long pipeline, long pipelineLayout) {
        static PipelineVariant invalid() {
            return new PipelineVariant(VK_NULL_HANDLE, VK_NULL_HANDLE);
        }
    }

    private record ResolvedAttribute(VertexAttributeSpec activeAttribute, ComponentSpec component, FieldSpec element, int vkFormat) {
    }

    private record VertexInputDescriptions(List<ComponentSpec> bindings, List<ResolvedAttribute> attributes) {
        static VertexInputDescriptions empty() {
            return new VertexInputDescriptions(List.of(), List.of());
        }
    }
}

