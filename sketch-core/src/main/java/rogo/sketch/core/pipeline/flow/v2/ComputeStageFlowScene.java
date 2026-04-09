package rogo.sketch.core.pipeline.flow.v2;

import rogo.sketch.core.api.graphics.AsyncTickable;
import rogo.sketch.core.api.graphics.DispatchableGraphics;
import rogo.sketch.core.api.graphics.Graphics;
import rogo.sketch.core.api.graphics.Tickable;
import rogo.sketch.core.packet.DispatchPacket;
import rogo.sketch.core.packet.PipelineStateKey;
import rogo.sketch.core.packet.RenderPacket;
import rogo.sketch.core.pipeline.PipelineType;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.container.GraphicsContainer;
import rogo.sketch.core.pipeline.flow.RenderFlowType;
import rogo.sketch.core.pipeline.flow.RenderPostProcessors;
import rogo.sketch.core.pipeline.information.ComputeInstanceInfo;
import rogo.sketch.core.pipeline.flow.container.DefaultBatchContainers;
import rogo.sketch.core.util.KeyId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public final class ComputeStageFlowScene<C extends RenderContext> implements StageFlowScene<C> {
    private final PipelineType pipelineType;
    private final ComputeInstanceStore instanceStore = new ComputeInstanceStore();
    private final ComputeExecutionOrderIndex executionOrderIndex = new ComputeExecutionOrderIndex();
    private final ComputeDispatchGroupIndex dispatchGroupIndex = new ComputeDispatchGroupIndex();
    private long orderCounter = 0L;

    public ComputeStageFlowScene(PipelineType pipelineType) {
        this.pipelineType = pipelineType;
    }

    @Override
    public PipelineType pipelineType() {
        return pipelineType;
    }

    @Override
    public void registerGraphicsInstance(
            Graphics graphics,
            rogo.sketch.core.pipeline.parmeter.RenderParameter renderParameter,
            KeyId containerId,
            Supplier<? extends GraphicsContainer<? extends RenderContext>> supplier) {
        if (!(graphics instanceof DispatchableGraphics dispatchableGraphics)) {
            throw new IllegalArgumentException("Compute stage requires DispatchableGraphics, got: "
                    + (graphics != null ? graphics.getClass().getName() : "null"));
        }

        instanceStore.remove(dispatchableGraphics);
        instanceStore.register(
                dispatchableGraphics,
                renderParameter,
                resolveContainerType(containerId),
                orderCounter++);
    }

    @Override
    public void tick(C context) {
        for (ComputeInstanceStore.Entry entry : instanceStore.records()) {
            if (entry.graphics() instanceof Tickable tickable) {
                tickable.tick();
            }
        }
    }

    @Override
    public void asyncTick(C context) {
        for (ComputeInstanceStore.Entry entry : instanceStore.records()) {
            if (entry.graphics() instanceof AsyncTickable asyncTickable) {
                asyncTickable.asyncTick();
            }
        }
    }

    @Override
    public void swapData() {
        for (ComputeInstanceStore.Entry entry : instanceStore.records()) {
            if (entry.graphics() instanceof AsyncTickable asyncTickable) {
                asyncTickable.swapData();
            }
        }
    }

    @Override
    public void prepareForFrame() {
        for (ComputeInstanceStore.Entry entry : instanceStore.records()) {
            if (!entry.graphics().shouldDiscard()) {
                entry.refreshCompiledRenderSetting(false);
            }
        }
    }

    @Override
    public void cleanupDiscardedInstances() {
        List<DispatchableGraphics> toRemove = new ArrayList<>();
        for (ComputeInstanceStore.Entry entry : instanceStore.records()) {
            if (entry.graphics().shouldDiscard()) {
                toRemove.add(entry.graphics());
            }
        }
        for (DispatchableGraphics graphics : toRemove) {
            instanceStore.remove(graphics);
        }
    }

    @Override
    public Map<PipelineStateKey, List<RenderPacket>> createRenderPackets(
            KeyId stageId,
            RenderFlowType flowType,
            RenderPostProcessors postProcessors,
            C context) {
        List<ComputeInstanceStore.Entry> visibleEntries = new ArrayList<>();
        for (ComputeInstanceStore.Entry entry : instanceStore.records()) {
            if (!entry.graphics().shouldDiscard() && entry.graphics().shouldRender()) {
                visibleEntries.add(entry);
            }
        }
        if (visibleEntries.isEmpty()) {
            return Collections.emptyMap();
        }

        List<ComputeDispatchSlice> slices = dispatchGroupIndex.build(executionOrderIndex.order(visibleEntries));
        if (slices.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<PipelineStateKey, List<RenderPacket>> packets = new LinkedHashMap<>();
        for (ComputeDispatchSlice slice : slices) {
            PipelineStateKey stateKey = slice.compiledRenderSetting().pipelineStateKey();
            List<RenderPacket> statePackets = packets.computeIfAbsent(stateKey, ignored -> new ArrayList<>());
            for (ComputeInstanceStore.Entry entry : slice.entries()) {
                ComputeInstanceInfo computeInfo = new ComputeInstanceInfo(
                        entry.graphics(),
                        entry.compiledRenderSetting() != null ? entry.compiledRenderSetting().renderSetting() : null,
                        entry.dispatchCommand());
                statePackets.add(new DispatchPacket(
                        stageId,
                        pipelineType,
                        stateKey,
                        slice.compiledRenderSetting().resourceBindingPlan(),
                        slice.uniformSnapshot(),
                        List.of(entry.graphics()),
                        1,
                        1,
                        1,
                        computeInfo,
                        entry.dispatchCommand()));
            }
        }
        return packets;
    }

    @Override
    public void clear() {
        instanceStore.clear();
    }

    @Override
    public void removeGraphicsInstance(Graphics graphics) {
        if (graphics instanceof DispatchableGraphics dispatchableGraphics) {
            instanceStore.remove(dispatchableGraphics);
        }
    }

    @Override
    public int instanceCount() {
        return instanceStore.size();
    }

    @Override
    public boolean hasInstances() {
        return !instanceStore.isEmpty();
    }

    private KeyId resolveContainerType(KeyId requested) {
        if (requested == null) {
            return DefaultBatchContainers.PRIORITY;
        }
        if (DefaultBatchContainers.QUEUE.equals(requested) || DefaultBatchContainers.PRIORITY.equals(requested)) {
            return requested;
        }
        return DefaultBatchContainers.PRIORITY;
    }
}

