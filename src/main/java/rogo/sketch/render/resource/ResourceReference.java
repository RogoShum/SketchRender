package rogo.sketch.render.resource;

import rogo.sketch.api.ResourceObject;
import rogo.sketch.util.Identifier;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Resource reference wrapper that allows for lazy loading and resource reloading.
 * Provides safe access to resources that may not always be available.
 */
public class ResourceReference<T extends ResourceObject> {

    private final Identifier resourceId;
    private final Identifier resourceType;
    private final Supplier<Optional<T>> resolver;

    @Nullable
    private T cachedResource;
    private boolean cacheValid = false;

    public ResourceReference(Identifier resourceId, Identifier resourceType, Supplier<Optional<T>> resolver) {
        this.resourceId = resourceId;
        this.resourceType = resourceType;
        this.resolver = resolver;
    }

    /**
     * Get the resource if available
     */
    public Optional<T> get() {
        if (!cacheValid) {
            cachedResource = resolver.get().orElse(null);
            cacheValid = true;
        }
        return Optional.ofNullable(cachedResource);
    }

    /**
     * Check if resource is currently available
     */
    public boolean isAvailable() {
        return get().isPresent();
    }

    /**
     * Execute action if resource is available
     */
    public void ifPresent(ResourceAction<T> action) {
        get().ifPresent(action::accept);
    }

    /**
     * Get resource or throw exception
     */
    public T getOrThrow() {
        return get().orElseThrow(() ->
                new ResourceNotFoundException("Resource not found: " + resourceId + " of type " + resourceType)
        );
    }

    /**
     * Get resource or return default
     */
    public T getOrDefault(T defaultResource) {
        return get().orElse(defaultResource);
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
    public Identifier getResourceId() {
        return resourceId;
    }

    /**
     * Get resource type
     */
    public Identifier getResourceType() {
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