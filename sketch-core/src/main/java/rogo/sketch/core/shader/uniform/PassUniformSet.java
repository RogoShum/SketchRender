package rogo.sketch.core.shader.uniform;

public record PassUniformSet(UniformValueSnapshot legacySnapshot) {
    private static final PassUniformSet EMPTY = new PassUniformSet(UniformValueSnapshot.empty());

    public PassUniformSet {
        legacySnapshot = legacySnapshot != null ? legacySnapshot : UniformValueSnapshot.empty();
    }

    public static PassUniformSet empty() {
        return EMPTY;
    }

    public boolean isEmpty() {
        return legacySnapshot.isEmpty();
    }
}
