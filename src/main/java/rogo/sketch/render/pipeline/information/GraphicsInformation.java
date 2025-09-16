package rogo.sketch.render.pipeline.information;

import org.joml.Matrix4f;
import rogo.sketch.api.graphics.GraphicsInstance;
import rogo.sketch.render.pipeline.RenderSetting;
import rogo.sketch.render.model.Mesh;
import rogo.sketch.render.model.ModelMesh;
import rogo.sketch.render.resource.ResourceBinding;
import rogo.sketch.render.vertex.InstancedVertexLayout;

import javax.annotation.Nullable;

/**
 * Contains all rendering information for a graphics instance in a single frame
 * RenderSetting is provided by GraphicsPassGroup, not extracted from the instance
 */
public class GraphicsInformation {
    private final GraphicsInstance instance;
    private final RenderSetting renderSetting;
    private final ResourceBinding resourceBinding;

    // Mesh data
    @Nullable
    private final ModelMesh modelMesh;
    @Nullable
    private final Mesh mesh;

    // Transformation data
    private final Matrix4f meshMatrix; // 4x4 transformation matrix

    // Vertex layout for instanced rendering
    @Nullable
    private final InstancedVertexLayout instancedVertexLayout;

    // Vertex information
    private final int vertexCount;
    private int vertexOffset = -1; // Set during queue organization

    // Instanced rendering flag
    private final boolean isInstancedRendering;

    public GraphicsInformation(GraphicsInstance instance,
                               RenderSetting renderSetting,
                               ResourceBinding resourceBinding,
                               @Nullable ModelMesh modelMesh,
                               @Nullable Mesh mesh,
                               Matrix4f meshMatrix,
                               @Nullable InstancedVertexLayout instancedVertexLayout,
                               int vertexCount,
                               boolean isInstancedRendering) {
        this.instance = instance;
        this.renderSetting = renderSetting;
        this.resourceBinding = resourceBinding;
        this.modelMesh = modelMesh;
        this.mesh = mesh;
        this.meshMatrix = new Matrix4f(meshMatrix);
        this.instancedVertexLayout = instancedVertexLayout;
        this.vertexCount = vertexCount;
        this.isInstancedRendering = isInstancedRendering;
    }

    // Getters
    public GraphicsInstance getInstance() {
        return instance;
    }

    public RenderSetting getRenderSetting() {
        return renderSetting;
    }

    public ResourceBinding getResourceBinding() {
        return resourceBinding;
    }

    @Nullable
    public ModelMesh getModelMesh() {
        return modelMesh;
    }

    @Nullable
    public Mesh getMesh() {
        return mesh;
    }

    public Matrix4f getMeshMatrix() {
        return meshMatrix;
    }

    @Nullable
    public InstancedVertexLayout getInstancedVertexLayout() {
        return instancedVertexLayout;
    }

    public int getVertexCount() {
        return vertexCount;
    }

    public int getVertexOffset() {
        return vertexOffset;
    }

    public void setVertexOffset(int vertexOffset) {
        this.vertexOffset = vertexOffset;
    }

    public boolean hasInstancedLayout() {
        return instancedVertexLayout != null;
    }

    public boolean hasModelMesh() {
        return modelMesh != null;
    }

    public boolean hasMesh() {
        return mesh != null;
    }

    public boolean isInstancedRendering() {
        return isInstancedRendering;
    }

    public boolean hasInstancedData() {
        return isInstancedRendering && instancedVertexLayout != null;
    }
}