package rogo.sketch.core.pipeline;

import rogo.sketch.core.pipeline.data.FrameDataDomain;
import rogo.sketch.core.pipeline.flow.RenderFlowType;
import rogo.sketch.core.pipeline.flow.v2.ComputeStageFlowScene;
import rogo.sketch.core.pipeline.flow.v2.FunctionStageFlowScene;
import rogo.sketch.core.pipeline.flow.v2.RasterStageFlowScene;
import rogo.sketch.core.pipeline.flow.v2.StageFlowScene;
import rogo.sketch.core.pipeline.module.diagnostic.RenderTraceRecorder;
import rogo.sketch.core.util.KeyId;

import java.util.Objects;

public final class PipelineType implements Comparable<PipelineType> {
    private final KeyId identifier;
    private final int priority;
    private final RenderFlowType defaultFlowType;
    private final PipelineFlowSceneFactory sceneFactory;

    public static final PipelineType COMPUTE = new PipelineType(
            "compute",
            100,
            RenderFlowType.COMPUTE,
            (stage, stageId, pipelineType, pipeline, dataDomain, renderTraceRecorder) -> new ComputeStageFlowScene<>(pipelineType));

    public static final PipelineType FUNCTION = new PipelineType(
            "function",
            200,
            RenderFlowType.FUNCTION,
            (stage, stageId, pipelineType, pipeline, dataDomain, renderTraceRecorder) -> new FunctionStageFlowScene<>(
                    pipelineType,
                    pipeline.resourceManager()));

    public static final PipelineType RASTERIZATION = new PipelineType(
            "rasterization",
            300,
            RenderFlowType.RASTERIZATION,
            (stage, stageId, pipelineType, pipeline, dataDomain, renderTraceRecorder) -> new RasterStageFlowScene<>(
                    stage,
                    stageId,
                    pipelineType,
                    pipeline.resourceManager(),
                    pipeline.getGeometryResourceCoordinator(pipelineType),
                    () -> pipeline.getPipelineDataStore(pipelineType, dataDomain),
                    renderTraceRecorder));

    public static final PipelineType TRANSLUCENT = new PipelineType(
            "translucent",
            400,
            RenderFlowType.RASTERIZATION,
            (stage, stageId, pipelineType, pipeline, dataDomain, renderTraceRecorder) -> new RasterStageFlowScene<>(
                    stage,
                    stageId,
                    pipelineType,
                    pipeline.resourceManager(),
                    pipeline.getGeometryResourceCoordinator(pipelineType),
                    () -> pipeline.getPipelineDataStore(pipelineType, dataDomain),
                    renderTraceRecorder));

    public PipelineType(
            KeyId identifier,
            int priority,
            RenderFlowType defaultFlowType,
            PipelineFlowSceneFactory sceneFactory) {
        this.identifier = Objects.requireNonNull(identifier, "identifier");
        this.priority = priority;
        this.defaultFlowType = Objects.requireNonNull(defaultFlowType, "defaultFlowType");
        this.sceneFactory = Objects.requireNonNull(sceneFactory, "sceneFactory");
    }

    public PipelineType(
            String identifier,
            int priority,
            RenderFlowType defaultFlowType,
            PipelineFlowSceneFactory sceneFactory) {
        this(KeyId.valueOf(identifier), priority, defaultFlowType, sceneFactory);
    }

    public int getPriority() {
        return priority;
    }

    public RenderFlowType getDefaultFlowType() {
        return defaultFlowType;
    }

    public KeyId getIdentifier() {
        return identifier;
    }

    @SuppressWarnings("unchecked")
    public <C extends RenderContext> StageFlowScene<C> createStageScene(
            KeyId stageId,
            GraphicsPipeline<C> pipeline,
            FrameDataDomain dataDomain,
            RenderTraceRecorder renderTraceRecorder) {
        return createStageScene(null, stageId, pipeline, dataDomain, renderTraceRecorder);
    }

    @SuppressWarnings("unchecked")
    public <C extends RenderContext> StageFlowScene<C> createStageScene(
            GraphicsStage stage,
            GraphicsPipeline<C> pipeline,
            FrameDataDomain dataDomain,
            RenderTraceRecorder renderTraceRecorder) {
        KeyId stageId = stage != null ? stage.getIdentifier() : null;
        return createStageScene(stage, stageId, pipeline, dataDomain, renderTraceRecorder);
    }

    @SuppressWarnings("unchecked")
    public <C extends RenderContext> StageFlowScene<C> createStageScene(
            GraphicsStage stage,
            KeyId stageId,
            GraphicsPipeline<C> pipeline,
            FrameDataDomain dataDomain,
            RenderTraceRecorder renderTraceRecorder) {
        return (StageFlowScene<C>) sceneFactory.create(stage, stageId, this, pipeline, dataDomain, renderTraceRecorder);
    }

    @Override
    public int compareTo(PipelineType other) {
        return Integer.compare(priority, other.priority);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof PipelineType that)) {
            return false;
        }
        return identifier.equals(that.identifier);
    }

    @Override
    public int hashCode() {
        return Objects.hash(identifier);
    }

    @Override
    public String toString() {
        return "PipelineType{" +
                "id=" + identifier +
                ", priority=" + priority +
                '}';
    }
}
