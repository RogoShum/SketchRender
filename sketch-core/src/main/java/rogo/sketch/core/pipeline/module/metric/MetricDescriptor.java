package rogo.sketch.core.pipeline.module.metric;

import rogo.sketch.core.util.KeyId;

public record MetricDescriptor(
        KeyId id,
        String moduleId,
        MetricKind kind,
        String displayKey,
        String detailKey
) {
}
