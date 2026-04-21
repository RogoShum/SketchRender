package rogo.sketch.backend.vulkan;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkImageBlit;
import org.lwjgl.vulkan.VkImageMemoryBarrier;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkCommandBufferAllocateInfo;
import org.lwjgl.vulkan.VkCommandBufferBeginInfo;
import org.lwjgl.vulkan.VkCommandPoolCreateInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkFenceCreateInfo;
import org.lwjgl.vulkan.VkImageViewCreateInfo;
import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkPresentInfoKHR;
import org.lwjgl.vulkan.VkQueue;
import org.lwjgl.vulkan.VkSemaphoreCreateInfo;
import org.lwjgl.vulkan.VkSubmitInfo;
import rogo.sketch.core.backend.BackendCapabilities;
import rogo.sketch.core.backend.BackendFrameExecutor;
import rogo.sketch.core.backend.BackendKind;
import rogo.sketch.core.backend.BackendPacketHandlerRegistry;
import rogo.sketch.core.backend.BackendResourceInstaller;
import rogo.sketch.core.backend.BackendResourceResolver;
import rogo.sketch.core.backend.BackendRuntime;
import rogo.sketch.core.backend.BackendStageScope;
import rogo.sketch.core.backend.AsyncGpuCompletion;
import rogo.sketch.core.backend.CommandRecorderFactory;
import rogo.sketch.core.backend.RenderDevice;
import rogo.sketch.core.backend.ResourceAllocator;
import rogo.sketch.core.backend.SubmissionScheduler;
import rogo.sketch.core.driver.state.snapshot.SnapshotScope;
import rogo.sketch.core.packet.ExecutionKey;
import rogo.sketch.core.packet.DrawPacket;
import rogo.sketch.core.packet.GeometryHandleKey;
import rogo.sketch.core.packet.RenderPacket;
import rogo.sketch.core.packet.RenderPacketQueue;
import rogo.sketch.core.packet.ResourceBindingPlan;
import rogo.sketch.core.packet.ResourceSetKey;
import rogo.sketch.core.pipeline.GraphicsPipeline;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.PipelineType;
import rogo.sketch.core.pipeline.RenderStateManager;
import rogo.sketch.core.pipeline.TargetBinding;
import rogo.sketch.core.pipeline.flow.RenderFlowType;
import rogo.sketch.core.pipeline.flow.RenderPostProcessors;
import rogo.sketch.core.pipeline.flow.impl.RasterizationPostProcessor;
import rogo.sketch.core.pipeline.graph.scheduler.SimpleProfiler;
import rogo.sketch.core.pipeline.kernel.FrameExecutionPlan;
import rogo.sketch.core.pipeline.kernel.PipelineExecutionSlice;
import rogo.sketch.core.pipeline.kernel.StageExecutionPlan;
import rogo.sketch.core.pipeline.module.diagnostic.SketchDiagnostics;
import rogo.sketch.core.resource.ResourceTypes;
import rogo.sketch.core.util.KeyId;

import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

import static org.lwjgl.vulkan.KHRSurface.vkDestroySurfaceKHR;
import static org.lwjgl.vulkan.KHRSwapchain.VK_ERROR_OUT_OF_DATE_KHR;
import static org.lwjgl.vulkan.KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
import static org.lwjgl.vulkan.KHRSwapchain.VK_STRUCTURE_TYPE_PRESENT_INFO_KHR;
import static org.lwjgl.vulkan.KHRSwapchain.VK_SUBOPTIMAL_KHR;
import static org.lwjgl.vulkan.KHRSwapchain.vkAcquireNextImageKHR;
import static org.lwjgl.vulkan.KHRSwapchain.vkDestroySwapchainKHR;
import static org.lwjgl.vulkan.KHRSwapchain.vkQueuePresentKHR;
import static org.lwjgl.vulkan.VK10.VK_COMMAND_BUFFER_LEVEL_PRIMARY;
import static org.lwjgl.vulkan.VK10.VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
import static org.lwjgl.vulkan.VK10.VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_FENCE_CREATE_SIGNALED_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_COLOR_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_VIEW_TYPE_2D;
import static org.lwjgl.vulkan.VK10.VK_NOT_READY;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_FENCE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_SUBMIT_INFO;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;
import static org.lwjgl.vulkan.VK10.vkAllocateCommandBuffers;
import static org.lwjgl.vulkan.VK10.vkBeginCommandBuffer;
import static org.lwjgl.vulkan.VK10.vkCreateCommandPool;
import static org.lwjgl.vulkan.VK10.vkCreateFence;
import static org.lwjgl.vulkan.VK10.vkCreateImageView;
import static org.lwjgl.vulkan.VK10.vkCreateSemaphore;
import static org.lwjgl.vulkan.VK10.vkCmdBlitImage;
import static org.lwjgl.vulkan.VK10.vkCmdPipelineBarrier;
import static org.lwjgl.vulkan.VK10.vkDestroyCommandPool;
import static org.lwjgl.vulkan.VK10.vkDestroyDevice;
import static org.lwjgl.vulkan.VK10.vkDestroyFence;
import static org.lwjgl.vulkan.VK10.vkDestroyImageView;
import static org.lwjgl.vulkan.VK10.vkDestroyInstance;
import static org.lwjgl.vulkan.VK10.vkDestroySemaphore;
import static org.lwjgl.vulkan.VK10.vkDeviceWaitIdle;
import static org.lwjgl.vulkan.VK10.vkEndCommandBuffer;
import static org.lwjgl.vulkan.VK10.vkFreeCommandBuffers;
import static org.lwjgl.vulkan.VK10.vkGetFenceStatus;
import static org.lwjgl.vulkan.VK10.vkQueueSubmit;
import static org.lwjgl.vulkan.VK10.vkResetCommandBuffer;
import static org.lwjgl.vulkan.VK10.vkResetFences;
import static org.lwjgl.vulkan.VK10.vkWaitForFences;

public final class VulkanBackendRuntime implements BackendRuntime {
    private static final int MAX_FRAMES_IN_FLIGHT = 2;
    private static final String DIAG_MODULE = "vulkan-runtime";

    private final String entryPoint;
    private final long windowHandle;
    private final VkInstance instance;
    private final VkPhysicalDevice physicalDevice;
    private final String physicalDeviceName;
    private final long surfaceHandle;
    private final VkDevice device;
    private final int graphicsQueueFamilyIndex;
    private final int presentQueueFamilyIndex;
    private final VkQueue graphicsQueue;
    private final VkQueue presentQueue;
    private long swapchainHandle;
    private int swapchainImageFormat;
    private int swapchainExtentWidth;
    private int swapchainExtentHeight;
    private long[] swapchainImages;
    private long[] swapchainImageViews;
    private VulkanTextureResource[] mainColorAttachments;
    private VulkanTextureResource[] swapchainDepthAttachments;
    private final BackendFrameExecutor frameExecutor = new VulkanFrameExecutor();
    private final VulkanPipelineLayoutCache pipelineLayoutCache;
    private final VulkanResourceResolver resourceResolver;
    private final VulkanResourceAllocator resourceAllocator;
    private final BackendResourceInstaller resourceInstaller;
    private final VulkanPacketExecutor packetExecutor;
    private VulkanRasterPipelineCache rasterPipelineCache;
    private final VulkanComputePipelineCache computePipelineCache;
    private final long commandPool;
    private final long asyncCommandPool;
    private final VulkanFrameSlot[] frameSlots;
    private final VulkanRenderDevice renderDevice;
    private final VulkanSubmissionScheduler submissionScheduler;
    private volatile long mainThreadId;
    private volatile boolean shutdown;
    private volatile boolean vSyncEnabled;
    private volatile int lastDrawImageIndex = -1;
    private long renderedFrameCount;
    private long swapchainGeneration;
    private long swapchainRecreationCount;
    private final boolean debugUtilsEnabled;
    private final boolean splitSubmitDiagnosticEnabled = Boolean.getBoolean("sketch.vulkan.split_submit_diagnostic");
    private int postResizeValidationFramesRemaining;

