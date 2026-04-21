package rogo.sketch.core.pipeline.module.diagnostic;

import rogo.sketch.core.graphics.ecs.GraphicsUniformSubject;
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
    private volatile boolean rangeScopeTracingEnabled;
    private final Set<KeyId> graphicsIds = ConcurrentHashMap.newKeySet();
    private final Set<KeyId> graphicsTags = ConcurrentHashMap.newKeySet();

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean rangeScopeTracingEnabled() {
        return rangeScopeTracingEnabled;
    }

    public void setRangeScopeTracingEnabled(boolean rangeScopeTracingEnabled) {
        this.rangeScopeTracingEnabled = rangeScopeTracingEnabled;
    }

    public void clearFilters() {
        graphicsIds.clear();
        graphicsTags.clear();
    }

    public void traceGraphicsId(KeyId graphicsId) {
        if (graphicsId != null) {
            graphicsIds.add(graphicsId);
        }
    }

    public void traceGraphicsTag(KeyId tag) {
        if (tag != null) {
            graphicsTags.add(tag);
        }
    }

    public boolean matches(GraphicsUniformSubject subject) {
        if (!enabled || subject == null) {
            return false;
        }
        if (graphicsIds.isEmpty() && graphicsTags.isEmpty()) {
            return true;
        }
        if (graphicsIds.contains(subject.identifier())) {
            return true;
        }
        for (KeyId tag : graphicsTags) {
            if (subject.hasTag(tag)) {
                return true;
            }
        }
        return false;
    }
}

