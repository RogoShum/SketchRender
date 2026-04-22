package rogo.sketch.backend.vulkan;

import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkQueue;
import org.lwjgl.vulkan.VkSubmitInfo;
import rogo.sketch.core.backend.BackendCapabilities;
import rogo.sketch.core.backend.BackendFrameExecutor;
import rogo.sketch.core.backend.BackendPacketHandlerRegistry;
import rogo.sketch.core.backend.BackendResourceRegistry;
import rogo.sketch.core.backend.BackendShaderProgramCache;
import rogo.sketch.core.backend.BackendStateApplier;
import rogo.sketch.core.backend.CommandEncoderFactory;
import rogo.sketch.core.backend.IndirectDrawService;
import rogo.sketch.core.backend.RenderDevice;
import rogo.sketch.core.backend.AsyncGpuCompletion;
import rogo.sketch.core.packet.RenderPacket;
import rogo.sketch.core.pipeline.GraphicsPipeline;
import rogo.sketch.core.pipeline.PipelineType;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.flow.RenderFlowType;
import rogo.sketch.core.pipeline.flow.RenderPostProcessors;
import rogo.sketch.core.pipeline.flow.impl.RasterizationPostProcessor;
import rogo.sketch.core.util.KeyId;

import java.util.List;
import java.util.function.LongSupplier;

final class VulkanRenderDevice implements RenderDevice {
    private final VkPhysicalDevice physicalDevice;
    private final VkDevice device;
    private final BackendCapabilities capabilities;
    private final int graphicsQueueFamilyIndex;
    private final int presentQueueFamilyIndex;
    private final VkQueue graphicsQueue;
    private final VkQueue presentQueue;
    private final long commandPool;
    private final VulkanFrameSlot[] frameSlots;
    private final BackendFrameExecutor frameExecutor;
    private final VulkanResourceResolver resourceResolver;
    private final VulkanPacketExecutor packetExecutor;
    private final VulkanPipelineLayoutCache pipelineLayoutCache;
    private final VulkanComputePipelineCache computePipelineCache;
    private final CommandEncoderFactory commandEncoderFactory;
    private final IndirectDrawService indirectDrawService;
    private final VulkanResourceAllocator resourceAllocator;
    private final LongSupplier frameEpochSupplier;
    private final VulkanBackendRuntime runtime;
    private VulkanRasterPipelineCache rasterPipelineCache;
    private final boolean debugUtilsEnabled;

    VulkanRenderDevice(
            VkPhysicalDevice physicalDevice,
            VkDevice device,
            BackendCapabilities capabilities,
            int graphicsQueueFamilyIndex,
            int presentQueueFamilyIndex,
            VkQueue graphicsQueue,
            VkQueue presentQueue,
            long commandPool,
            VulkanFrameSlot[] frameSlots,
            BackendFrameExecutor frameExecutor,
            VulkanResourceResolver resourceResolver,
            VulkanPacketExecutor packetExecutor,
            VulkanPipelineLayoutCache pipelineLayoutCache,
            VulkanComputePipelineCache computePipelineCache,
            VulkanRasterPipelineCache rasterPipelineCache,
            VulkanResourceAllocator resourceAllocator,
            LongSupplier frameEpochSupplier,
            VulkanBackendRuntime runtime,
            boolean debugUtilsEnabled) {
        this.physicalDevice = physicalDevice;
        this.device = device;
        this.capabilities = capabilities;
        this.graphicsQueueFamilyIndex = graphicsQueueFamilyIndex;
        this.presentQueueFamilyIndex = presentQueueFamilyIndex;
        this.graphicsQueue = graphicsQueue;
        this.presentQueue = presentQueue;
        this.commandPool = commandPool;
        this.frameSlots = frameSlots != null ? frameSlots.clone() : new VulkanFrameSlot[0];
        this.frameExecutor = frameExecutor;
        this.resourceResolver = resourceResolver;
        this.packetExecutor = packetExecutor;
        this.pipelineLayoutCache = pipelineLayoutCache;
        this.computePipelineCache = computePipelineCache;
        this.rasterPipelineCache = rasterPipelineCache;
        this.resourceAllocator = resourceAllocator;
        this.frameEpochSupplier = frameEpochSupplier;
        this.runtime = runtime;
        this.debugUtilsEnabled = debugUtilsEnabled;
        this.commandEncoderFactory = new VulkanCommandEncoderFactory(this);
        this.indirectDrawService = new VulkanCountedIndirectDraw();
    }

