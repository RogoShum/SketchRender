package rogo.sketch.feature.culling;

import rogo.sketch.SketchRender;
import rogo.sketch.render.GraphicsStage;
import rogo.sketch.util.Identifier;
import rogo.sketch.util.OrderRequirement;
import rogo.sketch.vanilla.MinecraftRenderStages;

public class CullingStages {
    public static final Identifier HIZ = Identifier.of(SketchRender.MOD_ID, "hierarchy_depth_buffer");
    public static final GraphicsStage HIZ_STAGE = new GraphicsStage(HIZ, OrderRequirement.Builder.<GraphicsStage>create().mustFollow(MinecraftRenderStages.BLOCK_ENTITIES).mustPrecede(MinecraftRenderStages.DESTROY_PROGRESS).build());
}