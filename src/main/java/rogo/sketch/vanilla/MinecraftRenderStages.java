package rogo.sketch.vanilla;

import rogo.sketch.render.GraphicsPipeline;
import rogo.sketch.render.GraphicsStage;
import rogo.sketch.util.OrderRequirement;

import java.util.HashSet;
import java.util.Set;

public class MinecraftRenderStages {
    public static final OrderRequirement<GraphicsStage> NONE_REQUIREMENT = OrderRequirement.Builder.<GraphicsStage>create().build();

    public static final GraphicsStage SKY = new GraphicsStage("vanilla_sky", NONE_REQUIREMENT);
    public static final GraphicsStage TERRAIN_SOLID = new GraphicsStage("vanilla_terrain_solid", NONE_REQUIREMENT);
    public static final GraphicsStage TERRAIN_CUTOUT_MIPPED = new GraphicsStage("vanilla_terrain_cutout_mipped", NONE_REQUIREMENT);
    public static final GraphicsStage TERRAIN_CUTOUT = new GraphicsStage("vanilla_terrain_cutout", NONE_REQUIREMENT);
    public static final GraphicsStage ENTITIES = new GraphicsStage("vanilla_entities", NONE_REQUIREMENT);
    public static final GraphicsStage BLOCK_ENTITIES = new GraphicsStage("vanilla_block_entities", NONE_REQUIREMENT);
    public static final GraphicsStage DESTROY_PROGRESS = new GraphicsStage("vanilla_destroy_progress", NONE_REQUIREMENT);
    public static final GraphicsStage BLOCK_OUTLINE = new GraphicsStage("vanilla_block_outline", NONE_REQUIREMENT);
    public static final GraphicsStage TERRAIN_TRANSLUCENT = new GraphicsStage("vanilla_terrain_translucent", NONE_REQUIREMENT);
    public static final GraphicsStage TERRAIN_TRIPWIRE = new GraphicsStage("vanilla_terrain_tripwire", NONE_REQUIREMENT);
    public static final GraphicsStage PARTICLE = new GraphicsStage("vanilla_particle", NONE_REQUIREMENT);
    public static final GraphicsStage CLOUDS = new GraphicsStage("vanilla_clouds", NONE_REQUIREMENT);
    public static final GraphicsStage WEATHER = new GraphicsStage("vanilla_weather", NONE_REQUIREMENT);

    private static final Set<GraphicsStage> extraStages = new HashSet<>();

    public static void registerVanillaStages(GraphicsPipeline<?> pipeline) {
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
    }

    public static void registerExtraStages(GraphicsPipeline<?> pipeline) {
        extraStages.forEach(pipeline::registerStage);
    }

    public static void addStage(GraphicsStage stage) {
        extraStages.add(stage);
    }
}