package rogo.sketch.core.pipeline.flow.impl;

import rogo.sketch.core.api.graphics.DispatchableGraphics;
import rogo.sketch.core.pipeline.RenderSetting;
import rogo.sketch.core.pipeline.flow.RenderBatch;
import rogo.sketch.core.pipeline.flow.container.DefaultBatchContainers;
import rogo.sketch.core.pipeline.information.ComputeInstanceInfo;
import rogo.sketch.core.pipeline.parmeter.RenderParameter;
import rogo.sketch.core.shader.ComputeShader;
import rogo.sketch.core.util.KeyId;

import java.util.function.BiConsumer;

/**
 * Compute adapter over merged batch container core.
 */
public class ComputeBatchContainer 
        extends AbstractMergedBatchContainer<DispatchableGraphics, ComputeInstanceInfo, RenderSetting> {

    public ComputeBatchContainer() {
        registerContainerDescriptor(DefaultBatchContainers.QUEUE_DESCRIPTOR);
        registerContainerDescriptor(DefaultBatchContainers.PRIORITY_DESCRIPTOR);
    }

    @Override
    protected KeyId defaultContainerId() {
        return DefaultBatchContainers.PRIORITY;
    }

    @Override
    protected RenderSetting computeBatchKey(
            DispatchableGraphics graphics,
            RenderParameter renderParameter,
            RenderSetting renderSetting) {
        return renderSetting;
    }

    @Override
    protected RenderBatch<ComputeInstanceInfo> createRenderBatch(
            RenderSetting batchKey,
            RenderSetting renderSetting,
            DispatchableGraphics graphics,
            RenderParameter renderParameter) {
        return new RenderBatch<>(renderSetting);
    }

    @Override
    protected ComputeInstanceInfo createInstanceInfo(
            DispatchableGraphics graphics,
            RenderSetting renderSetting,
            RenderParameter renderParameter) {
        BiConsumer<rogo.sketch.core.pipeline.RenderContext, ComputeShader> dispatchCommand = graphics.getDispatchCommand();
        return new ComputeInstanceInfo(graphics, renderSetting, dispatchCommand);
    }

    @Override
    public Class<DispatchableGraphics> getGraphicsType() {
        return DispatchableGraphics.class;
    }
    
    @Override
    public Class<ComputeInstanceInfo> getInfoType() {
        return ComputeInstanceInfo.class;
    }
}
