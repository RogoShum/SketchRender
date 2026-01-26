package rogo.sketch.core.pipeline.information;

import rogo.sketch.core.instance.FunctionGraphics;
import rogo.sketch.core.pipeline.PartialRenderSetting;
import rogo.sketch.core.pipeline.parmeter.RenderParameter;
import rogo.sketch.core.pipeline.RenderSetting;

public class FunctionInstanceInfo extends InstanceInfo {
    private static final PartialRenderSetting functionSetting = new PartialRenderSetting(null, null, false);
    private final FunctionGraphics functionGraphics;

    public FunctionInstanceInfo(FunctionGraphics functionGraphics, RenderParameter renderParameter) {
        super(functionGraphics, RenderSetting.fromPartial(renderParameter, functionSetting), null);
        this.functionGraphics = functionGraphics;
    }

    @Override
    public String getInfoType() {
        return "function";
    }

    public FunctionGraphics functionGraphics() {
        return functionGraphics;
    }
}