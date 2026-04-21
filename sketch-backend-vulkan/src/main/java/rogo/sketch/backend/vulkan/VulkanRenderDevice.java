package rogo.sketch.backend.vulkan;

import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkQueue;
import rogo.sketch.core.backend.BackendCapabilities;
import rogo.sketch.core.backend.BackendCountedIndirectDraw;
import rogo.sketch.core.backend.BackendFrameExecutor;
import rogo.sketch.core.backend.BackendPacketHandlerRegistry;
import rogo.sketch.core.backend.BackendShaderProgramCache;
import rogo.sketch.core.backend.BackendStateApplier;
import rogo.sketch.core.backend.CommandRecorderFactory;
import rogo.sketch.core.backend.RenderDevice;
import rogo.sketch.core.util.KeyId;

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
    private final CommandRecorderFactory commandRecorderFactory;
    private final BackendCountedIndirectDraw countedIndirectDraw;
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
        this.debugUtilsEnabled = debugUtilsEnabled;
        this.commandRecorderFactory = new VulkanCommandRecorderFactory(this);
        this.countedIndirectDraw = new VulkanCountedIndirectDraw();
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

    VulkanFrameSlot[] frameSlots() {
        return frameSlots.clone();
    }

    @Override
    public BackendFrameExecutor frameExecutor() {
        return frameExecutor;
    }

    @Override
    public BackendCountedIndirectDraw countedIndirectDraw() {
        return countedIndirectDraw;
    }

    @Override
    public VulkanResourceResolver resourceResolver() {
        return resourceResolver;
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
    public CommandRecorderFactory commandRecorderFactory() {
        return commandRecorderFactory;
    }

    void registerTextureResource(KeyId resourceId, VulkanTextureResource textureResource) {
        resourceResolver.registerTexture(resourceId, textureResource);
    }
}
