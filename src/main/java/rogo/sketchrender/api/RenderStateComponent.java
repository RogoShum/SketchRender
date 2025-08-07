package rogo.sketchrender.api;

import rogo.sketchrender.render.sketch.RenderContext;

public interface RenderStateComponent {
    Class<? extends RenderStateComponent> getType();

    boolean equals(Object other);

    void apply(RenderContext context);

    int hashCode();
}