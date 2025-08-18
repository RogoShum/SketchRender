package rogo.sketch.vanilla.graph;

import rogo.sketch.Config;
import rogo.sketch.feature.culling.CullingStateManager;
import rogo.sketch.render.instance.ComputeGraphics;
import rogo.sketch.util.Identifier;

public class ComputeEntityCullingGraphics extends ComputeGraphics {

    public ComputeEntityCullingGraphics(Identifier identifier) {
        super(identifier, (c) -> {
        }, (c, shader) -> {
            CullingStateManager.ENTITY_CULLING_MASK.updateEntityData();
            CullingStateManager.ENTITY_CULLING_MASK.bindSSBO();
            shader.dispatch((CullingStateManager.ENTITY_CULLING_MASK.getEntityMap().size() / 64 + 1), 1, 1);
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