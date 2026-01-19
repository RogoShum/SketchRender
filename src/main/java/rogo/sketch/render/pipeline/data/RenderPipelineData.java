package rogo.sketch.render.pipeline.data;

/**
 * Interface for resettable data components within the render pipeline.
 * <p>
 * Implementations of this interface manage transient render data that needs to
 * be
 * reset or cleared at the beginning of each frame or render pass (e.g.,
 * indirect
 * command buffers, instance offsets).
 * </p>
 */
public interface RenderPipelineData {
    /**
     * Reset the data state.
     * Should be called before command generation to clear previous frame's data.
     */
    void reset();
}
