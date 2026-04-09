package rogo.sketch.core.shader.uniform;

public record DrawUniformSet(UniformValueSnapshot snapshot) {
    private static final DrawUniformSet EMPTY = new DrawUniformSet(UniformValueSnapshot.empty());

    public DrawUniformSet {
        snapshot = snapshot != null ? snapshot : UniformValueSnapshot.empty();
    }

    public static DrawUniformSet empty() {
        return EMPTY;
    }

    public boolean isEmpty() {
        return snapshot.isEmpty();
    }
}
