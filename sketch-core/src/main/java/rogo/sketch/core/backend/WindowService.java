package rogo.sketch.core.backend;

import java.util.List;

public interface WindowService {
    long windowHandle();

    int framebufferWidth();

    int framebufferHeight();

    void resizeFramebuffer(int width, int height);

    default void applyWindowedSize(int width, int height) {
        resizeFramebuffer(width, height);
    }

    void setVSync(boolean enabled);

    boolean vSyncEnabled();

    default List<WindowDisplayMode> supportedDisplayModes() {
        return List.of(new WindowDisplayMode(framebufferWidth(), framebufferHeight(), 0));
    }

    default boolean fullscreenEnabled() {
        return false;
    }

    default void setFullscreen(boolean enabled) {
    }

    default void applyDisplayMode(WindowDisplayMode mode) {
        if (mode != null) {
            applyWindowedSize(mode.width(), mode.height());
        }
    }
}
