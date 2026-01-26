package rogo.sketch.core.pipeline.information;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import rogo.sketch.core.api.graphics.Graphics;
import rogo.sketch.core.api.model.PreparedMesh;
import rogo.sketch.core.pipeline.RenderSetting;
import rogo.sketch.core.resource.ResourceBinding;


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
public class RasterizationInstanceInfo extends InstanceInfo {
    // Mesh data
    @Nullable
    private final PreparedMesh mesh;

    // Transformation data
    private final Matrix4f meshMatrix;

    // Vertex information
    private final int vertexCount;
    private int vertexOffset = -1;
    private int indexOffset = -1;

    // Instanced rendering flag
    private final boolean isInstancedRendering;

    public RasterizationInstanceInfo(
            Graphics instance,
            RenderSetting renderSetting,
            ResourceBinding resourceBinding,
            @Nullable PreparedMesh mesh,
            Matrix4f meshMatrix,
            int vertexCount,
            boolean isInstancedRendering) {
        super(instance, renderSetting, resourceBinding);
        this.mesh = mesh;
        this.meshMatrix = new Matrix4f(meshMatrix);
        this.vertexCount = vertexCount;
        this.isInstancedRendering = isInstancedRendering;
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

    public Matrix4f getMeshMatrix() {
        return meshMatrix;
    }

    // ===== Vertex Information =====

    public int getVertexCount() {
        return vertexCount;
    }

    public int getVertexOffset() {
        return vertexOffset;
    }

    public void setVertexOffset(int vertexOffset) {
        this.vertexOffset = vertexOffset;
    }

    public int getIndexOffset() {
        return indexOffset;
    }

    public void setIndexOffset(int indexOffset) {
        this.indexOffset = indexOffset;
    }

    // ===== Instancing =====

    public boolean isInstancedRendering() {
        return isInstancedRendering;
    }
}