    public VulkanBackendRuntime(
            String entryPoint,
            long windowHandle,
            VkInstance instance,
            VkPhysicalDevice physicalDevice,
            String physicalDeviceName,
            long surfaceHandle,
            VkDevice device,
            int graphicsQueueFamilyIndex,
            int presentQueueFamilyIndex,
            VkQueue graphicsQueue,
            VkQueue presentQueue,
            long swapchainHandle,
            int swapchainImageFormat,
            int swapchainExtentWidth,
            int swapchainExtentHeight,
            long[] swapchainImages,
            boolean vSyncEnabled,
            boolean debugUtilsEnabled) {
        this.entryPoint = Objects.requireNonNull(entryPoint, "entryPoint");
        this.windowHandle = windowHandle;
        this.instance = Objects.requireNonNull(instance, "instance");
        this.physicalDevice = Objects.requireNonNull(physicalDevice, "physicalDevice");
        this.physicalDeviceName = Objects.requireNonNull(physicalDeviceName, "physicalDeviceName");
        this.surfaceHandle = surfaceHandle;
        this.device = Objects.requireNonNull(device, "device");
        this.graphicsQueueFamilyIndex = graphicsQueueFamilyIndex;
        this.presentQueueFamilyIndex = presentQueueFamilyIndex;
        this.graphicsQueue = Objects.requireNonNull(graphicsQueue, "graphicsQueue");
        this.presentQueue = Objects.requireNonNull(presentQueue, "presentQueue");
        this.pipelineLayoutCache = new VulkanPipelineLayoutCache(this.device);
        this.resourceResolver = new VulkanResourceResolver();
        this.resourceAllocator = new VulkanResourceAllocator(this, this.resourceResolver);
        this.resourceInstaller = new VulkanBackendResourceInstaller(this.resourceAllocator);
        this.packetExecutor = new VulkanPacketExecutor(
                this.device,
                this.pipelineLayoutCache,
                this.resourceAllocator.descriptorArena(),
                this.resourceAllocator.geometryArena(),
                this.resourceResolver,
                debugUtilsEnabled);
        this.debugUtilsEnabled = debugUtilsEnabled;
        this.computePipelineCache = new VulkanComputePipelineCache(this.device, this.pipelineLayoutCache);
        this.mainThreadId = Thread.currentThread().getId();
        this.vSyncEnabled = vSyncEnabled;

        long[] createdImageViews = null;
        VulkanTextureResource[] createdDepthAttachments = null;
        long createdCommandPool = VK_NULL_HANDLE;
        long createdAsyncCommandPool = VK_NULL_HANDLE;
        VulkanFrameSlot[] createdFrameSlots = null;

        try {
            this.swapchainHandle = swapchainHandle;
            this.swapchainImageFormat = swapchainImageFormat;
            this.swapchainExtentWidth = swapchainExtentWidth;
            this.swapchainExtentHeight = swapchainExtentHeight;
            this.swapchainImages = swapchainImages != null ? swapchainImages.clone() : new long[0];
            createdImageViews = createSwapchainImageViews(this.swapchainImages, this.swapchainImageFormat);
            this.mainColorAttachments = createMainColorAttachments(
                    this.swapchainExtentWidth,
                    this.swapchainExtentHeight,
                    this.swapchainImages.length,
                    this.swapchainImageFormat);
            createdDepthAttachments = createSwapchainDepthAttachments(
                    this.swapchainExtentWidth,
                    this.swapchainExtentHeight,
                    this.swapchainImages.length);
            this.rasterPipelineCache = new VulkanRasterPipelineCache(this.device, this.pipelineLayoutCache);
            this.rasterPipelineCache.recreate(
                    this.swapchainImageFormat,
                    resolveSwapchainDepthFormat(createdDepthAttachments),
                    this.swapchainExtentWidth,
                    this.swapchainExtentHeight,
                    toImageViews(this.mainColorAttachments),
                    toImageViews(createdDepthAttachments));
            createdCommandPool = createCommandPool();
            createdAsyncCommandPool = createCommandPool();
            createdFrameSlots = createFrameSlots(createdCommandPool);
        } catch (RuntimeException ex) {
            destroyCreatedResources(createdFrameSlots, createdCommandPool, createdAsyncCommandPool, createdImageViews);
            destroyDepthAttachments(createdDepthAttachments);
            resourceAllocator.shutdown();
            destroyBaseArtifacts();
            throw ex;
        }

        this.swapchainImageViews = createdImageViews;
        this.swapchainDepthAttachments = createdDepthAttachments != null ? createdDepthAttachments : new VulkanTextureResource[0];
        this.commandPool = createdCommandPool;
        this.asyncCommandPool = createdAsyncCommandPool;
        this.frameSlots = createdFrameSlots;
        this.renderDevice = new VulkanRenderDevice(
                this.physicalDevice,
                this.device,
                new BackendCapabilities(true, true, true, true, false),
                this.graphicsQueueFamilyIndex,
                this.presentQueueFamilyIndex,
                this.graphicsQueue,
                this.presentQueue,
                this.commandPool,
                this.frameSlots,
                this.frameExecutor,
                this.resourceResolver,
                this.packetExecutor,
                this.pipelineLayoutCache,
                this.computePipelineCache,
                this.rasterPipelineCache,
                this.debugUtilsEnabled);
        this.submissionScheduler = new VulkanSubmissionScheduler(this, this.swapchainImages.length);
        this.renderedFrameCount = 0L;
        this.swapchainGeneration = 1L;
        this.swapchainRecreationCount = 0L;
    }

    @Override
    public String backendName() {
        return "vulkan-lwjgl-frame-loop";
    }

    @Override
    public BackendKind kind() {
        return BackendKind.VULKAN;
    }

    @Override
    public RenderDevice renderDevice() {
        return renderDevice;
    }

    @Override
    public ResourceAllocator resourceAllocator() {
        return resourceAllocator;
    }

    @Override
    public SubmissionScheduler submissionScheduler() {
        return submissionScheduler;
    }

    @Override
    public BackendCapabilities capabilities() {
        return renderDevice.capabilities();
    }

    @Override
    public BackendFrameExecutor frameExecutor() {
        return renderDevice.frameExecutor();
    }

    @Override
    public BackendResourceResolver resourceResolver() {
        return renderDevice.resourceResolver();
    }

    @Override
    public BackendResourceInstaller resourceInstaller() {
        return resourceInstaller;
    }

    @Override
    public CommandRecorderFactory commandRecorderFactory() {
        return renderDevice.commandRecorderFactory();
    }

    @Override
    public boolean supportsGeometryMaterialization() {
        return true;
    }

