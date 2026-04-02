package rogo.sketch.core.backend;

import rogo.sketch.core.driver.GraphicsAPI;

public interface BackendRuntime {
    String backendName();

    BackendKind kind();

    BackendCapabilities capabilities();

    BackendFrameExecutor frameExecutor();

    default BackendPacketCompiler packetCompiler() {
        return new BackendPacketCompiler() {
        };
    }

    default GraphicsAPI legacyGraphicsApi() {
        return null;
    }

    default void shutdown() {
    }

    default void registerMainThread() {
    }

    default boolean isMainThread() {
        return true;
    }

    default void assertMainThread(String caller) {
    }

    default void assertRenderContext(String caller) {
    }

    default void initializeWorkerLane(BackendWorkerLane lane) {
    }

    default void destroyWorkerLane(BackendWorkerLane lane) {
    }

    default void onWorkerLaneStart(BackendWorkerLane lane) {
    }

    default void onWorkerLaneEnd(BackendWorkerLane lane) {
    }
}
