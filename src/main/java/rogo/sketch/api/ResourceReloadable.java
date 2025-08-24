package rogo.sketch.api;

import rogo.sketch.util.Identifier;

import java.io.IOException;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Interface for resources that can be reloaded when dependencies or configuration changes
 * 
 * @param <T> The type of resource that can be reloaded
 */
public interface ResourceReloadable<T extends ResourceObject> {
    
    /**
     * Check if this resource needs to be reloaded
     */
    boolean needsReload();
    
    /**
     * Reload the resource if needed
     */
    void reload() throws IOException;
    
    /**
     * Force reload the resource regardless of whether it needs it
     */
    void forceReload() throws IOException;
    
    /**
     * Get the current resource instance
     */
    T getCurrentResource();
    
    /**
     * Get the identifier of this resource
     */
    Identifier getResourceIdentifier();
    
    /**
     * Get the set of dependencies this resource depends on
     */
    Set<Identifier> getDependencies();
    
    /**
     * Check if any dependencies have changed
     */
    boolean hasDependencyChanges();
    
    /**
     * Update the timestamps of all dependencies to current time
     */
    void updateDependencyTimestamps();
    
    /**
     * Add a listener that will be called when the resource is reloaded
     */
    void addReloadListener(Consumer<T> listener);
    
    /**
     * Remove a reload listener
     */
    void removeReloadListener(Consumer<T> listener);
    
    /**
     * Get metadata about the last reload operation
     */
    ReloadMetadata getLastReloadMetadata();
    
    /**
     * Metadata about a reload operation
     */
    record ReloadMetadata(
        boolean successful,
        String errorMessage,
        Set<Identifier> dependencies,
        long timestamp
    ) {
        public static ReloadMetadata success(Set<Identifier> dependencies) {
            return new ReloadMetadata(true, null, dependencies, System.currentTimeMillis());
        }
        
        public static ReloadMetadata failure(String errorMessage, Set<Identifier> dependencies) {
            return new ReloadMetadata(false, errorMessage, dependencies, System.currentTimeMillis());
        }
    }
}
