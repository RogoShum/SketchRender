package rogo.sketch.core.shader.uniform;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class UniformLayout {
    private static final UniformLayout EMPTY = new UniformLayout(List.of());

    private final List<String> uniformNames;
    private final Map<String, Integer> slotByName;
    private final int hashCode;

    private UniformLayout(List<String> uniformNames) {
        this.uniformNames = List.copyOf(uniformNames);
        Map<String, Integer> slots = new LinkedHashMap<>();
        for (int i = 0; i < this.uniformNames.size(); i++) {
            slots.put(this.uniformNames.get(i), i);
        }
        this.slotByName = Collections.unmodifiableMap(slots);
        this.hashCode = Objects.hash(this.uniformNames);
    }

    public static UniformLayout empty() {
        return EMPTY;
    }

    public static UniformLayout from(Map<String, ?> uniformValues) {
        if (uniformValues == null || uniformValues.isEmpty()) {
            return EMPTY;
        }
        List<String> names = new ArrayList<>(uniformValues.keySet());
        Collections.sort(names);
        return new UniformLayout(names);
    }

    public boolean isEmpty() {
        return uniformNames.isEmpty();
    }

    public int size() {
        return uniformNames.size();
    }

    public int slotOf(String uniformName) {
        return slotByName.getOrDefault(uniformName, -1);
    }

    public String uniformName(int slot) {
        return uniformNames.get(slot);
    }

    public Set<String> uniformNames() {
        return slotByName.keySet();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof UniformLayout other)) return false;
        return uniformNames.equals(other.uniformNames);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }
}
