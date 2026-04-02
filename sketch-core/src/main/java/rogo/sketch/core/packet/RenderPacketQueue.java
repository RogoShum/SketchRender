package rogo.sketch.core.packet;

import rogo.sketch.core.backend.BackendStageScope;
import rogo.sketch.core.driver.GraphicsDriver;
import rogo.sketch.core.driver.state.snapshot.SnapshotScope;
import rogo.sketch.core.event.GraphicsPipelineStageEvent;
import rogo.sketch.core.event.bridge.EventBusBridge;
import rogo.sketch.core.pipeline.GraphicsPipeline;
import rogo.sketch.core.pipeline.GraphicsStage;
import rogo.sketch.core.pipeline.PipelineConfig;
import rogo.sketch.core.pipeline.PipelineType;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.RenderStateManager;
import rogo.sketch.core.pipeline.kernel.FrameExecutionPlan;
import rogo.sketch.core.pipeline.kernel.StageExecutionPlan;
import rogo.sketch.core.pipeline.module.diagnostic.RenderTraceRecorder;
import rogo.sketch.core.util.KeyId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RenderPacketQueue<C extends RenderContext> {
    private static final KeyId DEFERRED_TRANSLUCENT_SCOPE = KeyId.of("sketch:deferred_translucent_scope");

    private final GraphicsPipeline<C> graphicsPipeline;
    private final Map<PipelineType, Map<PipelineStateKey, List<RenderPacket>>> packetsByPipeline;
    private final Map<PipelineType, Map<KeyId, Map<PipelineStateKey, List<RenderPacket>>>> packetsByPipelineAndStage;
    private final Map<KeyId, StageExecutionPlan> stagePlans;
    private final Set<KeyId> stagedPackets = new LinkedHashSet<>();
    private final Map<PipelineType, Map<PipelineStateKey, List<RenderPacket>>> deferredTranslucentPackets = new LinkedHashMap<>();

    public RenderPacketQueue(GraphicsPipeline<C> graphicsPipeline) {
        this.graphicsPipeline = graphicsPipeline;
        this.packetsByPipeline = new LinkedHashMap<>();
        this.packetsByPipelineAndStage = new LinkedHashMap<>();
        this.stagePlans = new LinkedHashMap<>();
        for (PipelineType type : graphicsPipeline.getPipelineTypes()) {
            initializePipeline(type);
        }
    }

    private void initializePipeline(PipelineType pipelineType) {
        packetsByPipeline.put(pipelineType, new LinkedHashMap<>());
        packetsByPipelineAndStage.put(pipelineType, new LinkedHashMap<>());
    }

    public void installExecutionPlan(FrameExecutionPlan executionPlan) {
        clear();
        if (executionPlan == null || executionPlan.isEmpty()) {
            return;
        }

        for (Map.Entry<KeyId, StageExecutionPlan> entry : executionPlan.stagePlans().entrySet()) {
            KeyId stageId = entry.getKey();
            StageExecutionPlan stagePlan = entry.getValue();
            if (stageId == null || stagePlan == null || stagePlan.isEmpty()) {
                continue;
            }

            stagePlans.put(stageId, stagePlan);
            stagedPackets.add(stageId);
            for (Map.Entry<PipelineType, Map<PipelineStateKey, List<RenderPacket>>> pipelineEntry : stagePlan.packets().entrySet()) {
                PipelineType pipelineType = pipelineEntry.getKey();
                for (Map.Entry<PipelineStateKey, List<RenderPacket>> stateEntry : pipelineEntry.getValue().entrySet()) {
                    traceQueued(stageId, stateEntry.getValue());
                    for (RenderPacket packet : stateEntry.getValue()) {
                        addPacketInternal(pipelineType, packet, false);
                    }
                }
            }
        }
    }

    public void addPacket(PipelineType pipelineType, RenderPacket packet) {
        addPacketInternal(pipelineType, packet, true);
    }

    public void addPackets(PipelineType pipelineType, Map<PipelineStateKey, List<RenderPacket>> packets) {
        for (Map.Entry<PipelineStateKey, List<RenderPacket>> entry : packets.entrySet()) {
            for (RenderPacket packet : entry.getValue()) {
                addPacketInternal(pipelineType, packet, true);
            }
        }
    }

    public void executeStage(KeyId stageId, RenderStateManager manager, C context) {
        executeStageRange(List.of(stageId), manager, context);
    }

    public void executeStageRange(List<KeyId> stageIds, RenderStateManager manager, C context) {
        List<KeyId> orderedStageIds = normalizeStageIds(stageIds);
        if (orderedStageIds.isEmpty()) {
            return;
        }

        SnapshotScope rangeSnapshotScope = snapshotScopeForStages(orderedStageIds);
        boolean hasPackets = hasPacketsInStages(orderedStageIds);

        try (BackendStageScope ignored = hasPackets
                ? GraphicsDriver.runtime().frameExecutor().beginExecutionScope(
                graphicsPipeline,
                this,
                orderedStageIds,
                rangeSnapshotScope,
                context)
                : BackendStageScope.NO_OP) {
            manager.reset();
            for (KeyId stageId : orderedStageIds) {
                executeStageInternal(stageId, manager, context);
            }
        }
    }

    public void flushRemainingTranslucentPackets(RenderStateManager manager, C context) {
        if (deferredTranslucentPackets.isEmpty()) {
            return;
        }

        SnapshotScope snapshotScope = StageExecutionPlan
                .fromPackets(DEFERRED_TRANSLUCENT_SCOPE, deferredTranslucentPackets)
                .stageSnapshotScope();
        try (BackendStageScope ignored = GraphicsDriver.runtime().frameExecutor().beginExecutionScope(
                graphicsPipeline,
                this,
                List.of(),
                snapshotScope,
                context)) {
            manager.reset();
            flushDeferredTranslucentPackets(manager, context);
        }
    }

    public void executeImmediate(RenderPacket packet, RenderStateManager manager, C context) {
        GraphicsDriver.runtime().frameExecutor().executeImmediate(graphicsPipeline, packet, manager, context);
    }

    public Map<PipelineStateKey, List<RenderPacket>> packetsForStage(PipelineType pipelineType, KeyId stageId) {
        Map<KeyId, Map<PipelineStateKey, List<RenderPacket>>> stageMap = packetsByPipelineAndStage.get(pipelineType);
        if (stageMap == null) {
            return Map.of();
        }
        return stageMap.getOrDefault(stageId, Map.of());
    }

    public StageExecutionPlan stagePlan(KeyId stageId) {
        return stagePlans.get(stageId);
    }

    public SnapshotScope snapshotScopeForStages(List<KeyId> stageIds) {
        List<SnapshotScope> scopes = new ArrayList<>();
        for (KeyId stageId : normalizeStageIds(stageIds)) {
            StageExecutionPlan stagePlan = stagePlans.get(stageId);
            if (stagePlan != null && stagePlan.stageSnapshotScope() != null && !stagePlan.stageSnapshotScope().isEmpty()) {
                scopes.add(stagePlan.stageSnapshotScope());
            }
        }
        if (scopes.isEmpty()) {
            return SnapshotScope.empty();
        }
        return SnapshotScope.combine(scopes.toArray(new SnapshotScope[0]));
    }

    public Set<KeyId> stagedPacketIds() {
        return Set.copyOf(stagedPackets);
    }

    public void clear() {
        for (Map<PipelineStateKey, List<RenderPacket>> packets : packetsByPipeline.values()) {
            packets.clear();
        }
        for (Map<KeyId, Map<PipelineStateKey, List<RenderPacket>>> stageMap : packetsByPipelineAndStage.values()) {
            stageMap.clear();
        }
        stagePlans.clear();
        stagedPackets.clear();
        deferredTranslucentPackets.clear();
    }

    public boolean isEmpty() {
        return stagedPackets.isEmpty();
    }

    private void executeStageInternal(KeyId stageId, RenderStateManager manager, C context) {
        PipelineConfig config = graphicsPipeline.getConfig();
        PipelineConfig.TranslucencyStrategy strategy = config.getTranslucencyStrategy();
        GraphicsStage stage = graphicsPipeline.getStage(stageId);
        List<PipelineType> pipelineTypes = graphicsPipeline.getPipelineTypes();

        EventBusBridge.post(new GraphicsPipelineStageEvent<>(graphicsPipeline, stageId, context, GraphicsPipelineStageEvent.Phase.PRE));
        context.preStage(stageId);

        try {
            switch (strategy) {
                case INTERLEAVED -> {
                    for (PipelineType pipelineType : pipelineTypes) {
                        executeStageForPipeline(pipelineType, stageId, manager, context);
                    }
                }
                case DEDICATED_STAGES -> {
                    for (PipelineType pipelineType : pipelineTypes) {
                        if (PipelineType.TRANSLUCENT.equals(pipelineType)) {
                            accumulateTranslucentPackets(pipelineType, stageId);
                        } else {
                            executeStageForPipeline(pipelineType, stageId, manager, context);
                        }
                    }

                    boolean isDedicated = stage != null && stage.isDedicatedTranslucentStage()
                            || config.isDedicatedTranslucentStage(stageId);
                    if (isDedicated) {
                        flushDeferredTranslucentPackets(manager, context);
                    }
                }
                case FLEXIBLE -> {
                    for (PipelineType pipelineType : pipelineTypes) {
                        if (!PipelineType.TRANSLUCENT.equals(pipelineType)) {
                            executeStageForPipeline(pipelineType, stageId, manager, context);
                        }
                    }

                    if (shouldTranslucentFollowSolid(stage, stageId, config)) {
                        executeStageForPipeline(PipelineType.TRANSLUCENT, stageId, manager, context);
                    } else {
                        accumulateTranslucentPackets(PipelineType.TRANSLUCENT, stageId);
                    }
                }
            }
        } finally {
            context.postStage(stageId);
            EventBusBridge.post(new GraphicsPipelineStageEvent<>(graphicsPipeline, stageId, context, GraphicsPipelineStageEvent.Phase.POST));
        }
    }

    private void executeStageForPipeline(PipelineType pipelineType, KeyId stageId, RenderStateManager manager, C context) {
        Map<KeyId, Map<PipelineStateKey, List<RenderPacket>>> stageMap = packetsByPipelineAndStage.get(pipelineType);
        if (stageMap == null) {
            return;
        }

        Map<PipelineStateKey, List<RenderPacket>> stagePackets = stageMap.get(stageId);
        if (stagePackets == null || stagePackets.isEmpty()) {
            return;
        }

        executePacketMap(stagePackets, manager, context);
    }

    private void executePacketMap(Map<PipelineStateKey, List<RenderPacket>> packetMap, RenderStateManager manager, C context) {
        for (Map.Entry<PipelineStateKey, List<RenderPacket>> entry : packetMap.entrySet()) {
            GraphicsDriver.runtime().frameExecutor().executePacketGroup(
                    graphicsPipeline,
                    entry.getKey(),
                    entry.getValue(),
                    manager,
                    context);
        }
    }

    private void accumulateTranslucentPackets(PipelineType pipelineType, KeyId stageId) {
        Map<KeyId, Map<PipelineStateKey, List<RenderPacket>>> stageMap = packetsByPipelineAndStage.get(pipelineType);
        if (stageMap == null) {
            return;
        }

        Map<PipelineStateKey, List<RenderPacket>> stagePackets = stageMap.get(stageId);
        if (stagePackets == null || stagePackets.isEmpty()) {
            return;
        }

        Map<PipelineStateKey, List<RenderPacket>> deferred = deferredTranslucentPackets.computeIfAbsent(
                pipelineType,
                key -> new LinkedHashMap<>());
        for (Map.Entry<PipelineStateKey, List<RenderPacket>> entry : stagePackets.entrySet()) {
            deferred.computeIfAbsent(entry.getKey(), key -> new ArrayList<>()).addAll(entry.getValue());
        }
    }

    private void flushDeferredTranslucentPackets(RenderStateManager manager, C context) {
        List<PipelineType> pipelineTypes = new ArrayList<>(deferredTranslucentPackets.keySet());
        pipelineTypes.sort(Comparator.comparingInt(PipelineType::getPriority));
        for (PipelineType pipelineType : pipelineTypes) {
            Map<PipelineStateKey, List<RenderPacket>> packets = deferredTranslucentPackets.get(pipelineType);
            if (packets != null && !packets.isEmpty()) {
                executePacketMap(packets, manager, context);
            }
        }
        deferredTranslucentPackets.clear();
    }

    private boolean shouldTranslucentFollowSolid(GraphicsStage stage, KeyId stageId, PipelineConfig config) {
        if (stage != null && stage.getTranslucentFollowsSolid() != null) {
            return stage.getTranslucentFollowsSolid();
        }
        Boolean configSetting = config.getStageTranslucentFollowsSolid(stageId);
        if (configSetting != null) {
            return configSetting;
        }
        return true;
    }

    private void addPacketInternal(PipelineType pipelineType, RenderPacket packet, boolean updateStagePlan) {
        if (pipelineType == null || packet == null || packet.stateKey() == null || packet.stageId() == null) {
            return;
        }

        PipelineStateKey stateKey = packet.stateKey();
        KeyId stageId = packet.stageId();
        stagedPackets.add(stageId);

        Map<PipelineStateKey, List<RenderPacket>> pipelinePackets = packetsByPipeline.computeIfAbsent(
                pipelineType,
                pt -> {
                    initializePipeline(pt);
                    return packetsByPipeline.get(pt);
                });
        pipelinePackets.computeIfAbsent(stateKey, key -> new ArrayList<>()).add(packet);

        Map<KeyId, Map<PipelineStateKey, List<RenderPacket>>> stageMap = packetsByPipelineAndStage.computeIfAbsent(
                pipelineType,
                pt -> {
                    initializePipeline(pt);
                    return packetsByPipelineAndStage.get(pt);
                });
        stageMap.computeIfAbsent(stageId, key -> new LinkedHashMap<>())
                .computeIfAbsent(stateKey, key -> new ArrayList<>())
                .add(packet);

        if (updateStagePlan) {
            stagePlans.put(stageId, StageExecutionPlan.fromPackets(stageId, copyStagePackets(stageId)));
            traceQueued(stageId, List.of(packet));
        }
    }

    private Map<PipelineType, Map<PipelineStateKey, List<RenderPacket>>> copyStagePackets(KeyId stageId) {
        Map<PipelineType, Map<PipelineStateKey, List<RenderPacket>>> copied = new LinkedHashMap<>();
        for (Map.Entry<PipelineType, Map<KeyId, Map<PipelineStateKey, List<RenderPacket>>>> pipelineEntry : packetsByPipelineAndStage.entrySet()) {
            Map<PipelineStateKey, List<RenderPacket>> states = pipelineEntry.getValue().get(stageId);
            if (states == null || states.isEmpty()) {
                continue;
            }
            Map<PipelineStateKey, List<RenderPacket>> stateCopy = new LinkedHashMap<>();
            for (Map.Entry<PipelineStateKey, List<RenderPacket>> stateEntry : states.entrySet()) {
                stateCopy.put(stateEntry.getKey(), List.copyOf(stateEntry.getValue()));
            }
            copied.put(pipelineEntry.getKey(), Collections.unmodifiableMap(stateCopy));
        }
        return copied;
    }

    private boolean hasPacketsInStages(List<KeyId> stageIds) {
        for (KeyId stageId : stageIds) {
            if (stagedPackets.contains(stageId)) {
                return true;
            }
        }
        return false;
    }

    private void traceQueued(KeyId stageId, List<RenderPacket> packets) {
        RenderTraceRecorder renderTraceRecorder = graphicsPipeline.renderTraceRecorder();
        if (renderTraceRecorder == null || packets == null || packets.isEmpty()) {
            return;
        }
        for (RenderPacket packet : packets) {
            if (packet == null || packet.completionGraphics() == null) {
                continue;
            }
            for (var graphics : packet.completionGraphics()) {
                if (graphics != null) {
                    renderTraceRecorder.recordQueueInstalled(stageId, graphics);
                }
            }
        }
    }

    private List<KeyId> normalizeStageIds(List<KeyId> stageIds) {
        LinkedHashSet<KeyId> ordered = new LinkedHashSet<>();
        if (stageIds != null) {
            for (KeyId stageId : stageIds) {
                if (stageId != null) {
                    ordered.add(stageId);
                }
            }
        }
        return List.copyOf(ordered);
    }
}
