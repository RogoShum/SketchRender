package rogo.sketch.render;

import rogo.sketch.api.ResourceObject;
import rogo.sketch.api.ResourceReloadable;
import rogo.sketch.render.resource.GraphicsResourceManager;
import rogo.sketch.render.resource.ReloadableResourceSupport;
import rogo.sketch.render.resource.ResourceBinding;
import rogo.sketch.render.resource.ResourceTypes;
import rogo.sketch.render.state.FullRenderState;
import rogo.sketch.util.Identifier;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Partial render setting for JSON loading (without render parameters)
 * Enhanced with automatic reload support using the generic reloadable system
 */
public class PartialRenderSetting implements ResourceObject, ResourceReloadable<PartialRenderSetting> {
    protected final FullRenderState renderState;
    protected final ResourceBinding resourceBinding;
    protected final boolean shouldSwitchRenderState;
    private boolean disposed = false;
    
    // Enhanced features for automatic reloading
    private final Identifier sourceIdentifier;
    private static final ConcurrentHashMap<Identifier, PartialRenderSetting> activeSettings = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Consumer<PartialRenderSetting>, Object> updateListeners = new ConcurrentHashMap<>();
    
    // Reloadable support for generic resource reloading
    private ReloadableResourceSupport<PartialRenderSetting> reloadableSupport;

    public PartialRenderSetting(FullRenderState renderState, ResourceBinding resourceBinding, boolean shouldSwitchRenderState) {
        this(renderState, resourceBinding, shouldSwitchRenderState, null, null);
    }
    
    public PartialRenderSetting(FullRenderState renderState, ResourceBinding resourceBinding, boolean shouldSwitchRenderState, Identifier sourceIdentifier) {
        this(renderState, resourceBinding, shouldSwitchRenderState, sourceIdentifier, null);
    }
    
