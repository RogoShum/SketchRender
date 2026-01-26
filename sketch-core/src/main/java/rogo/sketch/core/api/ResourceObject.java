package rogo.sketch.core.api;

public interface ResourceObject {
    int getHandle();

    void dispose();

    boolean isDisposed();
}