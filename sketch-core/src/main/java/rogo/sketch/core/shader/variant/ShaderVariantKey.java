package rogo.sketch.core.shader.variant;

import java.util.*;

/**
 * Unique key for a shader variant based on active flags.
 * Guarantees that different orderings of the same flags produce the same key.
 * 
 * Example:
 *   ShaderVariantKey.of("TRANSLUCENT", "SHADOW_PASS") == ShaderVariantKey.of("SHADOW_PASS", "TRANSLUCENT")
 */
public final class ShaderVariantKey {
    public static final ShaderVariantKey EMPTY = new ShaderVariantKey(Collections.emptySortedSet());
    
    private final SortedSet<String> flags;
    private final int cachedHash;
    
    private ShaderVariantKey(SortedSet<String> flags) {
        this.flags = Collections.unmodifiableSortedSet(flags);
        this.cachedHash = Objects.hash(this.flags);
    }
    
    /**
     * Create a variant key from flags.
     * @param flags The variant flags
     * @return The variant key
     */
    public static ShaderVariantKey of(String... flags) {
        if (flags == null || flags.length == 0) {
            return EMPTY;
        }
        TreeSet<String> sortedFlags = new TreeSet<>(Arrays.asList(flags));
        return new ShaderVariantKey(sortedFlags);
    }
    
    /**
     * Create a variant key from a collection of flags.
     * @param flags The variant flags
     * @return The variant key
     */
    public static ShaderVariantKey of(Collection<String> flags) {
        if (flags == null || flags.isEmpty()) {
            return EMPTY;
        }
        return new ShaderVariantKey(new TreeSet<>(flags));
    }
    
    /**
     * Create a variant key from a set of flags.
     * @param flags The variant flags
     * @return The variant key
     */
    public static ShaderVariantKey of(Set<String> flags) {
        if (flags == null || flags.isEmpty()) {
            return EMPTY;
        }
        if (flags instanceof SortedSet<String> sorted) {
            return new ShaderVariantKey(new TreeSet<>(sorted));
        }
        return new ShaderVariantKey(new TreeSet<>(flags));
    }
    
    /**
     * Get the flags in this variant key.
     * @return Unmodifiable sorted set of flags
     */
    public SortedSet<String> getFlags() {
        return flags;
    }
    
    /**
     * Check if this key contains a specific flag.
     * @param flag The flag to check
     * @return true if the flag is present
     */
    public boolean hasFlag(String flag) {
        return flags.contains(flag);
    }
    
    /**
     * Check if this is an empty key (no flags).
     * @return true if empty
     */
    public boolean isEmpty() {
        return flags.isEmpty();
    }
    
    /**
     * Get the number of flags.
     * @return The flag count
     */
    public int size() {
        return flags.size();
    }
    
    /**
     * Create a new key with an additional flag.
     * @param flag The flag to add
     * @return The new variant key
     */
    public ShaderVariantKey with(String flag) {
        TreeSet<String> newFlags = new TreeSet<>(flags);
        newFlags.add(flag);
        return new ShaderVariantKey(newFlags);
    }
    
    /**
     * Create a new key without a specific flag.
     * @param flag The flag to remove
     * @return The new variant key
     */
    public ShaderVariantKey without(String flag) {
        if (!flags.contains(flag)) {
            return this;
        }
        TreeSet<String> newFlags = new TreeSet<>(flags);
        newFlags.remove(flag);
        if (newFlags.isEmpty()) {
            return EMPTY;
        }
        return new ShaderVariantKey(newFlags);
    }
    
    /**
     * Merge with another variant key.
     * @param other The other key
     * @return The merged key
     */
    public ShaderVariantKey merge(ShaderVariantKey other) {
        if (other == null || other.isEmpty()) {
            return this;
        }
        if (this.isEmpty()) {
            return other;
        }
        TreeSet<String> merged = new TreeSet<>(flags);
        merged.addAll(other.flags);
        return new ShaderVariantKey(merged);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ShaderVariantKey that = (ShaderVariantKey) o;
        return Objects.equals(flags, that.flags);
    }
    
    @Override
    public int hashCode() {
        return cachedHash;
    }
    
    @Override
    public String toString() {
        if (flags.isEmpty()) {
            return "ShaderVariantKey[]";
        }
        return "ShaderVariantKey[" + String.join(", ", flags) + "]";
    }
    
    /**
     * Get a string representation suitable for cache keys or logging.
     * @return Compact string representation
     */
    public String toCompactString() {
        if (flags.isEmpty()) {
            return "";
        }
        return String.join("+", flags);
    }
}

