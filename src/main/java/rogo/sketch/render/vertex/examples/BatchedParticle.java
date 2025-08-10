package rogo.sketch.render.vertex.examples;

import rogo.sketch.render.GraphicsInstance;
import rogo.sketch.render.RenderContext;
import rogo.sketch.render.data.filler.VertexFiller;
import rogo.sketch.render.vertex.VertexResourceType;
import rogo.sketch.util.Identifier;

/**
 * Example of a GraphicsInstance that uses shared batching
 */
public class BatchedParticle extends GraphicsInstance<RenderContext> {
    
    private float x, y, z;
    private float size;
    private boolean alive;
    
    public BatchedParticle(Identifier identifier, float x, float y, float z, float size) {
        super(identifier);
        this.x = x;
        this.y = y;
        this.z = z;
        this.size = size;
        this.alive = true;
    }

    @Override
    public void tick() {
        // Update particle position, check lifetime, etc.
        y -= 0.1f; // Simple gravity
        if (y < 0) {
            alive = false;
        }
    }

    @Override
    public void fillVertex(VertexFiller filler) {
        if (alive) {
            // Add quad vertices for this particle
            float halfSize = size * 0.5f;
            
            // Bottom-left
            filler.vertex(x - halfSize, y - halfSize, z);
            filler.color(1.0f, 1.0f, 1.0f, 1.0f);
            filler.uv(0.0f, 0.0f);
            
            // Bottom-right
            filler.vertex(x + halfSize, y - halfSize, z);
            filler.color(1.0f, 1.0f, 1.0f, 1.0f);
            filler.uv(1.0f, 0.0f);
            
            // Top-right
            filler.vertex(x + halfSize, y + halfSize, z);
            filler.color(1.0f, 1.0f, 1.0f, 1.0f);
            filler.uv(1.0f, 1.0f);
            
            // Top-left
            filler.vertex(x - halfSize, y + halfSize, z);
            filler.color(1.0f, 1.0f, 1.0f, 1.0f);
            filler.uv(0.0f, 1.0f);
            
            // Create quad using indices
            filler.quad(0, 1, 2, 3);
        }
    }

    @Override
    public boolean shouldDiscard() {
        return !alive;
    }

    @Override
    public boolean shouldRender() {
        return alive;
    }

    @Override
    public void render(RenderContext context) {
        // Rendering is handled by the shared batching system
    }

    @Override
    public VertexResourceType getVertexResourceType() {
        return VertexResourceType.SHARED_DYNAMIC; // Use shared batching
    }
}
