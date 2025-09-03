package rogo.sketch.render.information;

import rogo.sketch.api.GraphicsInstance;
import rogo.sketch.render.RenderContext;
import rogo.sketch.render.RenderSetting;
import rogo.sketch.render.data.filler.VertexFiller;
import rogo.sketch.render.model.ModelMesh;
import rogo.sketch.render.model.Mesh;
import rogo.sketch.render.resource.ResourceBinding;
import rogo.sketch.render.vertex.InstancedVertexLayout;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Collects rendering information from graphics instances
 */
public class InfoCollector {
    
    /**
     * Collect render information from a collection of graphics instances
     */
    public static <C extends RenderContext> List<GraphicsInformation> collectRenderInfo(
            Collection<GraphicsInstance> instances, C context) {
        List<GraphicsInformation> collected = new ArrayList<>();
        
        for (GraphicsInstance instance : instances) {
            if (instance.shouldRender()) {
                GraphicsInformation info = collectFromInstance(instance, context);
                if (info != null) {
                    collected.add(info);
                }
            }
        }
        
        return collected;
    }
    
    /**
     * Collect render information from a single graphics instance
     */
    @Nullable
    private static <C extends RenderContext> GraphicsInformation collectFromInstance(
            GraphicsInstance instance, C context) {
        
        // Extract render setting and resource binding
        RenderSetting renderSetting = extractRenderSetting(instance, context);
        if (renderSetting == null) {
            return null;
        }
        
        ResourceBinding resourceBinding = renderSetting.resourceBinding();
        
        // Extract mesh data
        ModelMesh modelMesh = extractModelMesh(instance);
        Mesh mesh = extractMesh(instance);
        
        // Extract transformation matrix
        float[] meshMatrix = extractMeshMatrix(instance);
        
        // Extract instanced vertex layout
        InstancedVertexLayout instancedLayout = extractInstancedVertexLayout(instance);
        
        // Calculate vertex count
        int vertexCount = calculateVertexCount(modelMesh, mesh, instance);
        
        // Calculate instance information
        int instanceCount = calculateInstanceCount(instance);
        boolean isInstancedRendering = instancedLayout != null && instanceCount > 1;
        
        return new GraphicsInformation(
                instance,
                renderSetting,
                resourceBinding,
                modelMesh,
                mesh,
                meshMatrix,
                instancedLayout,
                vertexCount,
                instanceCount,
                isInstancedRendering
        );
    }
    
    /**
     * Extract render setting from a graphics instance
     */
    @Nullable
    private static <C extends RenderContext> RenderSetting extractRenderSetting(
            GraphicsInstance instance, C context) {
        // This should be implemented based on how your instances provide render settings
        // For now, we'll assume there's a method to get it from context or instance
        if (instance instanceof RenderSettingProvider provider) {
            return provider.getRenderSetting(context);
        }
        return null;
    }
    
    /**
     * Extract model mesh from a graphics instance
     */
    @Nullable
    private static ModelMesh extractModelMesh(GraphicsInstance instance) {
        if (instance instanceof ModelMeshProvider provider) {
            return provider.getModelMesh();
        }
        return null;
    }
    
    /**
     * Extract mesh from a graphics instance
     */
    @Nullable
    private static Mesh extractMesh(GraphicsInstance instance) {
        if (instance instanceof MeshProvider provider) {
            return provider.getMesh();
        }
        return null;
    }
    
    /**
     * Extract transformation matrix from a graphics instance
     */
    private static float[] extractMeshMatrix(GraphicsInstance instance) {
        if (instance instanceof MatrixProvider provider) {
            return provider.getMeshMatrix();
        }
        // Return identity matrix as default
        return new float[]{
            1.0f, 0.0f, 0.0f, 0.0f,
            0.0f, 1.0f, 0.0f, 0.0f,
            0.0f, 0.0f, 1.0f, 0.0f,
            0.0f, 0.0f, 0.0f, 1.0f
        };
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
    private static int calculateVertexCount(ModelMesh modelMesh, Mesh mesh, GraphicsInstance instance) {
        if (modelMesh != null) {
            return modelMesh.getTotalVertexCount();
        }
        if (mesh != null) {
            return mesh.getTotalVertexCount();
        }
        if (instance instanceof VertexCountProvider provider) {
            return provider.getVertexCount();
        }
        return 0;
    }
    
    /**
     * Calculate instance count from graphics instance
     */
    private static int calculateInstanceCount(GraphicsInstance instance) {
        if (instance instanceof InstancedLayoutProvider provider) {
            return provider.getInstanceCount();
        }
        return 1; // Default to 1 instance for non-instanced rendering
    }
    
    // Provider interfaces for extracting data from graphics instances
    public interface RenderSettingProvider {
        <C extends RenderContext> RenderSetting getRenderSetting(C context);
    }
    
    public interface ModelMeshProvider {
        ModelMesh getModelMesh();
    }
    
    public interface MeshProvider {
        Mesh getMesh();
    }
    
    public interface MatrixProvider {
        float[] getMeshMatrix();
    }
    
    public interface InstancedLayoutProvider {
        InstancedVertexLayout getInstancedVertexLayout();
        
        /**
         * Fill instance-specific vertex data for this graphics instance
         * @param filler The vertex filler to write instance data to
         * @param instanceIndex The index of this instance in the batch
         */
        void fillInstanceVertexData(VertexFiller filler, int instanceIndex);
        
        /**
         * Get the number of instances this provider will render
         */
        int getInstanceCount();
        
        /**
         * Check if this provider has instance data to fill
         */
        default boolean hasInstanceData() {
            return getInstanceCount() > 0;
        }
    }
    
    public interface VertexCountProvider {
        int getVertexCount();
    }
}