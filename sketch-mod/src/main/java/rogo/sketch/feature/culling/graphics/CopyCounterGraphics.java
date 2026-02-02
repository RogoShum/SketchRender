package rogo.sketch.feature.culling.graphics;

import rogo.sketch.SketchRender;
import rogo.sketch.core.instance.ComputeGraphics;
import rogo.sketch.core.pipeline.PartialRenderSetting;
import rogo.sketch.core.resource.GraphicsResourceManager;
import rogo.sketch.core.resource.ResourceReference;
import rogo.sketch.core.resource.ResourceTypes;
import rogo.sketch.core.util.KeyId;

import static org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BARRIER_BIT;

public class CopyCounterGraphics extends ComputeGraphics {
    private final ResourceReference<PartialRenderSetting> copyCounterSetting = GraphicsResourceManager.getInstance()
            .getReference(ResourceTypes.PARTIAL_RENDER_SETTING, KeyId.of(SketchRender.MOD_ID, "copy_counter"));

    public CopyCounterGraphics(KeyId keyId) {
        super(keyId, null, (c, shader) -> {
            shader.dispatch(1, 1, 1);
            shader.memoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
        });
    }

    @Override
    public PartialRenderSetting getPartialRenderSetting() {
        if (copyCounterSetting.isAvailable()) {
            return copyCounterSetting.get();
        }

        return null;
    }

    @Override
    public boolean shouldDiscard() {
        return false;
    }

    @Override
    public boolean shouldRender() {
        return true;
    }
}