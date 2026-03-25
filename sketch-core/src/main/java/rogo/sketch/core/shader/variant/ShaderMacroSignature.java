package rogo.sketch.core.shader.variant;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Future-proof macro signature that can carry both flag presence and value
 * macros. Current shader caching still keys on {@link ShaderVariantKey}, but
 * module/macro systems can already reason about value-bearing signatures.
 */
public final class ShaderMacroSignature {
    public static final ShaderMacroSignature EMPTY =
            new ShaderMacroSignature(Collections.emptySortedSet(), Collections.emptySortedMap());

    private final SortedSet<String> flags;
    private final SortedMap<String, String> valuedMacros;
    private final int cachedHash;

    public ShaderMacroSignature(SortedSet<String> flags, SortedMap<String, String> valuedMacros) {
        this.flags = Collections.unmodifiableSortedSet(new TreeSet<>(flags));
        this.valuedMacros = Collections.unmodifiableSortedMap(new TreeMap<>(valuedMacros));
        this.cachedHash = Objects.hash(this.flags, this.valuedMacros);
    }

    public static ShaderMacroSignature of(Iterable<String> flags, Map<String, String> valuedMacros) {
        TreeSet<String> sortedFlags = new TreeSet<>();
        for (String flag : flags) {
            sortedFlags.add(flag);
        }
        TreeMap<String, String> sortedValues = new TreeMap<>(valuedMacros);
        if (sortedFlags.isEmpty() && sortedValues.isEmpty()) {
            return EMPTY;
        }
        return new ShaderMacroSignature(sortedFlags, sortedValues);
    }

    public SortedSet<String> flags() {
        return flags;
    }

    public SortedMap<String, String> valuedMacros() {
        return valuedMacros;
    }

    public boolean isEmpty() {
        return flags.isEmpty() && valuedMacros.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ShaderMacroSignature that = (ShaderMacroSignature) o;
        return Objects.equals(flags, that.flags) && Objects.equals(valuedMacros, that.valuedMacros);
    }

    @Override
    public int hashCode() {
        return cachedHash;
    }

    @Override
    public String toString() {
        return "ShaderMacroSignature{" +
                "flags=" + flags +
                ", valuedMacros=" + valuedMacros +
                '}';
    }
}
