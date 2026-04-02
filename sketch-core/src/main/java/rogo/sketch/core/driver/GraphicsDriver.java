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
    private static volatile GraphicsAPI legacyApiOverride;

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
        legacyApiOverride = null;
        return runtime;
    }

    public static synchronized void shutdown() {
        BackendRuntime current = runtime;
        runtime = NoOpBackendRuntime.INSTANCE;
        legacyApiOverride = null;
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

    @Deprecated
    public static GraphicsAPI getCurrentAPI() {
        GraphicsAPI api = runtime.legacyGraphicsApi();
        if (api == null) {
            api = legacyApiOverride;
        }
        if (api == null) {
            throw new IllegalStateException("Legacy GraphicsAPI is not available");
        }
        return api;
    }

    @Deprecated
    public static void setCurrentAPI(GraphicsAPI api) {
        legacyApiOverride = api;
    }
}
