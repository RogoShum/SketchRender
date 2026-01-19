package rogo.sketch.feature.culling.graphics;

import rogo.sketch.SketchRender;
import rogo.sketch.api.model.PreparedMesh;
import rogo.sketch.feature.culling.CullingStateManager;
import rogo.sketch.render.data.PrimitiveType;
import rogo.sketch.render.instance.MeshGraphics;
import rogo.sketch.render.model.DynamicMesh;
import rogo.sketch.render.vertex.DefaultDataFormats;
import rogo.sketch.util.KeyId;

/**
 * Entity culling test graphics instance using DynamicMesh
 */
public class EntityCullingTestGraphics extends MeshGraphics {
    private final PreparedMesh mesh;

    public EntityCullingTestGraphics(KeyId keyId) {
        super(keyId);
        // Use DynamicMesh for run-time generation (demonstration)
        // In reality, a static quad could be a BakedMesh, but user requested
        // DynamicMesh logic
        this.mesh = createDynamicMesh();
    }

    private PreparedMesh createDynamicMesh() {
        return new DynamicMesh(
                KeyId.of("mesh"),
                DefaultDataFormats.POSITION,
                PrimitiveType.QUADS,
                4, // vertexCount
                0, // indexCount (0 for non-indexed or auto-generated)
                filler -> {
                    // Full-screen quad vertices (NDC coordinates)
                    // Dynamic generation every frame (or when requested)
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
        return CullingStateManager.DEBUG > 0 && SketchRender.testEntity != null;
    }

    @Override
    public PreparedMesh getPreparedMesh() {
        return mesh;
    }
}
