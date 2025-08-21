package rogo.sketch.render;

import rogo.sketch.api.ResourceObject;
import rogo.sketch.render.resource.ResourceBinding;
import rogo.sketch.render.state.FullRenderState;
import rogo.sketch.util.Identifier;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class RenderSetting implements ResourceObject {
    private final FullRenderState renderState;
    private final ResourceBinding resourceBinding;
    private final RenderParameter renderParameter;
    private final boolean shouldSwitchRenderState;
    private boolean disposed = false;
    
    // Enhanced features for automatic reloading
    private final PartialRenderSetting sourcePartialSetting;
    private final ConcurrentHashMap<Consumer<RenderSetting>, Object> updateListeners = new ConcurrentHashMap<>();

    public RenderSetting(FullRenderState renderState, ResourceBinding resourceBinding, RenderParameter renderParameter, boolean shouldSwitchRenderState) {
        this(renderState, resourceBinding, renderParameter, shouldSwitchRenderState, null);
    }
    
    public RenderSetting(FullRenderState renderState, ResourceBinding resourceBinding, RenderParameter renderParameter, boolean shouldSwitchRenderState, PartialRenderSetting sourcePartialSetting) {
        this.renderState = renderState;
        this.resourceBinding = resourceBinding;
        this.renderParameter = renderParameter;
        this.shouldSwitchRenderState = shouldSwitchRenderState;
        this.sourcePartialSetting = sourcePartialSetting;
        
        // Setup reload listener if source partial setting is provided
        if (sourcePartialSetting != null && sourcePartialSetting.isReloadable()) {
            sourcePartialSetting.addUpdateListener(this::onPartialSettingUpdate);
        }
    }

    public FullRenderState renderState() {
        return renderState;
    }

    public ResourceBinding resourceBinding() {
        return resourceBinding;
    }

    public RenderParameter renderParameter() {
        return renderParameter;
    }

    public boolean shouldSwitchRenderState() {
        return shouldSwitchRenderState;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RenderSetting that = (RenderSetting) o;
        return shouldSwitchRenderState == that.shouldSwitchRenderState && Objects.equals(renderState, that.renderState) && Objects.equals(resourceBinding, that.resourceBinding) && Objects.equals(renderParameter, that.renderParameter);
    }

    @Override
    public int hashCode() {
        return Objects.hash(renderState, resourceBinding, renderParameter, shouldSwitchRenderState);
    }

    @Override
    public int getHandle() {
        return hashCode();
    }

    @Override
    public void dispose() {
        disposed = true;
        
        // Cleanup reload listener
        if (sourcePartialSetting != null && sourcePartialSetting.isReloadable()) {
            sourcePartialSetting.removeUpdateListener(this::onPartialSettingUpdate);
        }
        updateListeners.clear();
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }

    public static RenderSetting fromPartial(PartialRenderSetting partial, RenderParameter renderParameter) {
        return new RenderSetting(
                partial.renderState(),
                partial.resourceBinding(),
                renderParameter,
                partial.shouldSwitchRenderState(),
                partial
        );
    }

    public static RenderSetting basic(FullRenderState renderState, ResourceBinding resourceBinding, RenderParameter renderParameter) {
        return new RenderSetting(
                renderState,
                resourceBinding,
                renderParameter,
                true
        );
    }

    public static RenderSetting computeShader(PartialRenderSetting partial) {
        return new RenderSetting(
                partial.renderState(),
                partial.resourceBinding(),
                null,
                partial.shouldSwitchRenderState(),
                partial
        );
    }
    
    /**
     * Handle partial setting update
     */
    private void onPartialSettingUpdate(PartialRenderSetting newPartialSetting) {
        // Create new RenderSetting with updated partial setting
        RenderSetting updatedSetting = new RenderSetting(
            newPartialSetting.renderState(),
            newPartialSetting.resourceBinding(),
            this.renderParameter,
            newPartialSetting.shouldSwitchRenderState(),
            newPartialSetting
        );
        
        // Notify all listeners about the update
        notifyUpdateListeners(updatedSetting);
    }
    
    /**
     * Register a listener for setting updates
     */
    public void addUpdateListener(Consumer<RenderSetting> listener) {
        updateListeners.put(listener, new Object());
    }
    
    /**
     * Remove an update listener
     */
    public void removeUpdateListener(Consumer<RenderSetting> listener) {
        updateListeners.remove(listener);
    }
    
    /**
     * Notify all listeners about setting update
     */
    private void notifyUpdateListeners(RenderSetting newSetting) {
        for (Consumer<RenderSetting> listener : updateListeners.keySet()) {
            try {
                listener.accept(newSetting);
            } catch (Exception e) {
                System.err.println("Error in RenderSetting update listener: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    /**
     * Check if this setting supports automatic reloading
     */
    public boolean isReloadable() {
        return sourcePartialSetting != null && sourcePartialSetting.isReloadable();
    }
    
    /**
     * Get the source partial setting
     */
    public PartialRenderSetting getSourcePartialSetting() {
        return sourcePartialSetting;
    }
    
    /**
     * Force reload this setting from its source
     */
    public void forceReload() {
        if (sourcePartialSetting != null) {
            sourcePartialSetting.forceReload();
        }
    }
}