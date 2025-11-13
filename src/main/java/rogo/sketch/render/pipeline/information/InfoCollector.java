package rogo.sketch.render.pipeline.information;

import org.joml.Matrix4f;
import rogo.sketch.api.graphics.*;
import rogo.sketch.render.model.MeshGroup;
import rogo.sketch.render.model.BakedMesh;
import rogo.sketch.render.pipeline.RenderContext;
import rogo.sketch.render.pipeline.RenderSetting;
import rogo.sketch.render.resource.ResourceBinding;
import rogo.sketch.render.vertex.InstancedVertexLayout;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Collects rendering information from graphics instances
 * Note: RenderSetting should be provided by GraphicsPassGroup, not extracted here
 */
public class InfoCollector {

    /**
     * Collect render information from a collection of graphics instances with provided render setting
     */
    public static <C extends RenderContext> List<GraphicsInstanceInformation> collectRenderInfo(Collection<GraphicsInstance> instances, RenderSetting renderSetting, C context) {
        List<GraphicsInstanceInformation> collected = new ArrayList<>();

        for (GraphicsInstance instance : instances) {
            if (instance.shouldRender()) {
                GraphicsInstanceInformation info = collectFromInstance(instance, renderSetting, context);
                if (info != null) {
                    collected.add(info);
                }
            }
        }

        return collected;
    }

    /**
     * Collect render information from a single graphics instance with provided render setting
     */
    @Nullable
    private static <C extends RenderContext> GraphicsInstanceInformation collectFromInstance(GraphicsInstance instance, RenderSetting renderSetting, C context) {
        ResourceBinding resourceBinding = renderSetting.resourceBinding();

        // Extract mesh data
        BakedMesh modelMesh = extractModelMesh(instance);
        MeshGroup meshGroup = extractMesh(instance);

        // Extract transformation matrix
        Matrix4f meshMatrix = extractMeshMatrix(instance);

        // Extract instanced vertex layout
        InstancedVertexLayout instancedLayout = extractInstancedVertexLayout(instance);

        // Calculate vertex count
        int vertexCount = calculateVertexCount(modelMesh, meshGroup, instance);

        // Check if this is instanced rendering
        boolean isInstancedRendering = instancedLayout != null;

        return new GraphicsInstanceInformation(
                instance,
                renderSetting,
                resourceBinding,
                modelMesh,
                meshGroup,
                meshMatrix,
                instancedLayout,
                vertexCount,
                isInstancedRendering
        );
    }


    /**
     * Extract model mesh from a graphics instance
     */
    @Nullable
    private static BakedMesh extractModelMesh(GraphicsInstance instance) {
        if (instance instanceof ModelMeshProvider provider) {
            return provider.getModelMesh();
        }
        return null;
    }

    /**
     * Extract mesh from a graphics instance
     */
    @Nullable
    private static MeshGroup extractMesh(GraphicsInstance instance) {
        if (instance instanceof MeshProvider provider) {
            return provider.getMesh();
        }
        return null;
    }

    /**
     * Extract transformation matrix from a graphics instance
     */
    private static Matrix4f extractMeshMatrix(GraphicsInstance instance) {
        if (instance instanceof MatrixProvider provider) {
            return provider.getMeshMatrix();
        }
        // Return identity matrix as default
        return new Matrix4f();
    }

    /**
     * Extract instanced vertex layout from a graphics instance
     */
    @Nullable
    private static InstancedVertexLayout extractInstancedVertexLayout(GraphicsInstance instance) {
        if (instance instanceof InstancedLayoutProvider provider) {
            return provider.getInstancedVertexLayout();
        }
        return null;
    }

    /**
     * Calculate vertex count from available mesh data
     */
    private static int calculateVertexCount(BakedMesh modelMesh, MeshGroup meshGroup, GraphicsInstance instance) {
        if (modelMesh != null) {
            return modelMesh.getTotalVertexCount();
        }
        if (meshGroup != null) {
            return meshGroup.getTotalVertexCount();
        }
        if (instance instanceof VertexCountProvider provider) {
            return provider.getVertexCount();
        }
        return 0;
    }

}