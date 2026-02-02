package rogo.sketch.core.pipeline.flow.impl;

import rogo.sketch.core.api.graphics.FunctionalGraphics;
import rogo.sketch.core.command.FunctionCommand;
import rogo.sketch.core.command.RenderCommand;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.RenderSetting;
import rogo.sketch.core.pipeline.flow.BatchContainer;
import rogo.sketch.core.pipeline.flow.RenderBatch;
import rogo.sketch.core.pipeline.flow.RenderFlowContext;
import rogo.sketch.core.pipeline.flow.RenderFlowStrategy;
import rogo.sketch.core.pipeline.flow.RenderFlowType;
import rogo.sketch.core.pipeline.flow.RenderPostProcessors;
import rogo.sketch.core.pipeline.information.FunctionInstanceInfo;
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
    public Map<RenderSetting, List<RenderCommand>> createRenderCommands(BatchContainer<FunctionalGraphics, FunctionInstanceInfo> batchContainer, KeyId stageId, RenderFlowContext flowContext, RenderPostProcessors postProcessors, RenderContext context) {
        
        // Get active batches from container
        Collection<RenderBatch<FunctionInstanceInfo>> activeBatches = batchContainer.getActiveBatches();
        if (activeBatches.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<RenderSetting, List<FunctionalGraphics>> groupedFunctions = new LinkedHashMap<>();

        for (RenderBatch<FunctionInstanceInfo> batch : activeBatches) {
            // Filter visible instances
            List<FunctionInstanceInfo> visibleInfos = filterVisible(batch.getInstances());
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
    
    /**
     * Filter visible instances from a batch.
     */
    private List<FunctionInstanceInfo> filterVisible(List<FunctionInstanceInfo> infos) {
        List<FunctionInstanceInfo> visible = new ArrayList<>();
        for (FunctionInstanceInfo info : infos) {
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
