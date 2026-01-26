package rogo.sketch.core.pipeline.flow;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

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

    public Collection<RenderPostProcessor> getAll() {
        return processors.values();
    }
}
