package rogo.sketch.core.backend;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class BackendBootstrapRegistry {
    private static final Map<BackendKind, BackendBootstrap> BOOTSTRAPS = new EnumMap<>(BackendKind.class);

    private BackendBootstrapRegistry() {
    }

    public static synchronized void register(BackendBootstrap bootstrap) {
        if (bootstrap == null) {
            throw new IllegalArgumentException("bootstrap must not be null");
        }
        BOOTSTRAPS.put(bootstrap.kind(), bootstrap);
    }

    public static synchronized boolean contains(BackendKind kind) {
        return BOOTSTRAPS.containsKey(kind);
    }

    public static synchronized BackendBootstrap find(BackendKind kind) {
        return BOOTSTRAPS.get(kind);
    }

    public static synchronized BackendBootstrap require(BackendKind kind) {
        BackendBootstrap bootstrap = BOOTSTRAPS.get(kind);
        if (bootstrap == null) {
            throw new IllegalStateException("No backend bootstrap registered for " + kind);
        }
        return bootstrap;
    }

    public static synchronized Set<BackendKind> registeredKinds() {
        if (BOOTSTRAPS.isEmpty()) {
            return EnumSet.noneOf(BackendKind.class);
        }
        return EnumSet.copyOf(BOOTSTRAPS.keySet());
    }
}
