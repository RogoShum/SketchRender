package rogo.sketchrender.api;

import com.google.common.collect.ImmutableList;
import net.minecraftforge.common.ForgeConfigSpec;
import rogo.sketchrender.SketchRender;
import rogo.sketchrender.culling.CullingStateManager;

import java.util.ArrayList;
import java.util.List;

public class Config {
    public static ForgeConfigSpec CLIENT_CONFIG;
    private static ForgeConfigSpec.BooleanValue CULL_ENTITY;
    private static ForgeConfigSpec.BooleanValue CULL_BLOCK_ENTITY;
    private static ForgeConfigSpec.BooleanValue CULL_CHUNK;
    private static ForgeConfigSpec.BooleanValue COMPUTE_SHADER;
    private static ForgeConfigSpec.BooleanValue ASYNC;
    private static ForgeConfigSpec.BooleanValue AUTO_DISABLE_ASYNC;
    private static ForgeConfigSpec.IntValue UPDATE_DELAY;
    private static ForgeConfigSpec.ConfigValue<List<? extends String>> ENTITY_SKIP;
    private static ForgeConfigSpec.ConfigValue<List<? extends String>> BLOCK_ENTITY_SKIP;

    public static boolean doEntityCulling() {
        return getCullBlockEntity() || getCullEntity();
    }

    public static boolean getCullEntity() {
        if (unload() || !CullingStateManager.gl44())
            return false;
        return CULL_ENTITY.get();
    }

    public static void setCullEntity(boolean value) {
        CULL_ENTITY.set(value);
        CULL_ENTITY.save();
    }

    public static boolean getCullBlockEntity() {
        if (unload() || !CullingStateManager.gl44())
            return false;
        return CULL_BLOCK_ENTITY.get();
    }

    public static void setCullBlockEntity(boolean value) {
        CULL_BLOCK_ENTITY.set(value);
        CULL_BLOCK_ENTITY.save();
    }

    public static boolean getCullChunk() {
        if (unload())
            return false;

        return CULL_CHUNK.get();
    }

    public static boolean shouldCullChunk() {
        if (unload())
            return false;

        return getCullChunk();
    }

    public static void setCullChunk(boolean value) {
        CULL_CHUNK.set(value);
        CULL_CHUNK.save();
    }

    public static boolean shouldComputeShader() {
        if (unload())
            return false;

        return COMPUTE_SHADER.get();
    }

    public static void setComputeShader(boolean value) {
        COMPUTE_SHADER.set(value);
        COMPUTE_SHADER.save();
    }

    public static boolean getAsyncChunkRebuild() {
        if (unload())
            return false;

        if (!shouldCullChunk())
            return false;

        if (CullingStateManager.needPauseRebuild())
            return false;

        if (!SketchRender.hasSodium())
            return false;

        if (getAutoDisableAsync() && CullingStateManager.enabledShader())
            return false;

        return ASYNC.get();
    }

    public static void setAsyncChunkRebuild(boolean value) {
        if (!shouldCullChunk())
            return;

        if (!SketchRender.hasSodium())
            return;

        if (CullingStateManager.needPauseRebuild())
            return;

        ASYNC.set(value);
        ASYNC.save();
    }

    public static boolean getAutoDisableAsync() {
        if (unload())
            return false;

        return AUTO_DISABLE_ASYNC.get();
    }

    public static void setAutoDisableAsync(boolean value) {
        AUTO_DISABLE_ASYNC.set(value);
        AUTO_DISABLE_ASYNC.save();
    }

    public static int getShaderDynamicDelay() {
        return CullingStateManager.enabledShader() ? 1 : 0;
    }

    public static int getDepthUpdateDelay() {
        if (unload())
            return 1;
        return UPDATE_DELAY.get() <= 9 ? UPDATE_DELAY.get() + getShaderDynamicDelay() : UPDATE_DELAY.get();
    }

    public static void setDepthUpdateDelay(int value) {
        UPDATE_DELAY.set(value);
        UPDATE_DELAY.save();
    }

    public static List<? extends String> getEntitiesSkip() {
        if (unload())
            return ImmutableList.of();
        return ENTITY_SKIP.get();
    }

    public static List<? extends String> getBlockEntitiesSkip() {
        if (unload())
            return ImmutableList.of();
        return BLOCK_ENTITY_SKIP.get();
    }

    private static boolean loaded = false;

    public static void setLoaded() {
        loaded = true;
    }

    private static boolean unload() {
        return !loaded;
    }

    static {
        ForgeConfigSpec.Builder CLIENT_BUILDER = new ForgeConfigSpec.Builder();

        CLIENT_BUILDER.push("Culling Map update delay");
        UPDATE_DELAY = CLIENT_BUILDER.defineInRange("delay frame", 1, 0, 10);
        CLIENT_BUILDER.pop();

        CLIENT_BUILDER.push("Cull entity");
        CULL_ENTITY = CLIENT_BUILDER.define("enabled", true);
        CLIENT_BUILDER.pop();

        CLIENT_BUILDER.push("Cull block entity");
        CULL_BLOCK_ENTITY = CLIENT_BUILDER.define("enabled", true);
        CLIENT_BUILDER.pop();

        CLIENT_BUILDER.push("Cull chunk");
        CULL_CHUNK = CLIENT_BUILDER.define("enabled", true);
        CLIENT_BUILDER.pop();

        CLIENT_BUILDER.push("Compute shader");
        COMPUTE_SHADER = CLIENT_BUILDER.define("enabled", true);
        CLIENT_BUILDER.pop();

        CLIENT_BUILDER.push("Async chunk rebuild");
        ASYNC = CLIENT_BUILDER.define("enabled", true);
        CLIENT_BUILDER.pop();

        CLIENT_BUILDER.push("Auto disable async rebuild");
        AUTO_DISABLE_ASYNC = CLIENT_BUILDER.define("enabled", true);
        CLIENT_BUILDER.pop();

        List<String> list = new ArrayList<>();
        list.add("create:stationary_contraption");
        CLIENT_BUILDER.comment("Entity skip CULLING").push("Entity ResourceLocation");
        ENTITY_SKIP = CLIENT_BUILDER.comment("Entity that skip CULLING, example: \n" +
                "[\"minecraft:creeper\", \"minecraft:zombie\"]").defineList("list", list, (o -> o instanceof String));
        CLIENT_BUILDER.pop();

        list = new ArrayList<>();
        list.add("minecraft:beacon");
        CLIENT_BUILDER.comment("Block Entity skip CULLING").push("Block Entity ResourceLocation");
        BLOCK_ENTITY_SKIP = CLIENT_BUILDER.comment("Block Entity that skip CULLING, example: \n" +
                "[\"minecraft:chest\", \"minecraft:mob_spawner\"]").defineList("list", list, (o -> o instanceof String));
        CLIENT_BUILDER.pop();

        CLIENT_CONFIG = CLIENT_BUILDER.build();
    }
}
