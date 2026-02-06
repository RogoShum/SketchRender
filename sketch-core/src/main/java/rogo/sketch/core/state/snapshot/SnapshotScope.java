package rogo.sketch.core.state.snapshot;

import rogo.sketch.core.api.ShaderProvider;
import rogo.sketch.core.pipeline.RenderSetting;
import rogo.sketch.core.resource.ResourceBinding;
import rogo.sketch.core.resource.ResourceTypes;
import rogo.sketch.core.state.FullRenderState;
import rogo.sketch.core.util.KeyId;

import java.util.*;

/**
 * Defines the scope of GL states to capture in a snapshot.
 * Allows for optimized snapshot capture by only querying states that will be modified.
 */
public class SnapshotScope {
    
    /**
     * State types that can be captured
     */
    public enum StateType {
        BLEND,
        DEPTH_TEST,
        DEPTH_MASK,
        CULL,
        SCISSOR,
        STENCIL,
        VIEWPORT,
        COLOR_MASK,
        POLYGON_OFFSET,
        LOGIC_OP,
        SHADER_PROGRAM,
        VAO,
        FBO
    }
    
    private final EnumSet<StateType> stateTypes;
    private final int[] textureUnits;       // Texture units to capture
    private final int[] ssboBindings;       // SSBO binding points to capture
    private final int[] uboBindings;        // UBO binding points to capture
    private final int[] imageBindings;      // Image binding points to capture
    private final boolean captureAll;       // If true, capture all states
    
    private SnapshotScope(EnumSet<StateType> stateTypes, int[] textureUnits, 
                          int[] ssboBindings, int[] uboBindings, int[] imageBindings,
                          boolean captureAll) {
        this.stateTypes = stateTypes;
        this.textureUnits = textureUnits;
        this.ssboBindings = ssboBindings;
        this.uboBindings = uboBindings;
        this.imageBindings = imageBindings;
        this.captureAll = captureAll;
    }
    
    /**
     * Create a scope that captures all states (full snapshot)
     */
    public static SnapshotScope full() {
        return new SnapshotScope(
                EnumSet.allOf(StateType.class),
                new int[0],
                new int[0],
                new int[0],
                new int[0],
                true
        );
    }
    
    /**
     * Create an empty scope (captures nothing)
     */
    public static SnapshotScope empty() {
        return new SnapshotScope(
                EnumSet.noneOf(StateType.class),
                new int[0],
                new int[0],
                new int[0],
                new int[0],
                false
        );
    }
    
    /**
     * Create a scope from a RenderSetting
     */
    public static SnapshotScope fromRenderSetting(RenderSetting setting) {
        Builder builder = builder();
        
        // Add states from render state
        if (setting != null && setting.renderState() != null) {
            builder.addStatesFromRenderState(setting.renderState());
        }
        
        // Add bindings from resource binding
        if (setting != null && setting.resourceBinding() != null) {
            builder.addBindingsFromResourceBinding(setting.resourceBinding());
        }
        
        return builder.build();
    }
    
    /**
     * Create a scope from a ShaderProvider (captures bindings discovered in shader)
     */
    public static SnapshotScope fromShader(ShaderProvider shader) {
        if (shader == null) {
            return empty();
        }
        
        Builder builder = builder();
        builder.addState(StateType.SHADER_PROGRAM);
        
        Map<KeyId, Map<KeyId, Integer>> bindings = shader.getResourceBindings();
        
        // Texture bindings
        if (bindings.containsKey(ResourceTypes.TEXTURE)) {
            for (Integer unit : bindings.get(ResourceTypes.TEXTURE).values()) {
                builder.addTextureUnit(unit);
            }
        }
        
        // Image bindings
        if (bindings.containsKey(ResourceTypes.IMAGE_BUFFER)) {
            for (Integer unit : bindings.get(ResourceTypes.IMAGE_BUFFER).values()) {
                builder.addImageBinding(unit);
            }
        }
        
        // SSBO bindings
        if (bindings.containsKey(ResourceTypes.SHADER_STORAGE_BUFFER)) {
            for (Integer binding : bindings.get(ResourceTypes.SHADER_STORAGE_BUFFER).values()) {
                builder.addSSBOBinding(binding);
            }
        }
        
        // UBO bindings
        if (bindings.containsKey(ResourceTypes.UNIFORM_BLOCK)) {
            for (Integer binding : bindings.get(ResourceTypes.UNIFORM_BLOCK).values()) {
                builder.addUBOBinding(binding);
            }
        }
        
        return builder.build();
    }
    
