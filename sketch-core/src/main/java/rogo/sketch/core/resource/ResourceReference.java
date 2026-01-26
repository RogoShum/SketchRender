package rogo.sketch.core.resource;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.api.ResourceObject;
import rogo.sketch.core.util.KeyId;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Resource reference wrapper that allows for lazy loading and resource reloading.
 * Provides safe access to resources that may not always be available.
 */
public class ResourceReference<T extends ResourceObject> {

    private final KeyId resourceId;
    private final KeyId resourceType;
    private final Supplier<Optional<T>> resolver;

    @Nullable
    private T cachedResource;
    private boolean cacheValid = false;
    boolean persistentResources;

    public ResourceReference(KeyId resourceId, KeyId resourceType, Supplier<Optional<T>> resolver) {
        this(resourceId, resourceType, resolver, false);
    }

    public ResourceReference(KeyId resourceId, KeyId resourceType, Supplier<Optional<T>> resolver, boolean persistentResources) {
        this.resourceId = resourceId;
        this.resourceType = resourceType;
        this.resolver = resolver;
        this.persistentResources = persistentResources;
    }

    /**
     * Get the resource if available
     */
    public T get() {
        if (this.persistentResources) {
            return resolver.get().get();
        }

        if (!cacheValid) {
            cachedResource = resolver.get().orElse(null);
            cacheValid = true;
        }
        return cachedResource;
    }

    /**
     * Check if resource is currently available
     */
    public boolean isAvailable() {
        return resolver.get().isPresent();
    }

    /**
     * Execute action if resource is available
     */
    public void ifPresent(ResourceAction<T> action) {
        resolver.get().ifPresent(action::accept);
    }

    /**
     * Get resource or throw exception
     */
    public T getOrThrow() {
        return resolver.get().orElseThrow(() ->
                new ResourceNotFoundException("Resource not found: " + resourceId + " of type " + resourceType)
        );
    }

    /**
     * Get resource or return default
     */
    public T getOrDefault(T defaultResource) {
        return resolver.get().orElse(defaultResource);
    }

    /**
     * Invalidate cache to force refresh on next access
     */
    public void invalidate() {
        this.cacheValid = false;
        this.cachedResource = null;
    }

    /**
     * Get resource identifier
     */
    public KeyId getResourceId() {
        return resourceId;
    }

    /**
     * Get resource type
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