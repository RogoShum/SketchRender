package rogo.sketch.core.shader.uniform;

public record ResourceUniformSet(UniformValueSnapshot legacySnapshot) {
    private static final ResourceUniformSet EMPTY = new ResourceUniformSet(UniformValueSnapshot.empty());

    public ResourceUniformSet {
        legacySnapshot = legacySnapshot != null ? legacySnapshot : UniformValueSnapshot.empty();
    }

    public static ResourceUniformSet empty() {
        return EMPTY;
    }

    public boolean isEmpty() {
        return legacySnapshot.isEmpty();
    }
}
