package rogo.sketch.render.pipeline.flow.impl;

import rogo.sketch.api.graphics.DispatchProvider;
import rogo.sketch.api.graphics.Graphics;
import rogo.sketch.render.command.ComputeRenderCommand;
import rogo.sketch.render.command.RenderCommand;
import rogo.sketch.render.pipeline.flow.RenderBatch;
import rogo.sketch.render.pipeline.RenderContext;
import rogo.sketch.render.pipeline.RenderSetting;
import rogo.sketch.render.pipeline.flow.RenderFlowContext;
import rogo.sketch.render.pipeline.flow.RenderFlowStrategy;
import rogo.sketch.render.pipeline.flow.RenderFlowType;
import rogo.sketch.render.pipeline.flow.RenderPostProcessors;
import rogo.sketch.render.pipeline.information.ComputeInstanceInfo;
import rogo.sketch.render.pipeline.information.InstanceInfo;
import rogo.sketch.render.resource.ResourceBinding;
import rogo.sketch.render.shader.ComputeShader;
import rogo.sketch.util.KeyId;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
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
    public <C extends RenderContext> InstanceInfo collectInstanceInfo(
            Graphics instance,
            RenderSetting renderSetting,
            C context) {
        if (!instance.shouldRender()) {
            return null;
        }

        BiConsumer<RenderContext, ComputeShader> dispatchCommand = extractDispatchCommand(instance);
        if (dispatchCommand == null) {
            return null;
        }

        ResourceBinding resourceBinding = renderSetting.resourceBinding();

        return new ComputeInstanceInfo(
                instance,
                renderSetting,
                resourceBinding,
                dispatchCommand);
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
            return java.util.Collections.emptyMap();
        }

        // Create batches to handle uniform grouping
        List<RenderBatch<ComputeInstanceInfo>> batches = organize(computeInfos);
        Map<RenderSetting, List<RenderCommand>> commandsMap = new java.util.LinkedHashMap<>();

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
        Map<RenderSetting, List<T>> grouped = allData.stream()
                .collect(Collectors.groupingBy(InstanceInfo::getRenderSetting));

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
