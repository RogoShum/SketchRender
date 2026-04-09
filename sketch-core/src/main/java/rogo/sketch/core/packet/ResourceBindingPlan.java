package rogo.sketch.core.packet;

import rogo.sketch.core.resource.ResourceBinding;
import rogo.sketch.core.resource.ResourceTypes;
import rogo.sketch.core.util.KeyId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public record ResourceBindingPlan(
        KeyId layoutKey,
        List<BindingEntry> entries,
        ResourceBinding bindingReference
) {
    private static final KeyId EMPTY_LAYOUT = KeyId.of("sketch:empty_resource_layout");
    private static final ResourceBindingPlan EMPTY = new ResourceBindingPlan(EMPTY_LAYOUT, List.of(), null);

    public ResourceBindingPlan {
        layoutKey = layoutKey != null ? layoutKey : EMPTY_LAYOUT;
        entries = entries != null ? List.copyOf(entries) : List.of();
    }

    public static ResourceBindingPlan empty() {
        return EMPTY;
    }

    public static ResourceBindingPlan from(ResourceBinding binding) {
        if (binding == null) {
            return empty();
        }

        List<BindingEntry> compiledEntries = new ArrayList<>();
        List<String> layoutParts = new ArrayList<>();
        for (Map.Entry<KeyId, Map<KeyId, KeyId>> typeEntry : binding.getAllBindings().entrySet()) {
            KeyId resourceType = ResourceTypes.normalize(typeEntry.getKey());
            for (Map.Entry<KeyId, KeyId> bindingEntry : typeEntry.getValue().entrySet()) {
                compiledEntries.add(new BindingEntry(resourceType, bindingEntry.getKey(), bindingEntry.getValue()));
                layoutParts.add(resourceType + "#" + bindingEntry.getKey());
            }
        }

        compiledEntries.sort(Comparator
                .comparing((BindingEntry entry) -> entry.resourceType().toString())
                .thenComparing(entry -> entry.bindingName().toString())
                .thenComparing(entry -> entry.resourceId().toString()));
        layoutParts.sort(String::compareTo);

        String layoutSignature = layoutParts.isEmpty()
                ? EMPTY_LAYOUT.toString()
                : "sketch:resource_layout_" + Integer.toHexString(String.join("|", layoutParts).hashCode());
        return new ResourceBindingPlan(KeyId.of(layoutSignature), compiledEntries, binding);
    }

    public ResourceBinding binding() {
        return bindingReference;
    }

    public int resourceBindingHash() {
        return bindingReference != null ? bindingReference.hashCode() : 0;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public record BindingEntry(KeyId resourceType, KeyId bindingName, KeyId resourceId) {
    }
}

