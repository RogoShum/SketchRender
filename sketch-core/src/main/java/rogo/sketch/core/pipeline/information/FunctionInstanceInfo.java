package rogo.sketch.core.pipeline.information;

import rogo.sketch.core.api.graphics.FunctionalGraphics;
import rogo.sketch.core.pipeline.PartialRenderSetting;
import rogo.sketch.core.pipeline.parmeter.RenderParameter;
import rogo.sketch.core.pipeline.RenderSetting;

public class FunctionInstanceInfo extends InstanceInfo<FunctionalGraphics> {
    private static final PartialRenderSetting functionSetting = new PartialRenderSetting(null, null, false);
    private final FunctionalGraphics functionalGraphics;

    public FunctionInstanceInfo(FunctionalGraphics functionalGraphics, RenderParameter renderParameter) {
        super(functionalGraphics, RenderSetting.fromPartial(renderParameter, functionSetting));
        this.functionalGraphics = functionalGraphics;
    }

    @Override
    public String getInfoType() {
        return "function";
    }

    public FunctionalGraphics functionGraphics() {
        return functionalGraphics;
    }
}