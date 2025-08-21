package rogo.sketch.render;

import rogo.sketch.api.ResourceObject;
import rogo.sketch.render.resource.GraphicsResourceManager;
import rogo.sketch.render.resource.ResourceBinding;
import rogo.sketch.render.resource.ResourceTypes;
import rogo.sketch.render.state.FullRenderState;
import rogo.sketch.util.Identifier;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Partial render setting for JSON loading (without render parameters)
 * Enhanced with automatic reload support
 */
public class PartialRenderSetting implements ResourceObject {
    protected final FullRenderState renderState;
    protected final ResourceBinding resourceBinding;
    protected final boolean shouldSwitchRenderState;
    private boolean disposed = false;
    
    // Enhanced features for automatic reloading
    private final Identifier sourceIdentifier;
    private static final ConcurrentHashMap<Identifier, PartialRenderSetting> activeSettings = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Consumer<PartialRenderSetting>, Object> updateListeners = new ConcurrentHashMap<>();

    public PartialRenderSetting(FullRenderState renderState, ResourceBinding resourceBinding, boolean shouldSwitchRenderState) {
        this(renderState, resourceBinding, shouldSwitchRenderState, null);
    }
    
    public PartialRenderSetting(FullRenderState renderState, ResourceBinding resourceBinding, boolean shouldSwitchRenderState, Identifier sourceIdentifier) {
        this.renderState = renderState;
        this.resourceBinding = resourceBinding;
        this.shouldSwitchRenderState = shouldSwitchRenderState;
        this.sourceIdentifier = sourceIdentifier;
        
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
        if (sourceIdentifier != null) {
            Optional<PartialRenderSetting> reloaded = GraphicsResourceManager.getInstance()
                .getResource(ResourceTypes.PARTIAL_RENDER_SETTING, sourceIdentifier);
            
            if (reloaded.isPresent()) {
                onSourceResourceReload(sourceIdentifier, reloaded.get());
            }
        }
    }
}