    public PartialRenderSetting(FullRenderState renderState, 
                               ResourceBinding resourceBinding, 
                               boolean shouldSwitchRenderState, 
                               Identifier sourceIdentifier,
                               Function<Identifier, Optional<BufferedReader>> resourceProvider) {
        this.renderState = renderState;
        this.resourceBinding = resourceBinding;
        this.shouldSwitchRenderState = shouldSwitchRenderState;
        this.sourceIdentifier = sourceIdentifier;
        
        // Setup reloadable support if both identifier and provider are given
        if (sourceIdentifier != null && resourceProvider != null) {
            this.reloadableSupport = new ReloadableResourceSupport<PartialRenderSetting>(sourceIdentifier, resourceProvider) {
                @Override
                protected ResourceLoadResult<PartialRenderSetting> performReload() throws IOException {
                    // Reload from resource provider
                    Optional<PartialRenderSetting> reloaded = GraphicsResourceManager.getInstance()
                        .getResource(ResourceTypes.PARTIAL_RENDER_SETTING, sourceIdentifier);
                    
                    if (reloaded.isPresent()) {
                        return ResourceLoadResult.simple(reloaded.get());
                    } else {
                        throw new IOException("Failed to reload PartialRenderSetting: " + sourceIdentifier);
                    }
                }
            };
        }
        
        // Register for automatic reloading if source identifier is provided
        if (sourceIdentifier != null) {
            activeSettings.put(sourceIdentifier, this);
            setupReloadListener();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PartialRenderSetting that = (PartialRenderSetting) o;
        return shouldSwitchRenderState == that.shouldSwitchRenderState && Objects.equals(renderState, that.renderState) && Objects.equals(resourceBinding, that.resourceBinding);
    }

    @Override
    public int hashCode() {
        return Objects.hash(renderState, resourceBinding, shouldSwitchRenderState);
    }

    @Override
    public int getHandle() {
        return hashCode();
    }

    @Override
    public void dispose() {
        disposed = true;
        
        // Cleanup reload listener and active settings
        if (sourceIdentifier != null) {
            activeSettings.remove(sourceIdentifier);
            GraphicsResourceManager.getInstance().removeReloadListener(ResourceTypes.PARTIAL_RENDER_SETTING, sourceIdentifier);
        }
        updateListeners.clear();
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }

    public FullRenderState renderState() {
        return renderState;
    }

    public ResourceBinding resourceBinding() {
        return resourceBinding;
    }

    public boolean shouldSwitchRenderState() {
        return shouldSwitchRenderState;
    }

    /**
     * Create a basic partial render setting
     */
    public static PartialRenderSetting basic(FullRenderState renderState, ResourceBinding resourceBinding) {
        return new PartialRenderSetting(renderState, resourceBinding, true);
    }

    /**
     * Create a compute shader partial setting
     */
    public static PartialRenderSetting computeShader(ResourceBinding resourceBinding) {
        return new PartialRenderSetting(null, resourceBinding, false);
    }
    
    /**
     * Create a reloadable partial render setting with source tracking
     */
    public static PartialRenderSetting reloadable(FullRenderState renderState, ResourceBinding resourceBinding, 
                                                  boolean shouldSwitchRenderState, Identifier sourceIdentifier) {
        return new PartialRenderSetting(renderState, resourceBinding, shouldSwitchRenderState, sourceIdentifier);
    }
    
    /**
     * Setup reload listener for this setting
     */
    private void setupReloadListener() {
        GraphicsResourceManager.getInstance().registerReloadListener(
            ResourceTypes.PARTIAL_RENDER_SETTING, 
            sourceIdentifier, 
            this::onSourceResourceReload
        );
    }
    
    /**
     * Handle resource reload
     */
    private void onSourceResourceReload(Identifier resourceName, ResourceObject newResource) {
        if (newResource instanceof PartialRenderSetting newSetting) {
            // Update internal state (create new immutable copy)
            PartialRenderSetting updatedSetting = new PartialRenderSetting(
                newSetting.renderState,
                newSetting.resourceBinding,
                newSetting.shouldSwitchRenderState,
                this.sourceIdentifier
            );
            
            // Notify all listeners about the update
            notifyUpdateListeners(updatedSetting);
        }
    }
    
    /**
     * Register a listener for setting updates
     */
    public void addUpdateListener(Consumer<PartialRenderSetting> listener) {
        updateListeners.put(listener, new Object());
    }
    
    /**
     * Remove an update listener
     */
    public void removeUpdateListener(Consumer<PartialRenderSetting> listener) {
        updateListeners.remove(listener);
    }
    
    /**
     * Notify all listeners about setting update
     */
    private void notifyUpdateListeners(PartialRenderSetting newSetting) {
        for (Consumer<PartialRenderSetting> listener : updateListeners.keySet()) {
            try {
                listener.accept(newSetting);
            } catch (Exception e) {
                System.err.println("Error in PartialRenderSetting update listener: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    /**
     * Get the source identifier for this setting
     */
    public Identifier getSourceIdentifier() {
        return sourceIdentifier;
    }
    
    /**
     * Check if this setting supports automatic reloading
     */
    public boolean isReloadable() {
        return sourceIdentifier != null;
    }
    
    /**
     * Get an active setting by its source identifier
     */
    public static Optional<PartialRenderSetting> getActiveSetting(Identifier sourceIdentifier) {
        return Optional.ofNullable(activeSettings.get(sourceIdentifier));
    }
    
    /**
     * Force reload a setting from its source
     */
    public void forceReload() {
        if (reloadableSupport != null) {
            try {
                reloadableSupport.forceReload();
            } catch (IOException e) {
                System.err.println("Failed to force reload PartialRenderSetting: " + e.getMessage());
            }
        } else if (sourceIdentifier != null) {
            Optional<PartialRenderSetting> reloaded = GraphicsResourceManager.getInstance()
                .getResource(ResourceTypes.PARTIAL_RENDER_SETTING, sourceIdentifier);
            
            if (reloaded.isPresent()) {
                onSourceResourceReload(sourceIdentifier, reloaded.get());
            }
        }
    }
    
    /**
     * Create a fully reloadable partial render setting with resource provider
     */
    public static PartialRenderSetting fullyReloadable(FullRenderState renderState, 
                                                       ResourceBinding resourceBinding, 
                                                       boolean shouldSwitchRenderState, 
                                                       Identifier sourceIdentifier,
                                                       Function<Identifier, Optional<BufferedReader>> resourceProvider) {
        return new PartialRenderSetting(renderState, resourceBinding, shouldSwitchRenderState, sourceIdentifier, resourceProvider);
    }
    
    // ResourceReloadable implementation
    
    @Override
    public boolean needsReload() {
        return reloadableSupport != null && reloadableSupport.needsReload();
    }
    
    @Override
    public void reload() throws IOException {
        if (reloadableSupport != null) {
            reloadableSupport.reload();
        }
    }
    
    @Override
    public PartialRenderSetting getCurrentResource() {
        return reloadableSupport != null ? reloadableSupport.getCurrentResource() : this;
    }
    
    @Override
    public Identifier getResourceIdentifier() {
        return sourceIdentifier != null ? sourceIdentifier : Identifier.of("unknown");
    }
    
    @Override
    public Set<Identifier> getDependencies() {
        return reloadableSupport != null ? reloadableSupport.getDependencies() : Collections.emptySet();
    }
    
    @Override
    public boolean hasDependencyChanges() {
        return reloadableSupport != null && reloadableSupport.hasDependencyChanges();
    }
    
    @Override
    public void updateDependencyTimestamps() {
        if (reloadableSupport != null) {
            reloadableSupport.updateDependencyTimestamps();
        }
    }
    
    @Override
    public void addReloadListener(Consumer<PartialRenderSetting> listener) {
        if (reloadableSupport != null) {
            reloadableSupport.addReloadListener(listener);
        }
        // Also add to local listeners for backward compatibility
        updateListeners.put(listener, new Object());
    }
    
    @Override
    public void removeReloadListener(Consumer<PartialRenderSetting> listener) {
        if (reloadableSupport != null) {
            reloadableSupport.removeReloadListener(listener);
        }
        updateListeners.remove(listener);
    }
    
    @Override
    public ReloadMetadata getLastReloadMetadata() {
        return reloadableSupport != null ? reloadableSupport.getLastReloadMetadata() : null;
    }
}