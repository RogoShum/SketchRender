package rogo.sketch.render.resource;

import rogo.sketch.api.ResourceObject;
import rogo.sketch.api.ResourceReloadable;
import rogo.sketch.util.Identifier;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Generic support class for implementing reloadable resources
 * Provides common functionality for dependency tracking, change detection, and listener management
 * 
 * @param <T> The type of resource this supports
 */
public abstract class ReloadableResourceSupport<T extends ResourceObject> implements ResourceReloadable<T> {
    
    protected final Identifier resourceIdentifier;
    protected final Function<Identifier, Optional<BufferedReader>> resourceProvider;
    
    protected T currentResource;
    protected ReloadMetadata lastReloadMetadata;
    
    private final Map<Identifier, Long> dependencyTimestamps = new ConcurrentHashMap<>();
    private final Set<Consumer<T>> reloadListeners = ConcurrentHashMap.newKeySet();
    private volatile Set<Identifier> lastKnownDependencies = Collections.emptySet();

    public ReloadableResourceSupport(Identifier resourceIdentifier, 
                                   Function<Identifier, Optional<BufferedReader>> resourceProvider) {
        this.resourceIdentifier = resourceIdentifier;
        this.resourceProvider = resourceProvider;
    }

    @Override
    public final Identifier getResourceIdentifier() {
        return resourceIdentifier;
    }

    @Override
    public final T getCurrentResource() {
        return currentResource;
    }

    @Override
    public final Set<Identifier> getDependencies() {
        return Collections.unmodifiableSet(lastKnownDependencies);
    }

    @Override
    public final ReloadMetadata getLastReloadMetadata() {
        return lastReloadMetadata;
    }

    @Override
    public final void addReloadListener(Consumer<T> listener) {
        reloadListeners.add(listener);
    }

    @Override
    public final void removeReloadListener(Consumer<T> listener) {
        reloadListeners.remove(listener);
    }

    @Override
    public final boolean needsReload() {
        return hasDependencyChanges() || hasConfigurationChanges();
    }

    @Override
    public final void reload() throws IOException {
        if (!needsReload()) {
            return;
        }
        forceReload();
    }

    @Override
    public final void forceReload() throws IOException {
        System.out.println("Reloading resource: " + resourceIdentifier);
        
        T oldResource = currentResource;
        Set<Identifier> oldDependencies = lastKnownDependencies;
        
        try {
            // Perform the actual reload
            ResourceLoadResult<T> result = performReload();
            
            // Update state
            this.currentResource = result.resource();
            this.lastKnownDependencies = result.dependencies();
            updateDependencyTimestamps();
            
            // Record success
            this.lastReloadMetadata = ReloadMetadata.success(lastKnownDependencies);
            
            // Notify listeners
            notifyReloadListeners(currentResource);
            
            System.out.println("Resource reloaded successfully: " + resourceIdentifier);
            
        } catch (Exception e) {
            // Record failure and restore dependencies
            this.lastReloadMetadata = ReloadMetadata.failure(e.getMessage(), oldDependencies);
            this.lastKnownDependencies = oldDependencies;
            
            System.err.println("Resource reload failed for " + resourceIdentifier + ": " + e.getMessage());
            throw new IOException("Resource reload failed", e);
            
        } finally {
            // Cleanup old resource if different
            if (oldResource != null && oldResource != currentResource) {
                try {
                    oldResource.dispose();
                } catch (Exception e) {
                    System.err.println("Failed to dispose old resource: " + e.getMessage());
                }
            }
        }
    }

    @Override
    public final boolean hasDependencyChanges() {
        for (Identifier dependency : lastKnownDependencies) {
            if (hasDependencyChanged(dependency)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public final void updateDependencyTimestamps() {
        long currentTime = System.currentTimeMillis();
        for (Identifier dependency : lastKnownDependencies) {
            dependencyTimestamps.put(dependency, currentTime);
        }
    }

    /**
     * Check if a specific dependency has changed
     * Subclasses can override this for more sophisticated change detection
     */
    protected boolean hasDependencyChanged(Identifier dependency) {
        // Basic implementation: assume changed if we don't have a timestamp
        return !dependencyTimestamps.containsKey(dependency);
    }

    /**
     * Check if configuration has changed
     * Subclasses should override this if they support configuration
     */
    protected boolean hasConfigurationChanges() {
        return false;
    }

    /**
     * Perform the actual resource reload
     * Subclasses must implement this to define their specific reload logic
     */
    protected abstract ResourceLoadResult<T> performReload() throws IOException;

    /**
     * Initial load of the resource
     * Default implementation calls performReload, but subclasses can override
     */
    protected ResourceLoadResult<T> performInitialLoad() throws IOException {
        return performReload();
    }

    /**
     * Initialize this reloadable resource with an initial load
     */
    public final void initialize() throws IOException {
        ResourceLoadResult<T> result = performInitialLoad();
        this.currentResource = result.resource();
        this.lastKnownDependencies = result.dependencies();
        updateDependencyTimestamps();
        this.lastReloadMetadata = ReloadMetadata.success(lastKnownDependencies);
    }

    /**
     * Notify all reload listeners
     */
    protected final void notifyReloadListeners(T newResource) {
        for (Consumer<T> listener : reloadListeners) {
            try {
                listener.accept(newResource);
            } catch (Exception e) {
                System.err.println("Reload listener failed for " + resourceIdentifier + ": " + e.getMessage());
            }
        }
    }

    /**
     * Dispose this reloadable resource and cleanup
     */
    public void dispose() {
        if (currentResource != null) {
            currentResource.dispose();
        }
        reloadListeners.clear();
        dependencyTimestamps.clear();
    }

    /**
     * Result of a resource load operation
     */
    protected record ResourceLoadResult<T extends ResourceObject>(
            T resource,
            Set<Identifier> dependencies
    ) {
        public static <T extends ResourceObject> ResourceLoadResult<T> of(T resource, Set<Identifier> dependencies) {
            return new ResourceLoadResult<>(resource, dependencies);
        }

        public static <T extends ResourceObject> ResourceLoadResult<T> simple(T resource) {
            return new ResourceLoadResult<>(resource, Collections.emptySet());
        }
    }
}
