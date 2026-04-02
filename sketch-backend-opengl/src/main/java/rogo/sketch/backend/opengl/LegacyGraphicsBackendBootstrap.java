package rogo.sketch.backend.opengl;

import rogo.sketch.core.backend.BackendBootstrap;
import rogo.sketch.core.backend.BackendBootstrapContext;
import rogo.sketch.core.backend.BackendKind;
import rogo.sketch.core.backend.BackendRuntime;
import rogo.sketch.core.driver.GraphicsAPI;

import java.util.Objects;

public final class LegacyGraphicsBackendBootstrap implements BackendBootstrap {
    private final BackendKind kind;
    private final GraphicsAPI api;

    public LegacyGraphicsBackendBootstrap(BackendKind kind, GraphicsAPI api) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.api = Objects.requireNonNull(api, "api");
    }

    @Override
    public BackendKind kind() {
        return kind;
    }

    @Override
    public BackendRuntime bootstrap(BackendBootstrapContext context) {
        OpenGLProgramReflectionService.install();
        return new LegacyGraphicsBackendRuntime(kind, api, context.mainWindowHandle());
    }
}
