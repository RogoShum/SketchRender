package rogo.sketch.backend.vulkan;

final class VulkanQueueFamilies {
    static final VulkanQueueFamilies INCOMPLETE = new VulkanQueueFamilies(-1, -1);

    final int graphicsFamilyIndex;
    final int presentFamilyIndex;

    VulkanQueueFamilies(int graphicsFamilyIndex, int presentFamilyIndex) {
        this.graphicsFamilyIndex = graphicsFamilyIndex;
        this.presentFamilyIndex = presentFamilyIndex;
    }

    boolean complete() {
        return graphicsFamilyIndex >= 0 && presentFamilyIndex >= 0;
    }
}
