package rogo.sketch.render.vertex.examples;

import org.lwjgl.opengl.GL11;
import rogo.sketch.render.GraphicsInstance;
import rogo.sketch.render.RenderContext;
import rogo.sketch.render.RenderParameter;
import rogo.sketch.render.data.filler.VertexFiller;
import rogo.sketch.render.data.format.DataFormat;
import rogo.sketch.render.vertex.VertexResource;
import rogo.sketch.render.vertex.VertexResourceProvider;
import rogo.sketch.render.vertex.VertexResourceType;
import rogo.sketch.render.vertex.VertexRenderer;
import rogo.sketch.util.Identifier;

/**
 * Example of a GraphicsInstance with static geometry that owns its VertexResource
 */
public class StaticMeshInstance extends GraphicsInstance<RenderContext> implements VertexResourceProvider {
    
    private VertexResource vertexResource;
    private boolean vertexDataUploaded = false;
    private final DataFormat dataFormat;
    private final float[] vertexData;
    
    public StaticMeshInstance(Identifier identifier, DataFormat dataFormat, float[] vertexData) {
        super(identifier);
        this.dataFormat = dataFormat;
        this.vertexData = vertexData;
    }

    @Override
    public void tick() {
        // Static mesh doesn't need updates
    }

    @Override
    public void fillVertex(VertexFiller filler) {
        // This method is for shared batching - not used by static instances
        throw new UnsupportedOperationException("Static mesh instances use their own vertex resources");
    }

    @Override
    public boolean shouldDiscard() {
        return false;
    }

    @Override
    public boolean shouldRender() {
        return true;
    }

    @Override
    public void render(RenderContext context) {
        // Rendering is handled by customRender() from VertexResourceProvider
    }

    @Override
    public VertexResourceType getVertexResourceType() {
        return VertexResourceType.INSTANCE_STATIC;
    }

    @Override
    public VertexResource getOrCreateVertexResource() {
        if (vertexResource == null) {
            RenderParameter params = RenderParameter.createInstanceStatic(dataFormat, GL11.GL_TRIANGLES);
            vertexResource = params.createVertexResource();
        }
        return vertexResource;
    }

    @Override
    public boolean needsVertexUpdate() {
        return !vertexDataUploaded;
    }

    @Override
    public void fillVertexData(VertexFiller filler) {
        if (!vertexDataUploaded) {
            // Fill the static vertex data once
            for (int i = 0; i < vertexData.length; i += 3) {
                filler.vertex(vertexData[i], vertexData[i + 1], vertexData[i + 2]);
            }
            vertexDataUploaded = true;
        }
    }

    @Override
    public void customRender() {
        VertexResource resource = getOrCreateVertexResource();
        if (resource != null) {
            VertexRenderer.render(resource);
        }
    }
}
