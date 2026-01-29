package rogo.sketch.feature.culling.graphics;

import rogo.sketch.SketchRender;
import rogo.sketch.core.api.model.PreparedMesh;
import rogo.sketch.core.data.PrimitiveType;
import rogo.sketch.core.data.builder.VertexStreamBuilder;
import rogo.sketch.core.instance.MeshGraphics;
import rogo.sketch.core.model.DynamicMesh;
import rogo.sketch.core.pipeline.PartialRenderSetting;
import rogo.sketch.core.resource.GraphicsResourceManager;
import rogo.sketch.core.resource.ResourceReference;
import rogo.sketch.core.resource.ResourceTypes;
import rogo.sketch.core.util.KeyId;
import rogo.sketch.core.vertex.DefaultDataFormats;
import rogo.sketch.feature.culling.CullingStateManager;

/**
 * Entity culling test graphics instance using DynamicMesh
 */
public class EntityCullingTestGraphics extends MeshGraphics {
    private final ResourceReference<PartialRenderSetting> partialRenderSetting = GraphicsResourceManager.getInstance().getReference(ResourceTypes.PARTIAL_RENDER_SETTING, KeyId.of(SketchRender.MOD_ID, "culling_test_entity"));
    private final DynamicMesh mesh;

    public EntityCullingTestGraphics(KeyId keyId) {
        super(keyId);
        // Use DynamicMesh for run-time generation (demonstration)
        // In reality, a static quad could be a BakedMesh, but user requested
        // DynamicMesh logic
        this.mesh = createDynamicMesh();
    }

    private DynamicMesh createDynamicMesh() {
        return new DynamicMesh(
                KeyId.of("mesh"),
                DefaultDataFormats.POSITION,
                PrimitiveType.QUADS,
                4, // vertexCount
                0, // indexCount (0 for non-indexed or auto-generated)
                filler -> {
                    // Full-screen quad vertices (NDC coordinates)
                    // Dynamic generation every frame (or when requested)
                    filler.put(-1.0f, -1.0f, 0.0f)
                            .put(1.0f, -1.0f, 0.0f)
                            .put(1.0f, 1.0f, 0.0f)
                            .put(-1.0f, 1.0f, 0.0f);
                });
    }

    @Override
    public PartialRenderSetting getPartialRenderSetting() {
        if (partialRenderSetting.isAvailable()) {
            return partialRenderSetting.get();
        }

        return null;
    }

    @Override
    public boolean shouldTick() {
        return false;
    }

    @Override
    public boolean tickable() {
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

    @Override
    public void fillVertex(KeyId componentKey, VertexStreamBuilder builder) {
        if (mesh.generator() != null) {
            mesh.generator().accept(builder);
        }
    }
}