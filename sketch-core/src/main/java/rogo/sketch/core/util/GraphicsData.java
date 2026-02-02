package rogo.sketch.core.util;

public abstract class GraphicsData<T extends GraphicsData<T>> {
    public abstract void copyFrom(T other);
}
