package rogo.sketch.core.pipeline.flow.impl;

import rogo.sketch.core.api.graphics.DispatchableGraphics;
import rogo.sketch.core.command.ComputeRenderCommand;
import rogo.sketch.core.command.RenderCommand;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.RenderSetting;
import rogo.sketch.core.pipeline.information.ComputeInstanceInfo;
import rogo.sketch.core.util.KeyId;
import rogo.sketch.core.pipeline.flow.BatchContainer;
import rogo.sketch.core.pipeline.flow.RenderBatch;
import rogo.sketch.core.pipeline.flow.RenderFlowContext;
import rogo.sketch.core.pipeline.flow.RenderFlowStrategy;
import rogo.sketch.core.pipeline.flow.RenderFlowType;
import rogo.sketch.core.pipeline.flow.RenderPostProcessors;

import java.util.*;

/**
 * Flow strategy for compute shader operations.
 * <p>
 * Handles compute shader dispatch operations without geometry batching.
 * Each compute instance is executed as an individual dispatch call,
 * but instances are grouped into batches for uniform updates.
 * </p>
 */
public class ComputeFlowStrategy implements RenderFlowStrategy<DispatchableGraphics, ComputeInstanceInfo> {

    @Override
    public RenderFlowType getFlowType() {
        return RenderFlowType.COMPUTE;
    }
    
    @Override
    public Class<DispatchableGraphics> getGraphicsType() {
        return DispatchableGraphics.class;
    }
    
    @Override
    public Class<ComputeInstanceInfo> getInfoType() {
        return ComputeInstanceInfo.class;
    }

    @Override
    public Map<RenderSetting, List<RenderCommand>> createRenderCommands(
            BatchContainer<DispatchableGraphics, ComputeInstanceInfo> batchContainer,
            KeyId stageId,
            RenderFlowContext flowContext,
            RenderPostProcessors postProcessors,
            RenderContext context) {
        
        // Get active batches from container
        Collection<RenderBatch<ComputeInstanceInfo>> activeBatches = batchContainer.getActiveBatches();
        if (activeBatches.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<RenderSetting, List<RenderCommand>> commandsMap = new LinkedHashMap<>();

        for (RenderBatch<ComputeInstanceInfo> batch : activeBatches) {
            // Filter visible instances
            List<ComputeInstanceInfo> visibleInfos = filterVisible(batch.getInstances());
            if (visibleInfos.isEmpty()) {
                continue;
            }
            
            // Update uniforms for visible instances
            batch.setVisibleInstances(visibleInfos);
            batch.updateUniformsForVisible();
            
            RenderSetting setting = batch.getRenderSetting();
            List<RenderCommand> commands = commandsMap.computeIfAbsent(setting, k -> new ArrayList<>());

            for (ComputeInstanceInfo info : visibleInfos) {
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
     * Filter visible instances from a batch.
     */
    private List<ComputeInstanceInfo> filterVisible(List<ComputeInstanceInfo> infos) {
        List<ComputeInstanceInfo> visible = new ArrayList<>();
        for (ComputeInstanceInfo info : infos) {
            if (info.getInstance().shouldRender()) {
                visible.add(info);
            }
        }
        return visible;
    }

    @Override
    public boolean supportsBatching() {
        return false;
    }
}
