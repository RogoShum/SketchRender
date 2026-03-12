package rogo.sketch.core.pipeline.compute;

/**
 * Per-frame budget for async compute mesh task consumption.
 */
public record ComputeMeshTaskBudget(int maxTasksPerFrame) {
    public static final ComputeMeshTaskBudget DEFAULT = new ComputeMeshTaskBudget(8);
}


