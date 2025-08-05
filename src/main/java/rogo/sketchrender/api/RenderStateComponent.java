package rogo.sketchrender.api;

public interface RenderStateComponent {
    Class<? extends RenderStateComponent> getType();

    boolean equals(Object other);

    int hashCode();
}