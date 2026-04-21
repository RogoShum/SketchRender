package rogo.sketch.core.resource;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import rogo.sketch.core.backend.BackendInstalledBindableResource;
import rogo.sketch.core.driver.GraphicsDriver;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.shader.ShaderProgramHandle;
import rogo.sketch.core.util.KeyId;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class ResourceBinding {
    private static final FlatBindingEntry[] EMPTY_ENTRIES = new FlatBindingEntry[0];

    // Map: ResourceType -> (BindingName -> binding spec)
    private final Object2ObjectOpenHashMap<KeyId, Object2ObjectOpenHashMap<KeyId, BindingSpec>> bindings = new Object2ObjectOpenHashMap<>();
    private FlatBindingEntry[] flatEntries = EMPTY_ENTRIES;
    private int hash = 0;
    private int layoutHash = 0;
    private boolean flatEntriesDirty = true;

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
        addBinding(
                resourceType,
                bindingName,
                resourceKeyId,
                ResourceViewRole.defaultForResourceType(resourceType),
                null);
    }

    public void addBinding(
            KeyId resourceType,
            KeyId bindingName,
            KeyId resourceKeyId,
            ResourceViewRole viewRole,
            ResourceAccess access) {
        KeyId normalizedType = ResourceTypes.normalize(resourceType);
        ResourceViewRole resolvedRole = viewRole != null ? viewRole : ResourceViewRole.defaultForResourceType(normalizedType);
        ResourceAccess resolvedAccess = access != null ? access : ResourceViewRole.defaultAccessFor(resolvedRole);
        bindings.computeIfAbsent(normalizedType, k -> new Object2ObjectOpenHashMap<>())
                .put(bindingName, new BindingSpec(resourceKeyId, resolvedRole, resolvedAccess));
        markDirty();
    }

    /**
     * Get resource identifier by type and binding name
     */
    public KeyId getResourceIdentifier(KeyId resourceType, KeyId bindingName) {
        Object2ObjectOpenHashMap<KeyId, BindingSpec> typeBindings = bindings.get(ResourceTypes.normalize(resourceType));
        BindingSpec spec = typeBindings != null ? typeBindings.get(bindingName) : null;
        return spec != null ? spec.resourceId() : null;
    }

    /**
     * Get all bindings for a specific resource type
     */
    public Map<KeyId, KeyId> getBindingsForType(KeyId resourceType) {
        Object2ObjectOpenHashMap<KeyId, BindingSpec> typeBindings = bindings.get(ResourceTypes.normalize(resourceType));
        Object2ObjectOpenHashMap<KeyId, KeyId> copy = new Object2ObjectOpenHashMap<>();
        if (typeBindings != null) {
            for (Object2ObjectMap.Entry<KeyId, BindingSpec> entry : typeBindings.object2ObjectEntrySet()) {
                copy.put(entry.getKey(), entry.getValue().resourceId());
            }
        }
        return copy;
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
        Map<KeyId, BindingSpec> typeBindings = bindings.get(ResourceTypes.normalize(resourceType));
        return typeBindings != null && typeBindings.containsKey(bindingName);
    }

    /**
     * Remove a binding
     */
    public void removeBinding(KeyId resourceType, KeyId bindingName) {
        KeyId normalizedType = ResourceTypes.normalize(resourceType);
            Object2ObjectOpenHashMap<KeyId, BindingSpec> typeBindings = bindings.get(normalizedType);
        if (typeBindings != null) {
            typeBindings.remove(bindingName);
            if (typeBindings.isEmpty()) {
                bindings.remove(normalizedType);
            }
        }
        markDirty();
    }

    /**
     * Clear all bindings
     */
    public void clear() {
        bindings.clear();
        flatEntries = EMPTY_ENTRIES;
        hash = 0;
        layoutHash = 0;
        flatEntriesDirty = false;
    }

    /**
     * Get all bindings as a map
     */
    public Map<KeyId, Map<KeyId, KeyId>> getAllBindings() {
        Object2ObjectOpenHashMap<KeyId, Map<KeyId, KeyId>> copy = new Object2ObjectOpenHashMap<>();
        for (Object2ObjectMap.Entry<KeyId, Object2ObjectOpenHashMap<KeyId, BindingSpec>> entry : bindings.object2ObjectEntrySet()) {
            Object2ObjectOpenHashMap<KeyId, KeyId> typeCopy = new Object2ObjectOpenHashMap<>();
            for (Object2ObjectMap.Entry<KeyId, BindingSpec> bindingEntry : entry.getValue().object2ObjectEntrySet()) {
                typeCopy.put(bindingEntry.getKey(), bindingEntry.getValue().resourceId());
            }
            copy.put(entry.getKey(), typeCopy);
        }
        return copy;
    }

    /**
     * Merge another ResourceBinding into this one
     */
    public void merge(ResourceBinding other) {
        for (Object2ObjectMap.Entry<KeyId, Object2ObjectOpenHashMap<KeyId, BindingSpec>> typeEntry : other.bindings.object2ObjectEntrySet()) {
            KeyId resourceType = ResourceTypes.normalize(typeEntry.getKey());
            Object2ObjectOpenHashMap<KeyId, BindingSpec> otherBindings = typeEntry.getValue();

            Object2ObjectOpenHashMap<KeyId, BindingSpec> currentBindings = bindings.computeIfAbsent(resourceType, k -> new Object2ObjectOpenHashMap<>());
            currentBindings.putAll(otherBindings);
        }
        markDirty();
    }

    /**
     * Bind all resources to their appropriate shader slots
     * Called by RenderStateManager when switching resource bindings
     */
    public void bind(RenderContext context) {
        ShaderProgramHandle shader = context != null ? context.shaderProgramHandle() : null;

        if (shader != null) {
            Map<KeyId, Map<KeyId, Integer>> resourceBindings = shader.interfaceSpec().resourceBindings();
            KeyId currentType = null;
            Map<KeyId, Integer> currentTypeBindings = null;
            FlatBindingEntry[] entries = flatEntries();
            for (int i = 0; i < entries.length; i++) {
                FlatBindingEntry entry = entries[i];
                if (!Objects.equals(currentType, entry.resourceType())) {
                    currentType = entry.resourceType();
                    currentTypeBindings = resourceBindings.get(currentType);
                }
                if (currentTypeBindings == null || currentTypeBindings.isEmpty()) {
                    continue;
                }
                Integer binding = currentTypeBindings.get(entry.bindingName());
                if (binding != null) {
                    bindResource(entry.resourceType(), binding, entry.resourceId(), entry.viewRole(), entry.access());
                }
            }
        }
    }

    public FlatBindingEntry[] flatEntries() {
        ensureFlatEntries();
        return flatEntries;
    }

    public int resourceBindingHash() {
        ensureFlatEntries();
        return hash;
    }

    public int layoutHash() {
        ensureFlatEntries();
        return layoutHash;
    }

    /**
     * Bind a single resource to the context using cached ResourceReference
     */
    private void bindResource(
            KeyId resourceType,
            int binding,
            KeyId resourceKeyId,
            ResourceViewRole viewRole,
            ResourceAccess access) {
        KeyId normalizedType = ResourceTypes.normalize(resourceType);
        BackendInstalledBindableResource installedResource = GraphicsDriver.runtime()
                .resourceResolver()
                .resolveBindableResource(normalizedType, resourceKeyId);
        if (installedResource != null && !installedResource.isDisposed()) {
            installedResource.bind(normalizedType, binding, viewRole, access);
        }
    }


    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        ResourceBinding that = (ResourceBinding) obj;
        if (resourceBindingHash() != that.resourceBindingHash()) {
            return false;
        }
        if (layoutHash() != that.layoutHash()) {
            return false;
        }
        return Arrays.equals(flatEntries(), that.flatEntries());
    }

    @Override
    public int hashCode() {
        return resourceBindingHash();
    }

    @Override
    public String toString() {
        return "ResourceBinding{" + Arrays.toString(flatEntries()) + '}';
    }

    private void markDirty() {
        flatEntriesDirty = true;
    }

    private void ensureFlatEntries() {
        if (!flatEntriesDirty) {
            return;
        }
        if (bindings.isEmpty()) {
            flatEntries = EMPTY_ENTRIES;
            hash = 0;
            layoutHash = 0;
            flatEntriesDirty = false;
            return;
        }

        FlatBindingEntry[] rebuilt = new FlatBindingEntry[size()];
        int index = 0;
        for (Object2ObjectMap.Entry<KeyId, Object2ObjectOpenHashMap<KeyId, BindingSpec>> typeEntry : bindings.object2ObjectEntrySet()) {
            KeyId resourceType = ResourceTypes.normalize(typeEntry.getKey());
            for (Object2ObjectMap.Entry<KeyId, BindingSpec> bindingEntry : typeEntry.getValue().object2ObjectEntrySet()) {
                BindingSpec spec = bindingEntry.getValue();
                rebuilt[index++] = new FlatBindingEntry(
                        resourceType,
                        bindingEntry.getKey(),
                        spec.resourceId(),
                        spec.viewRole(),
                        spec.access());
            }
        }
        Arrays.sort(rebuilt, (left, right) -> {
            int typeCompare = left.resourceType().toString().compareTo(right.resourceType().toString());
            if (typeCompare != 0) {
                return typeCompare;
            }
            int bindingCompare = left.bindingName().toString().compareTo(right.bindingName().toString());
            if (bindingCompare != 0) {
                return bindingCompare;
            }
            return left.resourceId().toString().compareTo(right.resourceId().toString());
        });

        int bindingHash = 1;
        int nextLayoutHash = 1;
        for (FlatBindingEntry entry : rebuilt) {
            bindingHash = 31 * bindingHash + entry.hashCode();
            nextLayoutHash = 31 * nextLayoutHash + Objects.hash(entry.resourceType(), entry.bindingName(), entry.viewRole());
        }
        flatEntries = rebuilt;
        hash = bindingHash;
        layoutHash = nextLayoutHash;
        flatEntriesDirty = false;
    }

    private int size() {
        int size = 0;
        for (Object2ObjectMap.Entry<KeyId, Object2ObjectOpenHashMap<KeyId, BindingSpec>> entry : bindings.object2ObjectEntrySet()) {
            size += entry.getValue().size();
        }
        return size;
    }

    public record BindingSpec(KeyId resourceId, ResourceViewRole viewRole, ResourceAccess access) {
        public BindingSpec {
            viewRole = viewRole != null ? viewRole : ResourceViewRole.defaultForResourceType(null);
            access = access != null ? access : ResourceViewRole.defaultAccessFor(viewRole);
        }
    }

    public record FlatBindingEntry(
            KeyId resourceType,
            KeyId bindingName,
            KeyId resourceId,
            ResourceViewRole viewRole,
            ResourceAccess access) {
        public FlatBindingEntry(KeyId resourceType, KeyId bindingName, KeyId resourceId) {
            this(
                    resourceType,
                    bindingName,
                    resourceId,
                    ResourceViewRole.defaultForResourceType(resourceType),
                    null);
        }

        public FlatBindingEntry {
            viewRole = viewRole != null ? viewRole : ResourceViewRole.defaultForResourceType(resourceType);
            access = access != null ? access : ResourceViewRole.defaultAccessFor(viewRole);
        }
    }
}

