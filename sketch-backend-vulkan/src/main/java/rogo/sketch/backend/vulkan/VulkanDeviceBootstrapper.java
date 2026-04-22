package rogo.sketch.backend.vulkan;

import org.lwjgl.BufferUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWVulkan;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkApplicationInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkDeviceCreateInfo;
import org.lwjgl.vulkan.VkDeviceQueueCreateInfo;
import org.lwjgl.vulkan.VkExtensionProperties;
import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkInstanceCreateInfo;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkPhysicalDeviceFeatures;
import org.lwjgl.vulkan.VkPhysicalDeviceProperties;
import org.lwjgl.vulkan.VkQueue;
import org.lwjgl.vulkan.VkQueueFamilyProperties;
import org.lwjgl.vulkan.VkSurfaceCapabilitiesKHR;
import org.lwjgl.vulkan.VkSurfaceFormatKHR;
import org.lwjgl.vulkan.VkSwapchainCreateInfoKHR;
import rogo.sketch.core.debug.RenderDocRuntime;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.vulkan.KHRSurface.VK_COLOR_SPACE_SRGB_NONLINEAR_KHR;
import static org.lwjgl.vulkan.KHRSurface.VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR;
import static org.lwjgl.vulkan.KHRSurface.VK_PRESENT_MODE_FIFO_KHR;
import static org.lwjgl.vulkan.KHRSurface.VK_PRESENT_MODE_IMMEDIATE_KHR;
import static org.lwjgl.vulkan.KHRSurface.VK_PRESENT_MODE_MAILBOX_KHR;
import static org.lwjgl.vulkan.KHRSurface.vkDestroySurfaceKHR;
import static org.lwjgl.vulkan.KHRSurface.vkGetPhysicalDeviceSurfaceCapabilitiesKHR;
import static org.lwjgl.vulkan.KHRSurface.vkGetPhysicalDeviceSurfaceFormatsKHR;
import static org.lwjgl.vulkan.KHRSurface.vkGetPhysicalDeviceSurfacePresentModesKHR;
import static org.lwjgl.vulkan.KHRSurface.vkGetPhysicalDeviceSurfaceSupportKHR;
import static org.lwjgl.vulkan.KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRSwapchain.VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR;
import static org.lwjgl.vulkan.KHRSwapchain.vkCreateSwapchainKHR;
import static org.lwjgl.vulkan.KHRSwapchain.vkDestroySwapchainKHR;
import static org.lwjgl.vulkan.KHRSwapchain.vkGetSwapchainImagesKHR;
import static org.lwjgl.vulkan.EXTDebugUtils.VK_EXT_DEBUG_UTILS_EXTENSION_NAME;
import static org.lwjgl.vulkan.VK10.VK_API_VERSION_1_0;
import static org.lwjgl.vulkan.VK10.VK_FALSE;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_B8G8R8A8_SRGB;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_B8G8R8A8_UNORM;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_UNDEFINED;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_SAMPLED_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_STORAGE_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_TRANSFER_DST_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_TRANSFER_SRC_BIT;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU;
import static org.lwjgl.vulkan.VK10.VK_PHYSICAL_DEVICE_TYPE_INTEGRATED_GPU;
import static org.lwjgl.vulkan.VK10.VK_PHYSICAL_DEVICE_TYPE_VIRTUAL_GPU;
import static org.lwjgl.vulkan.VK10.VK_QUEUE_COMPUTE_BIT;
import static org.lwjgl.vulkan.VK10.VK_QUEUE_GRAPHICS_BIT;
import static org.lwjgl.vulkan.VK10.VK_QUEUE_TRANSFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_SHARING_MODE_CONCURRENT;
import static org.lwjgl.vulkan.VK10.VK_SHARING_MODE_EXCLUSIVE;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_APPLICATION_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;
import static org.lwjgl.vulkan.VK10.VK_TRUE;
import static org.lwjgl.vulkan.VK10.vkCreateDevice;
import static org.lwjgl.vulkan.VK10.vkCreateInstance;
import static org.lwjgl.vulkan.VK10.vkDestroyDevice;
import static org.lwjgl.vulkan.VK10.vkDestroyInstance;
import static org.lwjgl.vulkan.VK10.vkEnumerateDeviceExtensionProperties;
import static org.lwjgl.vulkan.VK10.vkEnumerateInstanceExtensionProperties;
import static org.lwjgl.vulkan.VK10.vkEnumeratePhysicalDevices;
import static org.lwjgl.vulkan.VK10.vkGetDeviceQueue;
import static org.lwjgl.vulkan.VK10.vkGetPhysicalDeviceProperties;
import static org.lwjgl.vulkan.VK10.vkGetPhysicalDeviceQueueFamilyProperties;

