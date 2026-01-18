package rogo.sketch.render.pipeline.flow;

/**
 * Interface for render post-processors.
 */
public interface RenderPostProcessor {
    /**
     * Execute the post-processing logic (e.g., data upload).
     */
    void execute();
}