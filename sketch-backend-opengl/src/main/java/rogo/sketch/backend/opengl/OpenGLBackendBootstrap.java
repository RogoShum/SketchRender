package rogo.sketch.backend.opengl;

import rogo.sketch.core.backend.BackendBootstrap;
import rogo.sketch.core.backend.BackendBootstrapContext;
import rogo.sketch.core.backend.BackendKind;
import rogo.sketch.core.backend.BackendRuntime;
import rogo.sketch.backend.opengl.driver.GraphicsAPI;

import java.util.Objects;

/**
 * Formal OpenGL backend bootstrap entry.
 */
public final class OpenGLBackendBootstrap implements BackendBootstrap {
    private final BackendKind kind;
    private final GraphicsAPI api;

    public OpenGLBackendBootstrap(BackendKind kind, GraphicsAPI api) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.api = Objects.requireNonNull(api, "api");
    }

    @Override
    public BackendKind kind() {
        return kind;
    }

    @Override
    public BackendRuntime bootstrap(BackendBootstrapContext context) {
        OpenGLProgramReflectionService.install(api);
        return new OpenGLBackendRuntime(kind, api, context.mainWindowHandle());
    }
}

