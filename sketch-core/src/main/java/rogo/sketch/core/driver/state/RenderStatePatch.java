package rogo.sketch.core.driver.state;

import rogo.sketch.core.api.RenderStateComponent;
import rogo.sketch.core.util.KeyId;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Sparse render-state override set used by authoring/resources.
 * Only explicitly specified state components are stored here.
 */
public final class RenderStatePatch {
    private static final RenderStatePatch EMPTY = new RenderStatePatch(Map.of());

    private final Map<KeyId, RenderStateComponent> overrides;
    private final int hash;

    private RenderStatePatch(Map<KeyId, RenderStateComponent> overrides) {
        this.overrides = Map.copyOf(overrides);
        this.hash = this.overrides.hashCode();
    }

    public static RenderStatePatch empty() {
        return EMPTY;
    }

    public static RenderStatePatch of(Map<KeyId, ? extends RenderStateComponent> overrides) {
        if (overrides == null || overrides.isEmpty()) {
            return empty();
        }
        Map<KeyId, RenderStateComponent> snapshot = new LinkedHashMap<>();
        for (Map.Entry<KeyId, ? extends RenderStateComponent> entry : overrides.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                snapshot.put(entry.getKey(), entry.getValue());
            }
        }
        return snapshot.isEmpty() ? empty() : new RenderStatePatch(snapshot);
    }

    public RenderStateComponent get(KeyId identifier) {
        return overrides.get(identifier);
    }

    public boolean contains(KeyId identifier) {
        return overrides.containsKey(identifier);
    }

    public Map<KeyId, RenderStateComponent> overrides() {
        return overrides;
    }

    public Collection<RenderStateComponent> components() {
        return overrides.values();
    }

    public boolean isEmpty() {
        return overrides.isEmpty();
    }

    public RenderStatePatch with(RenderStateComponent component) {
        Objects.requireNonNull(component, "component");
        Map<KeyId, RenderStateComponent> merged = new LinkedHashMap<>(overrides);
        merged.put(component.getIdentifier(), component);
        return new RenderStatePatch(merged);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RenderStatePatch that)) {
            return false;
        }
        return overrides.equals(that.overrides);
    }

    @Override
    public int hashCode() {
        return hash;
    }
}

