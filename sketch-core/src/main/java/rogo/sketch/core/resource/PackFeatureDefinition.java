package rogo.sketch.core.resource;

import rogo.sketch.core.shader.config.MacroEntryDescriptor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public record PackFeatureDefinition(
        String packId,
        Set<String> features,
        Map<String, String> macros,
        Map<String, MacroEntryDescriptor> entries
) {
    public PackFeatureDefinition(String packId, Set<String> features, Map<String, String> macros) {
        this(packId, features, macros, Map.of());
    }

    public static PackFeatureDefinition empty(String packId) {
        return new PackFeatureDefinition(packId, Set.of(), Map.of(), Map.of());
    }

    public boolean isEmpty() {
        return features.isEmpty() && macros.isEmpty() && entries.isEmpty();
    }

    public Map<String, MacroEntryDescriptor> resolvedEntries() {
        if (!entries.isEmpty()) {
            return entries;
        }
        Map<String, MacroEntryDescriptor> resolved = new LinkedHashMap<>();
        for (String feature : features) {
            resolved.put(feature, MacroEntryDescriptor.constantFlag(feature));
        }
        for (Map.Entry<String, String> entry : macros.entrySet()) {
            resolved.put(entry.getKey(), MacroEntryDescriptor.constantValue(entry.getKey(), entry.getValue()));
        }
        return resolved;
    }
}
