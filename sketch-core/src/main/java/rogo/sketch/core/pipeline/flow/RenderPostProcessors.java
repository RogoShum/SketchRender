package rogo.sketch.core.pipeline.flow;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Container for managing all RenderPostProcessors.
 */
public class RenderPostProcessors {
    private final Map<RenderFlowType, RenderPostProcessor> processors = new HashMap<>();

    public void register(RenderFlowType type, RenderPostProcessor processor) {
        processors.put(type, processor);
    }

    @SuppressWarnings("unchecked")
    public <T extends RenderPostProcessor> T get(RenderFlowType type) {
        return (T) processors.get(type);
    }

    public void executeAll() {
        processors.values().forEach(RenderPostProcessor::execute);
    }

    public void executeAllExcept(RenderFlowType... excludedTypes) {
        Set<RenderFlowType> excluded = new HashSet<>();
        if (excludedTypes != null) {
            java.util.Collections.addAll(excluded, excludedTypes);
        }
        processors.forEach((type, processor) -> {
            if (!excluded.contains(type)) {
                processor.execute();
            }
        });
    }

    public Collection<RenderPostProcessor> getAll() {
        return processors.values();
    }
}
