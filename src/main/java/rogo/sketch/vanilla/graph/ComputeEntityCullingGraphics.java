package rogo.sketch.vanilla.graph;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL13;
import rogo.sketch.Config;
import rogo.sketch.feature.culling.CullingStateManager;
import rogo.sketch.render.instance.ComputeGraphics;
import rogo.sketch.util.Identifier;

public class ComputeEntityCullingGraphics extends ComputeGraphics {

    public ComputeEntityCullingGraphics(Identifier identifier) {
        super(identifier, (c) -> {
        }, (c, shader) -> {
            CullingStateManager.ENTITY_CULLING_MASK.updateEntityData();
            shader.dispatch((CullingStateManager.ENTITY_CULLING_MASK.getEntityMap().size() / 64 + 1), 1, 1);

            RenderSystem.activeTexture(GL13.GL_TEXTURE1);
            RenderSystem.activeTexture(GL13.GL_TEXTURE0);
        });
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