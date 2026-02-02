package rogo.sketch.core.command;

import rogo.sketch.core.api.graphics.FunctionalGraphics;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.RenderSetting;
import rogo.sketch.core.pipeline.UniformBatchGroup;
import rogo.sketch.core.resource.ResourceBinding;
import rogo.sketch.core.util.KeyId;

import java.util.ArrayList;
import java.util.List;

public class FunctionCommand extends RenderCommand {
    private final static List<UniformBatchGroup> EMPTY = new ArrayList<>();
    private final List<? extends FunctionalGraphics> functions;

    public FunctionCommand(RenderSetting renderSetting, ResourceBinding resourceBinding, KeyId stageId, List<UniformBatchGroup> uniformBatches, List<? extends FunctionalGraphics> functions) {
        super(renderSetting, resourceBinding, stageId, uniformBatches);
        this.functions = functions;
    }

    @Override
    public void execute(RenderContext context) {
        for (FunctionalGraphics function : functions) {
            function.execute(context);
        }
    }

    @Override
    public void bindResources() {

    }

    @Override
    public void unbindResources() {

    }

    @Override
    public boolean requiresResourceBinding() {
        return false;
    }

    @Override
    public List<UniformBatchGroup> getUniformBatches() {
        return EMPTY;
    }

    @Override
    public String getCommandType() {
        return "function";
    }

    @Override
    public boolean isValid() {
        return functions != null && !functions.isEmpty();
    }
}