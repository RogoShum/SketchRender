package rogo.sketch.core.debugger;

import rogo.sketch.core.memory.MemoryDebugSnapshot;
import rogo.sketch.core.pipeline.kernel.FrameCaptureSnapshot;
import rogo.sketch.core.pipeline.module.runtime.ModuleRuntimeHost;

import java.util.List;

public interface DashboardDataSource {
    ModuleRuntimeHost runtimeHost();

    DashboardControlAccessor controlAccessor();

    default List<DashboardTreeNode> extraSettingRoots() {
        return List.of();
    }

    default List<DashboardMetricCard> extraMetricCards() {
        return List.of();
    }

    default List<Double> frameTimeHistory() {
        return List.of();
    }

    default MemoryDebugSnapshot memorySnapshot() {
        return MemoryDebugSnapshot.empty();
    }

    default void requestFrameCapture() {
    }

    default FrameCaptureSnapshot latestFrameCaptureSnapshot() {
        return FrameCaptureSnapshot.empty();
    }
}
