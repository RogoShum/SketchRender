package rogo.sketch.feature.culling.graphics;

import rogo.sketch.SketchRender;
import rogo.sketch.api.model.PreparedMesh;
import rogo.sketch.feature.culling.CullingStateManager;
import rogo.sketch.render.data.PrimitiveType;
import rogo.sketch.render.instance.MeshGraphics;
import rogo.sketch.render.model.DynamicMesh;
import rogo.sketch.render.vertex.DefaultDataFormats;
import rogo.sketch.util.Identifier;

/**
 * Block Entity culling test graphics instance using DynamicMesh
 */
public class BlockEntityCullingTestGraphics extends MeshGraphics {
    private final PreparedMesh mesh;

    public BlockEntityCullingTestGraphics(Identifier identifier) {
        super(identifier);
        this.mesh = createDynamicMesh();
    }

    private PreparedMesh createDynamicMesh() {
        return new DynamicMesh(
                DefaultDataFormats.POSITION,
                PrimitiveType.QUADS,
                4,
                0,
                filler -> {
                    filler.put(-1.0f, -1.0f, 0.0f).endVertex()
                            .put(1.0f, -1.0f, 0.0f).endVertex()
                            .put(1.0f, 1.0f, 0.0f).endVertex()
                            .put(-1.0f, 1.0f, 0.0f).endVertex();
                });
    }

    @Override
    public boolean shouldTick() {
        return false;
    }

    @Override
    public boolean shouldDiscard() {
        return false;
    }

    @Override
    public boolean shouldRender() {
        if (!CullingStateManager.anyCulling() || CullingStateManager.CHECKING_CULL) {
            return false;
        }
        return CullingStateManager.DEBUG > 0 && SketchRender.testBlockEntity != null;
    }

    @Override
    public PreparedMesh getPreparedMesh() {
        return mesh;
    }
}