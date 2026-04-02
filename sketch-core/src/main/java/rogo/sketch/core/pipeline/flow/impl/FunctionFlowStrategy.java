package rogo.sketch.core.pipeline.flow.impl;

import rogo.sketch.core.api.graphics.FunctionalGraphics;
import rogo.sketch.core.command.FunctionCommand;
import rogo.sketch.core.command.RenderCommand;
import rogo.sketch.core.instance.StandardFunctionGraphics;
import rogo.sketch.core.packet.BindRenderTargetPacket;
import rogo.sketch.core.packet.ClearPacket;
import rogo.sketch.core.packet.DrawBuffersPacket;
import rogo.sketch.core.packet.GenerateMipmapPacket;
import rogo.sketch.core.packet.PacketBuildContext;
import rogo.sketch.core.packet.PipelineStateKey;
import rogo.sketch.core.packet.RenderPacket;
import rogo.sketch.core.packet.ResourceBindingPlan;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.RenderSetting;
import rogo.sketch.core.pipeline.flow.BatchContainer;
import rogo.sketch.core.pipeline.flow.RenderBatch;
import rogo.sketch.core.pipeline.flow.RenderFlowStrategy;
import rogo.sketch.core.pipeline.flow.RenderFlowType;
import rogo.sketch.core.pipeline.flow.RenderPostProcessors;
import rogo.sketch.core.pipeline.information.FunctionInstanceInfo;
import rogo.sketch.core.shader.uniform.UniformValueSnapshot;
import rogo.sketch.core.util.KeyId;

import java.util.*;

/**
 * Flow strategy for function-type graphics operations.
 * <p>
 * Function graphics execute custom logic without requiring mesh data
 * or compute shader dispatch. They are simply invoked in order.
 * </p>
 */
public class FunctionFlowStrategy implements RenderFlowStrategy<FunctionalGraphics, FunctionInstanceInfo> {

    @Override
    public RenderFlowType getFlowType() {
        return RenderFlowType.FUNCTION;
    }
    
    @Override
    public Class<FunctionalGraphics> getGraphicsType() {
        return FunctionalGraphics.class;
    }
    
    @Override
    public Class<FunctionInstanceInfo> getInfoType() {
        return FunctionInstanceInfo.class;
    }

    @Override
    public Map<PipelineStateKey, List<RenderPacket>> buildPackets(
            BatchContainer<FunctionalGraphics, FunctionInstanceInfo> batchContainer,
            KeyId stageId,
            PacketBuildContext flowContext,
            RenderPostProcessors postProcessors,
            RenderContext context) {

        Collection<RenderBatch<FunctionInstanceInfo>> activeBatches = batchContainer.getActiveBatches();
        if (activeBatches.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<PipelineStateKey, List<RenderPacket>> packets = new LinkedHashMap<>();
        for (RenderBatch<FunctionInstanceInfo> batch : activeBatches) {
            List<FunctionInstanceInfo> visibleInfos = batch.getVisibleInstances();
            if (visibleInfos.isEmpty()) {
                continue;
            }

            RenderSetting setting = batch.getRenderSetting();
            PipelineStateKey stateKey = PipelineStateKey.from(setting);
            ResourceBindingPlan bindingPlan = ResourceBindingPlan.from(setting.resourceBinding());
            List<RenderPacket> statePackets = packets.computeIfAbsent(stateKey, key -> new ArrayList<>());

            for (FunctionInstanceInfo info : visibleInfos) {
                if (!(info.functionGraphics() instanceof StandardFunctionGraphics functionGraphics)) {
                    continue;
                }
                for (StandardFunctionGraphics.Command command : functionGraphics.commands()) {
                    RenderPacket packet = convertCommand(
                            flowContext,
                            stageId,
                            stateKey,
                            bindingPlan,
                            info.functionGraphics(),
                            command);
                    if (packet != null) {
                        statePackets.add(packet);
                    }
                }
            }
        }

        return packets;
    }

    @Deprecated
    @Override
    public Map<RenderSetting, List<RenderCommand>> createRenderCommands(BatchContainer<FunctionalGraphics, FunctionInstanceInfo> batchContainer, KeyId stageId, rogo.sketch.core.pipeline.flow.RenderFlowContext flowContext, RenderPostProcessors postProcessors, RenderContext context) {
        // Get active batches from container
        Collection<RenderBatch<FunctionInstanceInfo>> activeBatches = batchContainer.getActiveBatches();
        if (activeBatches.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<RenderSetting, List<FunctionalGraphics>> groupedFunctions = new LinkedHashMap<>();

        for (RenderBatch<FunctionInstanceInfo> batch : activeBatches) {
            List<FunctionInstanceInfo> visibleInfos = batch.getVisibleInstances();
            if (visibleInfos.isEmpty()) {
                continue;
            }
            
            RenderSetting setting = batch.getRenderSetting();
            List<FunctionalGraphics> functions = groupedFunctions.computeIfAbsent(setting, k -> new ArrayList<>());
            
            for (FunctionInstanceInfo info : visibleInfos) {
                functions.add(info.functionGraphics());
            }
        }

        Map<RenderSetting, List<RenderCommand>> commands = new LinkedHashMap<>();

        for (Map.Entry<RenderSetting, List<FunctionalGraphics>> entry : groupedFunctions.entrySet()) {
            List<FunctionalGraphics> functions = entry.getValue();
            FunctionCommand command = new FunctionCommand(entry.getKey(), null, stageId, null, functions);
            commands.put(entry.getKey(), List.of(command));
        }

        return commands;
    }
    
    @Override
    public boolean supportsBatching() {
        return false;
    }

    private RenderPacket convertCommand(
            PacketBuildContext flowContext,
            KeyId stageId,
            PipelineStateKey stateKey,
            ResourceBindingPlan bindingPlan,
            FunctionalGraphics graphics,
            StandardFunctionGraphics.Command command) {
        if (command instanceof StandardFunctionGraphics.ClearCommand clear) {
            return new ClearPacket(
                    stageId,
                    flowContext.pipelineType(),
                    stateKey,
                    bindingPlan,
                    UniformValueSnapshot.empty(),
                    List.of(graphics),
                    clear.renderTargetId(),
                    clear.clearColor(),
                    clear.clearDepth(),
                    clear.clearColorValue(),
                    clear.clearDepthValue(),
                    clear.colorMask());
        }
        if (command instanceof StandardFunctionGraphics.DrawBuffersCommand drawBuffers) {
            return new DrawBuffersPacket(
                    stageId,
                    flowContext.pipelineType(),
                    stateKey,
                    bindingPlan,
                    UniformValueSnapshot.empty(),
                    List.of(graphics),
                    drawBuffers.renderTargetId(),
                    drawBuffers.colorComponents());
        }
        if (command instanceof StandardFunctionGraphics.GenMipmapCommand mipmap) {
            return new GenerateMipmapPacket(
                    stageId,
                    flowContext.pipelineType(),
                    stateKey,
                    bindingPlan,
                    UniformValueSnapshot.empty(),
                    List.of(graphics),
                    mipmap.textureId());
        }
        if (command instanceof StandardFunctionGraphics.BindRenderTargetCommand bindRenderTarget) {
            return new BindRenderTargetPacket(
                    stageId,
                    flowContext.pipelineType(),
                    stateKey,
                    bindingPlan,
                    UniformValueSnapshot.empty(),
                    List.of(graphics),
                    bindRenderTarget.renderTargetId());
        }
        return null;
    }
}
