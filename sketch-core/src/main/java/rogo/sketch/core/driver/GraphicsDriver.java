package rogo.sketch.core.driver;

import rogo.sketch.core.backend.BackendBootstrap;
import rogo.sketch.core.backend.BackendBootstrapRegistry;
import rogo.sketch.core.backend.BackendBootstrapContext;
import rogo.sketch.core.backend.BackendCapabilities;
import rogo.sketch.core.backend.BackendKind;
import rogo.sketch.core.backend.BackendRuntime;
import rogo.sketch.core.backend.NoOpBackendRuntime;

import java.util.Set;

public final class GraphicsDriver {
    private static volatile BackendRuntime runtime = NoOpBackendRuntime.INSTANCE;

    private GraphicsDriver() {
    }

    public static void registerBackendBootstrap(BackendBootstrap bootstrap) {
        BackendBootstrapRegistry.register(bootstrap);
    }

    public static boolean hasBackendBootstrap(BackendKind kind) {
        return BackendBootstrapRegistry.contains(kind);
    }

    public static synchronized BackendRuntime bootstrap(
            BackendKind kind,
            BackendBootstrapContext context) {
        return bootstrap(BackendBootstrapRegistry.require(kind), context);
    }

    public static synchronized BackendRuntime bootstrap(
            BackendBootstrap bootstrap,
            BackendBootstrapContext context) {
        if (bootstrap == null) {
            throw new IllegalArgumentException("bootstrap must not be null");
        }
        if (isBootstrapped()) {
            throw new IllegalStateException("Graphics backend is already bootstrapped: " + runtime.kind());
        }
        BackendRuntime bootstrappedRuntime = bootstrap.bootstrap(context);
        if (bootstrappedRuntime == null) {
            throw new IllegalStateException("Backend bootstrap returned null runtime");
        }
        runtime = bootstrappedRuntime;
        return runtime;
    }

    public static synchronized void shutdown() {
        BackendRuntime current = runtime;
        runtime = NoOpBackendRuntime.INSTANCE;
        current.shutdown();
    }

    public static BackendRuntime runtime() {
        return runtime;
    }

    public static BackendKind kind() {
        return runtime.kind();
    }

    public static boolean isBootstrapped() {
        return runtime.kind() != BackendKind.UNINITIALIZED;
    }

    public static BackendCapabilities capabilities() {
        return runtime.capabilities();
    }

    public static Set<BackendKind> registeredBackends() {
        return BackendBootstrapRegistry.registeredKinds();
    }
}

