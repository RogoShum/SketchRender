package rogo.sketch.core.pipeline.kernel.commit;

import rogo.sketch.core.backend.CommandRecorder;
import rogo.sketch.core.driver.GraphicsDriver;
import rogo.sketch.core.packet.RenderPacketQueue;
import rogo.sketch.core.pipeline.GraphicsPipeline;
import rogo.sketch.core.pipeline.PipelineType;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.data.FrameDataDomain;
import rogo.sketch.core.pipeline.data.IndirectBufferData;
import rogo.sketch.core.pipeline.data.PipelineDataStore;
import rogo.sketch.core.pipeline.flow.RenderFlowType;
import rogo.sketch.core.pipeline.flow.RenderPostProcessors;
import rogo.sketch.core.pipeline.graph.pass.AsyncRenderPass;
import rogo.sketch.core.pipeline.graph.pass.SyncApplyPendingSettingsPass;
import rogo.sketch.core.pipeline.graph.pass.SyncCommitPass;
import rogo.sketch.core.pipeline.graph.pass.SyncPreparePass;
import rogo.sketch.core.pipeline.kernel.BuildResult;
import rogo.sketch.core.pipeline.kernel.FrameContext;
import rogo.sketch.core.pipeline.kernel.FrameExecutionPlan;

import java.util.List;

/**
 * Shared synchronous commit micro-pipeline.
 * <p>
 * This is the first concrete step toward the V4C submit/commit split: the old
 * {@link SyncCommitPass} remains the graph node, while the actual commit work is
 * expressed as an ordered list of commit steps.
 * </p>
 */
public final class FrameCommitPipeline<C extends RenderContext> {
    private final List<CommitStep<C>> steps;

    public FrameCommitPipeline() {
        this.steps = List.of(
                new ConsumeBuildResultStep<>(),
                new InstallGeometryUploadsStep<>(),
                new ExecuteNonRasterPostProcessorsStep<>(),
                new MaterializePendingGeometryStep<>(),
                new SwapFrameDataStoresStep<>(),
                new InstallExecutionPlanStep<>());
    }

    public void execute(FrameContext<C> frameContext) {
        if (frameContext == null) {
            return;
        }

        try (CommandRecorder recorder = GraphicsDriver.commandRecorderFactory()
                .create("frame_commit:" + frameContext.frameNumber())) {
            CommitContext<C> commitContext = new CommitContext<>(frameContext, recorder);
            for (CommitStep<C> step : steps) {
                if (step == null || !step.execute(commitContext)) {
                    break;
                }
            }
            recorder.submit();
        }
    }

    public static final class CommitContext<C extends RenderContext> {
        private final FrameContext<C> frameContext;
        private final CommandRecorder commandRecorder;
        private BuildResult buildResult;
        private FrameExecutionPlan executionPlan = FrameExecutionPlan.empty();
        private RenderPostProcessors postProcessors;
        private boolean geometryHandled;

        private CommitContext(FrameContext<C> frameContext, CommandRecorder commandRecorder) {
            this.frameContext = frameContext;
            this.commandRecorder = commandRecorder;
        }

        public FrameContext<C> frameContext() {
            return frameContext;
        }

        public GraphicsPipeline<C> pipeline() {
            return frameContext.pipeline();
        }

        public RenderPacketQueue<C> queue() {
            return frameContext.pipeline().getRenderPacketQueue();
        }

        public CommandRecorder commandRecorder() {
            return commandRecorder;
        }

        public BuildResult buildResult() {
            return buildResult;
        }

        public void setBuildResult(BuildResult buildResult) {
            this.buildResult = buildResult;
            this.executionPlan = buildResult != null && buildResult.executionPlan() != null
                    ? buildResult.executionPlan()
                    : FrameExecutionPlan.empty();
            this.postProcessors = buildResult != null ? buildResult.postProcessors() : null;
        }

        public FrameExecutionPlan executionPlan() {
            return executionPlan;
        }

