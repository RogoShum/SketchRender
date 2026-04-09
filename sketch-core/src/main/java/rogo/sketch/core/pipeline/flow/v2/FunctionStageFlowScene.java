package rogo.sketch.core.pipeline.flow.v2;

import rogo.sketch.core.api.graphics.AsyncTickable;
import rogo.sketch.core.api.graphics.FunctionalGraphics;
import rogo.sketch.core.api.graphics.Graphics;
import rogo.sketch.core.api.graphics.Tickable;
import rogo.sketch.core.instance.function.FunctionCommandCompilerRegistry;
import rogo.sketch.core.instance.StandardFunctionGraphics;
import rogo.sketch.core.packet.PipelineStateKey;
import rogo.sketch.core.packet.RenderPacket;
import rogo.sketch.core.pipeline.PipelineType;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.container.GraphicsContainer;
import rogo.sketch.core.pipeline.flow.RenderFlowType;
import rogo.sketch.core.pipeline.flow.RenderPostProcessors;
import rogo.sketch.core.pipeline.flow.container.DefaultBatchContainers;
import rogo.sketch.core.pipeline.module.diagnostic.SketchDiagnostics;
import rogo.sketch.core.util.KeyId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public final class FunctionStageFlowScene<C extends RenderContext> implements StageFlowScene<C> {
    private static final String DIAGNOSTIC_MODULE = "function-stage-flow";

    private final PipelineType pipelineType;
    private final FunctionInstanceStore instanceStore = new FunctionInstanceStore();
    private final FunctionExecutionOrderIndex executionOrderIndex = new FunctionExecutionOrderIndex();
    private final FunctionCommandCompilerRegistry compilerRegistry;
    private long orderCounter = 0L;

    public FunctionStageFlowScene(PipelineType pipelineType) {
        this(pipelineType, FunctionCommandCompilerRegistry.standard());
    }

    public FunctionStageFlowScene(PipelineType pipelineType, FunctionCommandCompilerRegistry compilerRegistry) {
        this.pipelineType = pipelineType;
        this.compilerRegistry = compilerRegistry;
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
        if (!(graphics instanceof FunctionalGraphics functionalGraphics)) {
            throw new IllegalArgumentException("Function stage requires FunctionalGraphics, got: "
                    + (graphics != null ? graphics.getClass().getName() : "null"));
        }

        instanceStore.remove(functionalGraphics);
        instanceStore.register(
                functionalGraphics,
                renderParameter,
                resolveContainerType(containerId),
                orderCounter++);
    }

    @Override
    public void tick(C context) {
        for (FunctionInstanceStore.Entry entry : instanceStore.records()) {
            if (entry.graphics() instanceof Tickable tickable) {
                tickable.tick();
            }
        }
    }

    @Override
    public void asyncTick(C context) {
        for (FunctionInstanceStore.Entry entry : instanceStore.records()) {
            if (entry.graphics() instanceof AsyncTickable asyncTickable) {
                asyncTickable.asyncTick();
            }
        }
    }

    @Override
    public void swapData() {
        for (FunctionInstanceStore.Entry entry : instanceStore.records()) {
            if (entry.graphics() instanceof AsyncTickable asyncTickable) {
                asyncTickable.swapData();
            }
        }
    }

    @Override
    public void prepareForFrame() {
        for (FunctionInstanceStore.Entry entry : instanceStore.records()) {
            if (!entry.graphics().shouldDiscard()) {
                entry.refreshCompiledRenderSetting();
            }
        }
    }

    @Override
    public void cleanupDiscardedInstances() {
        List<FunctionalGraphics> toRemove = new ArrayList<>();
        for (FunctionInstanceStore.Entry entry : instanceStore.records()) {
            if (entry.graphics().shouldDiscard()) {
                toRemove.add(entry.graphics());
            }
        }
        for (FunctionalGraphics graphics : toRemove) {
            instanceStore.remove(graphics);
        }
    }

    @Override
    public Map<PipelineStateKey, List<RenderPacket>> createRenderPackets(
            KeyId stageId,
            RenderFlowType flowType,
            RenderPostProcessors postProcessors,
            C context) {
        List<FunctionInstanceStore.Entry> activeEntries = new ArrayList<>();
        for (FunctionInstanceStore.Entry entry : instanceStore.records()) {
            if (!entry.graphics().shouldDiscard() && entry.graphics().shouldRender()) {
                activeEntries.add(entry);
            }
        }
        if (activeEntries.isEmpty()) {
            return Collections.emptyMap();
        }

        List<FunctionCommandSlice> slices = buildSlices(executionOrderIndex.order(activeEntries));
        if (slices.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<PipelineStateKey, List<RenderPacket>> packets = new LinkedHashMap<>();
        for (FunctionCommandSlice slice : slices) {
            PipelineStateKey stateKey = slice.compiledRenderSetting().pipelineStateKey();
            List<RenderPacket> statePackets = packets.computeIfAbsent(stateKey, ignored -> new ArrayList<>());
            for (FunctionCommandSlice.Invocation invocation : slice.invocations()) {
                RenderPacket packet = convertCommand(
                        stageId,
                        slice.compiledRenderSetting(),
                        invocation.entry().graphics(),
                        invocation.command());
                if (packet != null) {
                    statePackets.add(packet);
                }
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
        if (graphics instanceof FunctionalGraphics functionalGraphics) {
            instanceStore.remove(functionalGraphics);
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

    private List<FunctionCommandSlice> buildSlices(List<FunctionInstanceStore.Entry> orderedEntries) {
        Map<PipelineStateKey, List<FunctionCommandSlice.Invocation>> groupedInvocations = new LinkedHashMap<>();
        Map<PipelineStateKey, rogo.sketch.core.pipeline.CompiledRenderSetting> settingsByState = new LinkedHashMap<>();

        for (FunctionInstanceStore.Entry entry : orderedEntries) {
            if (!(entry.graphics() instanceof StandardFunctionGraphics standardFunctionGraphics)) {
                SketchDiagnostics.get().warn(
                        DIAGNOSTIC_MODULE,
                        "Skipping non-standard function graphics in new function flow: "
                                + entry.graphics().getIdentifier());
                continue;
            }

            PipelineStateKey stateKey = entry.compiledRenderSetting().pipelineStateKey();
            List<FunctionCommandSlice.Invocation> invocations = groupedInvocations.computeIfAbsent(stateKey, ignored -> new ArrayList<>());
            settingsByState.putIfAbsent(stateKey, entry.compiledRenderSetting());
            for (StandardFunctionGraphics.Command command : standardFunctionGraphics.commands()) {
                invocations.add(new FunctionCommandSlice.Invocation(entry, command));
            }
        }

        List<FunctionCommandSlice> slices = new ArrayList<>(groupedInvocations.size());
        for (Map.Entry<PipelineStateKey, List<FunctionCommandSlice.Invocation>> grouped : groupedInvocations.entrySet()) {
            slices.add(new FunctionCommandSlice(settingsByState.get(grouped.getKey()), grouped.getValue()));
        }
        return slices;
    }

    private RenderPacket convertCommand(
            KeyId stageId,
            rogo.sketch.core.pipeline.CompiledRenderSetting compiledRenderSetting,
            FunctionalGraphics graphics,
            StandardFunctionGraphics.Command command) {
        if (compiledRenderSetting == null || command == null) {
            return null;
        }
        return compilerRegistry.compile(stageId, pipelineType, compiledRenderSetting, graphics, command);
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

