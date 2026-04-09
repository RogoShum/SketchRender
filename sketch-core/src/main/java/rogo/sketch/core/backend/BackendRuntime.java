package rogo.sketch.core.backend;

import rogo.sketch.core.pipeline.GraphicsPipeline;
import rogo.sketch.core.pipeline.PipelineType;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.flow.RenderPostProcessors;
import rogo.sketch.core.pipeline.kernel.FrameExecutionPlan;

public interface BackendRuntime {
    String backendName();

    BackendKind kind();

    BackendCapabilities capabilities();

    BackendFrameExecutor frameExecutor();

    default BackendPacketCompiler packetCompiler() {
        return new BackendPacketCompiler() {
        };
    }

    default BackendShaderProgramCache shaderProgramCache() {
        return BackendShaderProgramCache.NO_OP;
    }

    default BackendResourceInstaller resourceInstaller() {
        return BackendResourceInstaller.NO_OP;
    }

    default BackendResourceResolver resourceResolver() {
        return BackendResourceResolver.NO_OP;
    }

    default BackendStateApplier stateApplier() {
        return BackendStateApplier.NO_OP;
    }

    default boolean supportsGeometryMaterialization() {
        return false;
    }

    default <C extends RenderContext> boolean installGeometryUploads(
            GraphicsPipeline<C> pipeline,
            FrameExecutionPlan executionPlan,
            boolean uploadGeometryData) {
        return false;
    }

    default <C extends RenderContext> boolean installImmediateGeometryBindings(
            GraphicsPipeline<C> pipeline,
            PipelineType pipelineType,
            RenderPostProcessors postProcessors) {
        return false;
    }

    default <C extends RenderContext> void materializePendingGeometryResources(GraphicsPipeline<C> pipeline) {
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

    default void installExecutionPlan(FrameExecutionPlan executionPlan) {
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

