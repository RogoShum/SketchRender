package rogo.sketch.module.memory;

import rogo.sketch.core.memory.MemoryDebugSnapshot;
import rogo.sketch.core.memory.UnifiedMemoryFabric;
import rogo.sketch.core.pipeline.module.runtime.ModuleRuntime;
import rogo.sketch.core.pipeline.module.runtime.ModuleRuntimeContext;

public class MemoryTelemetryModuleRuntime implements ModuleRuntime {
    private static final long SNAPSHOT_TTL_NANOS = 50_000_000L;

    private volatile MemoryDebugSnapshot cachedSnapshot = MemoryDebugSnapshot.empty();
    private volatile long cachedSnapshotNanos;

    @Override
    public String id() {
        return MemoryTelemetryModuleDescriptor.MODULE_ID;
    }

    @Override
    public void onProcessInit(ModuleRuntimeContext context) {
        context.registerMetric(
                new rogo.sketch.core.pipeline.module.metric.MetricDescriptor(
                        MemoryTelemetryModuleDescriptor.LIVE_TOTAL_METRIC,
                        id(),
                        rogo.sketch.core.pipeline.module.metric.MetricKind.BYTES,
                        "debug.dashboard.memory.total_live",
                        "debug.dashboard.memory.total_live.detail"),
                () -> snapshot().totalLiveBytes());
        context.registerMetric(
                new rogo.sketch.core.pipeline.module.metric.MetricDescriptor(
                        MemoryTelemetryModuleDescriptor.PEAK_TOTAL_METRIC,
                        id(),
                        rogo.sketch.core.pipeline.module.metric.MetricKind.BYTES,
                        "debug.dashboard.memory.total_peak",
                        "debug.dashboard.memory.total_peak.detail"),
                () -> snapshot().totalPeakBytes());
        context.registerMetric(
                new rogo.sketch.core.pipeline.module.metric.MetricDescriptor(
                        MemoryTelemetryModuleDescriptor.ALLOC_RATE_METRIC,
                        id(),
                        rogo.sketch.core.pipeline.module.metric.MetricKind.BYTES_PER_SECOND,
                        "debug.dashboard.memory.alloc_rate",
                        "debug.dashboard.memory.alloc_rate.detail"),
                () -> snapshot().allocBytesPerSecond());
        context.registerMetric(
                new rogo.sketch.core.pipeline.module.metric.MetricDescriptor(
                        MemoryTelemetryModuleDescriptor.FREE_RATE_METRIC,
                        id(),
                        rogo.sketch.core.pipeline.module.metric.MetricKind.BYTES_PER_SECOND,
                        "debug.dashboard.memory.free_rate",
                        "debug.dashboard.memory.free_rate.detail"),
                () -> snapshot().freeBytesPerSecond());
        context.registerMetric(
                new rogo.sketch.core.pipeline.module.metric.MetricDescriptor(
                        MemoryTelemetryModuleDescriptor.BUDGET_USAGE_METRIC,
                        id(),
                        rogo.sketch.core.pipeline.module.metric.MetricKind.PERCENT,
                        "debug.dashboard.memory.budget_usage",
                        "debug.dashboard.memory.budget_usage.detail"),
                () -> snapshot().totalBudgetUsageRatio());
    }

    private MemoryDebugSnapshot snapshot() {
        long now = System.nanoTime();
        MemoryDebugSnapshot snapshot = cachedSnapshot;
        if (snapshot != MemoryDebugSnapshot.empty() && now - cachedSnapshotNanos < SNAPSHOT_TTL_NANOS) {
            return snapshot;
        }
        synchronized (this) {
            if (cachedSnapshot != MemoryDebugSnapshot.empty() && now - cachedSnapshotNanos < SNAPSHOT_TTL_NANOS) {
                return cachedSnapshot;
            }
            cachedSnapshot = UnifiedMemoryFabric.get().snapshot();
            cachedSnapshotNanos = now;
            return cachedSnapshot;
        }
    }
}