        public RenderPostProcessors postProcessors() {
            return postProcessors;
        }

        public boolean uploadsCompleted() {
            return buildResult != null && buildResult.uploadsCompleted();
        }

        public boolean geometryHandled() {
            return geometryHandled;
        }

        public void setGeometryHandled(boolean geometryHandled) {
            this.geometryHandled = geometryHandled;
        }
    }

    private static final class ConsumeBuildResultStep<C extends RenderContext> implements CommitStep<C> {
        @Override
        public boolean execute(CommitContext<C> context) {
            context.setBuildResult(context.frameContext().kernel().consumeBuildResult());
            return context.buildResult() != null;
        }
    }

    private static final class InstallGeometryUploadsStep<C extends RenderContext> implements CommitStep<C> {
        @Override
        public boolean execute(CommitContext<C> context) {
            FrameExecutionPlan executionPlan = context.executionPlan();
            if (!executionPlan.geometryUploadPlans().isEmpty() || !executionPlan.resourceUploadPlans().isEmpty()) {
                boolean handled = GraphicsDriver.resourceAllocator().installExecutionPlan(
                        context.pipeline(),
                        executionPlan,
                        context.frameContext().frameNumber(),
                        GraphicsDriver.submissionScheduler().framesInFlight(),
                        !context.uploadsCompleted());
                context.setGeometryHandled(handled);
            }

            if (!context.geometryHandled() && !context.uploadsCompleted()) {
                for (FrameExecutionPlan.GeometryUploadPlan geometryUploadPlan : executionPlan.geometryUploadPlans()) {
                    if (geometryUploadPlan != null) {
                        geometryUploadPlan.releaseBuilderSnapshots();
                    }
                }
            }
            return true;
        }
    }

    private static final class ExecuteNonRasterPostProcessorsStep<C extends RenderContext> implements CommitStep<C> {
        @Override
        public boolean execute(CommitContext<C> context) {
            if (!context.uploadsCompleted() && context.postProcessors() != null) {
                context.postProcessors().executeAllExcept(RenderFlowType.RASTERIZATION);
            }
            return true;
        }
    }

    private static final class MaterializePendingGeometryStep<C extends RenderContext> implements CommitStep<C> {
        @Override
        public boolean execute(CommitContext<C> context) {
            context.pipeline().materializePendingGeometryBindings();
            return true;
        }
    }

    private static final class SwapFrameDataStoresStep<C extends RenderContext> implements CommitStep<C> {
        @Override
        public boolean execute(CommitContext<C> context) {
            context.pipeline().swapFrameDataStores();

            for (PipelineType pipelineType : context.pipeline().getPipelineTypes()) {
                PipelineDataStore readStore = context.pipeline().getPipelineDataStore(
                        pipelineType,
                        FrameDataDomain.SYNC_READ);
                PipelineDataStore writeStore = context.pipeline().getPipelineDataStore(
                        pipelineType,
                        FrameDataDomain.ASYNC_BUILD);
                IndirectBufferData readIndirectBufferData = readStore.get(IndirectBufferData.KEY);
                IndirectBufferData writeIndirectBufferData = writeStore.get(IndirectBufferData.KEY);
                if (readIndirectBufferData != null) {
                    readIndirectBufferData.materializePending();
                }
                if (writeIndirectBufferData != null) {
                    writeIndirectBufferData.materializePending();
                }
                if (readIndirectBufferData != null && writeIndirectBufferData != null) {
                    writeIndirectBufferData.synchronizeLayoutsFrom(readIndirectBufferData);
                }
            }
            return true;
        }
    }

    private static final class InstallExecutionPlanStep<C extends RenderContext> implements CommitStep<C> {
        @Override
        public boolean execute(CommitContext<C> context) {
            FrameExecutionPlan executionPlan = context.executionPlan();
            GraphicsDriver.submissionScheduler().installExecutionPlan(executionPlan);
            context.queue().installExecutionPlan(executionPlan);
            return true;
        }
    }
}
