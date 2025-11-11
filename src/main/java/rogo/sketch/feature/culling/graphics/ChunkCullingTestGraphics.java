package rogo.sketch.feature.culling.graphics;

import rogo.sketch.SketchRender;
import rogo.sketch.api.graphics.MeshGraphicsInstance;
import rogo.sketch.feature.culling.CullingStateManager;
import rogo.sketch.render.model.MeshGroup;
import rogo.sketch.render.pipeline.PartialRenderSetting;
import rogo.sketch.render.pipeline.RenderContext;
import rogo.sketch.render.pipeline.RenderParameter;
import rogo.sketch.render.pipeline.RenderSetting;
import rogo.sketch.render.data.PrimitiveType;
import rogo.sketch.render.data.Usage;
import rogo.sketch.render.model.MeshBuilder;
import rogo.sketch.render.model.MeshCompiler;
import rogo.sketch.render.model.ModelMesh;
import rogo.sketch.render.resource.GraphicsResourceManager;
import rogo.sketch.render.resource.ResourceTypes;
import rogo.sketch.render.vertex.DefaultDataFormats;
import rogo.sketch.util.Identifier;

import java.util.Optional;

/**
 * Chunk culling test graphics instance for the new pipeline
 * Renders culling visualization for chunks
 */
public class ChunkCullingTestGraphics implements MeshGraphicsInstance {
    private final Identifier identifier;
    private final ModelMesh modelMesh;
    private final RenderSetting renderSetting;

    public ChunkCullingTestGraphics(Identifier identifier) {
        this.identifier = identifier;
        this.modelMesh = createQuadMesh();
        this.renderSetting = createRenderSetting();
    }

    private ModelMesh createQuadMesh() {
        try {
            // Create a full-screen quad mesh for culling test rendering
            MeshGroup meshGroup = MeshBuilder.create("culling_test_chunk_quad", PrimitiveType.QUADS)
                    .subMesh("quad", 0, 4, DefaultDataFormats.POSITION)
                    // Full-screen quad vertices (NDC coordinates)
                    .vertices(-1.0f, -1.0f, 0.0f)
                    .vertices(1.0f, -1.0f, 0.0f)
                    .vertices(1.0f, 1.0f, 0.0f)
                    .vertices(-1.0f, 1.0f, 0.0f)
                    .indices(0, 1, 2, 2, 3, 0)
                    .build();

            // Compile to ModelMesh for GPU rendering
            return MeshCompiler.compile(meshGroup, MeshCompiler.staticMeshOptions()).getModelMesh();
        } catch (Exception e) {
            // Fallback: return null and let legacy system handle it
            System.err.println("Failed to create chunk culling test quad mesh: " + e.getMessage());
            return null;
        }
    }

    private RenderSetting createRenderSetting() {
        Identifier settingId = Identifier.of(SketchRender.MOD_ID, "culling_test_chunk");
        Optional<PartialRenderSetting> partialSetting = GraphicsResourceManager.getInstance()
                .getResource(ResourceTypes.PARTIAL_RENDER_SETTING, settingId);

        if (partialSetting.isPresent()) {
            RenderParameter renderParameter = new RenderParameter(
                    DefaultDataFormats.POSITION,
                    PrimitiveType.QUADS,
                    Usage.DYNAMIC_DRAW,
                    false
            );
            return RenderSetting.fromPartial(partialSetting.get(), renderParameter);
        }

        return null; // Fallback
    }

    @Override
    public Identifier getIdentifier() {
        return identifier;
    }

    @Override
    public boolean shouldTick() {
        return false;
    }

    @Override
    public <C extends RenderContext> void tick(C context) {
        // No ticking needed for static quad
    }

    @Override
    public boolean shouldDiscard() {
        return false;
    }

    @Override
    public boolean shouldRender() {
        // Only render if culling is active and not currently checking cull, and chunk test is enabled
        if (!CullingStateManager.anyCulling() || CullingStateManager.CHECKING_CULL) {
            return false;
        }

        return CullingStateManager.DEBUG > 0 && SketchRender.testPos != null;
    }

    @Override
    public <C extends RenderContext> void afterDraw(C context) {
        // Nothing to do after drawing
    }

    @Override
    public ModelMesh getModelMesh() {
        return modelMesh;
    }

    @Override
    public <C extends RenderContext> RenderSetting getRenderSetting(C context) {
        return renderSetting;
    }

    @Override
    public boolean needsUpdate() {
        return true; // Always update for dynamic culling tests
    }

    @Override
    public int getRenderPriority() {
        return 100; // Render chunks first
    }

    @Override
    public boolean isVisible() {
        return shouldRender();
    }

    /**
     * Clean up resources when this graphics instance is no longer needed
     */
    public void dispose() {
        if (modelMesh != null) {
            modelMesh.dispose();
        }
    }
}
