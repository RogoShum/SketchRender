package rogo.sketch.feature.culling.graphics;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL13;
import rogo.sketch.Config;
import rogo.sketch.SketchRender;
import rogo.sketch.feature.culling.CullingStateManager;
import rogo.sketch.render.instance.ComputeGraphics;
import rogo.sketch.render.pipeline.PartialRenderSetting;
import rogo.sketch.render.resource.GraphicsResourceManager;
import rogo.sketch.render.resource.ResourceReference;
import rogo.sketch.render.resource.ResourceTypes;
import rogo.sketch.util.KeyId;

public class ComputeEntityCullingGraphics extends ComputeGraphics {
    private final ResourceReference<PartialRenderSetting> renderSetting = GraphicsResourceManager.getInstance().getReference(ResourceTypes.PARTIAL_RENDER_SETTING, KeyId.of(SketchRender.MOD_ID, "cull_entity_batch"));

    public ComputeEntityCullingGraphics(KeyId keyId) {
        super(keyId, (c) -> {
        }, (c, shader) -> {
            CullingStateManager.ENTITY_CULLING_MASK.updateEntityData();
            shader.dispatch((CullingStateManager.ENTITY_CULLING_MASK.getEntityMap().size() / 64 + 1), 1, 1);

            RenderSystem.activeTexture(GL13.GL_TEXTURE1);
            RenderSystem.activeTexture(GL13.GL_TEXTURE0);
        });
    }

    @Override
    public PartialRenderSetting getPartialRenderSetting() {
        if (renderSetting.isAvailable()) {
            return renderSetting.get();
        }

        return null;
    }

    @Override
    public boolean shouldDiscard() {
        return false;
    }

    @Override
    public boolean shouldRender() {
        return Config.getCullEntity() && CullingStateManager.ENTITY_CULLING_MASK != null;
    }
}