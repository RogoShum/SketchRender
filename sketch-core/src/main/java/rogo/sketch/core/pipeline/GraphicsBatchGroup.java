package rogo.sketch.core.pipeline;

import rogo.sketch.core.graphics.ecs.GraphicsEntityId;
import rogo.sketch.core.packet.ExecutionKey;
import rogo.sketch.core.packet.RenderPacket;
import rogo.sketch.core.pipeline.flow.RenderFlowType;
import rogo.sketch.core.pipeline.flow.RenderPostProcessors;
import rogo.sketch.core.pipeline.flow.ecs.GraphicsContainerHints;
import rogo.sketch.core.pipeline.flow.ecs.PriorityIndexSystem;
import rogo.sketch.core.pipeline.flow.ecs.QueueIndexSystem;
import rogo.sketch.core.pipeline.flow.ecs.SpatialIndexSystem;
import rogo.sketch.core.pipeline.flow.v2.ComputeStageFlowScene;
import rogo.sketch.core.pipeline.flow.v2.FunctionStageFlowScene;
import rogo.sketch.core.pipeline.flow.v2.PreparedStageEntityView;
import rogo.sketch.core.pipeline.flow.v2.RasterStageFlowScene;
import rogo.sketch.core.pipeline.flow.v2.StageEntityView;
import rogo.sketch.core.pipeline.flow.v2.StageFlowScene;
import rogo.sketch.core.pipeline.kernel.StageExecutionPlan;
import rogo.sketch.core.pipeline.module.diagnostic.SketchDiagnostics;
import rogo.sketch.core.util.KeyId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static rogo.sketch.core.pipeline.PipelineType.COMPUTE;
import static rogo.sketch.core.pipeline.PipelineType.FUNCTION;
import static rogo.sketch.core.pipeline.PipelineType.RASTERIZATION;
import static rogo.sketch.core.pipeline.PipelineType.TRANSLUCENT;

/**
 * ECS-native stage-local pipeline router.
 */
public class GraphicsBatchGroup<C extends RenderContext> {
    private final GraphicsPipeline<C> graphicsPipeline;
    private final KeyId stageKeyId;
    private final Map<PipelineType, StageFlowScene<C>> flowScenes = new LinkedHashMap<>();
    private final Object stageStateLock = new Object();
    private final QueueIndexSystem queueIndexSystem = new QueueIndexSystem();
    private final PriorityIndexSystem priorityIndexSystem = new PriorityIndexSystem();
    private final SpatialIndexSystem<C> spatialIndexSystem = new SpatialIndexSystem<>();
    private final Map<PipelineType, CachedPreparedStageEntityView> preparedViewCache = new LinkedHashMap<>();
    private final Map<PipelineType, PreparedSceneState> preparedSceneCache = new LinkedHashMap<>();
    private final Map<PipelineType, CachedStageEntityView> stageViewCache = new LinkedHashMap<>();

    public GraphicsBatchGroup(GraphicsPipeline<C> graphicsPipeline, KeyId stageKeyId) {
        this.graphicsPipeline = graphicsPipeline;
        this.stageKeyId = stageKeyId;

        for (PipelineType pipelineType : graphicsPipeline.getPipelineTypes()) {
            initializePipeline(pipelineType);
        }
    }

    private void initializePipeline(PipelineType pipelineType) {
        if (pipelineType == RASTERIZATION || pipelineType == TRANSLUCENT) {
            flowScenes.put(pipelineType, new RasterStageFlowScene<>(
                    stageKeyId,
                    pipelineType,
                    graphicsPipeline.getGeometryResourceCoordinator(pipelineType),
                    () -> graphicsPipeline.getPipelineDataStore(pipelineType, rogo.sketch.core.pipeline.data.FrameDataDomain.ASYNC_BUILD),
                    graphicsPipeline.renderTraceRecorder()));
            return;
        }
        if (pipelineType == COMPUTE) {
            flowScenes.put(pipelineType, new ComputeStageFlowScene<>(pipelineType));
            return;
        }
        if (pipelineType == FUNCTION) {
            flowScenes.put(pipelineType, new FunctionStageFlowScene<>(pipelineType));
            return;
        }
        throw new IllegalArgumentException("Unsupported pipeline type " + pipelineType + " for stage " + stageKeyId);
    }

    public void tick(C context) {
        synchronized (stageStateLock) {
            for (Map.Entry<PipelineType, StageFlowScene<C>> entry : flowScenes.entrySet()) {
                StageEntityView view = preparePreparedStageEntityView(entry.getKey()).fullView();
                entry.getValue().tick(graphicsPipeline.graphicsWorld(), view, context);
            }
        }
    }

    public void asyncTick(C context) {
        synchronized (stageStateLock) {
            for (Map.Entry<PipelineType, StageFlowScene<C>> entry : flowScenes.entrySet()) {
                StageEntityView view = preparePreparedStageEntityView(entry.getKey()).fullView();
                entry.getValue().asyncTick(graphicsPipeline.graphicsWorld(), view, context);
            }
        }
    }

