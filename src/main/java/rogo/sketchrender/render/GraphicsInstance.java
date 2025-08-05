package rogo.sketchrender.render;

import rogo.sketchrender.util.Identifier;

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
    public void render(C context) {
        // Rendering logic to be implemented by subclasses or left blank for now.
    }
}