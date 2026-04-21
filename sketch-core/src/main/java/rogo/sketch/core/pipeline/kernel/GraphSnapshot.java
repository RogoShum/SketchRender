package rogo.sketch.core.pipeline.kernel;

import rogo.sketch.core.util.KeyId;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Immutable snapshot of the first V4C graph metadata seam.
 */
public record GraphSnapshot(
        long version,
        long createdFrame,
        Map<String, List<ModulePassDefinition>> modulePasses,
        Map<KeyId, FrameResourceHandle<?>> resourceHandles
) {
    public GraphSnapshot {
        modulePasses = modulePasses != null ? normalizePasses(modulePasses) : Map.of();
        resourceHandles = resourceHandles != null ? Collections.unmodifiableMap(new LinkedHashMap<>(resourceHandles)) : Map.of();
    }

    public static GraphSnapshot empty() {
        return new GraphSnapshot(0L, -1L, Map.of(), Map.of());
    }

    public Set<FrameResourceHandle<?>> allHandles() {
        if (resourceHandles.isEmpty()) {
            return Set.of();
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(resourceHandles.values()));
    }

    private static Map<String, List<ModulePassDefinition>> normalizePasses(
            Map<String, List<ModulePassDefinition>> modulePasses) {
        Map<String, List<ModulePassDefinition>> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, List<ModulePassDefinition>> entry : modulePasses.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue().isEmpty()) {
                continue;
            }
            normalized.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Collections.unmodifiableMap(normalized);
    }
}
