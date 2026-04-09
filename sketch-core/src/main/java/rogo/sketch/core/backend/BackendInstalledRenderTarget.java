package rogo.sketch.core.backend;

/**
 * Marker for installed backend render-target/framebuffer resources.
 */
public interface BackendInstalledRenderTarget extends BackendInstalledResource {
    void bind();
}

