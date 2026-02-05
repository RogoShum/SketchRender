package rogo.sketch.core.resource;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.api.ResourceObject;
import rogo.sketch.core.util.KeyId;

import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Resource reference wrapper that allows for lazy loading and resource reloading.
 * Uses a versioning mechanism for efficient cache invalidation:
 * - Normal case: only compares a single long value (very fast)
 * - After resource reload: version changes, triggers cache refresh
 * 
 * Provides safe access to resources that may not always be available.
 */
public class ResourceReference<T extends ResourceObject> {

    private final KeyId resourceId;
    private final KeyId resourceType;
    private final Supplier<T> resolver;
    private final LongSupplier versionSupplier;

    // Cache fields
    @Nullable
    private T cachedResource;
    private long cachedVersion = -1;

    /**
     * Create a resource reference with version tracking.
     *
     * @param resourceId      The resource identifier
     * @param resourceType    The resource type
     * @param resolver        Supplier that resolves the resource (returns null if not found)
     * @param versionSupplier Supplier that returns the current version of the resource
     */
    public ResourceReference(KeyId resourceId, KeyId resourceType, 
                            Supplier<T> resolver, LongSupplier versionSupplier) {
        this.resourceId = resourceId;
        this.resourceType = resourceType;
        this.resolver = resolver;
        this.versionSupplier = versionSupplier;
    }

    /**
     * Create a resource reference with a constant version (no auto-invalidation).
     * Use this for built-in resources that don't need reload tracking.
     *
     * @param resourceId   The resource identifier
     * @param resourceType The resource type
     * @param resolver     Supplier that resolves the resource
     */
    public ResourceReference(KeyId resourceId, KeyId resourceType, Supplier<T> resolver) {
        this(resourceId, resourceType, resolver, () -> 0L);
    }

    /**
     * Get the resource if available.
     * Uses version-based caching for fast access:
     * - If version matches, returns cached resource immediately
     * - If version differs, refreshes cache from resolver
     *
     * @return The resource, or null if not available
     */
    public T get() {
        long currentVersion = versionSupplier.getAsLong();

        if (cachedResource != null && !cachedResource.isDisposed() && cachedVersion == currentVersion) {
            // Fast path: cache is valid
            return cachedResource;
        }
        
        // Slow path: refresh cache
        cachedResource = resolver.get();
        cachedVersion = currentVersion;
        return cachedResource;
    }

    /**
     * Check if resource is currently available.
     */
    public boolean isAvailable() {
        T resource = get();
        return resource != null && !resource.isDisposed();
    }

    public boolean isEmpty() {
        return !isAvailable();
    }

    /**
     * Execute action if resource is available.
     */
    public void ifPresent(ResourceAction<T> action) {
        T resource = get();
        if (resource != null && !resource.isDisposed()) {
            action.accept(resource);
        }
    }

    /**
     * Get resource or throw exception.
     */
    public T getOrThrow() {
        T resource = get();
        if (resource == null) {
            throw new ResourceNotFoundException("Resource not found: " + resourceId + " of type " + resourceType);
        }

        if (resource.isDisposed()) {
            throw new ResourceNotFoundException("Resource disposed: " + resourceId + " of type " + resourceType);
        }

        return resource;
    }

    /**
     * Get resource or return default.
     */
    public T getOrDefault(T defaultResource) {
        T resource = get();
        return resource != null ? resource : defaultResource;
    }

    /**
     * Invalidate cache to force refresh on next access.
     * Note: With version-based caching, this is usually not needed
     * as the version supplier handles invalidation automatically.
     */
    public void invalidate() {
        this.cachedVersion = -1;
        this.cachedResource = null;
    }

    /**
     * Get resource identifier.
     */
    public KeyId getResourceId() {
        return resourceId;
    }

    /**
     * Get resource type.
     */
    public KeyId getResourceType() {
        return resourceType;
    }

    @FunctionalInterface
    public interface ResourceAction<T> {
        void accept(T resource);
    }

    public static class ResourceNotFoundException extends RuntimeException {
        public ResourceNotFoundException(String message) {
            super(message);
        }
    }
}