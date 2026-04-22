package rogo.sketch.core.pipeline.kernel;

import rogo.sketch.core.pipeline.GraphicsPipeline;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.graph.CompiledRenderGraph;
import rogo.sketch.core.pipeline.graph.CompiledTickGraph;
import rogo.sketch.core.pipeline.graph.RenderGraphBuilder;
import rogo.sketch.core.pipeline.graph.TickGraphBuilder;
import rogo.sketch.core.pipeline.graph.pass.AsyncRenderPass;
import rogo.sketch.core.pipeline.graph.pass.PostTickAsyncGraphicsPass;
import rogo.sketch.core.pipeline.graph.pass.PostTickGraphicsPass;
import rogo.sketch.core.pipeline.graph.pass.PreTickSwapDataPass;
import rogo.sketch.core.pipeline.graph.pass.SyncApplyPendingSettingsPass;
import rogo.sketch.core.pipeline.graph.pass.SyncCommitPass;
import rogo.sketch.core.pipeline.graph.pass.SyncPreparePass;
import rogo.sketch.core.pipeline.module.ModuleRegistry;
import rogo.sketch.core.pipeline.module.runtime.ModuleRuntimeHost;
import rogo.sketch.core.util.KeyId;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

final class FrameScheduler<C extends RenderContext> {
    private final GraphicsPipeline<C> pipeline;
    private final ModuleRegistry moduleRegistry;
    private final WorkerCoordinator workerCoordinator;
    private final ConcurrentHashMap<String, LifecyclePhase> passPhaseIndex = new ConcurrentHashMap<>();
    private final AtomicLong graphVersion = new AtomicLong();

    private CompiledTickGraph<C> compiledTickGraph;
    private CompiledRenderGraph<C> compiledFrameGraph;
    private volatile GraphSnapshot graphSnapshot = GraphSnapshot.empty();

    FrameScheduler(GraphicsPipeline<C> pipeline, ModuleRegistry moduleRegistry, WorkerCoordinator workerCoordinator) {
        this.pipeline = pipeline;
        this.moduleRegistry = moduleRegistry;
        this.workerCoordinator = workerCoordinator;
    }

    void rebuildGraphs(long frameNumber) {
        rebuildGraphSnapshot(frameNumber);

        TickGraphBuilder<C> tickBuilder = new TickGraphBuilder<>(pipeline);
        tickBuilder.addPostTickPass(new PostTickGraphicsPass<>());

        TickGraphBuilder.TickGraphContribution tickGraphContribution = moduleRegistry.contributeToTickGraph(tickBuilder);
        tickBuilder.addPostTickGlAsyncPass(
                new PostTickAsyncGraphicsPass<>(),
                tickGraphContribution.postTickGlAsyncPassNames().toArray(String[]::new));
        tickBuilder.addPreTickPass(
                new PreTickSwapDataPass<>(),
                tickGraphContribution.preTickPassNames().toArray(String[]::new));
        compiledTickGraph = tickBuilder.compile();

        RenderGraphBuilder<C> frameBuilder = new RenderGraphBuilder<>(pipeline);
        frameBuilder
                .addPass(new SyncCommitPass<>())
                .addPass(new SyncPreparePass<>(), SyncCommitPass.NAME);
        frameBuilder.addPass(new SyncApplyPendingSettingsPass<>(), SyncPreparePass.NAME);

        moduleRegistry.contributeToFrameGraph(frameBuilder);
        frameBuilder.addPass(new AsyncRenderPass<>(), SyncApplyPendingSettingsPass.NAME);
        compiledFrameGraph = frameBuilder.compile();
    }

    void onPreTick(PipelineKernel<C> kernel, C renderContext, long logicTick) {
        if (compiledTickGraph == null) {
            return;
        }
        TickContext<C> tickContext = new TickContext<>(pipeline, kernel, renderContext, logicTick);
        workerCoordinator.tickScheduler().awaitPendingAsync();
        workerCoordinator.tickGlScheduler().awaitPendingAsync();
        workerCoordinator.tickScheduler().execute(compiledTickGraph.preTickGraph(), tickContext, false);
    }

