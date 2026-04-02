package rogo.sketch.core.pipeline.flow.v2;

import rogo.sketch.core.api.graphics.Graphics;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.flow.BatchContainer;
import rogo.sketch.core.pipeline.information.InstanceInfo;
import rogo.sketch.core.pipeline.parmeter.RenderParameter;
import rogo.sketch.core.util.KeyId;

import java.util.function.Supplier;

/**
 * Legacy {@link BatchContainer}-backed registration bridge.
 * <p>
 * The V2 raster/translucent main path uses {@link InstanceRecordStore}
 * directly; this wrapper remains only for compat flows that still depend on
 * the old container model.
 * </p>
 */
@Deprecated(forRemoval = false)
public final class InstanceRegistry<G extends Graphics, I extends InstanceInfo<G>> {
    private final BatchContainer<G, I> legacyContainer;

    public InstanceRegistry(BatchContainer<G, I> legacyContainer) {
        this.legacyContainer = legacyContainer;
    }

    public void register(
            G graphics,
            RenderParameter renderParameter,
            KeyId containerId,
            Supplier<? extends rogo.sketch.core.pipeline.container.GraphicsContainer<? extends RenderContext>> supplier) {
        legacyContainer.addInstance(graphics, renderParameter, containerId, supplier);
    }

    public void unregister(Graphics graphics) {
        legacyContainer.removeGraphicsInstance(graphics);
    }

    public void prepareForFrame() {
        legacyContainer.prepareForFrame();
    }
}
