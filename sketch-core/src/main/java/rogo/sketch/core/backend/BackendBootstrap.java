package rogo.sketch.core.backend;

public interface BackendBootstrap {
    BackendKind kind();

    BackendRuntime bootstrap(BackendBootstrapContext context);
}
