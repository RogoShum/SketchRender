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
import rogo.sketch.core.driver.state.snapshot.SnapshotScope;
import rogo.sketch.core.packet.GeometryHandleKey;
import rogo.sketch.core.packet.PipelineStateKey;
import rogo.sketch.core.packet.RenderPacket;
import rogo.sketch.core.packet.RenderPacketQueue;
import rogo.sketch.core.pipeline.GraphicsPipeline;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.PipelineType;
import rogo.sketch.core.pipeline.RenderStateManager;
import rogo.sketch.core.pipeline.kernel.FrameExecutionPlan;
import rogo.sketch.core.util.KeyId;

import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
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
import static org.lwjgl.vulkan.VK10.vkQueueSubmit;
import static org.lwjgl.vulkan.VK10.vkResetCommandBuffer;
import static org.lwjgl.vulkan.VK10.vkResetFences;
import static org.lwjgl.vulkan.VK10.vkWaitForFences;

public final class VulkanBackendRuntime implements BackendRuntime {
    private static final int MAX_FRAMES_IN_FLIGHT = 2;

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
    private final BackendResourceInstaller resourceInstaller;
    private final VulkanDescriptorArena descriptorArena;
    private final VulkanGeometryArena geometryArena;
    private final VulkanPacketExecutor packetExecutor;
    private VulkanRasterPipelineCache rasterPipelineCache;
    private final VulkanComputePipelineCache computePipelineCache;
    private final long commandPool;
    private final VulkanFrameSlot[] frameSlots;
    private final List<RenderPacket> immediatePackets = new ArrayList<>();
    private long[] imagesInFlight;
    private volatile long mainThreadId;
    private volatile boolean shutdown;
    private volatile boolean framebufferResized;
    private volatile FrameExecutionPlan installedExecutionPlan = FrameExecutionPlan.empty();
    private int currentFrameIndex;
    private long renderedFrameCount;
    private long swapchainGeneration;
    private long swapchainRecreationCount;
    private final boolean debugUtilsEnabled;

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
        this.resourceInstaller = new VulkanBackendResourceInstaller(this);
        this.descriptorArena = new VulkanDescriptorArena(this.device, this.resourceResolver);
        this.geometryArena = new VulkanGeometryArena(this.physicalDevice, this.device);
        this.packetExecutor = new VulkanPacketExecutor(
                this.device,
                this.descriptorArena,
                this.geometryArena,
                this.resourceResolver,
                debugUtilsEnabled);
        this.debugUtilsEnabled = debugUtilsEnabled;
        this.computePipelineCache = new VulkanComputePipelineCache(this.device, this.pipelineLayoutCache);
        this.mainThreadId = Thread.currentThread().getId();
        this.framebufferResized = false;

