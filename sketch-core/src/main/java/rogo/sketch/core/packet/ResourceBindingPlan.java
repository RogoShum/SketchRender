package rogo.sketch.core.packet;

import rogo.sketch.core.resource.ResourceBinding;
import rogo.sketch.core.resource.ResourceAccess;
import rogo.sketch.core.resource.ResourceViewRole;
import rogo.sketch.core.util.KeyId;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

public record ResourceBindingPlan(
        KeyId layoutKey,
        BindingEntry[] entries,
        int layoutHash,
        int resourceBindingHash,
        ResourceBindingStamp stamp,
        ResourceBinding bindingReference
) {
    private static final KeyId EMPTY_LAYOUT = KeyId.of("sketch:empty_resource_layout");
    private static final ResourceBindingPlan EMPTY = new ResourceBindingPlan(
            EMPTY_LAYOUT,
            new BindingEntry[0],
            0,
            0,
            ResourceBindingStamp.NONE,
            null);

    public ResourceBindingPlan {
        layoutKey = layoutKey != null ? layoutKey : EMPTY_LAYOUT;
        entries = entries != null ? entries.clone() : new BindingEntry[0];
        stamp = stamp != null ? stamp : (entries.length == 0 ? ResourceBindingStamp.NONE : ResourceBindingStamp.next());
    }

    public static ResourceBindingPlan empty() {
        return EMPTY;
    }

    public static ResourceBindingPlan from(ResourceBinding binding) {
        return from(binding, null);
    }

    public static ResourceBindingPlan from(ResourceBinding binding, Map<KeyId, Map<KeyId, Integer>> shaderResourceBindings) {
        if (binding == null) {
            return empty();
        }
        ResourceBinding.FlatBindingEntry[] flatEntries = binding.flatEntries();
        BindingEntry[] compiledEntries = new BindingEntry[flatEntries.length];
        for (int i = 0; i < flatEntries.length; i++) {
            ResourceBinding.FlatBindingEntry entry = flatEntries[i];
            compiledEntries[i] = new BindingEntry(
                    entry.resourceType(),
                    entry.bindingName(),
                    entry.resourceId(),
                    resolveBindingSlot(shaderResourceBindings, entry.resourceType(), entry.bindingName()),
                    entry.viewRole(),
                    entry.access());
        }
        int layoutHash = computeLayoutHash(compiledEntries, binding.layoutHash());
        String layoutSignature = compiledEntries.length == 0
                ? EMPTY_LAYOUT.toString()
                : "sketch:resource_layout_" + Integer.toHexString(layoutHash);
        return new ResourceBindingPlan(
                KeyId.of(layoutSignature),
                compiledEntries,
                layoutHash,
                binding.resourceBindingHash(),
                ResourceBindingStamp.next(),
                binding);
    }

    private static int resolveBindingSlot(
            Map<KeyId, Map<KeyId, Integer>> shaderResourceBindings,
            KeyId resourceType,
            KeyId bindingName) {
        if (shaderResourceBindings == null || shaderResourceBindings.isEmpty() || resourceType == null || bindingName == null) {
            return -1;
        }
        Map<KeyId, Integer> typeBindings = shaderResourceBindings.get(rogo.sketch.core.resource.ResourceTypes.normalize(resourceType));
        Integer binding = typeBindings != null ? typeBindings.get(bindingName) : null;
        return binding != null ? binding : -1;
    }

    private static int computeLayoutHash(BindingEntry[] entries, int fallbackHash) {
        if (entries == null || entries.length == 0) {
            return 0;
        }
        int hash = 1;
        for (BindingEntry entry : entries) {
            hash = 31 * hash + Objects.hash(
                    entry.resourceType(),
                    entry.bindingName(),
                    entry.bindingSlot(),
                    entry.viewRole());
        }
        return hash != 1 ? hash : fallbackHash;
    }

    public ResourceBinding binding() {
        return bindingReference;
    }

    public int resourceBindingHash() {
        return resourceBindingHash;
    }

    public boolean isEmpty() {
        return entries.length == 0;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof ResourceBindingPlan other)) {
            return false;
        }
        return layoutHash == other.layoutHash
                && resourceBindingHash == other.resourceBindingHash
                && Objects.equals(layoutKey, other.layoutKey)
                && Arrays.equals(entries, other.entries);
    }

    @Override
    public int hashCode() {
        return Objects.hash(layoutKey, layoutHash, resourceBindingHash);
    }

    public record BindingEntry(
            KeyId resourceType,
            KeyId bindingName,
            KeyId resourceId,
            int bindingSlot,
            ResourceViewRole viewRole,
            ResourceAccess access) {
        public BindingEntry(KeyId resourceType, KeyId bindingName, KeyId resourceId) {
            this(
                    resourceType,
                    bindingName,
                    resourceId,
                    -1,
                    ResourceViewRole.defaultForResourceType(resourceType),
                    null);
        }

        public BindingEntry {
            viewRole = viewRole != null ? viewRole : ResourceViewRole.defaultForResourceType(resourceType);
            access = access != null ? access : ResourceViewRole.defaultAccessFor(viewRole);
        }

        public KeyId descriptorResourceType() {
            return viewRole.descriptorResourceType(resourceType);
        }
    }
}