final class VulkanDeviceBootstrapper {
    private VulkanDeviceBootstrapper() {
    }

    static VulkanBootstrapArtifacts bootstrap(String entryPoint, long windowHandle, boolean vSyncEnabled) {
        if (windowHandle == NULL) {
            throw new IllegalArgumentException("Vulkan backend requires a valid GLFW window handle");
        }
        if (!GLFWVulkan.glfwVulkanSupported()) {
            throw new IllegalStateException("GLFW reports that Vulkan is not supported on this machine");
        }

        VkInstance instance = null;
        long surfaceHandle = VK_NULL_HANDLE;
        VkDevice device = null;
        long swapchainHandle = VK_NULL_HANDLE;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            InstanceBootstrap instanceBootstrap = createInstance(entryPoint, stack);
            instance = instanceBootstrap.instance();
            surfaceHandle = createSurface(instance, windowHandle, stack);

            VulkanDeviceSelection selection = pickPhysicalDevice(instance, surfaceHandle);
            device = createLogicalDevice(selection, stack);
            VkQueue graphicsQueue = getQueue(device, selection.graphicsQueueFamilyIndex);
            VkQueue presentQueue = getQueue(device, selection.presentQueueFamilyIndex);
            VkQueue computeQueue = getQueue(device, selection.computeQueueFamilyIndex);
            VkQueue transferQueue = getQueue(device, selection.transferQueueFamilyIndex);
            VulkanSwapchainBundle swapchain = createSwapchain(
                    selection.physicalDevice,
                    selection.graphicsQueueFamilyIndex,
                    selection.presentQueueFamilyIndex,
                    device,
                    surfaceHandle,
                    windowHandle,
                    vSyncEnabled,
                    stack);
            swapchainHandle = swapchain.handle;

            return new VulkanBootstrapArtifacts(
                    instance,
                    selection.physicalDevice,
                    selection.deviceName,
                    surfaceHandle,
                    device,
                    selection.graphicsQueueFamilyIndex,
                    selection.presentQueueFamilyIndex,
                    selection.computeQueueFamilyIndex,
                    selection.transferQueueFamilyIndex,
                    graphicsQueue,
                    presentQueue,
                    computeQueue,
                    transferQueue,
                    swapchain.handle,
                    swapchain.imageFormat,
                    swapchain.extentWidth,
                    swapchain.extentHeight,
                    swapchain.images,
                    instanceBootstrap.debugUtilsEnabled());
        } catch (RuntimeException ex) {
            if (swapchainHandle != VK_NULL_HANDLE && device != null) {
                vkDestroySwapchainKHR(device, swapchainHandle, null);
            }
            if (device != null) {
                vkDestroyDevice(device, null);
            }
            if (surfaceHandle != VK_NULL_HANDLE && instance != null) {
                vkDestroySurfaceKHR(instance, surfaceHandle, null);
            }
            if (instance != null) {
                vkDestroyInstance(instance, null);
            }
            throw ex;
        }
    }

    static void checkVkResult(int result, String operation) {
        if (result != VK_SUCCESS) {
            throw new IllegalStateException(
                    operation + " failed with Vulkan error code " + result + " (" + describeVkResult(result) + ")");
        }
    }

    static String describeVkResult(int result) {
        return switch (result) {
            case 0 -> "VK_SUCCESS";
            case 1 -> "VK_NOT_READY";
            case 2 -> "VK_TIMEOUT";
            case 3 -> "VK_EVENT_SET";
            case 4 -> "VK_EVENT_RESET";
            case 5 -> "VK_INCOMPLETE";
            case -1 -> "VK_ERROR_OUT_OF_HOST_MEMORY";
            case -2 -> "VK_ERROR_OUT_OF_DEVICE_MEMORY";
            case -3 -> "VK_ERROR_INITIALIZATION_FAILED";
            case -4 -> "VK_ERROR_DEVICE_LOST";
            case -5 -> "VK_ERROR_MEMORY_MAP_FAILED";
            case -6 -> "VK_ERROR_LAYER_NOT_PRESENT";
            case -7 -> "VK_ERROR_EXTENSION_NOT_PRESENT";
            case -8 -> "VK_ERROR_FEATURE_NOT_PRESENT";
            case -9 -> "VK_ERROR_INCOMPATIBLE_DRIVER";
            case -10 -> "VK_ERROR_TOO_MANY_OBJECTS";
            case -11 -> "VK_ERROR_FORMAT_NOT_SUPPORTED";
            case -12 -> "VK_ERROR_FRAGMENTED_POOL";
            case -13 -> "VK_ERROR_UNKNOWN";
            case -1000001004 -> "VK_ERROR_OUT_OF_DATE_KHR";
            case 1000001003 -> "VK_SUBOPTIMAL_KHR";
            default -> "UNKNOWN_VK_RESULT";
        };
    }

    private static InstanceBootstrap createInstance(String entryPoint, MemoryStack stack) {
        PointerBuffer requiredExtensions = GLFWVulkan.glfwGetRequiredInstanceExtensions();
        if (requiredExtensions == null || requiredExtensions.remaining() == 0) {
            throw new IllegalStateException("GLFW did not provide required Vulkan instance extensions");
        }
        boolean debugUtilsEnabled = RenderDocRuntime.enabled()
                && supportsInstanceExtension(VK_EXT_DEBUG_UTILS_EXTENSION_NAME, stack);
        PointerBuffer enabledExtensions = stack.mallocPointer(requiredExtensions.remaining() + (debugUtilsEnabled ? 1 : 0));
        for (int i = requiredExtensions.position(); i < requiredExtensions.limit(); i++) {
            enabledExtensions.put(requiredExtensions.get(i));
        }
        if (debugUtilsEnabled) {
            enabledExtensions.put(stack.UTF8(VK_EXT_DEBUG_UTILS_EXTENSION_NAME));
        }
        enabledExtensions.flip();

        VkApplicationInfo applicationInfo = VkApplicationInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_APPLICATION_INFO)
                .pApplicationName(stack.UTF8(entryPoint))
                .applicationVersion(1)
                .pEngineName(stack.UTF8("Sketch"))
                .engineVersion(1)
                .apiVersion(VK_API_VERSION_1_0);

        VkInstanceCreateInfo createInfo = VkInstanceCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO)
                .pApplicationInfo(applicationInfo)
                .ppEnabledExtensionNames(enabledExtensions);

        PointerBuffer instancePointer = stack.mallocPointer(1);
        checkVkResult(vkCreateInstance(createInfo, null, instancePointer), "vkCreateInstance");
        return new InstanceBootstrap(new VkInstance(instancePointer.get(0), createInfo), debugUtilsEnabled);
    }

    private static boolean supportsInstanceExtension(String extensionName, MemoryStack stack) {
        IntBuffer extensionCount = stack.ints(0);
        checkVkResult(
                vkEnumerateInstanceExtensionProperties((java.nio.ByteBuffer) null, extensionCount, null),
                "vkEnumerateInstanceExtensionProperties(count)");
        if (extensionCount.get(0) <= 0) {
            return false;
        }
        VkExtensionProperties.Buffer properties = VkExtensionProperties.malloc(extensionCount.get(0), stack);
        checkVkResult(
                vkEnumerateInstanceExtensionProperties((java.nio.ByteBuffer) null, extensionCount, properties),
                "vkEnumerateInstanceExtensionProperties(list)");
        for (int i = 0; i < properties.capacity(); i++) {
            if (extensionName.equals(properties.get(i).extensionNameString())) {
                return true;
            }
        }
        return false;
    }

    private static long createSurface(VkInstance instance, long windowHandle, MemoryStack stack) {
        LongBuffer surfacePointer = stack.mallocLong(1);
        checkVkResult(
                GLFWVulkan.glfwCreateWindowSurface(instance, windowHandle, null, surfacePointer),
                "glfwCreateWindowSurface");
        return surfacePointer.get(0);
    }

    private static VulkanDeviceSelection pickPhysicalDevice(VkInstance instance, long surfaceHandle) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer deviceCount = stack.ints(0);
            checkVkResult(vkEnumeratePhysicalDevices(instance, deviceCount, null), "vkEnumeratePhysicalDevices(count)");
            if (deviceCount.get(0) <= 0) {
                throw new IllegalStateException("No Vulkan physical devices were found");
            }

            PointerBuffer devices = stack.mallocPointer(deviceCount.get(0));
            checkVkResult(vkEnumeratePhysicalDevices(instance, deviceCount, devices), "vkEnumeratePhysicalDevices(list)");

            VulkanDeviceSelection best = null;
            int bestScore = Integer.MIN_VALUE;
            for (int i = 0; i < devices.capacity(); i++) {
                VkPhysicalDevice physicalDevice = new VkPhysicalDevice(devices.get(i), instance);
                VulkanDeviceSelection candidate = evaluatePhysicalDevice(physicalDevice, surfaceHandle);
                if (candidate == null) {
                    continue;
                }
                if (candidate.score > bestScore) {
                    best = candidate;
                    bestScore = candidate.score;
                }
            }

            if (best == null) {
                throw new IllegalStateException("No suitable Vulkan physical device supports graphics, present, and swapchain");
            }
            return best;
        }
    }

    private static VulkanDeviceSelection evaluatePhysicalDevice(VkPhysicalDevice physicalDevice, long surfaceHandle) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VulkanQueueFamilies queueFamilies = findQueueFamilies(physicalDevice, surfaceHandle, stack);
            if (!queueFamilies.complete()) {
                return null;
            }
            if (!supportsSwapchainExtension(physicalDevice, stack)) {
                return null;
            }
            if (!hasUsableSwapchainSupport(physicalDevice, surfaceHandle, stack)) {
                return null;
            }

            VkPhysicalDeviceProperties properties = VkPhysicalDeviceProperties.calloc(stack);
            vkGetPhysicalDeviceProperties(physicalDevice, properties);
            return new VulkanDeviceSelection(
                    physicalDevice,
                    properties.deviceNameString(),
                    queueFamilies.graphicsFamilyIndex,
                    queueFamilies.presentFamilyIndex,
                    queueFamilies.computeFamilyIndex,
                    queueFamilies.transferFamilyIndex,
                    scorePhysicalDevice(properties));
        }
    }

    private static int scorePhysicalDevice(VkPhysicalDeviceProperties properties) {
        return switch (properties.deviceType()) {
            case VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU -> 1_000;
            case VK_PHYSICAL_DEVICE_TYPE_INTEGRATED_GPU -> 500;
            case VK_PHYSICAL_DEVICE_TYPE_VIRTUAL_GPU -> 250;
            default -> 100;
        };
    }

    private static VulkanQueueFamilies findQueueFamilies(VkPhysicalDevice physicalDevice, long surfaceHandle, MemoryStack stack) {
        IntBuffer queueFamilyCount = stack.ints(0);
        vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, queueFamilyCount, null);
        if (queueFamilyCount.get(0) <= 0) {
            return VulkanQueueFamilies.INCOMPLETE;
        }

        VkQueueFamilyProperties.Buffer queueFamilies = VkQueueFamilyProperties.malloc(queueFamilyCount.get(0), stack);
        vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, queueFamilyCount, queueFamilies);

        Integer graphicsIndex = null;
        Integer presentIndex = null;
        Integer computeIndex = null;
        Integer fallbackComputeIndex = null;
        Integer transferIndex = null;
        Integer fallbackTransferIndex = null;
        IntBuffer presentSupport = stack.ints(VK_FALSE);

        for (int i = 0; i < queueFamilies.capacity(); i++) {
            VkQueueFamilyProperties queueFamily = queueFamilies.get(i);
            int queueFlags = queueFamily.queueFlags();
            boolean supportsGraphics = (queueFlags & VK_QUEUE_GRAPHICS_BIT) != 0;
            boolean supportsCompute = (queueFlags & VK_QUEUE_COMPUTE_BIT) != 0;
            boolean supportsTransfer = (queueFlags & VK_QUEUE_TRANSFER_BIT) != 0;
            if (supportsGraphics) {
                graphicsIndex = i;
            }
            if (supportsCompute) {
                if (!supportsGraphics && computeIndex == null) {
                    computeIndex = i;
                }
                if (fallbackComputeIndex == null) {
                    fallbackComputeIndex = i;
                }
            }
            if (supportsTransfer) {
                if (!supportsGraphics && !supportsCompute && transferIndex == null) {
                    transferIndex = i;
                } else if (!supportsGraphics && transferIndex == null) {
                    transferIndex = i;
                }
                if (fallbackTransferIndex == null) {
                    fallbackTransferIndex = i;
                }
            }

            presentSupport.put(0, VK_FALSE);
            checkVkResult(
                    vkGetPhysicalDeviceSurfaceSupportKHR(physicalDevice, i, surfaceHandle, presentSupport),
                    "vkGetPhysicalDeviceSurfaceSupportKHR");
            if (presentSupport.get(0) == VK_TRUE) {
                presentIndex = i;
            }

            if (graphicsIndex != null && presentIndex != null) {
                break;
            }
        }

        if (graphicsIndex == null || presentIndex == null) {
            return VulkanQueueFamilies.INCOMPLETE;
        }
        int resolvedComputeIndex = computeIndex != null
                ? computeIndex
                : (fallbackComputeIndex != null ? fallbackComputeIndex : graphicsIndex);
        int resolvedTransferIndex = transferIndex != null
                ? transferIndex
                : (fallbackTransferIndex != null ? fallbackTransferIndex : graphicsIndex);
        return new VulkanQueueFamilies(
                graphicsIndex,
                presentIndex,
                resolvedComputeIndex,
                resolvedTransferIndex);
    }

    private static boolean supportsSwapchainExtension(VkPhysicalDevice physicalDevice, MemoryStack stack) {
        IntBuffer extensionCount = stack.ints(0);
        checkVkResult(
                vkEnumerateDeviceExtensionProperties(physicalDevice, (String) null, extensionCount, null),
                "vkEnumerateDeviceExtensionProperties(count)");
        if (extensionCount.get(0) <= 0) {
            return false;
        }

        VkExtensionProperties.Buffer properties = VkExtensionProperties.malloc(extensionCount.get(0), stack);
        checkVkResult(
                vkEnumerateDeviceExtensionProperties(physicalDevice, (String) null, extensionCount, properties),
                "vkEnumerateDeviceExtensionProperties(list)");

        for (int i = 0; i < properties.capacity(); i++) {
            if (VK_KHR_SWAPCHAIN_EXTENSION_NAME.equals(properties.get(i).extensionNameString())) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasUsableSwapchainSupport(VkPhysicalDevice physicalDevice, long surfaceHandle, MemoryStack stack) {
        IntBuffer formatCount = stack.ints(0);
        checkVkResult(
                vkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice, surfaceHandle, formatCount, null),
                "vkGetPhysicalDeviceSurfaceFormatsKHR(count)");
        if (formatCount.get(0) <= 0) {
            return false;
        }

        IntBuffer presentModeCount = stack.ints(0);
        checkVkResult(
                vkGetPhysicalDeviceSurfacePresentModesKHR(physicalDevice, surfaceHandle, presentModeCount, null),
                "vkGetPhysicalDeviceSurfacePresentModesKHR(count)");
        return presentModeCount.get(0) > 0;
    }

    private static VkDevice createLogicalDevice(VulkanDeviceSelection selection, MemoryStack stack) {
        Set<Integer> uniqueFamilies = new LinkedHashSet<>();
        uniqueFamilies.add(selection.graphicsQueueFamilyIndex);
        uniqueFamilies.add(selection.presentQueueFamilyIndex);
        uniqueFamilies.add(selection.computeQueueFamilyIndex);
        uniqueFamilies.add(selection.transferQueueFamilyIndex);

        VkDeviceQueueCreateInfo.Buffer queueInfos = VkDeviceQueueCreateInfo.calloc(uniqueFamilies.size(), stack);
        FloatBuffer queuePriority = stack.floats(1.0f);

        int queueInfoIndex = 0;
        for (int familyIndex : uniqueFamilies) {
            queueInfos.get(queueInfoIndex)
                    .sType(VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO)
                    .queueFamilyIndex(familyIndex)
                    .pQueuePriorities(queuePriority);
            queueInfoIndex++;
        }

        PointerBuffer deviceExtensions = stack.pointers(stack.UTF8(VK_KHR_SWAPCHAIN_EXTENSION_NAME));
        VkPhysicalDeviceFeatures deviceFeatures = VkPhysicalDeviceFeatures.calloc(stack);

        VkDeviceCreateInfo createInfo = VkDeviceCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO)
                .pQueueCreateInfos(queueInfos)
                .ppEnabledExtensionNames(deviceExtensions)
                .pEnabledFeatures(deviceFeatures);

        PointerBuffer devicePointer = stack.mallocPointer(1);
        checkVkResult(vkCreateDevice(selection.physicalDevice, createInfo, null, devicePointer), "vkCreateDevice");
        return new VkDevice(devicePointer.get(0), selection.physicalDevice, createInfo);
    }

    private static VkQueue getQueue(VkDevice device, int queueFamilyIndex) {
        PointerBuffer queuePointer = BufferUtils.createPointerBuffer(1);
        vkGetDeviceQueue(device, queueFamilyIndex, 0, queuePointer);
        return new VkQueue(queuePointer.get(0), device);
    }

    static VulkanSwapchainBundle createSwapchain(
            VkPhysicalDevice physicalDevice,
            int graphicsQueueFamilyIndex,
            int presentQueueFamilyIndex,
            VkDevice device,
            long surfaceHandle,
            long windowHandle,
            boolean vSyncEnabled) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            return createSwapchain(
                    physicalDevice,
                    graphicsQueueFamilyIndex,
                    presentQueueFamilyIndex,
                    device,
                    surfaceHandle,
                    windowHandle,
                    vSyncEnabled,
                    stack);
        }
    }

    private static VulkanSwapchainBundle createSwapchain(
            VkPhysicalDevice physicalDevice,
            int graphicsQueueFamilyIndex,
            int presentQueueFamilyIndex,
            VkDevice device,
            long surfaceHandle,
            long windowHandle,
            boolean vSyncEnabled,
            MemoryStack stack) {
        VkSurfaceCapabilitiesKHR capabilities = VkSurfaceCapabilitiesKHR.calloc(stack);
        checkVkResult(
                vkGetPhysicalDeviceSurfaceCapabilitiesKHR(physicalDevice, surfaceHandle, capabilities),
                "vkGetPhysicalDeviceSurfaceCapabilitiesKHR");

        SurfaceFormatChoice surfaceFormat = chooseSurfaceFormat(physicalDevice, surfaceHandle, stack);
        int presentMode = choosePresentMode(physicalDevice, surfaceHandle, stack, vSyncEnabled);
        SwapchainExtent extent = chooseExtent(capabilities, windowHandle);

        int imageCount = capabilities.minImageCount() + 1;
        if (capabilities.maxImageCount() > 0 && imageCount > capabilities.maxImageCount()) {
            imageCount = capabilities.maxImageCount();
        }

        IntBuffer queueFamilyIndices = null;
        int imageSharingMode = VK_SHARING_MODE_EXCLUSIVE;
        if (graphicsQueueFamilyIndex != presentQueueFamilyIndex) {
            imageSharingMode = VK_SHARING_MODE_CONCURRENT;
            queueFamilyIndices = stack.ints(graphicsQueueFamilyIndex, presentQueueFamilyIndex);
        }

        int imageUsage = resolveImageUsage(capabilities.supportedUsageFlags());

        VkSwapchainCreateInfoKHR createInfo = VkSwapchainCreateInfoKHR.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR)
                .surface(surfaceHandle)
                .minImageCount(imageCount)
                .imageFormat(surfaceFormat.format)
                .imageColorSpace(surfaceFormat.colorSpace)
                .imageExtent(extent.toVk(stack))
                .imageArrayLayers(1)
                .imageUsage(imageUsage)
                .imageSharingMode(imageSharingMode)
                .preTransform(capabilities.currentTransform())
                .compositeAlpha(VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR)
                .presentMode(presentMode)
                .clipped(true)
                .oldSwapchain(VK_NULL_HANDLE);

        if (queueFamilyIndices != null) {
            createInfo.pQueueFamilyIndices(queueFamilyIndices);
        }

        LongBuffer swapchainPointer = stack.mallocLong(1);
        checkVkResult(vkCreateSwapchainKHR(device, createInfo, null, swapchainPointer), "vkCreateSwapchainKHR");
        long swapchainHandle = swapchainPointer.get(0);

        IntBuffer swapchainImageCount = stack.ints(0);
        checkVkResult(vkGetSwapchainImagesKHR(device, swapchainHandle, swapchainImageCount, null), "vkGetSwapchainImagesKHR(count)");
        LongBuffer imageHandles = stack.mallocLong(swapchainImageCount.get(0));
        checkVkResult(vkGetSwapchainImagesKHR(device, swapchainHandle, swapchainImageCount, imageHandles), "vkGetSwapchainImagesKHR(list)");

        long[] images = new long[swapchainImageCount.get(0)];
        for (int i = 0; i < images.length; i++) {
            images[i] = imageHandles.get(i);
        }

        return new VulkanSwapchainBundle(
                swapchainHandle,
                surfaceFormat.format,
                extent.width,
                extent.height,
                images);
    }

    private static int resolveImageUsage(int supportedUsage) {
        int imageUsage = VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT;
        if ((supportedUsage & VK_IMAGE_USAGE_TRANSFER_DST_BIT) != 0) {
            imageUsage |= VK_IMAGE_USAGE_TRANSFER_DST_BIT;
        }
        if ((supportedUsage & VK_IMAGE_USAGE_TRANSFER_SRC_BIT) != 0) {
            imageUsage |= VK_IMAGE_USAGE_TRANSFER_SRC_BIT;
        }
        if ((supportedUsage & VK_IMAGE_USAGE_SAMPLED_BIT) != 0) {
            imageUsage |= VK_IMAGE_USAGE_SAMPLED_BIT;
        }
        if ((supportedUsage & VK_IMAGE_USAGE_STORAGE_BIT) != 0) {
            imageUsage |= VK_IMAGE_USAGE_STORAGE_BIT;
        }
        return imageUsage;
    }

    private static SurfaceFormatChoice chooseSurfaceFormat(VkPhysicalDevice physicalDevice, long surfaceHandle, MemoryStack stack) {
        IntBuffer formatCount = stack.ints(0);
        checkVkResult(
                vkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice, surfaceHandle, formatCount, null),
                "vkGetPhysicalDeviceSurfaceFormatsKHR(count)");
        if (formatCount.get(0) <= 0) {
            throw new IllegalStateException("No Vulkan surface formats are available");
        }

        VkSurfaceFormatKHR.Buffer formats = VkSurfaceFormatKHR.malloc(formatCount.get(0), stack);
        checkVkResult(
                vkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice, surfaceHandle, formatCount, formats),
                "vkGetPhysicalDeviceSurfaceFormatsKHR(list)");

        if (formats.capacity() == 1 && formats.get(0).format() == VK_FORMAT_UNDEFINED) {
            return new SurfaceFormatChoice(VK_FORMAT_B8G8R8A8_UNORM, VK_COLOR_SPACE_SRGB_NONLINEAR_KHR);
        }

        for (int i = 0; i < formats.capacity(); i++) {
            VkSurfaceFormatKHR format = formats.get(i);
            if (format.format() == VK_FORMAT_B8G8R8A8_UNORM
                    && format.colorSpace() == VK_COLOR_SPACE_SRGB_NONLINEAR_KHR) {
                return new SurfaceFormatChoice(format.format(), format.colorSpace());
            }
        }
        for (int i = 0; i < formats.capacity(); i++) {
            VkSurfaceFormatKHR format = formats.get(i);
            if (format.format() == VK_FORMAT_B8G8R8A8_SRGB
                    && format.colorSpace() == VK_COLOR_SPACE_SRGB_NONLINEAR_KHR) {
                return new SurfaceFormatChoice(format.format(), format.colorSpace());
            }
        }
        VkSurfaceFormatKHR fallback = formats.get(0);
        return new SurfaceFormatChoice(fallback.format(), fallback.colorSpace());
    }

    private static int choosePresentMode(
            VkPhysicalDevice physicalDevice,
            long surfaceHandle,
            MemoryStack stack,
            boolean vSyncEnabled) {
        IntBuffer presentModeCount = stack.ints(0);
        checkVkResult(
                vkGetPhysicalDeviceSurfacePresentModesKHR(physicalDevice, surfaceHandle, presentModeCount, null),
                "vkGetPhysicalDeviceSurfacePresentModesKHR(count)");
        if (presentModeCount.get(0) <= 0) {
            throw new IllegalStateException("No Vulkan present modes are available");
        }

        IntBuffer presentModes = stack.mallocInt(presentModeCount.get(0));
        checkVkResult(
                vkGetPhysicalDeviceSurfacePresentModesKHR(physicalDevice, surfaceHandle, presentModeCount, presentModes),
                "vkGetPhysicalDeviceSurfacePresentModesKHR(list)");

        if (!vSyncEnabled) {
            for (int i = 0; i < presentModes.capacity(); i++) {
                if (presentModes.get(i) == VK_PRESENT_MODE_IMMEDIATE_KHR) {
                    return VK_PRESENT_MODE_IMMEDIATE_KHR;
                }
            }
            for (int i = 0; i < presentModes.capacity(); i++) {
                if (presentModes.get(i) == VK_PRESENT_MODE_MAILBOX_KHR) {
                    return VK_PRESENT_MODE_MAILBOX_KHR;
                }
            }
        }
        return VK_PRESENT_MODE_FIFO_KHR;
    }

    private static SwapchainExtent chooseExtent(VkSurfaceCapabilitiesKHR capabilities, long windowHandle) {
        if (capabilities.currentExtent().width() != 0xFFFFFFFF) {
            return new SwapchainExtent(
                    capabilities.currentExtent().width(),
                    capabilities.currentExtent().height());
        }

        int[] width = new int[1];
        int[] height = new int[1];
        GLFW.glfwGetFramebufferSize(windowHandle, width, height);

        int clampedWidth = clamp(
                Math.max(1, width[0]),
                capabilities.minImageExtent().width(),
                capabilities.maxImageExtent().width());
        int clampedHeight = clamp(
                Math.max(1, height[0]),
                capabilities.minImageExtent().height(),
                capabilities.maxImageExtent().height());
        return new SwapchainExtent(clampedWidth, clampedHeight);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class SwapchainExtent {
        final int width;
        final int height;

        private SwapchainExtent(int width, int height) {
            this.width = width;
            this.height = height;
        }

        org.lwjgl.vulkan.VkExtent2D toVk(MemoryStack stack) {
            return org.lwjgl.vulkan.VkExtent2D.calloc(stack).set(width, height);
        }
    }

    private static final class SurfaceFormatChoice {
        final int format;
        final int colorSpace;

        private SurfaceFormatChoice(int format, int colorSpace) {
            this.format = format;
            this.colorSpace = colorSpace;
        }
    }

    private record InstanceBootstrap(VkInstance instance, boolean debugUtilsEnabled) {
    }
}

