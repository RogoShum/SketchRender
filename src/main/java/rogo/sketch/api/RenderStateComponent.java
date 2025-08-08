package rogo.sketch.api;

import rogo.sketch.render.RenderContext;

public interface RenderStateComponent {
    Class<? extends RenderStateComponent> getType();

    boolean equals(Object other);

    void apply(RenderContext context);

    int hashCode();
}