package rogo.sketch.core.shader.uniform;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;

public final class UniformLayout {
    private static final UniformLayout EMPTY = new UniformLayout(new String[0], true);

    private final String[] uniformNames;
    private final Object2IntOpenHashMap<String> slotByName;
    private final int hashCode;

    private UniformLayout(String[] uniformNames, boolean trustedArray) {
        this.uniformNames = trustedArray ? uniformNames : uniformNames.clone();
        this.slotByName = new Object2IntOpenHashMap<>(this.uniformNames.length);
        this.slotByName.defaultReturnValue(-1);
        for (int i = 0; i < this.uniformNames.length; i++) {
            this.slotByName.put(this.uniformNames[i], i);
        }
        this.hashCode = Arrays.hashCode(this.uniformNames);
    }

    public static UniformLayout empty() {
        return EMPTY;
    }

    public static UniformLayout from(Map<String, ?> uniformValues) {
        if (uniformValues == null || uniformValues.isEmpty()) {
            return EMPTY;
        }
        String[] names = uniformValues.keySet().toArray(String[]::new);
        Arrays.sort(names);
        return fromSorted(names);
    }

    static UniformLayout fromSorted(String[] sortedUniformNames) {
        if (sortedUniformNames == null || sortedUniformNames.length == 0) {
            return EMPTY;
        }
        return new UniformLayout(sortedUniformNames, true);
    }

    public boolean isEmpty() {
        return uniformNames.length == 0;
    }

    public int size() {
        return uniformNames.length;
    }

    public int slotOf(String uniformName) {
        return slotByName.getInt(uniformName);
    }

    public String uniformName(int slot) {
        return uniformNames[slot];
    }

    public Set<String> uniformNames() {
        ObjectLinkedOpenHashSet<String> names = new ObjectLinkedOpenHashSet<>(uniformNames.length);
        names.addAll(Arrays.asList(uniformNames));
        return names;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof UniformLayout other)) return false;
        return Arrays.equals(uniformNames, other.uniformNames);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }
}
