package rogo.sketch.module.transform;

import rogo.sketch.core.pipeline.module.descriptor.ModuleDescriptor;
import rogo.sketch.core.pipeline.module.descriptor.ModuleDescriptorContext;
import rogo.sketch.core.pipeline.module.metric.MetricDescriptor;
import rogo.sketch.core.pipeline.module.metric.MetricKind;
import rogo.sketch.core.pipeline.module.runtime.ModuleRuntime;
import rogo.sketch.core.util.KeyId;

public class TransformModuleDescriptor implements ModuleDescriptor {
    public static final KeyId ACTIVE_COUNT_METRIC = KeyId.of("sketch_render", "transform_active_count");

    @Override
    public String id() {
        return TransformModule.MODULE_NAME;
    }

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public void describe(ModuleDescriptorContext context) {
        context.registerMetricDescriptor(new MetricDescriptor(
                ACTIVE_COUNT_METRIC,
                id(),
                MetricKind.COUNT,
                "metric." + id() + ".active_count",
                "metric." + id() + ".active_count.detail"));
    }

    @Override
    public ModuleRuntime createRuntime() {
        return new TransformModule();
    }
}