        long[] createdImageViews = null;
        VulkanTextureResource[] createdDepthAttachments = null;
        long createdCommandPool = VK_NULL_HANDLE;
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
            createdFrameSlots = createFrameSlots(createdCommandPool);
        } catch (RuntimeException ex) {
            destroyCreatedResources(createdFrameSlots, createdCommandPool, createdImageViews);
            destroyDepthAttachments(createdDepthAttachments);
            destroyBaseArtifacts();
            throw ex;
        }

        this.swapchainImageViews = createdImageViews;
        this.swapchainDepthAttachments = createdDepthAttachments != null ? createdDepthAttachments : new VulkanTextureResource[0];
        this.commandPool = createdCommandPool;
        this.frameSlots = createdFrameSlots;
        this.imagesInFlight = new long[this.swapchainImages.length];
        this.currentFrameIndex = 0;
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
    public BackendCapabilities capabilities() {
        return BackendCapabilities.NONE;
    }

    @Override
    public BackendFrameExecutor frameExecutor() {
        return frameExecutor;
    }

    @Override
    public BackendResourceResolver resourceResolver() {
        return resourceResolver;
    }

    @Override
    public BackendResourceInstaller resourceInstaller() {
        return resourceInstaller;
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
        framebufferResized = true;
    }

    @Override
    public synchronized void installExecutionPlan(FrameExecutionPlan executionPlan) {
        FrameExecutionPlan nextExecutionPlan = executionPlan != null ? executionPlan : FrameExecutionPlan.empty();
        geometryArena.install(nextExecutionPlan.geometryUploadPlans(), renderedFrameCount, frameSlots.length);
        descriptorArena.install(nextExecutionPlan.resourceUploadPlans());
        this.installedExecutionPlan = nextExecutionPlan;
    }

    public synchronized void registerInterleavedColorGeometry(GeometryHandleKey geometryHandle, float[] vertexData, int vertexCount) {
        geometryArena.registerInterleavedColorGeometry(geometryHandle, vertexData, vertexCount);
    }

    public void registerTextureResource(KeyId resourceId, VulkanTextureResource textureResource) {
        resourceResolver.registerTexture(resourceId, textureResource);
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

    public boolean debugUtilsEnabled() {
        return debugUtilsEnabled;
    }

    public BackendPacketHandlerRegistry<VulkanPacketHandler> packetHandlerRegistry() {
        return packetExecutor.packetHandlerRegistry();
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
        assertMainThread("VulkanBackendRuntime.drawFrame");
        if (shutdown) {
            throw new IllegalStateException("Vulkan backend has already been shut down");
        }
        if (framebufferResized) {
            recreateSwapchain();
        }

        VulkanFrameSlot frame = frameSlots[currentFrameIndex];
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VulkanDeviceBootstrapper.checkVkResult(
                    vkWaitForFences(device, stack.longs(frame.inFlightFence), true, Long.MAX_VALUE),
                    "vkWaitForFences");

            IntBuffer imageIndexBuffer = stack.ints(0);
            int acquireResult = vkAcquireNextImageKHR(
                    device,
                    swapchainHandle,
                    Long.MAX_VALUE,
                    frame.imageAvailableSemaphore,
                    VK_NULL_HANDLE,
                    imageIndexBuffer);
            if (acquireResult == VK_ERROR_OUT_OF_DATE_KHR) {
                recreateSwapchain();
                return false;
            }
            if (acquireResult != VK_SUCCESS && acquireResult != VK_SUBOPTIMAL_KHR) {
                throw new IllegalStateException("vkAcquireNextImageKHR failed with Vulkan error code " + acquireResult);
            }

            int imageIndex = imageIndexBuffer.get(0);
            long imageFence = imagesInFlight[imageIndex];
            if (imageFence != VK_NULL_HANDLE) {
                VulkanDeviceBootstrapper.checkVkResult(
                        vkWaitForFences(device, stack.longs(imageFence), true, Long.MAX_VALUE),
                        "vkWaitForFences(image)");
            }

            imagesInFlight[imageIndex] = frame.inFlightFence;
            VulkanDeviceBootstrapper.checkVkResult(vkResetFences(device, stack.longs(frame.inFlightFence)), "vkResetFences");
            VulkanDeviceBootstrapper.checkVkResult(vkResetCommandBuffer(frame.commandBuffer, 0), "vkResetCommandBuffer");

            recordCommandBuffer(frame.commandBuffer, imageIndex);

            VkSubmitInfo submitInfo = VkSubmitInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
                    .pWaitSemaphores(stack.longs(frame.imageAvailableSemaphore))
                    .pWaitDstStageMask(stack.ints(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT))
                    .pCommandBuffers(stack.pointers(frame.commandBuffer.address()))
                    .pSignalSemaphores(stack.longs(frame.renderFinishedSemaphore));

            VulkanDeviceBootstrapper.checkVkResult(
                    vkQueueSubmit(graphicsQueue, submitInfo, frame.inFlightFence),
                    "vkQueueSubmit");

            VkPresentInfoKHR presentInfo = VkPresentInfoKHR.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PRESENT_INFO_KHR)
                    .pWaitSemaphores(stack.longs(frame.renderFinishedSemaphore))
                    .swapchainCount(1)
                    .pSwapchains(stack.longs(swapchainHandle))
                    .pImageIndices(imageIndexBuffer);

            int presentResult = vkQueuePresentKHR(presentQueue, presentInfo);
            if (presentResult == VK_ERROR_OUT_OF_DATE_KHR || presentResult == VK_SUBOPTIMAL_KHR || framebufferResized) {
                recreateSwapchain();
                return false;
            }
            if (presentResult != VK_SUCCESS) {
                throw new IllegalStateException("vkQueuePresentKHR failed with Vulkan error code " + presentResult);
            }

            currentFrameIndex = (currentFrameIndex + 1) % frameSlots.length;
            renderedFrameCount++;
            return true;
        }
    }

    @Override
    public synchronized void shutdown() {
        if (shutdown) {
            return;
        }
        shutdown = true;

        vkDeviceWaitIdle(device);
        destroyCreatedResources(frameSlots, commandPool, swapchainImageViews);
        destroyBaseArtifacts();
    }

    private void recreateSwapchain() {
        waitForValidFramebuffer();
        vkDeviceWaitIdle(device);

        destroySwapchainResources();

        VulkanSwapchainBundle swapchain = VulkanDeviceBootstrapper.createSwapchain(
                physicalDevice,
                graphicsQueueFamilyIndex,
                presentQueueFamilyIndex,
                device,
                surfaceHandle,
                windowHandle);

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
        imagesInFlight = new long[swapchainImages.length];
        framebufferResized = false;
        swapchainGeneration++;
        swapchainRecreationCount++;
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
            attachments[i] = VulkanTextureFactory.createDepthTexture(physicalDevice, device, width, height);
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

    private void recordCommandBuffer(VkCommandBuffer commandBuffer, int imageIndex) {
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
                    installedExecutionPlan,
                    consumeImmediatePackets(),
                    imageIndex);
            recordPresentBlit(commandBuffer, imageIndex, stack);

            VulkanDeviceBootstrapper.checkVkResult(
                    vkEndCommandBuffer(commandBuffer),
                    "vkEndCommandBuffer");
        }
    }

    private synchronized List<RenderPacket> consumeImmediatePackets() {
        if (immediatePackets.isEmpty()) {
            return List.of();
        }
        List<RenderPacket> snapshot = List.copyOf(immediatePackets);
        immediatePackets.clear();
        return snapshot;
    }

    private synchronized void queueImmediatePacket(RenderPacket packet) {
        if (packet == null) {
            return;
        }
        immediatePackets.add(packet);
    }

    private void destroyCreatedResources(VulkanFrameSlot[] createdFrameSlots, long createdCommandPool, long[] createdImageViews) {
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
        geometryArena.destroy();
        descriptorArena.destroy();
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
        imagesInFlight = new long[0];

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
                PipelineStateKey stateKey,
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
            queueImmediatePacket(packet);
        }
    }
}

