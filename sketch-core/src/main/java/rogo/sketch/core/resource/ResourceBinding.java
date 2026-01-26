package rogo.sketch.core.resource;

import rogo.sketch.core.api.BindingResource;
import rogo.sketch.core.api.ResourceObject;
import rogo.sketch.core.api.ShaderProvider;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.util.KeyId;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class ResourceBinding {
    // Map: ResourceType -> (BindingName -> ResourceIdentifier)
    private final Map<KeyId, Map<KeyId, KeyId>> bindings = new HashMap<>();
    private int hash = 0;

    public ResourceBinding() {
    }

    /**
     * Add a resource binding
     *
     * @param resourceType       Type of resource (e.g., "texture", "ssbo", "ubo")
     * @param bindingName        Name used in shader to reference this resource
     * @param resourceKeyId Identifier of the actual resource
     */
    public void addBinding(KeyId resourceType, KeyId bindingName, KeyId resourceKeyId) {
        bindings.computeIfAbsent(resourceType, k -> new HashMap<>()).put(bindingName, resourceKeyId);
        hash = Objects.hash(bindings);
    }

    /**
     * Get resource identifier by type and binding name
     */
    public KeyId getResourceIdentifier(KeyId resourceType, KeyId bindingName) {
        Map<KeyId, KeyId> typeBindings = bindings.get(resourceType);
        return typeBindings != null ? typeBindings.get(bindingName) : null;
    }

    /**
     * Get all bindings for a specific resource type
     */
    public Map<KeyId, KeyId> getBindingsForType(KeyId resourceType) {
        return bindings.getOrDefault(resourceType, new HashMap<>());
    }

    /**
     * Get all resource types that have bindings
     */
    public Set<KeyId> getResourceTypes() {
        return bindings.keySet();
    }

    /**
     * Check if a binding exists
     */
    public boolean hasBinding(KeyId resourceType, KeyId bindingName) {
        Map<KeyId, KeyId> typeBindings = bindings.get(resourceType);
        return typeBindings != null && typeBindings.containsKey(bindingName);
    }

    /**
     * Remove a binding
     */
    public void removeBinding(KeyId resourceType, KeyId bindingName) {
        Map<KeyId, KeyId> typeBindings = bindings.get(resourceType);
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
    public Map<KeyId, Map<KeyId, KeyId>> getAllBindings() {
        return new HashMap<>(bindings);
    }

    /**
     * Merge another ResourceBinding into this one
     */
    public void merge(ResourceBinding other) {
        for (Map.Entry<KeyId, Map<KeyId, KeyId>> typeEntry : other.bindings.entrySet()) {
            KeyId resourceType = typeEntry.getKey();
            Map<KeyId, KeyId> otherBindings = typeEntry.getValue();

            Map<KeyId, KeyId> currentBindings = bindings.computeIfAbsent(resourceType, k -> new HashMap<>());
            currentBindings.putAll(otherBindings);
        }
    }

    /**
     * Bind all resources to their appropriate shader slots
     * Called by RenderStateManager when switching resource bindings
     */
    public void bind(RenderContext context) {
        ShaderProvider shader = context.shaderProvider();

        if (shader != null) {
            for (Map.Entry<KeyId, Map<KeyId, KeyId>> typeEntry : bindings.entrySet()) {
                KeyId resourceType = typeEntry.getKey();
                if (shader.getResourceBindings().containsKey(resourceType)) {
                    Map<KeyId, KeyId> typeBindings = typeEntry.getValue();

                    for (Map.Entry<KeyId, KeyId> bindingEntry : typeBindings.entrySet()) {
                        KeyId bindingName = bindingEntry.getKey();
                        KeyId resourceKeyId = bindingEntry.getValue();

                        if (shader.getResourceBindings().get(resourceType).containsKey(bindingName)) {
                            int binding = shader.getResourceBindings().get(resourceType).get(bindingName);
                            bindResource(resourceType, binding, resourceKeyId);
                        }
                    }
                }
            }
        }
    }

    /**
     * Bind a single resource to the context using cached ResourceReference
     */
    private void bindResource(KeyId resourceType, int binding, KeyId resourceKeyId) {
        ResourceReference<? extends ResourceObject> reference = GraphicsResourceManager.getInstance().getReference(resourceType, resourceKeyId);

        reference.ifPresent(resource -> {
            if (resource instanceof BindingResource bindingResource) {
                bindingResource.bind(resourceType, binding);
            }
        });
    }


    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        ResourceBinding that = (ResourceBinding) obj;
        return Objects.equals(bindings, that.bindings);
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public String toString() {
        return "ResourceBinding{" + bindings + '}';
    }
}