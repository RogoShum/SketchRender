package rogo.sketch.render.resource;

import rogo.sketch.api.BindingResource;
import rogo.sketch.api.ResourceObject;
import rogo.sketch.api.ShaderProvider;
import rogo.sketch.render.RenderContext;
import rogo.sketch.util.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class ResourceBinding {
    // Map: ResourceType -> (BindingName -> ResourceIdentifier)
    private final Map<Identifier, Map<Identifier, Identifier>> bindings = new HashMap<>();

    public ResourceBinding() {
    }

    /**
     * Add a resource binding
     *
     * @param resourceType       Type of resource (e.g., "texture", "ssbo", "ubo")
     * @param bindingName        Name used in shader to reference this resource
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
        ShaderProvider shader = context.shaderProvider();

        if (shader != null) {
            for (Map.Entry<Identifier, Map<Identifier, Identifier>> typeEntry : bindings.entrySet()) {
                Identifier resourceType = typeEntry.getKey();
                if (shader.getResourceBindings().containsKey(resourceType)) {
                    Map<Identifier, Identifier> typeBindings = typeEntry.getValue();

                    for (Map.Entry<Identifier, Identifier> bindingEntry : typeBindings.entrySet()) {
                        Identifier bindingName = bindingEntry.getKey();
                        Identifier resourceIdentifier = bindingEntry.getValue();

                        if (shader.getResourceBindings().get(resourceType).containsKey(bindingName)) {
                            int binding = shader.getResourceBindings().get(resourceType).get(bindingName);
                            bindResource(resourceType, binding, resourceIdentifier);
                        }
                    }
                }
            }
        }
    }

    /**
     * Bind a single resource to the context using cached ResourceReference
     */
    private void bindResource(Identifier resourceType, int binding, Identifier resourceIdentifier) {
        ResourceReference<? extends ResourceObject> reference = GraphicsResourceManager.getInstance().getReference(resourceType, resourceIdentifier);

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
        return Objects.hash(bindings);
    }

    @Override
    public String toString() {
        return "ResourceBinding{" + bindings + '}';
    }
}