package rogo.sketch.core.pipeline.module.diagnostic;

import rogo.sketch.core.api.graphics.Graphics;
import rogo.sketch.core.util.KeyId;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pipeline-level render trace filter configuration.
 * <p>
 * When enabled, tracing can target specific graphics identifiers and/or class
 * names. If no filters are configured, all graphics are traced.
 * </p>
 */
public final class RenderTraceConfig {
    private volatile boolean enabled;
    private final Set<KeyId> graphicsIds = ConcurrentHashMap.newKeySet();
    private final Set<String> graphicsClasses = ConcurrentHashMap.newKeySet();

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void clearFilters() {
        graphicsIds.clear();
        graphicsClasses.clear();
    }

    public void traceGraphicsId(KeyId graphicsId) {
        if (graphicsId != null) {
            graphicsIds.add(graphicsId);
        }
    }

    public void traceGraphicsClass(Class<?> graphicsClass) {
        if (graphicsClass != null) {
            traceGraphicsClassName(graphicsClass.getName());
        }
    }

    public void traceGraphicsClassName(String className) {
        if (className != null && !className.isBlank()) {
            graphicsClasses.add(className);
        }
    }

    public boolean matches(Graphics graphics) {
        if (!enabled || graphics == null) {
            return false;
        }
        if (graphicsIds.isEmpty() && graphicsClasses.isEmpty()) {
            return true;
        }
        if (graphicsIds.contains(graphics.getIdentifier())) {
            return true;
        }
        return graphicsClasses.contains(graphics.getClass().getName());
    }
}
