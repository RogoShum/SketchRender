package rogo.sketch.render.resource;

import rogo.sketch.render.RenderContext;
import rogo.sketch.util.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ResourceBinding {
    // Map: ResourceType -> (BindingName -> ResourceIdentifier)
    private final Map<Identifier, Map<Identifier, Identifier>> bindings = new HashMap<>();
    
    public ResourceBinding() {
    }
    
    /**
     * Add a resource binding
     * @param resourceType Type of resource (e.g., "texture", "ssbo", "ubo")
     * @param bindingName Name used in shader to reference this resource
     * @param resourceIdentifier Identifier of the actual resource
     */
    public void addBinding(Identifier resourceType, Identifier bindingName, Identifier resourceIdentifier) {
        bindings.computeIfAbsent(resourceType, k -> new HashMap<>())
                .put(bindingName, resourceIdentifier);
    }
    
    /**
     * Get resource identifier by type and binding name
     */
    public Identifier getResourceIdentifier(Identifier resourceType, Identifier bindingName) {
        Map<Identifier, Identifier> typeBindings = bindings.get(resourceType);
        return typeBindings != null ? typeBindings.get(bindingName) : null;
    }
    
    /**
     * Get all bindings for a specific resource type
     */
    public Map<Identifier, Identifier> getBindingsForType(Identifier resourceType) {
        return bindings.getOrDefault(resourceType, new HashMap<>());
    }
    
    /**
     * Get all resource types that have bindings
     */
    public Set<Identifier> getResourceTypes() {
        return bindings.keySet();
    }
    
    /**
     * Check if a binding exists
     */
    public boolean hasBinding(Identifier resourceType, Identifier bindingName) {
        Map<Identifier, Identifier> typeBindings = bindings.get(resourceType);
        return typeBindings != null && typeBindings.containsKey(bindingName);
    }
    
    /**
     * Remove a binding
     */
    public void removeBinding(Identifier resourceType, Identifier bindingName) {
        Map<Identifier, Identifier> typeBindings = bindings.get(resourceType);
        if (typeBindings != null) {
            typeBindings.remove(bindingName);
            if (typeBindings.isEmpty()) {
                bindings.remove(resourceType);
            }
        }
    }
    
    /**
     * Clear all bindings
     */
    public void clear() {
        bindings.clear();
    }
    
    /**
     * Get all bindings as a map
     */
    public Map<Identifier, Map<Identifier, Identifier>> getAllBindings() {
        return new HashMap<>(bindings);
    }
    
    /**
     * Merge another ResourceBinding into this one
     */
    public void merge(ResourceBinding other) {
        for (Map.Entry<Identifier, Map<Identifier, Identifier>> typeEntry : other.bindings.entrySet()) {
            Identifier resourceType = typeEntry.getKey();
            Map<Identifier, Identifier> otherBindings = typeEntry.getValue();
            
            Map<Identifier, Identifier> currentBindings = bindings.computeIfAbsent(resourceType, k -> new HashMap<>());
            currentBindings.putAll(otherBindings);
        }
    }
    
    /**
     * Bind all resources to their appropriate shader slots
     * Called by RenderStateManager when switching resource bindings
     */
    public void bind(RenderContext context) {
        // TODO: Implement resource binding based on context and available resource managers
        // This would typically:
        // 1. Get GraphicsResourceManager from context
        // 2. For each resource type and binding, get the actual resource
        // 3. Bind the resource to the appropriate OpenGL slot (texture units, SSBOs, etc.)
        
        // For now, this is a placeholder - actual implementation would depend on
        // the specifics of how resources are managed and bound in the system
        for (Map.Entry<Identifier, Map<Identifier, Identifier>> typeEntry : bindings.entrySet()) {
            Identifier resourceType = typeEntry.getKey();
            Map<Identifier, Identifier> typeBindings = typeEntry.getValue();
            
            for (Map.Entry<Identifier, Identifier> bindingEntry : typeBindings.entrySet()) {
                Identifier bindingName = bindingEntry.getKey();
                Identifier resourceIdentifier = bindingEntry.getValue();
                
                // Bind resource to context based on type
                bindResource(context, resourceType, bindingName, resourceIdentifier);
            }
        }
    }
    
    /**
     * Bind a single resource to the context
     */
    private void bindResource(RenderContext context, Identifier resourceType, Identifier bindingName, Identifier resourceIdentifier) {
        // TODO: Implement specific resource binding logic
        // This would switch on resourceType and handle different binding strategies
        
        // Example structure:
        // switch (resourceType.toString()) {
        //     case "texture" -> bindTexture(context, bindingName, resourceIdentifier);
        //     case "ssbo" -> bindSSBO(context, bindingName, resourceIdentifier);
        //     case "ubo" -> bindUBO(context, bindingName, resourceIdentifier);
        //     // etc.
        // }
    }
    
    @Override
    public String toString() {
        return "ResourceBinding{" + bindings + '}';
    }
}