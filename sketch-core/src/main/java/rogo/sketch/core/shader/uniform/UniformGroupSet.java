package rogo.sketch.core.shader.uniform;

public record UniformGroupSet(
        FrameUniformSet frameUniforms,
        PassUniformSet passUniforms,
        ResourceUniformSet resourceUniforms,
        DrawUniformSet drawUniforms
) {
    private static final UniformGroupSet EMPTY = new UniformGroupSet(
            FrameUniformSet.empty(),
            PassUniformSet.empty(),
            ResourceUniformSet.empty(),
            DrawUniformSet.empty());

    public UniformGroupSet {
        frameUniforms = frameUniforms != null ? frameUniforms : FrameUniformSet.empty();
        passUniforms = passUniforms != null ? passUniforms : PassUniformSet.empty();
        resourceUniforms = resourceUniforms != null ? resourceUniforms : ResourceUniformSet.empty();
        drawUniforms = drawUniforms != null ? drawUniforms : DrawUniformSet.empty();
    }

    public static UniformGroupSet empty() {
        return EMPTY;
    }

    public static UniformGroupSet fromSnapshot(UniformValueSnapshot snapshot) {
        return new UniformGroupSet(
                FrameUniformSet.empty(),
                PassUniformSet.empty(),
                new ResourceUniformSet(snapshot),
                DrawUniformSet.empty());
    }

    public boolean isEmpty() {
        return frameUniforms.isEmpty()
                && passUniforms.isEmpty()
                && resourceUniforms.isEmpty()
                && drawUniforms.isEmpty();
    }
}
