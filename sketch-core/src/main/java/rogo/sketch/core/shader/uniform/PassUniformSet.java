package rogo.sketch.core.shader.uniform;

public record PassUniformSet(UniformValueSnapshot snapshot) {
    private static final PassUniformSet EMPTY = new PassUniformSet(UniformValueSnapshot.empty());

    public PassUniformSet {
        snapshot = snapshot != null ? snapshot : UniformValueSnapshot.empty();
    }

    public static PassUniformSet empty() {
        return EMPTY;
    }

    public boolean isEmpty() {
        return snapshot.isEmpty();
    }
}