    public void swapData() {
        synchronized (stageStateLock) {
            for (Map.Entry<PipelineType, StageFlowScene<C>> entry : flowScenes.entrySet()) {
                StageEntityView view = preparePreparedStageEntityView(entry.getKey()).fullView();
                entry.getValue().swapData(graphicsPipeline.graphicsWorld(), view);
            }
        }
    }

    public KeyId getStageIdentifier() {
        return stageKeyId;
    }

    public Map<ExecutionKey, List<RenderPacket>> createRenderPackets(PipelineType pipelineType, C context, RenderPostProcessors postProcessors) {
        try {
            synchronized (stageStateLock) {
                StageFlowScene<C> scene = flowScenes.get(pipelineType);
                if (scene == null) {
                    return Collections.emptyMap();
                }

                StageEntityView view = buildStageEntityView(pipelineType, context);
                RenderFlowType flowType = pipelineType.getDefaultFlowType();
                Map<ExecutionKey, List<RenderPacket>> packets = scene.createRenderPackets(view, flowType, postProcessors, context);
                return packets != null ? packets : Collections.emptyMap();
            }
        } catch (Exception e) {
            SketchDiagnostics.get().error("graphics-batch-group", "Failed to create render packets for stage " + stageKeyId, e);
            return Collections.emptyMap();
        }
    }

    public Map<PipelineType, Map<ExecutionKey, List<RenderPacket>>> createAllRenderPackets(C context, RenderPostProcessors postProcessors) {
        Map<PipelineType, Map<ExecutionKey, List<RenderPacket>>> allPackets = new LinkedHashMap<>();

        for (PipelineType pipelineType : flowScenes.keySet()) {
            Map<ExecutionKey, List<RenderPacket>> packets = createRenderPackets(pipelineType, context, postProcessors);
            if (!packets.isEmpty()) {
                allPackets.put(pipelineType, packets);
            }
        }
        return allPackets;
    }

    public StageExecutionPlan createStageExecutionPlan(C context, RenderPostProcessors postProcessors) {
        return StageExecutionPlan.fromPackets(stageKeyId, createAllRenderPackets(context, postProcessors));
    }

    public void cleanupDiscardedInstances() {
        synchronized (stageStateLock) {
            for (Map.Entry<PipelineType, StageFlowScene<C>> entry : flowScenes.entrySet()) {
                StageEntityView view = preparePreparedStageEntityView(entry.getKey()).fullView();
                entry.getValue().cleanupDiscardedEntities(
                        graphicsPipeline.graphicsWorld(),
                        graphicsPipeline.graphicsEntityAssembler(),
                        view);
            }
            preparedViewCache.clear();
            preparedSceneCache.clear();
            stageViewCache.clear();
        }
    }

    public void prepareForFrame(C context) {
        synchronized (stageStateLock) {
            for (Map.Entry<PipelineType, StageFlowScene<C>> entry : flowScenes.entrySet()) {
                prepareSceneForPreparedView(entry.getKey(), context);
            }
        }
    }

    public void prepareForFrame() {
        prepareForFrame(graphicsPipeline.currentContext());
    }

    public void clear() {
        synchronized (stageStateLock) {
            for (StageFlowScene<C> scene : flowScenes.values()) {
                scene.clear();
            }
            preparedViewCache.clear();
            preparedSceneCache.clear();
            stageViewCache.clear();
        }
    }

    public void prepareNextFrameStageViews() {
        synchronized (stageStateLock) {
            preparedViewCache.clear();
            preparedSceneCache.clear();
            stageViewCache.clear();
            for (PipelineType pipelineType : flowScenes.keySet()) {
                preparePreparedStageEntityView(pipelineType);
                prepareSceneForPreparedView(pipelineType, null);
            }
        }
    }

    public int getTotalInstanceCount() {
        int total = 0;
        for (PipelineType pipelineType : flowScenes.keySet()) {
            total += graphicsPipeline.stageMembershipIndex().entities(stageKeyId, pipelineType).size();
        }
        return total;
    }

    public boolean hasInstances() {
        return getTotalInstanceCount() > 0;
    }

    private StageEntityView buildStageEntityView(PipelineType pipelineType, C context) {
        PreparedStageEntityView preparedView = preparePreparedStageEntityView(pipelineType);
        CachedStageEntityView cached = stageViewCache.get(pipelineType);
        long membershipVersion = graphicsPipeline.stageMembershipIndex().version();
        int logicTick = graphicsPipeline.currentLogicTick();
        int renderTick = context != null ? context.renderTick() : 0;
        float partialTicks = context != null ? context.partialTicks() : 0.0f;
        int frustumIdentity = context != null && context.getFrustum() != null
                ? System.identityHashCode(context.getFrustum())
                : 0;
        if (cached != null
                && cached.logicTick() == logicTick
                && cached.renderTick() == renderTick
                && Float.compare(cached.partialTicks(), partialTicks) == 0
                && cached.frustumIdentity() == frustumIdentity
                && cached.membershipVersion() == membershipVersion) {
            return cached.view();
        }

        StageEntityView view = preparedView.finalizeForFrame(spatialIndexSystem, context);
        stageViewCache.put(pipelineType, new CachedStageEntityView(
                logicTick,
                renderTick,
                partialTicks,
                frustumIdentity,
                membershipVersion,
                view));
        return view;
    }