    @Override
    public BackendCapabilities capabilities() {
        return capabilities;
    }

    VkPhysicalDevice physicalDevice() {
        return physicalDevice;
    }

    VkDevice device() {
        return device;
    }

    int graphicsQueueFamilyIndex() {
        return graphicsQueueFamilyIndex;
    }

    int presentQueueFamilyIndex() {
        return presentQueueFamilyIndex;
    }

    VkQueue graphicsQueue() {
        return graphicsQueue;
    }

    VkQueue presentQueue() {
        return presentQueue;
    }

    long commandPoolHandle() {
        return commandPool;
    }

    void submitGraphicsAndWait(VkSubmitInfo submitInfo, String operation) {
        runtime.submitGraphicsAndWait(submitInfo, operation);
    }

    VulkanFrameSlot[] frameSlots() {
        return frameSlots.clone();
    }

    @Override
    public BackendFrameExecutor frameExecutor() {
        return frameExecutor;
    }

    @Override
    public boolean supportsGeometryMaterialization() {
        return true;
    }

    @Override
    public <C extends RenderContext> boolean installImmediateGeometryBindings(
            GraphicsPipeline<C> pipeline,
            PipelineType pipelineType,
            RenderPostProcessors postProcessors) {
        if (postProcessors == null) {
            return false;
        }
        RasterizationPostProcessor rasterizationPostProcessor = postProcessors.get(RenderFlowType.RASTERIZATION);
        if (rasterizationPostProcessor == null) {
            return false;
        }
        resourceAllocator.geometryArena().install(
                rasterizationPostProcessor.geometryUploadPlans(),
                frameEpochSupplier.getAsLong(),
                2);
        return true;
    }

    @Override
    public <C extends RenderContext> AsyncGpuCompletion submitAsyncPackets(
            GraphicsPipeline<C> pipeline,
            List<RenderPacket> packets,
            C context) {
        return runtime.submitAsyncPackets(pipeline, packets, context);
    }

    @Override
    public IndirectDrawService indirectDrawService() {
        return indirectDrawService;
    }

    @Override
    public BackendResourceRegistry resourceRegistry() {
        return resourceAllocator;
    }

    @Override
    public BackendShaderProgramCache shaderProgramCache() {
        return BackendShaderProgramCache.NO_OP;
    }

    @Override
    public BackendStateApplier stateApplier() {
        return BackendStateApplier.NO_OP;
    }

    VulkanPacketExecutor packetExecutor() {
        return packetExecutor;
    }

    BackendPacketHandlerRegistry<VulkanPacketHandler> packetHandlerRegistry() {
        return packetExecutor.packetHandlerRegistry();
    }

    VulkanPipelineLayoutCache pipelineLayoutCache() {
        return pipelineLayoutCache;
    }

    VulkanComputePipelineCache computePipelineCache() {
        return computePipelineCache;
    }

    VulkanRasterPipelineCache rasterPipelineCache() {
        return rasterPipelineCache;
    }

    void setRasterPipelineCache(VulkanRasterPipelineCache rasterPipelineCache) {
        this.rasterPipelineCache = rasterPipelineCache;
    }

    boolean debugUtilsEnabled() {
        return debugUtilsEnabled;
    }

    @Override
    public CommandEncoderFactory commandEncoderFactory() {
        return commandEncoderFactory;
    }

    void registerTextureResource(KeyId resourceId, VulkanTextureResource textureResource) {
        resourceResolver.registerTexture(resourceId, textureResource);
    }
}
