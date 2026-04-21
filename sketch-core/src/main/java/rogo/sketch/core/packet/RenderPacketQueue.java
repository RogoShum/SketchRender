package rogo.sketch.core.packet;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
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
import rogo.sketch.core.pipeline.kernel.PacketGroup;
import rogo.sketch.core.pipeline.kernel.PipelineExecutionSlice;
import rogo.sketch.core.pipeline.kernel.StageExecutionPlan;
import rogo.sketch.core.pipeline.module.diagnostic.RenderTraceRecorder;
import rogo.sketch.core.pipeline.submit.StageSubmitNode;
import rogo.sketch.core.pipeline.submit.StageWindow;
import rogo.sketch.core.util.KeyId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RenderPacketQueue<C extends RenderContext> {
    private static final KeyId DEFERRED_TRANSLUCENT_SCOPE = KeyId.of("sketch:deferred_translucent_scope");

    private final GraphicsPipeline<C> graphicsPipeline;
    private final Object2ObjectLinkedOpenHashMap<KeyId, StageExecutionPlan> stagePlans = new Object2ObjectLinkedOpenHashMap<>();
    private final Object2ObjectLinkedOpenHashMap<PipelineType, ObjectArrayList<PacketGroup>> deferredTranslucentPackets =
            new Object2ObjectLinkedOpenHashMap<>();

    public RenderPacketQueue(GraphicsPipeline<C> graphicsPipeline) {
        this.graphicsPipeline = graphicsPipeline;
    }

    public void installExecutionPlan(FrameExecutionPlan executionPlan) {
        if (executionPlan == null || executionPlan.isEmpty()) {
            clear();
            return;
        }

        Map<KeyId, StageExecutionPlan> nextStagePlans = executionPlan.stagePlans();
        List<KeyId> removedStages = new ArrayList<>();
        for (KeyId stageId : stagePlans.keySet()) {
            if (!nextStagePlans.containsKey(stageId)) {
                removedStages.add(stageId);
            }
        }
        for (KeyId removedStage : removedStages) {
            removeStagePlan(removedStage);
        }

        for (Map.Entry<KeyId, StageExecutionPlan> entry : nextStagePlans.entrySet()) {
            KeyId stageId = entry.getKey();
            StageExecutionPlan stagePlan = entry.getValue();
            if (stageId == null || stagePlan == null || stagePlan.isEmpty()) {
                removeStagePlan(stageId);
                continue;
            }
            StageExecutionPlan existingPlan = stagePlans.get(stageId);
            if (existingPlan != null
                    && stagePlan.planHash() == existingPlan.planHash()
                    && stagePlan.equals(existingPlan)) {
                continue;
            }
            removeStagePlan(stageId);
            installStagePlan(stageId, stagePlan);
        }
    }

    public void addPacket(PipelineType pipelineType, RenderPacket packet) {
        addPacketInternal(pipelineType, packet, true);
    }

    public void addPackets(PipelineType pipelineType, Map<ExecutionKey, List<RenderPacket>> packets) {
        for (Map.Entry<ExecutionKey, List<RenderPacket>> entry : packets.entrySet()) {
            for (RenderPacket packet : entry.getValue()) {
                addPacketInternal(pipelineType, packet, true);
            }
        }
    }

    public void executeStage(KeyId stageId, RenderStateManager manager, C context) {
        executeStageRange(List.of(stageId), manager, context, "single");
    }

    public void executeStageRange(List<KeyId> stageIds, RenderStateManager manager, C context) {
        executeStageRange(stageIds, manager, context, "range");
    }

    public void executeStageRange(List<KeyId> stageIds, RenderStateManager manager, C context, String scopeLabel) {
        List<KeyId> orderedStageIds = normalizeStageIds(stageIds);
        if (orderedStageIds.isEmpty()) {
            return;
        }

        SnapshotScope rangeSnapshotScope = snapshotScopeForStages(orderedStageIds);
        boolean hasPackets = hasPacketsInStages(orderedStageIds);
        traceRangeScopeBegin(scopeLabel, orderedStageIds, hasPackets, rangeSnapshotScope != null && !rangeSnapshotScope.isEmpty());

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
        } finally {
            traceRangeScopeEnd(scopeLabel, orderedStageIds);
        }
    }

    public void flushRemainingTranslucentPackets(RenderStateManager manager, C context) {
        if (deferredTranslucentPackets.isEmpty()) {
            return;
        }

        SnapshotScope snapshotScope = buildDeferredTranslucentSnapshotScope();
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

    public Map<ExecutionKey, List<RenderPacket>> packetsForStage(PipelineType pipelineType, KeyId stageId) {
        StageExecutionPlan stagePlan = stagePlans.get(stageId);
        if (stagePlan == null) {
            return Map.of();
        }
        PipelineExecutionSlice slice = stagePlan.pipelineSlice(pipelineType);
        return slice != null ? slice.packetMap() : Map.of();
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
        return Set.copyOf(stagePlans.keySet());
    }

    public void clear() {
        stagePlans.clear();
        deferredTranslucentPackets.clear();
    }

    private void removeStagePlan(KeyId stageId) {
        if (stageId == null) {
            return;
        }
        stagePlans.remove(stageId);
    }

    private void installStagePlan(KeyId stageId, StageExecutionPlan stagePlan) {
        if (stageId == null || stagePlan == null || stagePlan.isEmpty()) {
            return;
        }
        stagePlans.put(stageId, stagePlan);
        for (PipelineExecutionSlice pipelineSlice : stagePlan.pipelineSlices()) {
            if (pipelineSlice == null || pipelineSlice.isEmpty()) {
                continue;
            }
            for (int i = 0; i < pipelineSlice.groupCount(); i++) {
                traceQueued(stageId, pipelineSlice.groupAt(i).packetView());
            }
        }
    }

    public boolean isEmpty() {
        return stagePlans.isEmpty();
    }

    private void executeStageInternal(KeyId stageId, RenderStateManager manager, C context) {
        GraphicsStage stage = graphicsPipeline.getStage(stageId);

        EventBusBridge.post(new GraphicsPipelineStageEvent<>(graphicsPipeline, stageId, context, GraphicsPipelineStageEvent.Phase.PRE));
        context.preStage(stageId);

        try {
            for (StageWindow window : StageWindow.executionOrder()) {
                executeStageSubmitNodes(stageId, window, manager, context);
                GraphicsDriver.runtime().frameExecutor().executeStageWindow(
                        graphicsPipeline,
                        this,
                        stage,
                        stageId,
                        window,
                        manager,
                        context);
            }
        } finally {
            context.postStage(stageId);
            EventBusBridge.post(new GraphicsPipelineStageEvent<>(graphicsPipeline, stageId, context, GraphicsPipelineStageEvent.Phase.POST));
        }
    }

    private void executeStageSubmitNodes(KeyId stageId, StageWindow window, RenderStateManager manager, C context) {
        List<StageSubmitNode> submitNodes = graphicsPipeline.stageSubmitNodes(stageId, window);
        if (submitNodes.isEmpty()) {
            return;
        }
        for (StageSubmitNode submitNode : submitNodes) {
            submitNode.execute(graphicsPipeline, this, manager, context);
        }
    }

    public void executeStageDispatchWindow(KeyId stageId, RenderStateManager manager, C context) {
        for (PipelineType pipelineType : graphicsPipeline.getPipelineTypes()) {
            if (PipelineType.COMPUTE.equals(pipelineType) || PipelineType.FUNCTION.equals(pipelineType)) {
                executeStageForPipeline(pipelineType, stageId, manager, context);
            }
        }
    }

    public void executeStageDrawWindow(
            KeyId stageId,
            GraphicsStage stage,
            RenderStateManager manager,
            C context) {
        PipelineConfig config = graphicsPipeline.getConfig();
        PipelineConfig.TranslucencyStrategy strategy = config.getTranslucencyStrategy();

        switch (strategy) {
            case INTERLEAVED -> {
                executeStageForPipeline(PipelineType.RASTERIZATION, stageId, manager, context);
                executeStageForPipeline(PipelineType.TRANSLUCENT, stageId, manager, context);
            }
            case DEDICATED_STAGES -> {
                executeStageForPipeline(PipelineType.RASTERIZATION, stageId, manager, context);

                if (!hasStagePipelinePackets(stageId, PipelineType.TRANSLUCENT)) {
                    return;
                }

                accumulateTranslucentPackets(PipelineType.TRANSLUCENT, stageId);
                boolean isDedicated = stage != null && stage.isDedicatedTranslucentStage()
                        || config.isDedicatedTranslucentStage(stageId);
                if (isDedicated) {
                    flushDeferredTranslucentPackets(manager, context);
                }
            }
            case FLEXIBLE -> {
                executeStageForPipeline(PipelineType.RASTERIZATION, stageId, manager, context);
                if (shouldTranslucentFollowSolid(stage, stageId, config)) {
                    executeStageForPipeline(PipelineType.TRANSLUCENT, stageId, manager, context);
                } else {
                    accumulateTranslucentPackets(PipelineType.TRANSLUCENT, stageId);
                }
            }
        }
    }

    private void executeStageForPipeline(PipelineType pipelineType, KeyId stageId, RenderStateManager manager, C context) {
        StageExecutionPlan stagePlan = stagePlans.get(stageId);
        if (stagePlan == null) {
            return;
        }
        PipelineExecutionSlice executionSlice = stagePlan.pipelineSlice(pipelineType);
        if (executionSlice == null || executionSlice.isEmpty()) {
            return;
        }
        executePacketGroups(executionSlice, manager, context);
    }

    private void executePacketGroups(PipelineExecutionSlice executionSlice, RenderStateManager manager, C context) {
        for (int i = 0; i < executionSlice.groupCount(); i++) {
            PacketGroup group = executionSlice.groupAt(i);
            GraphicsDriver.runtime().frameExecutor().executePacketGroup(
                    graphicsPipeline,
                    group.stateKey(),
                    group.packetView(),
                    manager,
                    context);
        }
    }

    private void accumulateTranslucentPackets(PipelineType pipelineType, KeyId stageId) {
        StageExecutionPlan stagePlan = stagePlans.get(stageId);
        if (stagePlan == null) {
            return;
        }
        PipelineExecutionSlice executionSlice = stagePlan.pipelineSlice(pipelineType);
        if (executionSlice == null || executionSlice.isEmpty()) {
            return;
        }

        ObjectArrayList<PacketGroup> deferredGroups = deferredTranslucentPackets.computeIfAbsent(
                pipelineType,
                key -> new ObjectArrayList<>());
        for (int i = 0; i < executionSlice.groupCount(); i++) {
            deferredGroups.add(executionSlice.groupAt(i));
        }
    }

    private void flushDeferredTranslucentPackets(RenderStateManager manager, C context) {
        List<PipelineType> pipelineTypes = new ArrayList<>(deferredTranslucentPackets.keySet());
        pipelineTypes.sort(Comparator.comparingInt(PipelineType::getPriority));
        for (PipelineType pipelineType : pipelineTypes) {
            ObjectArrayList<PacketGroup> groups = deferredTranslucentPackets.get(pipelineType);
            if (groups == null || groups.isEmpty()) {
                continue;
            }
            for (int i = 0; i < groups.size(); i++) {
                PacketGroup group = groups.get(i);
                GraphicsDriver.runtime().frameExecutor().executePacketGroup(
                        graphicsPipeline,
                        group.stateKey(),
                        group.packetView(),
                        manager,
                        context);
            }
        }
        deferredTranslucentPackets.clear();
    }

    private SnapshotScope buildDeferredTranslucentSnapshotScope() {
        Map<PipelineType, Map<ExecutionKey, List<RenderPacket>>> deferredPackets = new java.util.LinkedHashMap<>();
        for (Map.Entry<PipelineType, ObjectArrayList<PacketGroup>> entry : deferredTranslucentPackets.entrySet()) {
            if (entry.getValue() == null || entry.getValue().isEmpty()) {
                continue;
            }
            Map<ExecutionKey, List<RenderPacket>> statePackets = new java.util.LinkedHashMap<>();
            for (int i = 0; i < entry.getValue().size(); i++) {
                PacketGroup group = entry.getValue().get(i);
                statePackets.put(group.stateKey(), group.packetView());
            }
            deferredPackets.put(entry.getKey(), Map.copyOf(statePackets));
        }
        return StageExecutionPlan.fromPackets(DEFERRED_TRANSLUCENT_SCOPE, deferredPackets).stageSnapshotScope();
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

        KeyId stageId = packet.stageId();

        if (updateStagePlan) {
            stagePlans.put(stageId, appendPacket(stagePlans.get(stageId), pipelineType, packet));
            traceQueued(stageId, List.of(packet));
        }
    }

    private StageExecutionPlan appendPacket(
            StageExecutionPlan existingPlan,
            PipelineType pipelineType,
            RenderPacket packet) {
        Map<PipelineType, Map<ExecutionKey, List<RenderPacket>>> stagePackets =
                existingPlan != null ? new java.util.LinkedHashMap<>(existingPlan.packets()) : new java.util.LinkedHashMap<>();
        Map<ExecutionKey, List<RenderPacket>> statePackets =
                new java.util.LinkedHashMap<>(stagePackets.getOrDefault(pipelineType, Map.of()));
        List<RenderPacket> packets = new ArrayList<>(statePackets.getOrDefault(packet.stateKey(), List.of()));
        packets.add(packet);
        statePackets.put(packet.stateKey(), List.copyOf(packets));
        stagePackets.put(pipelineType, Map.copyOf(statePackets));
        return StageExecutionPlan.fromPackets(packet.stageId(), stagePackets);
    }

    private boolean hasPacketsInStages(List<KeyId> stageIds) {
        for (KeyId stageId : stageIds) {
            if (stagePlans.containsKey(stageId)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasStagePipelinePackets(KeyId stageId, PipelineType pipelineType) {
        StageExecutionPlan stagePlan = stagePlans.get(stageId);
        if (stagePlan == null) {
            return false;
        }
        PipelineExecutionSlice executionSlice = stagePlan.pipelineSlice(pipelineType);
        return executionSlice != null && !executionSlice.isEmpty();
    }

    private void traceQueued(KeyId stageId, List<RenderPacket> packets) {
        RenderTraceRecorder renderTraceRecorder = graphicsPipeline.renderTraceRecorder();
        if (renderTraceRecorder == null || packets == null || packets.isEmpty()) {
            return;
        }
        for (RenderPacket packet : packets) {
            if (packet == null || packet.completionSubjects() == null) {
                continue;
            }
            for (var subject : packet.completionSubjects()) {
                if (subject != null) {
                    renderTraceRecorder.recordQueueInstalled(stageId, subject);
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

    private void traceRangeScopeBegin(String scopeLabel, List<KeyId> stageIds, boolean hasPackets, boolean hasSnapshotScope) {
        RenderTraceRecorder renderTraceRecorder = graphicsPipeline.renderTraceRecorder();
        if (renderTraceRecorder != null) {
            renderTraceRecorder.recordRangeScopeBegin(scopeLabel, stageIds, hasPackets, hasSnapshotScope);
        }
    }

    private void traceRangeScopeEnd(String scopeLabel, List<KeyId> stageIds) {
        RenderTraceRecorder renderTraceRecorder = graphicsPipeline.renderTraceRecorder();
        if (renderTraceRecorder != null) {
            renderTraceRecorder.recordRangeScopeEnd(scopeLabel, stageIds);
        }
    }
}

