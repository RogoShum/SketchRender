package rogo.sketch.feature.culling;

import rogo.sketch.SketchRender;
import rogo.sketch.core.pipeline.GraphicsStage;
import rogo.sketch.core.util.KeyId;
import rogo.sketch.core.util.OrderRequirement;
import rogo.sketch.vanilla.MinecraftRenderStages;

public class CullingStages {
    public static final KeyId TERRAIN_CULLING = KeyId.of(SketchRender.MOD_ID, "terrain_culling");
    public static final KeyId HIZ = KeyId.of(SketchRender.MOD_ID, "hierarchy_depth_buffer");
    public static final GraphicsStage TERRAIN_CULLING_STAGE = new GraphicsStage(
            TERRAIN_CULLING,
            OrderRequirement.Builder.<GraphicsStage>create()
                    .mustFollow(MinecraftRenderStages.SKY)
                    .mustPrecede(MinecraftRenderStages.TERRAIN_SOLID)
                    .build());
    public static final GraphicsStage HIZ_STAGE = new GraphicsStage(HIZ, OrderRequirement.Builder.<GraphicsStage>create().mustFollow(MinecraftRenderStages.BLOCK_ENTITIES).mustPrecede(MinecraftRenderStages.DESTROY_PROGRESS).build());
}
