package rogo.sketch.vanilla.backend.opengl;

import rogo.sketch.backend.opengl.OpenGLBackendBootstrap;
import rogo.sketch.backend.opengl.OpenGLStateAccess;
import rogo.sketch.core.backend.BackendBootstrap;
import rogo.sketch.core.backend.BackendBootstrapContext;
import rogo.sketch.core.backend.BackendKind;
import rogo.sketch.core.backend.BackendRuntime;
import rogo.sketch.backend.opengl.driver.GraphicsAPI;

import java.util.Objects;

public final class MinecraftOpenGLBackendBootstrap implements BackendBootstrap {
    private final BackendKind kind;
    private final GraphicsAPI api;

    public MinecraftOpenGLBackendBootstrap(BackendKind kind, GraphicsAPI api) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.api = Objects.requireNonNull(api, "api");
    }

    @Override
    public BackendKind kind() {
        return kind;
    }

    @Override
    public BackendRuntime bootstrap(BackendBootstrapContext context) {
        BackendRuntime nativeRuntime = new OpenGLBackendBootstrap(kind, api).bootstrap(context);
        OpenGLStateAccess stateAccess = new MinecraftOpenGLStateAccess(api);
        return new MinecraftOpenGLBackendRuntime(nativeRuntime, api, stateAccess);
    }
}