    void onPostTick(PipelineKernel<C> kernel, C renderContext, long logicTick) {
        if (compiledTickGraph == null) {
            return;
        }
        TickContext<C> tickContext = new TickContext<>(pipeline, kernel, renderContext, logicTick);
        workerCoordinator.tickScheduler().execute(compiledTickGraph.postTickGraph(), tickContext, false);
        if (compiledTickGraph.postTickGlAsyncGraph().passCount() > 0) {
            workerCoordinator.tickGlScheduler().execute(compiledTickGraph.postTickGlAsyncGraph(), tickContext, false);
        }
    }

    void executeFrame(PipelineKernel<C> kernel, C renderContext, long frameNumber) {
        if (compiledFrameGraph == null) {
            return;
        }
        FrameContext<C> frameContext = new FrameContext<>(pipeline, kernel, renderContext, frameNumber);
        workerCoordinator.frameScheduler().execute(compiledFrameGraph, frameContext, false);
    }

    GraphSnapshot graphSnapshot() {
        return graphSnapshot;
    }

    boolean isGraphCompiled() {
        return compiledTickGraph != null && compiledFrameGraph != null;
    }

    LifecyclePhase phaseForPass(String moduleId, String passId) {
        if (moduleId != null && passId != null) {
            LifecyclePhase qualified = passPhaseIndex.get(moduleId + ":" + passId);
            if (qualified != null) {
                return qualified;
            }
        }
        return passId != null ? passPhaseIndex.get(passId) : null;
    }

    PassExecutionContext passExecutionContext(
            PipelineKernel<C> kernel,
            String moduleId,
            String passId,
            long frameEpoch,
            long logicTickEpoch) {
        LifecyclePhase phase = phaseForPass(moduleId, passId);
        if (phase == null) {
            phase = LifecyclePhase.SYNC_PRE_BUILD;
        }
        return new PassExecutionContext(kernel, moduleId, passId, phase, frameEpoch, logicTickEpoch);
    }

    private void rebuildGraphSnapshot(long frameNumber) {
        ModuleRuntimeHost runtimeHost = moduleRegistry.runtimeHost();
        Map<String, List<ModulePassDefinition>> modulePasses = Map.of();
        Map<KeyId, FrameResourceHandle<?>> resourceHandles = new LinkedHashMap<>();

        if (runtimeHost != null) {
            GraphSnapshot assembled = runtimeHost.assembleGraphSnapshot(graphVersion.get() + 1L, frameNumber);
            modulePasses = assembled.modulePasses();
            resourceHandles.putAll(assembled.resourceHandles());
        }

        resourceHandles.put(PipelineKernel.BUILD_RESULT_HANDLE.id(), PipelineKernel.BUILD_RESULT_HANDLE);

        GraphSnapshot snapshot = new GraphSnapshot(
                graphVersion.incrementAndGet(),
                frameNumber,
                modulePasses,
                resourceHandles);
        graphSnapshot = snapshot;
        installGraphSnapshot(snapshot);
    }

    private void installGraphSnapshot(GraphSnapshot snapshot) {
        passPhaseIndex.clear();

        registerCorePhase(SyncCommitPass.NAME, LifecyclePhase.SYNC_COMMIT);
        registerCorePhase(SyncPreparePass.NAME, LifecyclePhase.SYNC_PREPARE);
        registerCorePhase(SyncApplyPendingSettingsPass.NAME, LifecyclePhase.SYNC_PRE_BUILD);
        registerCorePhase(AsyncRenderPass.NAME, LifecyclePhase.ASYNC_BUILD);

        for (List<ModulePassDefinition> definitions : snapshot.modulePasses().values()) {
            for (ModulePassDefinition definition : definitions) {
                registerPhase(definition.moduleId(), definition.passId(), definition.phase());
            }
        }
    }

    private void registerCorePhase(String passId, LifecyclePhase phase) {
        passPhaseIndex.put(passId, phase);
    }

    private void registerPhase(String moduleId, String passId, LifecyclePhase phase) {
        passPhaseIndex.put(moduleId + ":" + passId, phase);
        passPhaseIndex.putIfAbsent(passId, phase);
    }
}
