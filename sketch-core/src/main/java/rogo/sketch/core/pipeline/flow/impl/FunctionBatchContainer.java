package rogo.sketch.core.pipeline.flow.impl;

import rogo.sketch.core.api.graphics.FunctionalGraphics;
import rogo.sketch.core.pipeline.RenderSetting;
import rogo.sketch.core.pipeline.flow.RenderBatch;
import rogo.sketch.core.pipeline.flow.container.DefaultBatchContainers;
import rogo.sketch.core.pipeline.information.FunctionInstanceInfo;
import rogo.sketch.core.pipeline.parmeter.RenderParameter;
import rogo.sketch.core.util.KeyId;

/**
 * Function adapter over merged batch container core.
 */
public class FunctionBatchContainer
        extends AbstractMergedBatchContainer<FunctionalGraphics, FunctionInstanceInfo, RenderSetting> {

    public FunctionBatchContainer() {
        registerContainerDescriptor(DefaultBatchContainers.QUEUE_DESCRIPTOR);
        registerContainerDescriptor(DefaultBatchContainers.PRIORITY_DESCRIPTOR);
    }

    @Override
    protected KeyId defaultContainerId() {
        return DefaultBatchContainers.PRIORITY;
    }

    @Override
    protected RenderSetting computeBatchKey(
            FunctionalGraphics graphics,
            RenderParameter renderParameter,
            RenderSetting renderSetting) {
        return renderSetting;
    }

    @Override
    protected RenderBatch<FunctionInstanceInfo> createRenderBatch(
            RenderSetting batchKey,
            RenderSetting renderSetting,
            FunctionalGraphics graphics,
            RenderParameter renderParameter) {
        return new RenderBatch<>(renderSetting);
    }

    @Override
    protected FunctionInstanceInfo createInstanceInfo(
            FunctionalGraphics graphics,
            RenderSetting renderSetting,
            RenderParameter renderParameter) {
        return new FunctionInstanceInfo(graphics, renderParameter);
    }

    @Override
    public Class<FunctionalGraphics> getGraphicsType() {
        return FunctionalGraphics.class;
    }
    
    @Override
    public Class<FunctionInstanceInfo> getInfoType() {
        return FunctionInstanceInfo.class;
    }
}
