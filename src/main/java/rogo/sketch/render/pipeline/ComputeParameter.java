package rogo.sketch.render.pipeline;

import rogo.sketch.render.pipeline.flow.RenderFlowType;

import java.util.Objects;

/**
 * Render parameter for compute shader operations.
 * <p>
 * Compute shaders don't require traditional rendering parameters like
 * vertex formats or primitive types. This class provides a minimal
 * parameter set for compute dispatch operations.
 * </p>
 */
public class ComputeParameter extends RenderParameter {
    private final int workGroupSizeX;
    private final int workGroupSizeY;
    private final int workGroupSizeZ;

    /**
     * Create a compute parameter with default work group size (1, 1, 1).
     */
    public ComputeParameter() {
        this(1, 1, 1);
    }

    /**
     * Create a compute parameter with specified work group size.
     *
     * @param workGroupSizeX Work group size in X dimension
     * @param workGroupSizeY Work group size in Y dimension
     * @param workGroupSizeZ Work group size in Z dimension
     */
    public ComputeParameter(int workGroupSizeX, int workGroupSizeY, int workGroupSizeZ) {
        this.workGroupSizeX = workGroupSizeX;
        this.workGroupSizeY = workGroupSizeY;
        this.workGroupSizeZ = workGroupSizeZ;
    }

    @Override
    public RenderFlowType getFlowType() {
        return RenderFlowType.COMPUTE;
    }

    /**
     * Get the work group size in X dimension.
     *
     * @return Work group size X
     */
    public int workGroupSizeX() {
        return workGroupSizeX;
    }

    /**
     * Get the work group size in Y dimension.
     *
     * @return Work group size Y
     */
    public int workGroupSizeY() {
        return workGroupSizeY;
    }

    /**
     * Get the work group size in Z dimension.
     *
     * @return Work group size Z
     */
    public int workGroupSizeZ() {
        return workGroupSizeZ;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof ComputeParameter that))
            return false;
        return workGroupSizeX == that.workGroupSizeX &&
                workGroupSizeY == that.workGroupSizeY &&
                workGroupSizeZ == that.workGroupSizeZ;
    }

    @Override
    public int hashCode() {
        return Objects.hash(workGroupSizeX, workGroupSizeY, workGroupSizeZ);
    }

    @Override
    public String toString() {
        return "ComputeParameter{" +
                "workGroupSize=(" + workGroupSizeX + ", " + workGroupSizeY + ", " + workGroupSizeZ + ")" +
                '}';
    }
}
