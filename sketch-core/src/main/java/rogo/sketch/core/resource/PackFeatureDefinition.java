package rogo.sketch.core.resource;

import java.util.Map;
import java.util.Set;

/**
 * Definition of features provided by a resource pack.
 * Used to register pack-specific macros and features with the shader system.
 */
public record PackFeatureDefinition(
    /**
     * Unique identifier for the resource pack.
     */
    String packId,
    
    /**
     * Set of feature flags enabled by this pack.
     * These will be added as shader macros with value "1".
     */
    Set<String> features,
    
    /**
     * Map of macro names to their values.
     * These will be added to the shader macro context.
     */
    Map<String, String> macros
) {
    /**
     * Create an empty feature definition.
     */
    public static PackFeatureDefinition empty(String packId) {
        return new PackFeatureDefinition(packId, Set.of(), Map.of());
    }
    
    /**
     * Check if this definition has any features or macros.
     */
    public boolean isEmpty() {
        return features.isEmpty() && macros.isEmpty();
    }
}

