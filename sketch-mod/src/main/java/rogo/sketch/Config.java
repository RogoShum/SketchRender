package rogo.sketch;

import com.google.common.collect.ImmutableList;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.ForgeConfigSpec;
import rogo.sketch.config.ForgeSettingAdapter;
import rogo.sketch.core.pipeline.module.setting.ModuleSettingRegistry;
import rogo.sketch.core.util.GLFeatureChecker;
import rogo.sketch.feature.culling.CullingStateManager;
import rogo.sketch.module.culling.CullingModuleDescriptor;

import java.util.ArrayList;
import java.util.List;

public class Config {
    public static ForgeConfigSpec CLIENT_CONFIG;
    private static final ForgeSettingAdapter CORE_SETTINGS = new ForgeSettingAdapter();
    private static final ForgeConfigSpec.BooleanValue CULL_ENTITY;
    private static final ForgeConfigSpec.BooleanValue CULL_BLOCK_ENTITY;
    private static final ForgeConfigSpec.BooleanValue CULL_CHUNK;
    private static final ForgeConfigSpec.BooleanValue ASYNC;
    private static final ForgeConfigSpec.BooleanValue AUTO_DISABLE_ASYNC;
    private static final ForgeConfigSpec.DoubleValue UPDATE_DELAY;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> ENTITY_SKIP;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> BLOCK_ENTITY_SKIP;
    private static boolean loaded = false;

    public static boolean doEntityCulling() {
        return getCullBlockEntity() || getCullEntity();
    }

    public static boolean getCullEntity() {
        return isSettingActive(CullingModuleDescriptor.CULL_ENTITY)
                && CORE_SETTINGS.getBoolean(CullingModuleDescriptor.CULL_ENTITY, () -> supportsEntityCulling() && CULL_ENTITY.get());
    }

    public static void setCullEntity(boolean value) {
        writeBoolean(CULL_ENTITY, CullingModuleDescriptor.CULL_ENTITY, value);
    }

    public static boolean getCullBlockEntity() {
        return isSettingActive(CullingModuleDescriptor.CULL_BLOCK_ENTITY)
                && CORE_SETTINGS.getBoolean(CullingModuleDescriptor.CULL_BLOCK_ENTITY, () -> supportsEntityCulling() && CULL_BLOCK_ENTITY.get());
    }

    public static void setCullBlockEntity(boolean value) {
        writeBoolean(CULL_BLOCK_ENTITY, CullingModuleDescriptor.CULL_BLOCK_ENTITY, value);
    }

    public static boolean getCullChunk() {
        return isSettingActive(CullingModuleDescriptor.CULL_CHUNK)
                && CORE_SETTINGS.getBoolean(CullingModuleDescriptor.CULL_CHUNK, () -> supportsChunkCulling() && CULL_CHUNK.get());
    }

