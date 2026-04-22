package rogo.sketch.module.memory;

import rogo.sketch.core.pipeline.module.descriptor.ModuleDescriptor;
import rogo.sketch.core.pipeline.module.descriptor.ModuleDescriptorContext;
import rogo.sketch.core.pipeline.module.metric.MetricDescriptor;
import rogo.sketch.core.pipeline.module.metric.MetricKind;
import rogo.sketch.core.pipeline.module.runtime.ModuleRuntime;
import rogo.sketch.core.util.KeyId;

public class MemoryTelemetryModuleDescriptor implements ModuleDescriptor {
    public static final String MODULE_ID = "memory_telemetry";
    public static final KeyId LIVE_TOTAL_METRIC = KeyId.of("sketch_render", "memory_live_total");
    public static final KeyId PEAK_TOTAL_METRIC = KeyId.of("sketch_render", "memory_peak_total");
    public static final KeyId ALLOC_RATE_METRIC = KeyId.of("sketch_render", "memory_alloc_rate");
    public static final KeyId FREE_RATE_METRIC = KeyId.of("sketch_render", "memory_free_rate");
    public static final KeyId BUDGET_USAGE_METRIC = KeyId.of("sketch_render", "memory_budget_usage");

    @Override
    public String id() {
        return MODULE_ID;
    }

    @Override
    public void describe(ModuleDescriptorContext context) {
        context.registerMetricDescriptor(new MetricDescriptor(
                LIVE_TOTAL_METRIC,
                MODULE_ID,
                MetricKind.BYTES,
                "debug.dashboard.memory.total_live",
                "debug.dashboard.memory.total_live.detail"));
        context.registerMetricDescriptor(new MetricDescriptor(
                PEAK_TOTAL_METRIC,
                MODULE_ID,
                MetricKind.BYTES,
                "debug.dashboard.memory.total_peak",
                "debug.dashboard.memory.total_peak.detail"));
        context.registerMetricDescriptor(new MetricDescriptor(
                ALLOC_RATE_METRIC,
                MODULE_ID,
                MetricKind.BYTES_PER_SECOND,
                "debug.dashboard.memory.alloc_rate",
                "debug.dashboard.memory.alloc_rate.detail"));
        context.registerMetricDescriptor(new MetricDescriptor(
                FREE_RATE_METRIC,
                MODULE_ID,
                MetricKind.BYTES_PER_SECOND,
                "debug.dashboard.memory.free_rate",
                "debug.dashboard.memory.free_rate.detail"));
        context.registerMetricDescriptor(new MetricDescriptor(
                BUDGET_USAGE_METRIC,
                MODULE_ID,
                MetricKind.PERCENT,
                "debug.dashboard.memory.budget_usage",
                "debug.dashboard.memory.budget_usage.detail"));
    }

    @Override
    public ModuleRuntime createRuntime() {
        return new MemoryTelemetryModuleRuntime();
    }
}