    private PreparedStageEntityView preparePreparedStageEntityView(PipelineType pipelineType) {
        CachedPreparedStageEntityView cached = preparedViewCache.get(pipelineType);
        long membershipVersion = graphicsPipeline.stageMembershipIndex().version();
        int logicTick = graphicsPipeline.currentLogicTick();
        if (cached != null
                && cached.logicTick() == logicTick
                && cached.membershipVersion() == membershipVersion) {
            return cached.view();
        }

        List<GraphicsEntityId> entityIds = graphicsPipeline.stageMembershipIndex().entities(stageKeyId, pipelineType);
        if (entityIds.isEmpty()) {
            PreparedStageEntityView emptyView = new PreparedStageEntityView(stageKeyId, pipelineType, List.of());
            preparedViewCache.put(pipelineType, new CachedPreparedStageEntityView(logicTick, membershipVersion, emptyView));
            return emptyView;
        }

        List<StageEntityView.Entry> snapshots = graphicsPipeline.snapshotEntitiesIfPresent(entityIds);
        if (snapshots.isEmpty()) {
            PreparedStageEntityView emptyView = new PreparedStageEntityView(stageKeyId, pipelineType, List.of());
            preparedViewCache.put(pipelineType, new CachedPreparedStageEntityView(logicTick, membershipVersion, emptyView));
            return emptyView;
        }

        Map<KeyId, List<StageEntityView.Entry>> byContainer = new LinkedHashMap<>();
        for (StageEntityView.Entry entry : snapshots) {
            if (entry == null) {
                continue;
            }
            byContainer.computeIfAbsent(entry.containerType(), ignored -> new ArrayList<>()).add(entry);
        }
        if (byContainer.isEmpty()) {
            PreparedStageEntityView emptyView = new PreparedStageEntityView(stageKeyId, pipelineType, List.of());
            preparedViewCache.put(pipelineType, new CachedPreparedStageEntityView(logicTick, membershipVersion, emptyView));
            return emptyView;
        }

        List<Map.Entry<KeyId, List<StageEntityView.Entry>>> orderedContainers = new ArrayList<>(byContainer.entrySet());
        orderedContainers.sort(Comparator
                .comparingInt((Map.Entry<KeyId, List<StageEntityView.Entry>> container) ->
                        GraphicsContainerHints.orderOf(container.getKey()))
                .thenComparing(container -> container.getKey().toString()));

        List<PreparedStageEntityView.ContainerSlice> orderedSlices = new ArrayList<>(orderedContainers.size());
        for (Map.Entry<KeyId, List<StageEntityView.Entry>> containerEntries : orderedContainers) {
            List<StageEntityView.Entry> resolved = orderContainerEntries(containerEntries.getKey(), containerEntries.getValue());
            orderedSlices.add(new PreparedStageEntityView.ContainerSlice(containerEntries.getKey(), resolved));
        }

        PreparedStageEntityView view = new PreparedStageEntityView(stageKeyId, pipelineType, orderedSlices);
        preparedViewCache.put(pipelineType, new CachedPreparedStageEntityView(logicTick, membershipVersion, view));
        return view;
    }

    private void prepareSceneForPreparedView(PipelineType pipelineType, C context) {
        StageFlowScene<C> scene = flowScenes.get(pipelineType);
        if (scene == null) {
            return;
        }
        long membershipVersion = graphicsPipeline.stageMembershipIndex().version();
        int logicTick = graphicsPipeline.currentLogicTick();
        PreparedSceneState cached = preparedSceneCache.get(pipelineType);
        if (cached != null
                && cached.logicTick() == logicTick
                && cached.membershipVersion() == membershipVersion) {
            return;
        }
        StageEntityView view = preparePreparedStageEntityView(pipelineType).fullView();
        scene.prepareForFrame(graphicsPipeline.graphicsWorld(), view, context);
        preparedSceneCache.put(pipelineType, new PreparedSceneState(logicTick, membershipVersion));
    }

    private List<StageEntityView.Entry> orderContainerEntries(KeyId containerType, List<StageEntityView.Entry> entries) {
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }
        if (GraphicsContainerHints.PRIORITY.equals(containerType)) {
            return priorityIndexSystem.order(entries);
        }
        return queueIndexSystem.order(entries);
    }

    private record CachedStageEntityView(
            int logicTick,
            int renderTick,
            float partialTicks,
            int frustumIdentity,
            long membershipVersion,
            StageEntityView view
    ) {
    }

    private record CachedPreparedStageEntityView(
            int logicTick,
            long membershipVersion,
            PreparedStageEntityView view
    ) {
    }

    private record PreparedSceneState(
            int logicTick,
            long membershipVersion
    ) {
    }
}
