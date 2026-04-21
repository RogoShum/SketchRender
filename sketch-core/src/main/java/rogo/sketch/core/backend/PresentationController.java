package rogo.sketch.core.backend;

public interface PresentationController {
    void notifyFramebufferResized(int width, int height);

    default void applyWindowSettings(WindowService windowService) {
    }
}
