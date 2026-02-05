package rogo.sketch.core.resource;

import rogo.sketch.core.util.KeyId;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Resource scan provider interface - implemented by platform-specific code (e.g., Minecraft).
 * Abstracts the resource loading flow to allow sketch-core to remain platform-independent.
 */
public interface ResourceScanProvider {
    
    /**
     * Scan and return all resources of a specific type.
     *
     * @param resourceType The resource type to scan for
     * @return Map of resource IDs to their input streams
     */
    Map<KeyId, InputStream> scanResources(KeyId resourceType);
    
    /**
     * Get a sub-resource stream by identifier.
     * Used for loading dependent files like GLSL imports.
     *
     * @param identifier The resource identifier
     * @return Optional containing the input stream if found
     */
    Optional<InputStream> getSubResource(KeyId identifier);
    
    /**
     * Get the list of resource pack feature definitions.
     * Used for loading pack-specific macros and features.
     *
     * @return List of pack feature definitions
     */
    List<PackFeatureDefinition> getPackFeatures();
    
    /**
     * Check if a resource exists.
     *
     * @param identifier The resource identifier
     * @return true if the resource exists
     */
    default boolean resourceExists(KeyId identifier) {
        return getSubResource(identifier).isPresent();
    }
}

