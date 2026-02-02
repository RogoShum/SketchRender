package rogo.sketch.core.pipeline.information;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.api.graphics.MeshBasedGraphics;
import rogo.sketch.core.api.model.PreparedMesh;
import rogo.sketch.core.pipeline.RenderSetting;


/**
 * Instance information for rasterization rendering.
 * <p>
 * Contains all data needed for geometry rendering with vertex/fragment shaders:
 * <ul>
 * <li>Mesh data and transformation matrix</li>
 * <li>Vertex/index counts and offsets</li>
 * </ul>
 * </p>
 */
public class RasterizationInstanceInfo extends InstanceInfo<MeshBasedGraphics> {
    // Mesh data
    @Nullable
    private final PreparedMesh mesh;
    // Vertex information
    private final int vertexCount;

    public RasterizationInstanceInfo(
            MeshBasedGraphics instance,
            RenderSetting renderSetting,
            @Nullable PreparedMesh mesh,
            int vertexCount) {
        super(instance, renderSetting);
        this.mesh = mesh;
        this.vertexCount = vertexCount;
    }

    @Override
    public String getInfoType() {
        return "rasterization";
    }

    // ===== Mesh Data =====

    @Nullable
    public PreparedMesh getMesh() {
        return mesh;
    }

    public boolean hasMesh() {
        return mesh != null;
    }

    // ===== Vertex Information =====

    public int getVertexCount() {
        return vertexCount;
    }
}