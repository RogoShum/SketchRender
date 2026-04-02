package rogo.sketch.core.shader.uniform;

public record DrawUniformSet(UniformValueSnapshot legacySnapshot) {
    private static final DrawUniformSet EMPTY = new DrawUniformSet(UniformValueSnapshot.empty());

    public DrawUniformSet {
        legacySnapshot = legacySnapshot != null ? legacySnapshot : UniformValueSnapshot.empty();
    }

    public static DrawUniformSet empty() {
        return EMPTY;
    }

    public boolean isEmpty() {
        return legacySnapshot.isEmpty();
    }
}
