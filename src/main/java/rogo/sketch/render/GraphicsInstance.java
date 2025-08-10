package rogo.sketch.render;

import rogo.sketch.render.data.filler.VertexFiller;
import rogo.sketch.render.vertex.VertexResourceProvider;
import rogo.sketch.render.vertex.VertexResourceType;
import rogo.sketch.util.Identifier;

public abstract class GraphicsInstance<C extends RenderContext> {
    private final Identifier identifier;

    public GraphicsInstance(Identifier identifier) {
        this.identifier = identifier;
    }

    public Identifier getIdentifier() {
        return identifier;
    }

    /**
     * Called every tick to update the instance.
     */
    public abstract void tick();

    public abstract void fillVertex(VertexFiller filler);

    /**
     * Whether this instance should be discarded.
     */
    public abstract boolean shouldDiscard();

    /**
     * Whether this instance should be rendered.
     */
    public abstract boolean shouldRender();

    /**
     * Render this instance. (Implementation left blank)
     */
    public abstract void render(C context);
    
    /**
     * Get the vertex resource type for this instance
     * Override this to use independent vertex resources
     */
    public VertexResourceType getVertexResourceType() {
        return VertexResourceType.SHARED_DYNAMIC; // Default to shared batching
    }
    
    /**
     * Check if this instance provides its own vertex resource
     */
    public final boolean isVertexResourceProvider() {
        return this instanceof VertexResourceProvider;
    }
    
    /**
     * Get as VertexResourceProvider if applicable
     */
    public final VertexResourceProvider asVertexResourceProvider() {
        if (this instanceof VertexResourceProvider) {
            return (VertexResourceProvider) this;
        }
        throw new IllegalStateException("GraphicsInstance is not a VertexResourceProvider");
    }
}