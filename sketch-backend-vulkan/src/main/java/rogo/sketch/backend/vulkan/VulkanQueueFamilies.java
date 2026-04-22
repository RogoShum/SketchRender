package rogo.sketch.backend.vulkan;

final class VulkanQueueFamilies {
    static final VulkanQueueFamilies INCOMPLETE = new VulkanQueueFamilies(-1, -1, -1, -1);

    final int graphicsFamilyIndex;
    final int presentFamilyIndex;
    final int computeFamilyIndex;
    final int transferFamilyIndex;

    VulkanQueueFamilies(
            int graphicsFamilyIndex,
            int presentFamilyIndex,
            int computeFamilyIndex,
            int transferFamilyIndex) {
        this.graphicsFamilyIndex = graphicsFamilyIndex;
        this.presentFamilyIndex = presentFamilyIndex;
        this.computeFamilyIndex = computeFamilyIndex;
        this.transferFamilyIndex = transferFamilyIndex;
    }

    boolean complete() {
        return graphicsFamilyIndex >= 0 && presentFamilyIndex >= 0;
    }

    boolean hasDedicatedComputeFamily() {
        return computeFamilyIndex >= 0 && computeFamilyIndex != graphicsFamilyIndex;
    }

    boolean hasDedicatedTransferFamily() {
        return transferFamilyIndex >= 0 && transferFamilyIndex != graphicsFamilyIndex;
    }
}
