package rogo.sketchrender.api;

public interface RenderStateComponent {
    Class<? extends RenderStateComponent> getType();

    boolean equals(Object other);

    void apply(RenderStateComponent prev);

    int hashCode();
}