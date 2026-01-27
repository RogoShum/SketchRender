package rogo.sketch.core.pipeline.flow.impl;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.api.graphics.DispatchProvider;
import rogo.sketch.core.api.graphics.Graphics;
import rogo.sketch.core.command.ComputeRenderCommand;
import rogo.sketch.core.command.RenderCommand;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.parmeter.RenderParameter;
import rogo.sketch.core.pipeline.RenderSetting;
import rogo.sketch.core.pipeline.information.ComputeInstanceInfo;
import rogo.sketch.core.pipeline.information.InstanceInfo;
import rogo.sketch.core.resource.ResourceBinding;
import rogo.sketch.core.shader.ComputeShader;
import rogo.sketch.core.util.KeyId;
import rogo.sketch.core.pipeline.flow.RenderBatch;
import rogo.sketch.core.pipeline.flow.RenderFlowContext;
import rogo.sketch.core.pipeline.flow.RenderFlowStrategy;
import rogo.sketch.core.pipeline.flow.RenderFlowType;
import rogo.sketch.core.pipeline.flow.RenderPostProcessors;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * Flow strategy for compute shader operations.
 * <p>
 * Handles compute shader dispatch operations without geometry batching.
 * Each compute instance is executed as an individual dispatch call,
 * but instances are grouped into batches for uniform updates.
 * </p>
 */
public class ComputeFlowStrategy implements RenderFlowStrategy {

    @Override
    public RenderFlowType getFlowType() {
        return RenderFlowType.COMPUTE;
    }

    @Override
    @Nullable
    public <C extends RenderContext> InstanceInfo collectInstanceInfo(Graphics instance, RenderParameter renderParameter, C context) {
        BiConsumer<RenderContext, ComputeShader> dispatchCommand = extractDispatchCommand(instance);
        if (dispatchCommand == null || instance.getPartialRenderSetting() == null) {
            return null;
        }

        RenderSetting renderSetting = RenderSetting.fromPartial(renderParameter, instance.getPartialRenderSetting());
        ResourceBinding resourceBinding = renderSetting.resourceBinding();

        return new ComputeInstanceInfo(instance, renderSetting, resourceBinding, dispatchCommand);
    }

    @Override
    public Map<RenderSetting, List<RenderCommand>> createRenderCommands(
            Collection<InstanceInfo> infos,
            KeyId stageId,
            RenderFlowContext flowContext,
            RenderPostProcessors postProcessors) {
        // Filter to ComputeInstanceInfo
        List<ComputeInstanceInfo> computeInfos = infos.stream()
                .filter(info -> info instanceof ComputeInstanceInfo)
                .map(info -> (ComputeInstanceInfo) info)
                .toList();

        if (computeInfos.isEmpty()) {
            return Collections.emptyMap();
        }

        // Create batches to handle uniform grouping
        List<RenderBatch<ComputeInstanceInfo>> batches = organize(computeInfos);
        Map<RenderSetting, List<RenderCommand>> commandsMap = new LinkedHashMap<>();

        for (RenderBatch<ComputeInstanceInfo> batch : batches) {
            RenderSetting setting = batch.getRenderSetting();
            List<RenderCommand> commands = commandsMap.computeIfAbsent(setting, k -> new ArrayList<>());

            for (ComputeInstanceInfo info : batch.getInstances()) {
                // Pass the batch to the command so it can process uniforms if needed
                commands.add(new ComputeRenderCommand.Builder()
                        .renderSetting(info.getRenderSetting())
                        .stageId(stageId)
                        .computeInfo(info)
                        .dispatchFunction(info.getDispatchCommand())
                        .workGroups(1, 1, 1)
                        .addUniformBatch(batch.getUniformBatches())
                        .build());
            }
        }

        return commandsMap;
    }

    /**
     * Organize instances into batches based on RenderSetting.
     */
    public <T extends InstanceInfo> List<RenderBatch<T>> organize(Collection<T> allData) {
        if (allData.isEmpty())
            return List.of();

        // Group by RenderSetting
        Map<RenderSetting, List<T>> grouped = new LinkedHashMap<>();
        for (T data : allData) {
            RenderSetting setting = data.getRenderSetting();
            List<T> group = grouped.computeIfAbsent(setting, k -> new ArrayList<>());
            group.add(data);
        }

        List<RenderBatch<T>> batches = new ArrayList<>();
        for (Map.Entry<RenderSetting, List<T>> entry : grouped.entrySet()) {
            batches.add(new RenderBatch<>(entry.getKey(), entry.getValue()));
        }

        return batches;
    }

    @Override
    public boolean supportsBatching() {
        return false;
    }

    @Nullable
    private static BiConsumer<RenderContext, ComputeShader> extractDispatchCommand(Graphics instance) {
        if (instance instanceof DispatchProvider computeGraphics) {
            return computeGraphics.getDispatchCommand();
        }
        return null;
    }
}
