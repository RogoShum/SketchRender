package rogo.sketch.vanilla;

import rogo.sketch.render.pipeline.GraphicsPipeline;
import rogo.sketch.render.pipeline.GraphicsStage;
import rogo.sketch.util.OrderRequirement;

import java.util.HashSet;
import java.util.Set;

public class MinecraftRenderStages {
    public static final OrderRequirement<GraphicsStage> NONE_REQUIREMENT = OrderRequirement.Builder.<GraphicsStage>create().build();

    public static final GraphicsStage RENDER_START = new GraphicsStage("vanilla_render_start", NONE_REQUIREMENT);
    public static final GraphicsStage PREPARE_FRUSTUM = new GraphicsStage("vanilla_prepare_frustum", OrderRequirement.Builder.<GraphicsStage>create().mustFollow(RENDER_START).build());
    public static final GraphicsStage SKY = new GraphicsStage("vanilla_sky", OrderRequirement.Builder.<GraphicsStage>create().mustFollow(PREPARE_FRUSTUM).build());
    public static final GraphicsStage TERRAIN_SOLID = new GraphicsStage("vanilla_terrain_solid", OrderRequirement.Builder.<GraphicsStage>create().mustFollow(SKY).build());
    public static final GraphicsStage TERRAIN_CUTOUT_MIPPED = new GraphicsStage("vanilla_terrain_cutout_mipped", OrderRequirement.Builder.<GraphicsStage>create().mustFollow(TERRAIN_SOLID).build());
    public static final GraphicsStage TERRAIN_CUTOUT = new GraphicsStage("vanilla_terrain_cutout", OrderRequirement.Builder.<GraphicsStage>create().mustFollow(TERRAIN_CUTOUT_MIPPED).build());
    public static final GraphicsStage ENTITIES = new GraphicsStage("vanilla_entities", OrderRequirement.Builder.<GraphicsStage>create().mustFollow(TERRAIN_CUTOUT).build());
    public static final GraphicsStage BLOCK_ENTITIES = new GraphicsStage("vanilla_block_entities", OrderRequirement.Builder.<GraphicsStage>create().mustFollow(ENTITIES).build());
    public static final GraphicsStage DESTROY_PROGRESS = new GraphicsStage("vanilla_destroy_progress", OrderRequirement.Builder.<GraphicsStage>create().mustFollow(BLOCK_ENTITIES).build());
    public static final GraphicsStage BLOCK_OUTLINE = new GraphicsStage("vanilla_block_outline", OrderRequirement.Builder.<GraphicsStage>create().mustFollow(DESTROY_PROGRESS).build());
    public static final GraphicsStage TERRAIN_TRANSLUCENT = new GraphicsStage("vanilla_terrain_translucent", OrderRequirement.Builder.<GraphicsStage>create().mustFollow(BLOCK_OUTLINE).build());
    public static final GraphicsStage TERRAIN_TRIPWIRE = new GraphicsStage("vanilla_terrain_tripwire", OrderRequirement.Builder.<GraphicsStage>create().mustFollow(TERRAIN_TRANSLUCENT).build());
    public static final GraphicsStage PARTICLE = new GraphicsStage("vanilla_particle", OrderRequirement.Builder.<GraphicsStage>create().mustFollow(TERRAIN_TRIPWIRE).build());
    public static final GraphicsStage CLOUDS = new GraphicsStage("vanilla_clouds", OrderRequirement.Builder.<GraphicsStage>create().mustFollow(PARTICLE).build());
    public static final GraphicsStage WEATHER = new GraphicsStage("vanilla_weather", OrderRequirement.Builder.<GraphicsStage>create().mustFollow(CLOUDS).build());
    public static final GraphicsStage LEVEL_END = new GraphicsStage("vanilla_level_end", OrderRequirement.Builder.<GraphicsStage>create().mustFollow(WEATHER).build());
    public static final GraphicsStage HAND = new GraphicsStage("vanilla_hand", OrderRequirement.Builder.<GraphicsStage>create().mustFollow(LEVEL_END).build());
    public static final GraphicsStage POST_PROGRESS = new GraphicsStage("vanilla_post_progress", OrderRequirement.Builder.<GraphicsStage>create().mustFollow(HAND).build());
    public static final GraphicsStage GUI = new GraphicsStage("vanilla_gui", OrderRequirement.Builder.<GraphicsStage>create().mustFollow(POST_PROGRESS).build());
    public static final GraphicsStage RENDER_END = new GraphicsStage("vanilla_render_end", OrderRequirement.Builder.<GraphicsStage>create().mustFollow(GUI).build());

    private static final Set<GraphicsStage> extraStages = new HashSet<>();

    public static void registerVanillaStages(GraphicsPipeline<?> pipeline) {
        pipeline.registerStage(RENDER_START);
        pipeline.registerStage(PREPARE_FRUSTUM);
        pipeline.registerStage(SKY);
        pipeline.registerStage(TERRAIN_SOLID);
        pipeline.registerStage(TERRAIN_CUTOUT_MIPPED);
        pipeline.registerStage(TERRAIN_CUTOUT);
        pipeline.registerStage(ENTITIES);
        pipeline.registerStage(BLOCK_ENTITIES);
        pipeline.registerStage(DESTROY_PROGRESS);
        pipeline.registerStage(BLOCK_OUTLINE);
        pipeline.registerStage(TERRAIN_TRANSLUCENT);
        pipeline.registerStage(TERRAIN_TRIPWIRE);
        pipeline.registerStage(PARTICLE);
        pipeline.registerStage(CLOUDS);
        pipeline.registerStage(WEATHER);
        pipeline.registerStage(LEVEL_END);
        pipeline.registerStage(HAND);
        pipeline.registerStage(POST_PROGRESS);
        pipeline.registerStage(GUI);
        pipeline.registerStage(RENDER_END);
    }

    public static void registerExtraStages(GraphicsPipeline<?> pipeline) {
        extraStages.forEach(pipeline::registerStage);
    }

    public static void addStage(GraphicsStage stage) {
        extraStages.add(stage);
    }
}