    public static void setCullChunk(boolean value) {
        boolean resolved = supportsChunkCulling() && value;
        writeBoolean(CULL_CHUNK, CullingModuleDescriptor.CULL_CHUNK, resolved);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.levelRenderer != null) {
            minecraft.levelRenderer.allChanged();
        }
    }

    public static boolean getAsyncChunkRebuild() {
        return CORE_SETTINGS.getBoolean(CullingModuleDescriptor.ASYNC_CHUNK_REBUILD, () ->
                !unload() && getCullChunk() && !(getAutoDisableAsync() && CullingStateManager.enabledShader()) && ASYNC.get());
    }

    public static void setAsyncChunkRebuild(boolean value) {
        if (!getCullChunk()) {
            return;
        }
        writeBoolean(ASYNC, CullingModuleDescriptor.ASYNC_CHUNK_REBUILD, value);
    }

    public static boolean getAutoDisableAsync() {
        return CORE_SETTINGS.getBoolean(CullingModuleDescriptor.AUTO_DISABLE_ASYNC, () -> !unload() && AUTO_DISABLE_ASYNC.get());
    }

    public static void setAutoDisableAsync(boolean value) {
        writeBoolean(AUTO_DISABLE_ASYNC, CullingModuleDescriptor.AUTO_DISABLE_ASYNC, value);
    }

    public static double getDepthUpdateDelay() {
        return Math.max(CORE_SETTINGS.getFloat(CullingModuleDescriptor.DEPTH_UPDATE_DELAY, () ->
                unload() ? 1.0f : UPDATE_DELAY.get().floatValue()), 1.0f);
    }

    public static void setDepthUpdateDelay(double value) {
        double resolved = Math.max(1.0f, value);
        UPDATE_DELAY.set(resolved);
        UPDATE_DELAY.save();
        CORE_SETTINGS.syncValueToCore(CullingModuleDescriptor.DEPTH_UPDATE_DELAY, (float) resolved);
    }

    public static List<? extends String> getEntitiesSkip() {
        return unload() ? ImmutableList.of() : ENTITY_SKIP.get();
    }

    public static List<? extends String> getBlockEntitiesSkip() {
        return unload() ? ImmutableList.of() : BLOCK_ENTITY_SKIP.get();
    }

    public static void setLoaded() {
        loaded = true;
    }

    public static void attachCoreSettings(ModuleSettingRegistry registry) {
        CORE_SETTINGS.attach(registry);
    }

    public static ForgeSettingAdapter coreSettings() {
        return CORE_SETTINGS;
    }

    private static boolean supportsEntityCulling() {
        return !unload() && GLFeatureChecker.supportsPersistentMapping();
    }

    private static boolean supportsChunkCulling() {
        return !unload() && GLFeatureChecker.supportsIndirectDrawCount() && SketchRender.hasSodium();
    }

    private static boolean unload() {
        return !loaded;
    }

    private static boolean isSettingActive(rogo.sketch.core.util.KeyId settingId) {
        ModuleSettingRegistry registry = CORE_SETTINGS.registry();
        return registry == null || registry.isActive(settingId);
    }

    private static void writeBoolean(ForgeConfigSpec.BooleanValue configValue, rogo.sketch.core.util.KeyId settingId, boolean value) {
        configValue.set(value);
        configValue.save();
        CORE_SETTINGS.syncValueToCore(settingId, value);
    }

    private static void registerBindings() {
        CORE_SETTINGS.bindBoolean(
                CullingModuleDescriptor.CULL_ENTITY,
                Config::getCullEntity,
                Config::setCullEntity,
                Config::supportsEntityCulling,
                () -> "sketch_render.detail.gl44");
        CORE_SETTINGS.bindBoolean(
                CullingModuleDescriptor.CULL_BLOCK_ENTITY,
                Config::getCullBlockEntity,
                Config::setCullBlockEntity,
                Config::supportsEntityCulling,
                () -> "sketch_render.detail.gl44");
        CORE_SETTINGS.bindBoolean(
                CullingModuleDescriptor.CULL_CHUNK,
                Config::getCullChunk,
                Config::setCullChunk,
                Config::supportsChunkCulling,
                () -> SketchRender.hasSodium() ? "sketch_render.detail.gl46" : "sketch_render.detail.sodium");
        CORE_SETTINGS.bindBoolean(
                CullingModuleDescriptor.ASYNC_CHUNK_REBUILD,
                Config::getAsyncChunkRebuild,
                Config::setAsyncChunkRebuild,
                () -> getCullChunk() && SketchRender.hasSodium(),
                () -> SketchRender.hasSodium() ? "sketch_render.detail.async_chunk_build" : "sketch_render.detail.sodium");
        CORE_SETTINGS.bindBoolean(
                CullingModuleDescriptor.AUTO_DISABLE_ASYNC,
                Config::getAutoDisableAsync,
                Config::setAutoDisableAsync,
                () -> true,
                () -> null);
        CORE_SETTINGS.bindFloat(
                CullingModuleDescriptor.DEPTH_UPDATE_DELAY,
                () -> (float) getDepthUpdateDelay(),
                value -> setDepthUpdateDelay(value.doubleValue()),
                () -> true,
                () -> null);
    }

    static {
        ForgeConfigSpec.Builder CLIENT_BUILDER = new ForgeConfigSpec.Builder();

        CLIENT_BUILDER.push("Culling Precision");
        UPDATE_DELAY = CLIENT_BUILDER.defineInRange("level", 4d, 0d, 10d);
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

        CLIENT_BUILDER.push("Async chunk rebuild");
        ASYNC = CLIENT_BUILDER.define("enabled", true);
        CLIENT_BUILDER.pop();

        CLIENT_BUILDER.push("Auto disable async rebuild");
        AUTO_DISABLE_ASYNC = CLIENT_BUILDER.define("enabled", true);
        CLIENT_BUILDER.pop();

        List<String> list = new ArrayList<>();
        list.add("create:stationary_contraption");
        CLIENT_BUILDER.comment("Entity skip culling").push("Entity ResourceLocation");
        ENTITY_SKIP = CLIENT_BUILDER.comment("Entity that skip culling, example: \n" +
                "[\"minecraft:creeper\", \"minecraft:zombie\"]").defineList("list", list, o -> o instanceof String);
        CLIENT_BUILDER.pop();

        list = new ArrayList<>();
        list.add("minecraft:beacon");
        CLIENT_BUILDER.comment("Block Entity skip culling").push("Block Entity ResourceLocation");
        BLOCK_ENTITY_SKIP = CLIENT_BUILDER.comment("Block Entity that skip culling, example: \n" +
                "[\"minecraft:chest\", \"minecraft:mob_spawner\"]").defineList("list", list, o -> o instanceof String);
        CLIENT_BUILDER.pop();

        CLIENT_CONFIG = CLIENT_BUILDER.build();
        registerBindings();
    }
}
