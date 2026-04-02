package rogo.sketch.backend.vulkan;

final class VulkanSwapchainBundle {
    final long handle;
    final int imageFormat;
    final int extentWidth;
    final int extentHeight;
    final long[] images;

    VulkanSwapchainBundle(long handle, int imageFormat, int extentWidth, int extentHeight, long[] images) {
        this.handle = handle;
        this.imageFormat = imageFormat;
        this.extentWidth = extentWidth;
        this.extentHeight = extentHeight;
        this.images = images != null ? images.clone() : new long[0];
    }
}