    @Override
    public <C extends RenderContext> boolean installGeometryUploads(
            GraphicsPipeline<C> pipeline,
            FrameExecutionPlan executionPlan,
            boolean uploadGeometryData) {
        FrameExecutionPlan nextExecutionPlan = executionPlan != null ? executionPlan : FrameExecutionPlan.empty();
        resourceAllocator.installExecutionPlan(nextExecutionPlan, renderedFrameCount, submissionScheduler.framesInFlight());
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
                renderedFrameCount,
                submissionScheduler.framesInFlight());
        return true;
    }

    @Override
    public void installImmediateResourceBindings(List<RenderPacket> packets) {
        if (packets == null || packets.isEmpty()) {
            return;
        }
        LinkedHashMap<ResourceSetKey, FrameExecutionPlan.ResourceUploadPlan> uploadPlans = new LinkedHashMap<>();
        for (RenderPacket packet : packets) {
            if (packet == null) {
                continue;
            }
            ResourceBindingPlan bindingPlan = packet.bindingPlan();
            if (bindingPlan == null || bindingPlan.isEmpty()) {
                continue;
            }
            ResourceSetKey resourceSetKey = packet.resourceSetKey();
            if (resourceSetKey == null || resourceSetKey.isEmpty()) {
                resourceSetKey = ResourceSetKey.from(bindingPlan, packet.uniformGroups().resourceUniforms());
            }
            ExecutionKey stateKey = packet.stateKey();
            FrameExecutionPlan.ResourceUploadPlan uploadPlan = new FrameExecutionPlan.ResourceUploadPlan(
                    packet.stageId(),
                    resourceSetKey,
                    bindingPlan,
                    packet.uniformGroups(),
                    stateKey != null ? stateKey.shaderId() : KeyId.of("sketch:unbound_shader"),
                    bindingPlan.layoutKey());
            uploadPlans.put(resourceSetKey, uploadPlan);
        }
        if (!uploadPlans.isEmpty()) {
            resourceAllocator.descriptorArena().install(List.copyOf(uploadPlans.values()));
        }
    }

    @Override
    public void registerMainThread() {
        mainThreadId = Thread.currentThread().getId();
    }

    @Override
    public boolean isMainThread() {
        return Thread.currentThread().getId() == mainThreadId;
    }

    @Override
    public void assertMainThread(String caller) {
        if (!isMainThread()) {
            throw new IllegalStateException("Expected main thread in " + caller + " for Vulkan backend");
        }
    }

    @Override
    public void assertRenderContext(String caller) {
        assertMainThread(caller);
    }

    public void markFramebufferResized() {
        submissionScheduler.markFramebufferResized();
    }

    public synchronized void setVSyncEnabled(boolean enabled) {
        if (this.vSyncEnabled == enabled) {
            return;
        }
        this.vSyncEnabled = enabled;
        recreateSwapchain();
    }

    @Override
    public synchronized void installExecutionPlan(FrameExecutionPlan executionPlan) {
        FrameExecutionPlan nextExecutionPlan = executionPlan != null ? executionPlan : FrameExecutionPlan.empty();
        resourceAllocator.installExecutionPlan(nextExecutionPlan, renderedFrameCount, submissionScheduler.framesInFlight());
        submissionScheduler.installExecutionPlan(nextExecutionPlan);
    }

    public synchronized void registerInterleavedColorGeometry(GeometryHandleKey geometryHandle, float[] vertexData, int vertexCount) {
        resourceAllocator.geometryArena().registerInterleavedColorGeometry(geometryHandle, vertexData, vertexCount);
    }

    public void registerTextureResource(KeyId resourceId, VulkanTextureResource textureResource) {
        renderDevice.registerTextureResource(resourceId, textureResource);
    }

    public void registerUniformBufferResource(KeyId resourceId, VulkanUniformBufferResource uniformBufferResource) {
        resourceResolver.registerUniformBuffer(resourceId, uniformBufferResource);
    }

    void registerStorageBufferResource(KeyId resourceId, VulkanStorageBufferResource storageBufferResource) {
        resourceResolver.registerStorageBuffer(resourceId, storageBufferResource);
    }

    void registerCounterBufferResource(KeyId resourceId, VulkanCounterBufferResource counterBufferResource) {
        resourceResolver.registerCounterBuffer(resourceId, counterBufferResource);
    }

    VulkanResourceResolver resourceResolverInternal() {
        return resourceResolver;
    }

    public VulkanTextureResource createPlaceholderTextureResource(int width, int height) {
        return VulkanTextureFactory.createPlaceholderTexture(physicalDevice, device, width, height);
    }

    public VulkanTextureResource createPlaceholderTextureResource(int width, int height, int mipLevels) {
        return VulkanTextureFactory.createPlaceholderTexture(physicalDevice, device, width, height, mipLevels);
    }

    public VulkanTextureResource createRenderTargetColorTextureResource(int width, int height, int mipLevels) {
        return VulkanTextureFactory.createRenderTargetColorTexture(physicalDevice, device, width, height, mipLevels);
    }

    public VulkanTextureResource createDepthTextureResource(int width, int height) {
        return VulkanTextureFactory.createDepthTexture(physicalDevice, device, width, height);
    }

    public VulkanTextureResource createSampledDepthTextureResource(int width, int height) {
        return VulkanTextureFactory.createSampledDepthTexture(physicalDevice, device, width, height);
    }

    public VulkanTextureResource createStorageTextureResource(int width, int height) {
        return VulkanTextureFactory.createSampledStorageTexture(physicalDevice, device, width, height);
    }

    public VulkanTextureResource createSampledFloatTextureResource(int width, int height) {
        return VulkanTextureFactory.createSampledFloatTexture(physicalDevice, device, width, height);
    }

    public VulkanTextureResource createHiZStorageTextureResource(int width, int height) {
        return VulkanTextureFactory.createSampledStorageTexture(
                physicalDevice,
                device,
                width,
                height,
                1,
                org.lwjgl.vulkan.VK10.VK_FORMAT_R32_SFLOAT);
    }

    public boolean debugUtilsEnabled() {
        return renderDevice.debugUtilsEnabled();
    }

    public BackendPacketHandlerRegistry<VulkanPacketHandler> packetHandlerRegistry() {
        return renderDevice.packetHandlerRegistry();
    }

    public VulkanUniformBufferResource createUniformBufferResource(long size) {
        return new VulkanUniformBufferResource(physicalDevice, device, size);
    }

    rogo.sketch.core.backend.BackendStorageBuffer createStorageBufferResource(
            KeyId resourceId,
            rogo.sketch.core.resource.descriptor.ResolvedBufferResource descriptor,
            java.nio.ByteBuffer initialData) {
        return new VulkanStorageBufferResource(resourceId, physicalDevice, device, descriptor, initialData);
    }

    public VulkanUniformBufferResource createUniformBufferResource(byte[] initialBytes) {
        byte[] bytes = initialBytes != null ? initialBytes.clone() : new byte[0];
        VulkanUniformBufferResource resource = createUniformBufferResource(Math.max(bytes.length, 16));
        resource.update(bytes);
        return resource;
    }

    public boolean drawFrame() {
        return submissionScheduler.drawFrame();
    }

    public synchronized void waitForDeviceIdle() {
        if (!shutdown) {
            vkDeviceWaitIdle(device);
        }
    }

    public synchronized VulkanTextureResource currentPresentedDepthAttachment() {
        if (swapchainDepthAttachments.length == 0) {
            return null;
        }
        if (lastDrawImageIndex < 0 || lastDrawImageIndex >= swapchainDepthAttachments.length) {
            return swapchainDepthAttachments[0];
        }
        return swapchainDepthAttachments[lastDrawImageIndex];
    }

    public synchronized VulkanTextureResource currentPresentedColorAttachment() {
        if (mainColorAttachments.length == 0) {
            return null;
        }
        if (lastDrawImageIndex < 0 || lastDrawImageIndex >= mainColorAttachments.length) {
            return mainColorAttachments[0];
        }
        return mainColorAttachments[lastDrawImageIndex];
    }

    @Override
    public synchronized void shutdown() {
        if (shutdown) {
            return;
        }
        shutdown = true;

        vkDeviceWaitIdle(device);
        submissionScheduler.shutdown();
        packetExecutor.destroy();
        resourceAllocator.shutdown();
        destroyCreatedResources(frameSlots, commandPool, asyncCommandPool, swapchainImageViews);
        destroyBaseArtifacts();
    }

    @Override
    public synchronized <C extends RenderContext> AsyncGpuCompletion submitAsyncPackets(
            GraphicsPipeline<C> pipeline,
            List<RenderPacket> packets,
            C context) {
        if (packets == null || packets.isEmpty()) {
            return AsyncGpuCompletion.completed();
        }
        String threadName = Thread.currentThread().getName();
        beginProfile("VkAsyncSubmit:InstallBindings", threadName);
        installImmediateResourceBindings(packets);
        endProfile("VkAsyncSubmit:InstallBindings", threadName);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            beginProfile("VkAsyncSubmit:AllocateCommandBuffer", threadName);
            VkCommandBufferAllocateInfo allocateInfo = VkCommandBufferAllocateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
                    .commandPool(asyncCommandPool)
                    .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                    .commandBufferCount(1);

            PointerBuffer commandBufferPointer = stack.mallocPointer(1);
            VulkanDeviceBootstrapper.checkVkResult(
                    vkAllocateCommandBuffers(device, allocateInfo, commandBufferPointer),
                    "vkAllocateCommandBuffers(async)");
            VkCommandBuffer commandBuffer = new VkCommandBuffer(commandBufferPointer.get(0), device);
            endProfile("VkAsyncSubmit:AllocateCommandBuffer", threadName);

            beginProfile("VkAsyncSubmit:Record", threadName);
            VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
                    .flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);
            VulkanDeviceBootstrapper.checkVkResult(
                    vkBeginCommandBuffer(commandBuffer, beginInfo),
                    "vkBeginCommandBuffer(async)");
            packetExecutor.record(
                    commandBuffer,
                    rasterPipelineCache,
                    computePipelineCache,
                    FrameExecutionPlan.empty(),
                    packets,
                    0);
            VulkanDeviceBootstrapper.checkVkResult(
                    vkEndCommandBuffer(commandBuffer),
                    "vkEndCommandBuffer(async)");
            endProfile("VkAsyncSubmit:Record", threadName);

            LongBuffer fencePointer = stack.mallocLong(1);
            VkFenceCreateInfo fenceInfo = VkFenceCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_FENCE_CREATE_INFO);
            VulkanDeviceBootstrapper.checkVkResult(
                    vkCreateFence(device, fenceInfo, null, fencePointer),
                    "vkCreateFence(async)");
            long fence = fencePointer.get(0);
            try {
                beginProfile("VkAsyncSubmit:QueueSubmit", threadName);
                VkSubmitInfo submitInfo = VkSubmitInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
                        .pCommandBuffers(stack.pointers(commandBuffer.address()));
                int submitResult = vkQueueSubmit(graphicsQueue, submitInfo, fence);
                if (submitResult != VK_SUCCESS) {
                    throw new IllegalStateException(vkFailureMessage(
                            "vkQueueSubmit(async)",
                            submitResult,
                            "packets=" + summarizePackets(packets)
                                    + ", swapchainGeneration=" + swapchainGeneration
                                    + ", swapchainExtent=" + swapchainExtentWidth + "x" + swapchainExtentHeight
                                    + ", renderedFrameCount=" + renderedFrameCount
                                    + ", lastDrawImageIndex=" + lastDrawImageIndex
                                    + ", thread=" + threadName));
                }
                endProfile("VkAsyncSubmit:QueueSubmit", threadName);
                return new VulkanAsyncFenceCompletion(this, asyncCommandPool, commandBuffer.address(), fence);
            } catch (RuntimeException runtimeException) {
                endProfile("VkAsyncSubmit:QueueSubmit", threadName);
                releaseSubmittedCommand(asyncCommandPool, commandBuffer.address(), fence);
                throw runtimeException;
            }
        }
    }

    synchronized boolean pollSubmittedFence(long fence) {
        if (fence == 0L) {
            return true;
        }
        int result = vkGetFenceStatus(device, fence);
        if (result == VK_SUCCESS) {
            return true;
        }
        if (result == VK_NOT_READY) {
            return false;
        }
        throw new IllegalStateException("vkGetFenceStatus(async) failed with Vulkan error code " + result);
    }

    synchronized void awaitSubmittedFence(long fence) {
        if (fence == 0L) {
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VulkanDeviceBootstrapper.checkVkResult(
                    vkWaitForFences(device, stack.longs(fence), true, Long.MAX_VALUE),
                    "vkWaitForFences(async)");
        }
    }

    synchronized void releaseSubmittedCommand(long commandPoolHandle, long commandBufferHandle, long fence) {
        if (fence != 0L) {
            vkDestroyFence(device, fence, null);
        }
        if (commandBufferHandle != 0L && commandPoolHandle != 0L) {
            vkFreeCommandBuffers(device, commandPoolHandle, new VkCommandBuffer(commandBufferHandle, device));
        }
    }

    private synchronized void executeImmediatePacketsNow(List<RenderPacket> packets) {
        if (packets == null || packets.isEmpty()) {
            return;
        }
        String threadName = Thread.currentThread().getName();
        beginProfile("VkImmediate:InstallBindings", threadName);
        installImmediateResourceBindings(packets);
        endProfile("VkImmediate:InstallBindings", threadName);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            beginProfile("VkImmediate:AllocateCommandBuffer", threadName);
            VkCommandBufferAllocateInfo allocateInfo = VkCommandBufferAllocateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
                    .commandPool(asyncCommandPool)
                    .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                    .commandBufferCount(1);

            PointerBuffer commandBufferPointer = stack.mallocPointer(1);
            VulkanDeviceBootstrapper.checkVkResult(
                    vkAllocateCommandBuffers(device, allocateInfo, commandBufferPointer),
                    "vkAllocateCommandBuffers(immediate)");
            VkCommandBuffer commandBuffer = new VkCommandBuffer(commandBufferPointer.get(0), device);
            endProfile("VkImmediate:AllocateCommandBuffer", threadName);

            beginProfile("VkImmediate:Record", threadName);
            VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
                    .flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);
            VulkanDeviceBootstrapper.checkVkResult(
                    vkBeginCommandBuffer(commandBuffer, beginInfo),
                    "vkBeginCommandBuffer(immediate)");
            packetExecutor.record(
                    commandBuffer,
                    rasterPipelineCache,
                    computePipelineCache,
                    FrameExecutionPlan.empty(),
                    packets,
                    Math.max(0, lastDrawImageIndex));
            VulkanDeviceBootstrapper.checkVkResult(
                    vkEndCommandBuffer(commandBuffer),
                    "vkEndCommandBuffer(immediate)");
            endProfile("VkImmediate:Record", threadName);

            LongBuffer fencePointer = stack.mallocLong(1);
            VkFenceCreateInfo fenceInfo = VkFenceCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_FENCE_CREATE_INFO);
            VulkanDeviceBootstrapper.checkVkResult(
                    vkCreateFence(device, fenceInfo, null, fencePointer),
                    "vkCreateFence(immediate)");
            long fence = fencePointer.get(0);
            try {
                beginProfile("VkImmediate:QueueSubmit", threadName);
                VkSubmitInfo submitInfo = VkSubmitInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
                        .pCommandBuffers(stack.pointers(commandBuffer.address()));
                int submitResult = vkQueueSubmit(graphicsQueue, submitInfo, fence);
                if (submitResult != VK_SUCCESS) {
                    throw new IllegalStateException(vkFailureMessage(
                            "vkQueueSubmit(immediate)",
                            submitResult,
                            "packets=" + summarizePackets(packets)
                                    + ", swapchainGeneration=" + swapchainGeneration
                                    + ", swapchainExtent=" + swapchainExtentWidth + "x" + swapchainExtentHeight
                                    + ", renderedFrameCount=" + renderedFrameCount
                                    + ", lastDrawImageIndex=" + lastDrawImageIndex
                                    + ", thread=" + threadName));
                }
                endProfile("VkImmediate:QueueSubmit", threadName);
                beginProfile("VkImmediate:WaitFence", threadName);
                VulkanDeviceBootstrapper.checkVkResult(
                        vkWaitForFences(device, stack.longs(fence), true, Long.MAX_VALUE),
                        "vkWaitForFences(immediate)");
                endProfile("VkImmediate:WaitFence", threadName);
            } finally {
                releaseSubmittedCommand(asyncCommandPool, commandBuffer.address(), fence);
            }
        }
    }

    private void recreateSwapchain() {
        waitForValidFramebuffer();
        vkDeviceWaitIdle(device);
        lastDrawImageIndex = -1;
        packetExecutor.invalidateNamedRenderTargetCaches();

        destroySwapchainResources();

        VulkanSwapchainBundle swapchain = VulkanDeviceBootstrapper.createSwapchain(
                physicalDevice,
                graphicsQueueFamilyIndex,
                presentQueueFamilyIndex,
                device,
                surfaceHandle,
                windowHandle,
                vSyncEnabled);

        swapchainHandle = swapchain.handle;
        swapchainImageFormat = swapchain.imageFormat;
        swapchainExtentWidth = swapchain.extentWidth;
        swapchainExtentHeight = swapchain.extentHeight;
        swapchainImages = swapchain.images.clone();
        swapchainImageViews = createSwapchainImageViews(swapchainImages, swapchainImageFormat);
        mainColorAttachments = createMainColorAttachments(swapchainExtentWidth, swapchainExtentHeight, swapchainImages.length, swapchainImageFormat);
        swapchainDepthAttachments = createSwapchainDepthAttachments(swapchainExtentWidth, swapchainExtentHeight, swapchainImages.length);
        rasterPipelineCache.recreate(
                swapchainImageFormat,
                resolveSwapchainDepthFormat(swapchainDepthAttachments),
                swapchainExtentWidth,
                swapchainExtentHeight,
                toImageViews(mainColorAttachments),
                toImageViews(swapchainDepthAttachments));
        submissionScheduler.onSwapchainRecreated(swapchainImages.length);
        swapchainGeneration++;
        swapchainRecreationCount++;
        postResizeValidationFramesRemaining = 6;
    }

    private void waitForValidFramebuffer() {
        int[] width = new int[1];
        int[] height = new int[1];
        do {
            GLFW.glfwGetFramebufferSize(windowHandle, width, height);
            if (width[0] == 0 || height[0] == 0) {
                GLFW.glfwWaitEvents();
            }
        } while (!shutdown && (width[0] == 0 || height[0] == 0));
    }

    private long[] createSwapchainImageViews(long[] images, int imageFormat) {
        long[] imageViews = new long[images.length];
        try (MemoryStack stack = MemoryStack.stackPush()) {
            for (int i = 0; i < images.length; i++) {
                VkImageViewCreateInfo createInfo = VkImageViewCreateInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
                        .image(images[i])
                        .viewType(VK_IMAGE_VIEW_TYPE_2D)
                        .format(imageFormat);
                createInfo.subresourceRange()
                        .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                        .baseMipLevel(0)
                        .levelCount(1)
                        .baseArrayLayer(0)
                        .layerCount(1);

                LongBuffer imageViewPointer = stack.mallocLong(1);
                VulkanDeviceBootstrapper.checkVkResult(
                        vkCreateImageView(device, createInfo, null, imageViewPointer),
                        "vkCreateImageView");
                imageViews[i] = imageViewPointer.get(0);
            }
        }
        return imageViews;
    }

    private VulkanTextureResource[] createSwapchainDepthAttachments(int width, int height, int count) {
        VulkanTextureResource[] attachments = new VulkanTextureResource[Math.max(0, count)];
        for (int i = 0; i < attachments.length; i++) {
            attachments[i] = VulkanTextureFactory.createSampledDepthTexture(physicalDevice, device, width, height);
        }
        return attachments;
    }

    private VulkanTextureResource[] createMainColorAttachments(int width, int height, int count, int imageFormat) {
        VulkanTextureResource[] attachments = new VulkanTextureResource[Math.max(0, count)];
        for (int i = 0; i < attachments.length; i++) {
            attachments[i] = VulkanTextureFactory.createRenderTargetColorTexture(
                    physicalDevice,
                    device,
                    width,
                    height,
                    1,
                    imageFormat);
        }
        return attachments;
    }

    private long[] toImageViews(VulkanTextureResource[] textures) {
        if (textures == null || textures.length == 0) {
            return new long[0];
        }
        long[] imageViews = new long[textures.length];
        for (int i = 0; i < textures.length; i++) {
            imageViews[i] = textures[i] != null ? textures[i].imageView() : VK_NULL_HANDLE;
        }
        return imageViews;
    }

    private int resolveSwapchainDepthFormat(VulkanTextureResource[] textures) {
        if (textures == null || textures.length == 0 || textures[0] == null) {
            throw new IllegalStateException("Swapchain depth attachments were not created");
        }
        return textures[0].format();
    }

    private long createCommandPool() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkCommandPoolCreateInfo createInfo = VkCommandPoolCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO)
                    .flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT)
                    .queueFamilyIndex(graphicsQueueFamilyIndex);
            LongBuffer commandPoolPointer = stack.mallocLong(1);
            VulkanDeviceBootstrapper.checkVkResult(
                    vkCreateCommandPool(device, createInfo, null, commandPoolPointer),
                    "vkCreateCommandPool");
            return commandPoolPointer.get(0);
        }
    }

    private VulkanFrameSlot[] createFrameSlots(long createdCommandPool) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkCommandBufferAllocateInfo allocateInfo = VkCommandBufferAllocateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
                    .commandPool(createdCommandPool)
                    .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                    .commandBufferCount(MAX_FRAMES_IN_FLIGHT);

            PointerBuffer commandBufferPointers = stack.mallocPointer(MAX_FRAMES_IN_FLIGHT);
            VulkanDeviceBootstrapper.checkVkResult(
                    vkAllocateCommandBuffers(device, allocateInfo, commandBufferPointers),
                    "vkAllocateCommandBuffers");

            VulkanFrameSlot[] slots = new VulkanFrameSlot[MAX_FRAMES_IN_FLIGHT];
            VkSemaphoreCreateInfo semaphoreInfo = VkSemaphoreCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO);
            VkFenceCreateInfo fenceInfo = VkFenceCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_FENCE_CREATE_INFO)
                    .flags(VK_FENCE_CREATE_SIGNALED_BIT);

            for (int i = 0; i < MAX_FRAMES_IN_FLIGHT; i++) {
                LongBuffer imageAvailablePointer = stack.mallocLong(1);
                LongBuffer renderFinishedPointer = stack.mallocLong(1);
                LongBuffer fencePointer = stack.mallocLong(1);

                VulkanDeviceBootstrapper.checkVkResult(
                        vkCreateSemaphore(device, semaphoreInfo, null, imageAvailablePointer),
                        "vkCreateSemaphore(imageAvailable)");
                VulkanDeviceBootstrapper.checkVkResult(
                        vkCreateSemaphore(device, semaphoreInfo, null, renderFinishedPointer),
                        "vkCreateSemaphore(renderFinished)");
                VulkanDeviceBootstrapper.checkVkResult(
                        vkCreateFence(device, fenceInfo, null, fencePointer),
                        "vkCreateFence");

                slots[i] = new VulkanFrameSlot(
                        new VkCommandBuffer(commandBufferPointers.get(i), device),
                        imageAvailablePointer.get(0),
                        renderFinishedPointer.get(0),
                        fencePointer.get(0));
            }
            return slots;
        }
    }

    private void recordCommandBuffer(
            VkCommandBuffer commandBuffer,
            int imageIndex,
            FrameExecutionPlan executionPlan,
            List<RenderPacket> immediatePackets) {
        recordCommandBuffer(commandBuffer, imageIndex, executionPlan, immediatePackets, true);
    }

    private void recordCommandBuffer(
            VkCommandBuffer commandBuffer,
            int imageIndex,
            FrameExecutionPlan executionPlan,
            List<RenderPacket> immediatePackets,
            boolean includePresentBlit) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
                    .flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);
            VulkanDeviceBootstrapper.checkVkResult(
                    vkBeginCommandBuffer(commandBuffer, beginInfo),
                    "vkBeginCommandBuffer");
            packetExecutor.record(
                    commandBuffer,
                    rasterPipelineCache,
                    computePipelineCache,
                    executionPlan != null ? executionPlan : FrameExecutionPlan.empty(),
                    immediatePackets != null ? immediatePackets : List.of(),
                    imageIndex);
            if (includePresentBlit) {
                recordPresentBlit(commandBuffer, imageIndex, stack);
            }

            VulkanDeviceBootstrapper.checkVkResult(
                    vkEndCommandBuffer(commandBuffer),
                    "vkEndCommandBuffer");
        }
    }

    private void destroyCreatedResources(
            VulkanFrameSlot[] createdFrameSlots,
            long createdCommandPool,
            long createdAsyncCommandPool,
            long[] createdImageViews) {
        if (rasterPipelineCache != null) {
            rasterPipelineCache.destroy();
            rasterPipelineCache = null;
        }
        computePipelineCache.destroy();
        if (createdFrameSlots != null) {
            for (VulkanFrameSlot frameSlot : createdFrameSlots) {
                if (frameSlot == null) {
                    continue;
                }
                if (frameSlot.inFlightFence != VK_NULL_HANDLE) {
                    vkDestroyFence(device, frameSlot.inFlightFence, null);
                }
                if (frameSlot.renderFinishedSemaphore != VK_NULL_HANDLE) {
                    vkDestroySemaphore(device, frameSlot.renderFinishedSemaphore, null);
                }
                if (frameSlot.imageAvailableSemaphore != VK_NULL_HANDLE) {
                    vkDestroySemaphore(device, frameSlot.imageAvailableSemaphore, null);
                }
            }
        }

        if (createdCommandPool != VK_NULL_HANDLE) {
            vkDestroyCommandPool(device, createdCommandPool, null);
        }
        if (createdAsyncCommandPool != VK_NULL_HANDLE) {
            vkDestroyCommandPool(device, createdAsyncCommandPool, null);
        }

        if (createdImageViews != null) {
            for (long imageView : createdImageViews) {
                if (imageView != VK_NULL_HANDLE) {
                    vkDestroyImageView(device, imageView, null);
                }
            }
        }
        destroyColorAttachments(mainColorAttachments);
        mainColorAttachments = new VulkanTextureResource[0];
        destroyDepthAttachments(swapchainDepthAttachments);
        swapchainDepthAttachments = new VulkanTextureResource[0];
        resourceResolver.destroy();
        pipelineLayoutCache.destroy();
    }

    private void destroySwapchainResources() {
        if (rasterPipelineCache != null) {
            rasterPipelineCache.destroy();
        }
        if (swapchainImageViews != null) {
            for (long imageView : swapchainImageViews) {
                if (imageView != VK_NULL_HANDLE) {
                    vkDestroyImageView(device, imageView, null);
                }
            }
        }
        swapchainImageViews = new long[0];
        destroyColorAttachments(mainColorAttachments);
        mainColorAttachments = new VulkanTextureResource[0];
        destroyDepthAttachments(swapchainDepthAttachments);
        swapchainDepthAttachments = new VulkanTextureResource[0];
        swapchainImages = new long[0];

        if (swapchainHandle != VK_NULL_HANDLE) {
            vkDestroySwapchainKHR(device, swapchainHandle, null);
            swapchainHandle = VK_NULL_HANDLE;
        }
    }

    private void destroyDepthAttachments(VulkanTextureResource[] depthAttachments) {
        if (depthAttachments == null) {
            return;
        }
        for (VulkanTextureResource depthAttachment : depthAttachments) {
            if (depthAttachment == null || depthAttachment.isDisposed()) {
                continue;
            }
            depthAttachment.dispose();
        }
    }

    private void destroyColorAttachments(VulkanTextureResource[] colorAttachments) {
        if (colorAttachments == null) {
            return;
        }
        for (VulkanTextureResource colorAttachment : colorAttachments) {
            if (colorAttachment == null || colorAttachment.isDisposed()) {
                continue;
            }
            colorAttachment.dispose();
        }
    }

    private void recordPresentBlit(VkCommandBuffer commandBuffer, int imageIndex, MemoryStack stack) {
        if (imageIndex < 0 || imageIndex >= swapchainImages.length || imageIndex >= mainColorAttachments.length) {
            return;
        }
        VulkanTextureResource source = mainColorAttachments[imageIndex];
        if (source == null || source.isDisposed()) {
            return;
        }

        transitionImageLayout(commandBuffer, source.image(), VK_IMAGE_ASPECT_COLOR_BIT,
                VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL);
        transitionImageLayout(commandBuffer, swapchainImages[imageIndex], VK_IMAGE_ASPECT_COLOR_BIT,
                VK_IMAGE_LAYOUT_PRESENT_SRC_KHR, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);

        VkImageBlit.Buffer blit = VkImageBlit.calloc(1, stack);
        blit.srcSubresource()
                .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                .mipLevel(0)
                .baseArrayLayer(0)
                .layerCount(1);
        blit.srcOffsets(0).set(0, 0, 0);
        blit.srcOffsets(1).set(swapchainExtentWidth, swapchainExtentHeight, 1);
        blit.dstSubresource()
                .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                .mipLevel(0)
                .baseArrayLayer(0)
                .layerCount(1);
        blit.dstOffsets(0).set(0, swapchainExtentHeight, 0);
        blit.dstOffsets(1).set(swapchainExtentWidth, 0, 1);
        vkCmdBlitImage(
                commandBuffer,
                source.image(),
                VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                swapchainImages[imageIndex],
                VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                blit,
                org.lwjgl.vulkan.VK10.VK_FILTER_NEAREST);

        transitionImageLayout(commandBuffer, source.image(), VK_IMAGE_ASPECT_COLOR_BIT,
                VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
        transitionImageLayout(commandBuffer, swapchainImages[imageIndex], VK_IMAGE_ASPECT_COLOR_BIT,
                VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK_IMAGE_LAYOUT_PRESENT_SRC_KHR);
    }

    private void transitionImageLayout(
            VkCommandBuffer commandBuffer,
            long image,
            int aspectMask,
            int oldLayout,
            int newLayout) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.calloc(1, stack)
                    .sType(org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                    .oldLayout(oldLayout)
                    .newLayout(newLayout)
                    .srcQueueFamilyIndex(org.lwjgl.vulkan.VK10.VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(org.lwjgl.vulkan.VK10.VK_QUEUE_FAMILY_IGNORED)
                    .image(image);
            barrier.get(0).subresourceRange()
                    .aspectMask(aspectMask)
                    .baseMipLevel(0)
                    .levelCount(1)
                    .baseArrayLayer(0)
                    .layerCount(1);
            barrier.get(0).srcAccessMask(accessMaskForLayout(oldLayout));
            barrier.get(0).dstAccessMask(accessMaskForLayout(newLayout));

            vkCmdPipelineBarrier(
                    commandBuffer,
                    stageMaskForLayout(oldLayout),
                    stageMaskForLayout(newLayout),
                    0,
                    null,
                    null,
                    barrier);
        }
    }

    private int accessMaskForLayout(int layout) {
        return switch (layout) {
            case VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL -> org.lwjgl.vulkan.VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
            case VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL -> org.lwjgl.vulkan.VK10.VK_ACCESS_TRANSFER_READ_BIT;
            case VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL -> org.lwjgl.vulkan.VK10.VK_ACCESS_TRANSFER_WRITE_BIT;
            case VK_IMAGE_LAYOUT_PRESENT_SRC_KHR, org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_UNDEFINED -> 0;
            default -> 0;
        };
    }

    private int stageMaskForLayout(int layout) {
        return switch (layout) {
            case VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL -> org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
            case VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL -> org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_TRANSFER_BIT;
            case VK_IMAGE_LAYOUT_PRESENT_SRC_KHR, org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_UNDEFINED -> org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
            default -> org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_ALL_COMMANDS_BIT;
        };
    }

    private void destroyBaseArtifacts() {
        if (swapchainHandle != VK_NULL_HANDLE) {
            vkDestroySwapchainKHR(device, swapchainHandle, null);
        }
        vkDestroyDevice(device, null);
        if (surfaceHandle != VK_NULL_HANDLE) {
            vkDestroySurfaceKHR(instance, surfaceHandle, null);
        }
        vkDestroyInstance(instance, null);
    }

    public String entryPoint() {
        return entryPoint;
    }

    VkPhysicalDevice physicalDevice() {
        return physicalDevice;
    }

    VkDevice device() {
        return device;
    }

    public long windowHandle() {
        return windowHandle;
    }

    public long instanceHandle() {
        return instance.address();
    }

    public long physicalDeviceHandle() {
        return physicalDevice.address();
    }

    public String physicalDeviceName() {
        return physicalDeviceName;
    }

    public long surfaceHandle() {
        return surfaceHandle;
    }

    public long deviceHandle() {
        return device.address();
    }

    public int graphicsQueueFamilyIndex() {
        return graphicsQueueFamilyIndex;
    }

    public int presentQueueFamilyIndex() {
        return presentQueueFamilyIndex;
    }

    public long graphicsQueueHandle() {
        return graphicsQueue.address();
    }

    public long presentQueueHandle() {
        return presentQueue.address();
    }

    public long swapchainHandle() {
        return swapchainHandle;
    }

    public int swapchainImageFormat() {
        return swapchainImageFormat;
    }

    public int swapchainExtentWidth() {
        return swapchainExtentWidth;
    }

    public int swapchainExtentHeight() {
        return swapchainExtentHeight;
    }

    public int swapchainImageCount() {
        return swapchainImages.length;
    }

    public long[] swapchainImages() {
        return swapchainImages.clone();
    }

    public int swapchainImageViewCount() {
        return swapchainImageViews.length;
    }

    public long commandPoolHandle() {
        return commandPool;
    }

    public int maxFramesInFlight() {
        return frameSlots.length;
    }

    public long renderedFrameCount() {
        return renderedFrameCount;
    }

    public long swapchainGeneration() {
        return swapchainGeneration;
    }

    public long swapchainRecreationCount() {
        return swapchainRecreationCount;
    }

    public synchronized boolean postResizeValidationActive() {
        return postResizeValidationFramesRemaining > 0;
    }

    synchronized boolean drawScheduledFrame(VulkanSubmissionScheduler scheduler) {
        assertMainThread("VulkanBackendRuntime.drawFrame");
        if (shutdown) {
            throw new IllegalStateException("Vulkan backend has already been shut down");
        }
        if (scheduler.framebufferResized()) {
            beginProfile("VkDrawFrame:RecreateSwapchain", Thread.currentThread().getName());
            recreateSwapchain();
            endProfile("VkDrawFrame:RecreateSwapchain", Thread.currentThread().getName());
        }

        VulkanFrameSlot frame = scheduler.currentFrame();
        int frameIndex = scheduler.currentFrameIndex();
        String threadName = Thread.currentThread().getName();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            beginProfile("VkDrawFrame:WaitInFlightFence", threadName);
            VulkanDeviceBootstrapper.checkVkResult(
                    vkWaitForFences(device, stack.longs(frame.inFlightFence), true, Long.MAX_VALUE),
                    "vkWaitForFences");
            endProfile("VkDrawFrame:WaitInFlightFence", threadName);

            IntBuffer imageIndexBuffer = stack.ints(0);
            beginProfile("VkDrawFrame:AcquireNextImage", threadName);
            int acquireResult = vkAcquireNextImageKHR(
                    device,
                    swapchainHandle,
                    Long.MAX_VALUE,
                    frame.imageAvailableSemaphore,
                    VK_NULL_HANDLE,
                    imageIndexBuffer);
            endProfile("VkDrawFrame:AcquireNextImage", threadName);
            if (acquireResult == VK_ERROR_OUT_OF_DATE_KHR) {
                beginProfile("VkDrawFrame:RecreateSwapchain", threadName);
                recreateSwapchain();
                endProfile("VkDrawFrame:RecreateSwapchain", threadName);
                return false;
            }
            if (acquireResult != VK_SUCCESS && acquireResult != VK_SUBOPTIMAL_KHR) {
                throw new IllegalStateException(vkFailureMessage(
                        "vkAcquireNextImageKHR",
                        acquireResult,
                        "frameIndex=" + frameIndex
                                + ", swapchainGeneration=" + swapchainGeneration
                                + ", swapchainExtent=" + swapchainExtentWidth + "x" + swapchainExtentHeight
                                + ", renderedFrameCount=" + renderedFrameCount));
            }

            int imageIndex = imageIndexBuffer.get(0);
            long imageFence = scheduler.imageFence(imageIndex);
            if (imageFence != VK_NULL_HANDLE) {
                beginProfile("VkDrawFrame:WaitImageFence", threadName);
                VulkanDeviceBootstrapper.checkVkResult(
                        vkWaitForFences(device, stack.longs(imageFence), true, Long.MAX_VALUE),
                        "vkWaitForFences(image)");
                endProfile("VkDrawFrame:WaitImageFence", threadName);
            }

            scheduler.bindImageFence(imageIndex, frame.inFlightFence);
            beginProfile("VkDrawFrame:ResetFence", threadName);
            VulkanDeviceBootstrapper.checkVkResult(vkResetFences(device, stack.longs(frame.inFlightFence)), "vkResetFences");
            endProfile("VkDrawFrame:ResetFence", threadName);
            beginProfile("VkDrawFrame:ResetCommandBuffer", threadName);
            VulkanDeviceBootstrapper.checkVkResult(vkResetCommandBuffer(frame.commandBuffer, 0), "vkResetCommandBuffer");
            endProfile("VkDrawFrame:ResetCommandBuffer", threadName);

            beginProfile("VkDrawFrame:ConsumeImmediatePackets", threadName);
            List<RenderPacket> immediatePackets = scheduler.consumeImmediatePackets();
            endProfile("VkDrawFrame:ConsumeImmediatePackets", threadName);

            beginProfile("VkDrawFrame:PostResizeValidate", threadName);
            validatePacketsAfterResize(scheduler.installedExecutionPlan(), immediatePackets, imageIndex);
            endProfile("VkDrawFrame:PostResizeValidate", threadName);
            FrameExecutionPlan installedExecutionPlan = scheduler.installedExecutionPlan();
            if (shouldUseSplitSubmitDiagnostic()) {
                beginProfile("VkDrawFrame:SplitSubmitDiagnostic", threadName);
                executeSplitSubmitDiagnostic(
                        frame,
                        frameIndex,
                        imageIndex,
                        installedExecutionPlan,
                        immediatePackets,
                        threadName,
                        stack);
                endProfile("VkDrawFrame:SplitSubmitDiagnostic", threadName);
            } else {
                beginProfile("VkDrawFrame:RecordCommandBuffer", threadName);
                recordCommandBuffer(
                        frame.commandBuffer,
                        imageIndex,
                        installedExecutionPlan,
                        immediatePackets);
                endProfile("VkDrawFrame:RecordCommandBuffer", threadName);

                VkSubmitInfo submitInfo = VkSubmitInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
                        .pWaitSemaphores(stack.longs(frame.imageAvailableSemaphore))
                        .pWaitDstStageMask(stack.ints(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT))
                        .pCommandBuffers(stack.pointers(frame.commandBuffer.address()))
                        .pSignalSemaphores(stack.longs(frame.renderFinishedSemaphore));

                beginProfile("VkDrawFrame:QueueSubmit", threadName);
                int submitResult = vkQueueSubmit(graphicsQueue, submitInfo, frame.inFlightFence);
                endProfile("VkDrawFrame:QueueSubmit", threadName);
                if (submitResult != VK_SUCCESS) {
                    throw new IllegalStateException(vkFailureMessage(
                            "vkQueueSubmit",
                            submitResult,
                            "frameIndex=" + frameIndex
                                    + ", imageIndex=" + imageIndex
                                    + ", swapchainGeneration=" + swapchainGeneration
                                    + ", swapchainRecreationCount=" + swapchainRecreationCount
                                    + ", swapchainExtent=" + swapchainExtentWidth + "x" + swapchainExtentHeight
                                    + ", renderedFrameCount=" + renderedFrameCount
                                    + ", lastDrawImageIndex=" + lastDrawImageIndex
                                    + ", immediatePackets=" + summarizePackets(immediatePackets)));
                }
            }

            VkPresentInfoKHR presentInfo = VkPresentInfoKHR.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PRESENT_INFO_KHR)
                    .pWaitSemaphores(stack.longs(frame.renderFinishedSemaphore))
                    .swapchainCount(1)
                    .pSwapchains(stack.longs(swapchainHandle))
                    .pImageIndices(imageIndexBuffer);

            beginProfile("VkDrawFrame:Present", threadName);
            int presentResult = vkQueuePresentKHR(presentQueue, presentInfo);
            endProfile("VkDrawFrame:Present", threadName);
            if (presentResult == VK_ERROR_OUT_OF_DATE_KHR || presentResult == VK_SUBOPTIMAL_KHR || scheduler.framebufferResized()) {
                beginProfile("VkDrawFrame:RecreateSwapchain", threadName);
                recreateSwapchain();
                endProfile("VkDrawFrame:RecreateSwapchain", threadName);
                return false;
            }
            if (presentResult != VK_SUCCESS) {
                throw new IllegalStateException(vkFailureMessage(
                        "vkQueuePresentKHR",
                        presentResult,
                        "frameIndex=" + frameIndex
                                + ", imageIndex=" + imageIndex
                                + ", swapchainGeneration=" + swapchainGeneration
                                + ", swapchainExtent=" + swapchainExtentWidth + "x" + swapchainExtentHeight
                                + ", renderedFrameCount=" + renderedFrameCount));
            }

            lastDrawImageIndex = imageIndex;
            scheduler.advanceFrame();
            renderedFrameCount++;
            if (postResizeValidationFramesRemaining > 0) {
                postResizeValidationFramesRemaining--;
            }
            return true;
        } catch (RuntimeException runtimeException) {
            SketchDiagnostics.get().error(DIAG_MODULE, "Vulkan draw frame failed", runtimeException);
            throw runtimeException;
        }
    }

    private void beginProfile(String name, String threadName) {
        SimpleProfiler.get().begin(name, threadName);
    }

    private void endProfile(String name, String threadName) {
        SimpleProfiler.get().end(name, threadName);
    }

    private String summarizePackets(List<RenderPacket> packets) {
        if (packets == null || packets.isEmpty()) {
            return "[]";
        }
        List<String> summary = new ArrayList<>();
        int limit = Math.min(packets.size(), 6);
        for (int i = 0; i < limit; i++) {
            RenderPacket packet = packets.get(i);
            if (packet == null) {
                summary.add("null");
                continue;
            }
            summary.add(packet.packetType()
                    + "@stage=" + packet.stageId()
                    + "/shader=" + (packet.stateKey() != null ? packet.stateKey().shaderId() : "null"));
        }
        if (packets.size() > limit) {
            summary.add("...+" + (packets.size() - limit));
        }
        return summary.toString();
    }

    private String vkFailureMessage(String operation, int result, String details) {
        return operation
                + " failed with Vulkan error code " + result
                + " (" + VulkanDeviceBootstrapper.describeVkResult(result) + ")"
                + (details == null || details.isBlank() ? "" : " | " + details);
    }

    private void validatePacketsAfterResize(FrameExecutionPlan executionPlan, List<RenderPacket> immediatePackets, int imageIndex) {
        if (postResizeValidationFramesRemaining <= 0) {
            return;
        }
        List<RenderPacket> packets = new ArrayList<>();
        if (executionPlan != null && !executionPlan.isEmpty()) {
            for (var stagePlan : executionPlan.stagePlans().values()) {
                if (stagePlan == null || stagePlan.isEmpty()) {
                    continue;
                }
                for (PipelineExecutionSlice pipelineSlice : stagePlan.pipelineSlices()) {
                    if (pipelineSlice == null) {
                        continue;
                    }
                    for (int i = 0; i < pipelineSlice.groupCount(); i++) {
                        packets.addAll(pipelineSlice.groupAt(i).packetView());
                    }
                }
            }
        }
        if (immediatePackets != null && !immediatePackets.isEmpty()) {
            packets.addAll(immediatePackets);
        }
        for (RenderPacket packet : packets) {
            validatePacketResources(packet, imageIndex);
        }
    }

    private void validatePacketResources(RenderPacket packet, int imageIndex) {
        if (packet == null) {
            return;
        }
        KeyId renderTargetId = null;
        if (packet.packetKind() == rogo.sketch.core.packet.RenderPacketKind.DRAW) {
            renderTargetId = packet.stateKey() != null ? packet.stateKey().renderTargetKey() : null;
        } else if (packet instanceof rogo.sketch.core.packet.ClearPacket clearPacket) {
            renderTargetId = clearPacket.renderTargetId() != null
                    ? clearPacket.renderTargetId()
                    : (packet.stateKey() != null ? packet.stateKey().renderTargetKey() : null);
        }
        if (renderTargetId != null) {
            VulkanResourceResolver.ResolvedRenderTarget renderTarget = resourceResolver.resolveRenderTargetResource(renderTargetId);
            if (renderTarget == null) {
                throw new IllegalStateException("Post-resize Vulkan validation failed: missing render target "
                        + renderTargetId + " for packet " + summarizePackets(List.of(packet)));
            }
            if (renderTarget.colorAttachments().isEmpty()) {
                throw new IllegalStateException("Post-resize Vulkan validation failed: render target "
                        + renderTargetId + " has no color attachments for packet " + summarizePackets(List.of(packet)));
            }
            for (var entry : renderTarget.colorAttachments().entrySet()) {
                VulkanTextureResource attachment = entry.getValue();
                if (attachment == null || attachment.isDisposed()) {
                    throw new IllegalStateException("Post-resize Vulkan validation failed: render target "
                            + renderTargetId + " color[" + entry.getKey() + "] is "
                            + describeTexture(attachment) + " for packet " + summarizePackets(List.of(packet)));
                }
            }
        } else if (packet.packetKind() == rogo.sketch.core.packet.RenderPacketKind.DRAW
                || packet.packetKind() == rogo.sketch.core.packet.RenderPacketKind.CLEAR) {
            throw new IllegalStateException("Post-resize Vulkan validation failed: packet requires an explicit render target "
                    + summarizePackets(List.of(packet)));
        }
        ResourceBindingPlan bindingPlan = packet.bindingPlan();
        if (bindingPlan == null || bindingPlan.isEmpty()) {
            return;
        }
        for (ResourceBindingPlan.BindingEntry entry : bindingPlan.entries()) {
            if (entry == null || entry.resourceId() == null || entry.resourceType() == null) {
                continue;
            }
            KeyId normalizedType = ResourceTypes.normalize(entry.resourceType());
            if (ResourceTypes.TEXTURE.equals(normalizedType) || ResourceTypes.IMAGE.equals(normalizedType)) {
                VulkanTextureResource texture = resourceResolver.resolveTextureResource(entry.resourceId());
                if (texture == null || texture.isDisposed()) {
                    throw new IllegalStateException("Post-resize Vulkan validation failed: texture "
                            + entry.resourceId() + " resolved to " + describeTexture(texture)
                            + " for packet " + summarizePackets(List.of(packet)));
                }
                continue;
            }
            if (ResourceTypes.UNIFORM_BUFFER.equals(normalizedType)) {
                VulkanUniformBufferResource buffer = resourceResolver.resolveUniformBufferResource(entry.resourceId());
                if (buffer == null || buffer.isDisposed()) {
                    throw new IllegalStateException("Post-resize Vulkan validation failed: uniform buffer "
                            + entry.resourceId() + " is missing/disposed for packet " + summarizePackets(List.of(packet)));
                }
                continue;
            }
            if (ResourceTypes.STORAGE_BUFFER.equals(normalizedType)) {
                VulkanDescriptorBufferResource buffer = resourceResolver.resolveStorageBufferResource(entry.resourceId());
                if (buffer == null || buffer.isDisposed()) {
                    throw new IllegalStateException("Post-resize Vulkan validation failed: storage buffer "
                            + entry.resourceId() + " is missing/disposed for packet " + summarizePackets(List.of(packet)));
                }
            }
        }
    }

    private boolean shouldUseSplitSubmitDiagnostic() {
        return splitSubmitDiagnosticEnabled && postResizeValidationFramesRemaining > 0;
    }

    private void executeSplitSubmitDiagnostic(
            VulkanFrameSlot frame,
            int frameIndex,
            int imageIndex,
            FrameExecutionPlan executionPlan,
            List<RenderPacket> immediatePackets,
            String threadName,
            MemoryStack stack) {
        List<SplitSubmitChunk> chunks = buildSplitSubmitChunks(executionPlan, immediatePackets);
        boolean waitOnImageAvailable = true;
        for (SplitSubmitChunk chunk : chunks) {
            String label = chunk.label();
            beginProfile("VkDrawFrame:SplitRecord:" + label, threadName);
            recordCommandBuffer(
                    frame.commandBuffer,
                    imageIndex,
                    chunk.executionPlan(),
                    chunk.immediatePackets(),
                    chunk.presentToSwapchain());
            endProfile("VkDrawFrame:SplitRecord:" + label, threadName);

            VkSubmitInfo submitInfo = VkSubmitInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
                    .pCommandBuffers(stack.pointers(frame.commandBuffer.address()));
            if (waitOnImageAvailable) {
                submitInfo.pWaitSemaphores(stack.longs(frame.imageAvailableSemaphore))
                        .pWaitDstStageMask(stack.ints(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT));
            }
            if (chunk.signalRenderFinished()) {
                submitInfo.pSignalSemaphores(stack.longs(frame.renderFinishedSemaphore));
            }

            beginProfile("VkDrawFrame:SplitQueueSubmit:" + label, threadName);
            int submitResult = vkQueueSubmit(graphicsQueue, submitInfo, frame.inFlightFence);
            endProfile("VkDrawFrame:SplitQueueSubmit:" + label, threadName);
            if (submitResult != VK_SUCCESS) {
                throw new IllegalStateException(vkFailureMessage(
                        "vkQueueSubmit",
                        submitResult,
                        "diagnosticChunk=" + label
                                + ", frameIndex=" + frameIndex
                                + ", imageIndex=" + imageIndex
                                + ", swapchainGeneration=" + swapchainGeneration
                                + ", swapchainRecreationCount=" + swapchainRecreationCount
                                + ", swapchainExtent=" + swapchainExtentWidth + "x" + swapchainExtentHeight
                                + ", renderedFrameCount=" + renderedFrameCount
                                + ", lastDrawImageIndex=" + lastDrawImageIndex
                                + ", chunkPackets=" + summarizeChunkPackets(chunk)));
            }

            if (!chunk.signalRenderFinished()) {
                beginProfile("VkDrawFrame:SplitWait:" + label, threadName);
                VulkanDeviceBootstrapper.checkVkResult(
                        vkWaitForFences(device, stack.longs(frame.inFlightFence), true, Long.MAX_VALUE),
                        "vkWaitForFences(split:" + label + ")");
                endProfile("VkDrawFrame:SplitWait:" + label, threadName);
                beginProfile("VkDrawFrame:SplitResetFence:" + label, threadName);
                VulkanDeviceBootstrapper.checkVkResult(
                        vkResetFences(device, stack.longs(frame.inFlightFence)),
                        "vkResetFences(split:" + label + ")");
                endProfile("VkDrawFrame:SplitResetFence:" + label, threadName);
                beginProfile("VkDrawFrame:SplitResetCommandBuffer:" + label, threadName);
                VulkanDeviceBootstrapper.checkVkResult(
                        vkResetCommandBuffer(frame.commandBuffer, 0),
                        "vkResetCommandBuffer(split:" + label + ")");
                endProfile("VkDrawFrame:SplitResetCommandBuffer:" + label, threadName);
            }
            waitOnImageAvailable = false;
        }
    }

    private List<SplitSubmitChunk> buildSplitSubmitChunks(
            FrameExecutionPlan executionPlan,
            List<RenderPacket> immediatePackets) {
        FrameExecutionPlan effectivePlan = executionPlan != null ? executionPlan : FrameExecutionPlan.empty();
        List<SplitSubmitChunk> chunks = new ArrayList<>();
        boolean includeUploads = true;
        for (var stageEntry : effectivePlan.stagePlans().entrySet()) {
            StageExecutionPlan stagePlan = stageEntry.getValue();
            if (stagePlan == null || stagePlan.isEmpty()) {
                continue;
            }
            for (PipelineExecutionSlice pipelineSlice : stagePlan.pipelineSlices()) {
                if (pipelineSlice == null || pipelineSlice.isEmpty()) {
                    continue;
                }
                for (int groupIndex = 0; groupIndex < pipelineSlice.groupCount(); groupIndex++) {
                    var group = pipelineSlice.groupAt(groupIndex);
                    List<RenderPacket> groupPackets = group.packetView();
                    for (int packetIndex = 0; packetIndex < groupPackets.size(); packetIndex++) {
                        RenderPacket packet = groupPackets.get(packetIndex);
                        if (packet == null) {
                            continue;
                        }
                        chunks.add(new SplitSubmitChunk(
                                buildPacketDiagnosticLabel(stageEntry.getKey(), pipelineSlice.pipelineType(), groupIndex, packetIndex, packet),
                                new FrameExecutionPlan(
                                        java.util.Map.of(
                                                stageEntry.getKey(),
                                                StageExecutionPlan.fromPackets(stageEntry.getKey(), java.util.Map.of(
                                                        pipelineSlice.pipelineType(),
                                                        java.util.Map.of(group.stateKey(), List.of(packet))))),
                                        includeUploads ? effectivePlan.geometryUploadPlans() : List.of(),
                                        includeUploads ? effectivePlan.resourceUploadPlans() : List.of(),
                                        includeUploads ? effectivePlan.geometryConsumers() : java.util.Map.of(),
                                        null),
                                List.of(),
                                false,
                                false));
                        includeUploads = false;
                    }
                }
            }
        }
        List<RenderPacket> effectiveImmediatePackets =
                immediatePackets != null && !immediatePackets.isEmpty() ? List.copyOf(immediatePackets) : List.of();
        if (chunks.isEmpty()) {
            chunks.add(new SplitSubmitChunk(
                    "frame",
                    new FrameExecutionPlan(
                            java.util.Map.of(),
                            includeUploads ? effectivePlan.geometryUploadPlans() : List.of(),
                            includeUploads ? effectivePlan.resourceUploadPlans() : List.of(),
                            includeUploads ? effectivePlan.geometryConsumers() : java.util.Map.of(),
                            null),
                    effectiveImmediatePackets,
                    true,
                    true));
            return chunks;
        }
        if (!effectiveImmediatePackets.isEmpty()) {
            chunks.add(new SplitSubmitChunk(
                    "immediate",
                    FrameExecutionPlan.empty(),
                    effectiveImmediatePackets,
                    true,
                    true));
            return chunks;
        }
        SplitSubmitChunk lastChunk = chunks.remove(chunks.size() - 1);
        chunks.add(new SplitSubmitChunk(
                lastChunk.label(),
                lastChunk.executionPlan(),
                lastChunk.immediatePackets(),
                true,
                true));
        return chunks;
    }

    private String buildPacketDiagnosticLabel(
            KeyId stageId,
            PipelineType pipelineType,
            int groupIndex,
            int packetIndex,
            RenderPacket packet) {
        String packetType = packet.packetType() != null ? String.valueOf(packet.packetType().id()) : "unknown_packet";
        KeyId shaderId = packet.stateKey() != null ? packet.stateKey().shaderId() : null;
        KeyId renderTargetId = packet.stateKey() != null ? packet.stateKey().renderTargetKey() : null;
        ResourceBindingPlan bindingPlan = packet.bindingPlan() != null && !packet.bindingPlan().isEmpty()
                ? packet.bindingPlan()
                : packet.stateKey() != null ? packet.stateKey().bindingPlan() : ResourceBindingPlan.empty();
        return String.valueOf(stageId)
                + "|pipeline=" + pipelineType
                + "|group=" + groupIndex
                + "|packet=" + packetIndex
                + "|type=" + packetType
                + "|shader=" + (shaderId != null ? shaderId : "null")
                + "|target=" + (renderTargetId != null ? renderTargetId : "null")
                + "|bindings=" + summarizeBindingPlan(bindingPlan)
                + "|geometry=" + describePacketGeometry(packet);
    }

    private String summarizeBindingPlan(ResourceBindingPlan bindingPlan) {
        if (bindingPlan == null || bindingPlan.isEmpty()) {
            return "[]";
        }
        List<String> entries = new ArrayList<>(bindingPlan.entries().length);
        for (ResourceBindingPlan.BindingEntry entry : bindingPlan.entries()) {
            if (entry == null) {
                continue;
            }
            entries.add(entry.bindingName() + "=" + entry.resourceId());
        }
        return entries.isEmpty() ? "[]" : entries.toString();
    }

    private String describePacketGeometry(RenderPacket packet) {
        if (packet instanceof DrawPacket drawPacket && drawPacket.geometryHandle() != null) {
            return String.valueOf(drawPacket.geometryHandle().vertexBufferKey());
        }
        return "none";
    }

    private String summarizeChunkPackets(SplitSubmitChunk chunk) {
        List<RenderPacket> packets = new ArrayList<>();
        if (chunk.executionPlan() != null && !chunk.executionPlan().isEmpty()) {
            for (StageExecutionPlan stagePlan : chunk.executionPlan().stagePlans().values()) {
                if (stagePlan == null || stagePlan.isEmpty()) {
                    continue;
                }
                for (PipelineExecutionSlice pipelineSlice : stagePlan.pipelineSlices()) {
                    for (int i = 0; i < pipelineSlice.groupCount(); i++) {
                        packets.addAll(pipelineSlice.groupAt(i).packetView());
                    }
                }
            }
        }
        if (chunk.immediatePackets() != null && !chunk.immediatePackets().isEmpty()) {
            packets.addAll(chunk.immediatePackets());
        }
        return summarizePackets(packets);
    }

    private String describeTexture(VulkanTextureResource texture) {
        if (texture == null) {
            return "null";
        }
        return "imageView=" + texture.imageView()
                + ", image=" + texture.image()
                + ", extent=" + texture.width() + "x" + texture.height()
                + ", disposed=" + texture.isDisposed();
    }

    VulkanFrameSlot frameSlot(int index) {
        return frameSlots[index];
    }

    boolean isShutdown() {
        return shutdown;
    }

    private record SplitSubmitChunk(
            String label,
            FrameExecutionPlan executionPlan,
            List<RenderPacket> immediatePackets,
            boolean signalRenderFinished,
            boolean presentToSwapchain) {
    }

    private final class VulkanFrameExecutor implements BackendFrameExecutor {
        @Override
        public <C extends RenderContext> BackendStageScope beginExecutionScope(
                GraphicsPipeline<C> pipeline,
                RenderPacketQueue<C> queue,
                List<KeyId> stageIds,
                SnapshotScope snapshotScope,
                C context) {
            return BackendStageScope.NO_OP;
        }

        @Override
        public <C extends RenderContext> void executePacketGroup(
                GraphicsPipeline<C> pipeline,
                ExecutionKey stateKey,
                List<RenderPacket> packets,
                RenderStateManager manager,
                C context) {
            // Vulkan's normal render path consumes the installed FrameExecutionPlan from drawFrame().
            // Packet group callbacks are intentionally no-ops to avoid reviving the old queue-based path.
        }

        @Override
        public <C extends RenderContext> void executeImmediate(
                GraphicsPipeline<C> pipeline,
                RenderPacket packet,
                RenderStateManager manager,
                C context) {
            if (packet == null) {
                return;
            }
            executeImmediatePacketsNow(List.of(packet));
        }
    }
}