    /**
     * Combine multiple scopes into one
     */
    public static SnapshotScope combine(SnapshotScope... scopes) {
        Builder builder = builder();
        
        for (SnapshotScope scope : scopes) {
            if (scope.captureAll) {
                return full();
            }
            
            builder.stateTypes.addAll(scope.stateTypes);
            builder.textureUnits.addAll(toList(scope.textureUnits));
            builder.ssboBindings.addAll(toList(scope.ssboBindings));
            builder.uboBindings.addAll(toList(scope.uboBindings));
            builder.imageBindings.addAll(toList(scope.imageBindings));
        }
        
        return builder.build();
    }
    
    private static List<Integer> toList(int[] array) {
        List<Integer> list = new ArrayList<>();
        for (int i : array) {
            list.add(i);
        }
        return list;
    }
    
    // ==================== Accessors ====================
    
    public boolean shouldCapture(StateType type) {
        return captureAll || stateTypes.contains(type);
    }
    
    public boolean shouldCaptureAll() {
        return captureAll;
    }
    
    public EnumSet<StateType> getStateTypes() {
        return stateTypes;
    }
    
    public int[] getTextureUnits() {
        return textureUnits;
    }
    
    public int[] getSSBOBindings() {
        return ssboBindings;
    }
    
    public int[] getUBOBindings() {
        return uboBindings;
    }
    
    public int[] getImageBindings() {
        return imageBindings;
    }
    
    public boolean isEmpty() {
        return !captureAll && stateTypes.isEmpty() && 
               textureUnits.length == 0 && ssboBindings.length == 0 && 
               uboBindings.length == 0 && imageBindings.length == 0;
    }
    
    // ==================== Builder ====================
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private final EnumSet<StateType> stateTypes = EnumSet.noneOf(StateType.class);
        private final Set<Integer> textureUnits = new TreeSet<>();
        private final Set<Integer> ssboBindings = new TreeSet<>();
        private final Set<Integer> uboBindings = new TreeSet<>();
        private final Set<Integer> imageBindings = new TreeSet<>();
        
        public Builder addState(StateType type) {
            stateTypes.add(type);
            return this;
        }
        
        public Builder addStates(StateType... types) {
            for (StateType type : types) {
                stateTypes.add(type);
            }
            return this;
        }
        
        public Builder addStatesFromRenderState(FullRenderState state) {
            if (state == null) return this;
            
            // Add all state types that the render state might modify
            // In practice, we should check which components are non-default
            stateTypes.add(StateType.BLEND);
            stateTypes.add(StateType.DEPTH_TEST);
            stateTypes.add(StateType.DEPTH_MASK);
            stateTypes.add(StateType.CULL);
            stateTypes.add(StateType.SCISSOR);
            stateTypes.add(StateType.STENCIL);
            stateTypes.add(StateType.VIEWPORT);
            stateTypes.add(StateType.COLOR_MASK);
            stateTypes.add(StateType.POLYGON_OFFSET);
            stateTypes.add(StateType.LOGIC_OP);
            stateTypes.add(StateType.FBO);
            
            return this;
        }
        
        public Builder addBindingsFromResourceBinding(ResourceBinding binding) {
            if (binding == null) return this;
            
            Map<KeyId, Map<KeyId, KeyId>> allBindings = binding.getAllBindings();
            
            // We don't know the actual binding points from ResourceBinding
            // Those are determined at bind time from the shader
            // So we just mark that we need to capture the shader program
            if (!allBindings.isEmpty()) {
                stateTypes.add(StateType.SHADER_PROGRAM);
            }
            
            return this;
        }
        
        public Builder addTextureUnit(int unit) {
            textureUnits.add(unit);
            return this;
        }
        
        public Builder addSSBOBinding(int binding) {
            ssboBindings.add(binding);
            return this;
        }
        
        public Builder addUBOBinding(int binding) {
            uboBindings.add(binding);
            return this;
        }
        
        public Builder addImageBinding(int binding) {
            imageBindings.add(binding);
            return this;
        }
        
        public SnapshotScope build() {
            return new SnapshotScope(
                    EnumSet.copyOf(stateTypes),
                    toIntArray(textureUnits),
                    toIntArray(ssboBindings),
                    toIntArray(uboBindings),
                    toIntArray(imageBindings),
                    false
            );
        }
        
        private int[] toIntArray(Set<Integer> set) {
            int[] array = new int[set.size()];
            int i = 0;
            for (Integer value : set) {
                array[i++] = value;
            }
            return array;
        }
    }
}


