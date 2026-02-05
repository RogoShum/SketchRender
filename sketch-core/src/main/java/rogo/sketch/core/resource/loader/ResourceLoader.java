package rogo.sketch.core.resource.loader;

import rogo.sketch.core.api.ResourceObject;
import rogo.sketch.core.util.KeyId;

/**
 * Interface for loading resources from generic data (JSON, Binary, etc.)
 */
public interface ResourceLoader<T extends ResourceObject> {

    /**
     * Load a resource from the provided context.
     * This is the primary method that should be implemented by all loaders.
     *
     * @param context The resource loading context containing all necessary data
     * @return The loaded resource or null if failed
     */
    T load(ResourceLoadContext context);

    /**
     * Get the resource type this loader handles.
     *
     * @return The resource type identifier
     */
    KeyId getResourceType();
}