package rogo.sketch.core.pipeline.flow.impl;

import rogo.sketch.core.api.graphics.Graphics;
import rogo.sketch.core.command.FunctionCommand;
import rogo.sketch.core.command.RenderCommand;
import rogo.sketch.core.instance.FunctionGraphics;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.parmeter.RenderParameter;
import rogo.sketch.core.pipeline.RenderSetting;
import rogo.sketch.core.pipeline.flow.RenderFlowContext;
import rogo.sketch.core.pipeline.flow.RenderFlowStrategy;
import rogo.sketch.core.pipeline.flow.RenderFlowType;
import rogo.sketch.core.pipeline.flow.RenderPostProcessors;
import rogo.sketch.core.pipeline.information.FunctionInstanceInfo;
import rogo.sketch.core.pipeline.information.InstanceInfo;
import rogo.sketch.core.util.KeyId;

import java.util.*;

public class FunctionFlowStrategy implements RenderFlowStrategy {

    @Override
    public RenderFlowType getFlowType() {
        return RenderFlowType.FUNCTION;
    }

    @Override
    public <C extends RenderContext> InstanceInfo collectInstanceInfo(Graphics instance, RenderParameter renderParameter, C context) {
        if (instance instanceof FunctionGraphics functionGraphics) {
            return new FunctionInstanceInfo(functionGraphics, renderParameter);
        }
        return null;
    }

    @Override
    public Map<RenderSetting, List<RenderCommand>> createRenderCommands(Collection<InstanceInfo> infos, KeyId stageId, RenderFlowContext flowContext, RenderPostProcessors postProcessors) {
        Map<RenderSetting, List<FunctionGraphics>> groupedFunctions = new HashMap<>();

        for (InstanceInfo info : infos) {
            if (info instanceof FunctionInstanceInfo functionInfo) {
                RenderSetting setting = functionInfo.getRenderSetting();
                groupedFunctions.computeIfAbsent(setting, k -> new ArrayList<>()).add(functionInfo.functionGraphics());
            }
        }

        Map<RenderSetting, List<RenderCommand>> commands = new HashMap<>();

        for (Map.Entry<RenderSetting, List<FunctionGraphics>> entry : groupedFunctions.entrySet()) {
            List<FunctionGraphics> functions = entry.getValue();
            // Sort by priority (FunctionGraphics implements Comparable)
            Collections.sort(functions);

            FunctionCommand command = new FunctionCommand(entry.getKey(), null, stageId, null, functions);
            commands.put(entry.getKey(), List.of(command));
        }

        return commands;
    }
}