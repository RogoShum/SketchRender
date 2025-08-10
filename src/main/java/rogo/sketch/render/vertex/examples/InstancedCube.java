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

import java.util.ArrayList;
import java.util.List;

/**
 * Example of instanced rendering with static geometry and dynamic instance data
 */
public class InstancedCube extends GraphicsInstance<RenderContext> implements VertexResourceProvider {
    
    private VertexResource vertexResource;
    private boolean staticDataUploaded = false;
    private final DataFormat staticFormat; // Cube geometry
    private final DataFormat instanceFormat; // Per-instance data (position, scale, etc.)
    
    private final List<CubeInstance> instances = new ArrayList<>();
    
    public InstancedCube(Identifier identifier, DataFormat staticFormat, DataFormat instanceFormat) {
        super(identifier);
        this.staticFormat = staticFormat;
        this.instanceFormat = instanceFormat;
    }
    
    public void addInstance(float x, float y, float z, float scale) {
        instances.add(new CubeInstance(x, y, z, scale));
    }
    
    public void removeInstance(int index) {
        if (index >= 0 && index < instances.size()) {
            instances.remove(index);
        }
    }

    @Override
    public void tick() {
        // Update instance data if needed
        for (CubeInstance instance : instances) {
            instance.update();
        }
    }

    @Override
    public void fillVertex(VertexFiller filler) {
        // Not used for instanced rendering
        throw new UnsupportedOperationException("Instanced cubes use their own vertex resources");
    }

    @Override
    public boolean shouldDiscard() {
        return instances.isEmpty();
    }

    @Override
    public boolean shouldRender() {
        return !instances.isEmpty();
    }

    @Override
    public void render(RenderContext context) {
        // Rendering handled by customRender()
    }

    @Override
    public VertexResourceType getVertexResourceType() {
        return VertexResourceType.INSTANCE_INSTANCED;
    }

    @Override
    public VertexResource getOrCreateVertexResource() {
        if (vertexResource == null) {
            vertexResource = VertexResource.createInstanced(staticFormat, instanceFormat, GL11.GL_TRIANGLES);
        }
        return vertexResource;
    }

    @Override
    public boolean needsVertexUpdate() {
        return !staticDataUploaded || !instances.isEmpty();
    }

    @Override
    public void fillVertexData(VertexFiller filler) {
        VertexResource resource = getOrCreateVertexResource();
        
        // Fill static cube geometry once
        if (!staticDataUploaded) {
            fillCubeGeometry(filler);
            staticDataUploaded = true;
        }
        
        // Clear and refill instance data
        resource.clearInstances();
        for (CubeInstance instance : instances) {
            var instanceFiller = resource.addInstance();
            instanceFiller.writeFloat(instance.x);
            instanceFiller.writeFloat(instance.y);
            instanceFiller.writeFloat(instance.z);
            instanceFiller.writeFloat(instance.scale);
        }
        resource.endInstanceFill();
    }
    
    private void fillCubeGeometry(VertexFiller filler) {
        // Define a simple cube geometry
        float[] cubeVertices = {
            // Front face
            -1.0f, -1.0f,  1.0f,  // 0
             1.0f, -1.0f,  1.0f,  // 1
             1.0f,  1.0f,  1.0f,  // 2
            -1.0f,  1.0f,  1.0f,  // 3
            // Back face
            -1.0f, -1.0f, -1.0f,  // 4
             1.0f, -1.0f, -1.0f,  // 5
             1.0f,  1.0f, -1.0f,  // 6
            -1.0f,  1.0f, -1.0f   // 7
        };
        
        // Add vertices
        for (int i = 0; i < cubeVertices.length; i += 3) {
            filler.vertex(cubeVertices[i], cubeVertices[i + 1], cubeVertices[i + 2]);
            filler.color(1.0f, 1.0f, 1.0f, 1.0f);
        }
        
        // Add cube faces using indices
        // Front face
        filler.quad(0, 1, 2, 3);
        // Back face
        filler.quad(5, 4, 7, 6);
        // Left face
        filler.quad(4, 0, 3, 7);
        // Right face
        filler.quad(1, 5, 6, 2);
        // Bottom face
        filler.quad(4, 5, 1, 0);
        // Top face
        filler.quad(3, 2, 6, 7);
    }

    @Override
    public void customRender() {
        VertexResource resource = getOrCreateVertexResource();
        if (resource != null && resource.hasInstances()) {
            VertexRenderer.render(resource);
        }
    }
    
    private static class CubeInstance {
        float x, y, z, scale;
        
        CubeInstance(float x, float y, float z, float scale) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.scale = scale;
        }
        
        void update() {
            // Update instance data (rotation, animation, etc.)
        }
    }
}
