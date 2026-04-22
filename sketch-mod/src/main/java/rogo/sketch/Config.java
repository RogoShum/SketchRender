package rogo.sketch;

import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.loading.FMLPaths;
import rogo.sketch.config.ConfigLayoutStrategy;
import rogo.sketch.config.ConfigScope;
import rogo.sketch.config.PropertyCodecs;
import rogo.sketch.config.PropertySettingAdapter;
import rogo.sketch.config.SketchConfigService;
import rogo.sketch.core.backend.BackendKind;
import rogo.sketch.core.debugger.DashboardDockSlotId;
import rogo.sketch.core.debugger.DashboardPanelId;
import rogo.sketch.core.debugger.DashboardPanelMode;
import rogo.sketch.core.debugger.DashboardWindowId;
import rogo.sketch.core.pipeline.module.setting.ModuleSettingRegistry;
import rogo.sketch.core.pipeline.module.setting.SettingNode;
import rogo.sketch.core.pipeline.module.setting.SettingChangeEvent;
import rogo.sketch.core.ui.geometry.UiRect;
import rogo.sketch.backend.opengl.util.GLFeatureChecker;
import rogo.sketch.core.util.KeyId;
import rogo.sketch.feature.culling.MinecraftShaderCapabilityService;
import rogo.sketch.module.culling.CullingModuleDescriptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class Config {
    private static final SketchConfigService CONFIG_SERVICE = new SketchConfigService(
            FMLPaths.CONFIGDIR.get(),
            SketchRender.MOD_ID,
            ConfigLayoutStrategy.BY_NAMESPACE_PREFIXED_MODULE_FILE,
            SketchRender.MOD_ID);
    private static final ConfigScope RENDER_SCOPE = new ConfigScope("setting", SketchRender.MOD_ID);
    private static final ConfigScope CULLING_SCOPE = new ConfigScope("setting", SketchRender.MOD_ID);
    private static final ConfigScope DASHBOARD_UI_SCOPE = new ConfigScope("screen", "dashboard");
    private static final PropertySettingAdapter CORE_SETTINGS = new PropertySettingAdapter();

    private static final String PROP_GRAPHICS_BACKEND = "graphics_backend";
    private static final String PROP_CULL_ENTITY = "cull_entity";
    private static final String PROP_CULL_BLOCK_ENTITY = "cull_block_entity";
    private static final String PROP_CULL_CHUNK = "cull_chunk";
    private static final String PROP_ASYNC_CHUNK_REBUILD = "async_chunk_rebuild";
    private static final String PROP_AUTO_DISABLE_ASYNC = "auto_disable_async";
    private static final String PROP_DEPTH_UPDATE_DELAY = "culling_precision";
    private static final String PROP_ENTITY_SKIP = "entity_skip";
    private static final String PROP_BLOCK_ENTITY_SKIP = "block_entity_skip";

    private static final List<String> DEFAULT_ENTITY_SKIP = List.of("create:stationary_contraption");
    private static final List<String> DEFAULT_BLOCK_ENTITY_SKIP = List.of("minecraft:beacon");
    private static final Consumer<SettingChangeEvent> CORE_SETTING_LISTENER = Config::onCoreSettingChanged;
    private static boolean loaded = false;
    private static ModuleSettingRegistry attachedRegistry;
    private static volatile ObjectOpenHashSet<String> cachedEntitySkipSet;
    private static volatile ObjectOpenHashSet<String> cachedBlockEntitySkipSet;

    public static boolean doEntityCulling() {
        return getCullBlockEntity() || getCullEntity();
    }

    public static boolean getCullEntity() {
        return isSettingActive(CullingModuleDescriptor.CULL_ENTITY)
                && CORE_SETTINGS.getBoolean(CullingModuleDescriptor.CULL_ENTITY, () -> supportsEntityCulling() && storedCullEntity());
    }

    public static void setCullEntity(boolean value) {
        writeBoolean(CullingModuleDescriptor.CULL_ENTITY, Config::setStoredCullEntity, Config::storedCullEntity, value);
    }

    public static boolean getCullBlockEntity() {
        return isSettingActive(CullingModuleDescriptor.CULL_BLOCK_ENTITY)
                && CORE_SETTINGS.getBoolean(CullingModuleDescriptor.CULL_BLOCK_ENTITY, () -> supportsEntityCulling() && storedCullBlockEntity());
    }

    public static void setCullBlockEntity(boolean value) {
        writeBoolean(CullingModuleDescriptor.CULL_BLOCK_ENTITY, Config::setStoredCullBlockEntity, Config::storedCullBlockEntity, value);
    }

    public static boolean getCullChunk() {
        return isSettingActive(CullingModuleDescriptor.CULL_CHUNK)
                && CORE_SETTINGS.getBoolean(CullingModuleDescriptor.CULL_CHUNK, () -> supportsChunkCulling() && storedCullChunk());
    }

    public static void setCullChunk(boolean value) {
        setStoredCullChunk(supportsChunkCulling() && value);
        CORE_SETTINGS.syncValueToCore(CullingModuleDescriptor.CULL_CHUNK, storedCullChunk());
    }

    public static boolean getAsyncChunkRebuild() {
        return isSettingActive(CullingModuleDescriptor.ASYNC_CHUNK_REBUILD)
                && CORE_SETTINGS.getBoolean(CullingModuleDescriptor.ASYNC_CHUNK_REBUILD, () ->
                !unload() && getCullChunk() && !(getAutoDisableAsync() && MinecraftShaderCapabilityService.getInstance().enabledShader()) && storedAsyncChunkRebuild());
    }

    public static void setAsyncChunkRebuild(boolean value) {
        if (!previewBoolean(CullingModuleDescriptor.CULL_CHUNK, () -> supportsChunkCulling() && storedCullChunk())) {
            return;
        }
        writeBoolean(CullingModuleDescriptor.ASYNC_CHUNK_REBUILD, Config::setStoredAsyncChunkRebuild, Config::storedAsyncChunkRebuild, value);
    }

    public static boolean getAutoDisableAsync() {
        return CORE_SETTINGS.getBoolean(CullingModuleDescriptor.AUTO_DISABLE_ASYNC, () -> !unload() && storedAutoDisableAsync());
    }

    public static void setAutoDisableAsync(boolean value) {
        writeBoolean(CullingModuleDescriptor.AUTO_DISABLE_ASYNC, Config::setStoredAutoDisableAsync, Config::storedAutoDisableAsync, value);
    }

    public static double getDepthUpdateDelay() {
        return Math.max(CORE_SETTINGS.getFloat(CullingModuleDescriptor.DEPTH_UPDATE_DELAY, () ->
                unload() ? 1.0f : storedDepthUpdateDelay().floatValue()), 1.0f);
    }

    public static void setDepthUpdateDelay(double value) {
        double resolved = Math.max(1.0f, value);
        setStoredDepthUpdateDelay(resolved);
        CORE_SETTINGS.syncValueToCore(CullingModuleDescriptor.DEPTH_UPDATE_DELAY, storedDepthUpdateDelay().floatValue());
    }

    public static List<? extends String> getEntitiesSkip() {
        return unload() ? ImmutableList.of() : storedEntitiesSkip();
    }

    public static List<? extends String> getBlockEntitiesSkip() {
        return unload() ? ImmutableList.of() : storedBlockEntitiesSkip();
    }

    public static ObjectOpenHashSet<String> cachedEntitySkipSet() {
        if (unload()) {
            return new ObjectOpenHashSet<>();
        }
        ObjectOpenHashSet<String> cached = cachedEntitySkipSet;
        if (cached != null) {
            return cached;
        }
        ObjectOpenHashSet<String> resolved = new ObjectOpenHashSet<>(storedEntitiesSkip());
        resolved.trim();
        cachedEntitySkipSet = resolved;
        return resolved;
    }

    public static ObjectOpenHashSet<String> cachedBlockEntitySkipSet() {
        if (unload()) {
            return new ObjectOpenHashSet<>();
        }
        ObjectOpenHashSet<String> cached = cachedBlockEntitySkipSet;
        if (cached != null) {
            return cached;
        }
        ObjectOpenHashSet<String> resolved = new ObjectOpenHashSet<>(storedBlockEntitiesSkip());
        resolved.trim();
        cachedBlockEntitySkipSet = resolved;
        return resolved;
    }

    public static DashboardPanelMode getDashboardPanelMode(String workspaceId, DashboardPanelId panelId) {
        String raw = CONFIG_SERVICE.get(DASHBOARD_UI_SCOPE, panelKey(workspaceId, panelId, "mode"), PropertyCodecs.STRING, DashboardPanelMode.DOCKED.name());
        try {
            return DashboardPanelMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return DashboardPanelMode.DOCKED;
        }
    }

    public static BackendKind getGraphicsBackendKind() {
        String raw = CONFIG_SERVICE.get(RENDER_SCOPE, PROP_GRAPHICS_BACKEND, PropertyCodecs.STRING, BackendKind.OPENGL.name());
        return parseBackendKind(raw);
    }

    public static void setGraphicsBackendKind(BackendKind backendKind) {
        if (backendKind == null || backendKind == BackendKind.UNINITIALIZED) {
            return;
        }
        CONFIG_SERVICE.set(RENDER_SCOPE, PROP_GRAPHICS_BACKEND, PropertyCodecs.STRING, backendKind.name());
    }

    public static void setDashboardPanelMode(String workspaceId, DashboardPanelId panelId, DashboardPanelMode mode) {
        if (workspaceId == null || workspaceId.isBlank() || panelId == null || mode == null) {
            return;
        }
        CONFIG_SERVICE.set(DASHBOARD_UI_SCOPE, panelKey(workspaceId, panelId, "mode"), PropertyCodecs.STRING, mode.name());
    }

    public static DashboardDockSlotId getDashboardPanelHomeSlotId(String workspaceId, DashboardPanelId panelId, DashboardDockSlotId fallback) {
        if (workspaceId == null || workspaceId.isBlank() || panelId == null) {
            return fallback;
        }
        String raw = CONFIG_SERVICE.get(DASHBOARD_UI_SCOPE, panelKey(workspaceId, panelId, "home_slot"), PropertyCodecs.STRING,
                fallback != null ? fallback.value() : "");
        DashboardDockSlotId slotId = DashboardDockSlotId.ofNullable(raw);
        return slotId != null ? slotId : fallback;
    }

    public static void setDashboardPanelHomeSlotId(String workspaceId, DashboardPanelId panelId, DashboardDockSlotId slotId) {
        if (workspaceId == null || workspaceId.isBlank() || panelId == null || slotId == null) {
            return;
        }
        CONFIG_SERVICE.set(DASHBOARD_UI_SCOPE, panelKey(workspaceId, panelId, "home_slot"), PropertyCodecs.STRING, slotId.value());
    }

    public static DashboardDockSlotId getDashboardPanelDockedSlotId(String workspaceId, DashboardPanelId panelId, DashboardDockSlotId fallback) {
        if (workspaceId == null || workspaceId.isBlank() || panelId == null) {
            return fallback;
        }
        String raw = CONFIG_SERVICE.get(DASHBOARD_UI_SCOPE, panelKey(workspaceId, panelId, "docked_slot"), PropertyCodecs.STRING,
                fallback != null ? fallback.value() : "");
        DashboardDockSlotId slotId = DashboardDockSlotId.ofNullable(raw);
        return slotId != null ? slotId : fallback;
    }

    public static void setDashboardPanelDockedSlotId(String workspaceId, DashboardPanelId panelId, DashboardDockSlotId slotId) {
        if (workspaceId == null || workspaceId.isBlank() || panelId == null || slotId == null) {
            return;
        }
        CONFIG_SERVICE.set(DASHBOARD_UI_SCOPE, panelKey(workspaceId, panelId, "docked_slot"), PropertyCodecs.STRING, slotId.value());
    }

    public static UiRect getDashboardPanelFloatingBounds(String workspaceId, DashboardPanelId panelId) {
        return new UiRect(
                CONFIG_SERVICE.get(DASHBOARD_UI_SCOPE, panelKey(workspaceId, panelId, "x"), PropertyCodecs.INTEGER, 0),
                CONFIG_SERVICE.get(DASHBOARD_UI_SCOPE, panelKey(workspaceId, panelId, "y"), PropertyCodecs.INTEGER, 0),
                CONFIG_SERVICE.get(DASHBOARD_UI_SCOPE, panelKey(workspaceId, panelId, "width"), PropertyCodecs.INTEGER, 0),
                CONFIG_SERVICE.get(DASHBOARD_UI_SCOPE, panelKey(workspaceId, panelId, "height"), PropertyCodecs.INTEGER, 0));
    }

    public static void setDashboardPanelFloatingBounds(String workspaceId, DashboardPanelId panelId, UiRect bounds) {
        if (workspaceId == null || workspaceId.isBlank() || panelId == null || bounds == null) {
            return;
        }
        CONFIG_SERVICE.set(DASHBOARD_UI_SCOPE, panelKey(workspaceId, panelId, "x"), PropertyCodecs.INTEGER, bounds.x());
        CONFIG_SERVICE.set(DASHBOARD_UI_SCOPE, panelKey(workspaceId, panelId, "y"), PropertyCodecs.INTEGER, bounds.y());
        CONFIG_SERVICE.set(DASHBOARD_UI_SCOPE, panelKey(workspaceId, panelId, "width"), PropertyCodecs.INTEGER, bounds.width());
        CONFIG_SERVICE.set(DASHBOARD_UI_SCOPE, panelKey(workspaceId, panelId, "height"), PropertyCodecs.INTEGER, bounds.height());
    }

    public static double getDashboardSlotSizeRatio(String workspaceId, DashboardDockSlotId slotId, double fallback) {
        if (workspaceId == null || workspaceId.isBlank() || slotId == null) {
            return fallback;
        }
        double ratio = CONFIG_SERVICE.get(DASHBOARD_UI_SCOPE, slotKey(workspaceId, slotId, "size_ratio"), PropertyCodecs.DOUBLE, fallback);
        return Double.isFinite(ratio) ? ratio : fallback;
    }

    public static void setDashboardSlotSizeRatio(String workspaceId, DashboardDockSlotId slotId, double ratio) {
        if (workspaceId == null || workspaceId.isBlank() || slotId == null || !Double.isFinite(ratio)) {
            return;
        }
        CONFIG_SERVICE.set(DASHBOARD_UI_SCOPE, slotKey(workspaceId, slotId, "size_ratio"), PropertyCodecs.DOUBLE, ratio);
    }

    public static int getDashboardScaleLevel(String workspaceId) {
        if (workspaceId == null || workspaceId.isBlank()) {
            return 3;
        }
        int value = CONFIG_SERVICE.get(DASHBOARD_UI_SCOPE, workspaceKey(workspaceId, "scale_level"), PropertyCodecs.INTEGER, 3);
        return Math.max(1, Math.min(5, value));
    }

    public static void setDashboardScaleLevel(String workspaceId, int scaleLevel) {
        if (workspaceId == null || workspaceId.isBlank()) {
            return;
        }
        CONFIG_SERVICE.set(DASHBOARD_UI_SCOPE, workspaceKey(workspaceId, "scale_level"), PropertyCodecs.INTEGER,
                Math.max(1, Math.min(5, scaleLevel)));
    }

    public static boolean getDashboardWindowVisible(String workspaceId, DashboardWindowId windowId) {
        if (workspaceId == null || workspaceId.isBlank() || windowId == null) {
            return true;
        }
        return CONFIG_SERVICE.get(DASHBOARD_UI_SCOPE, windowKey(workspaceId, windowId, "visible"), PropertyCodecs.BOOLEAN, true);
    }

    public static void setDashboardWindowVisible(String workspaceId, DashboardWindowId windowId, boolean visible) {
        if (workspaceId == null || workspaceId.isBlank() || windowId == null) {
            return;
        }
        CONFIG_SERVICE.set(DASHBOARD_UI_SCOPE, windowKey(workspaceId, windowId, "visible"), PropertyCodecs.BOOLEAN, visible);
    }

    public static DashboardPanelId getDashboardWindowPanelId(String workspaceId, DashboardWindowId windowId) {
        if (workspaceId == null || workspaceId.isBlank() || windowId == null) {
            return windowId != null ? windowId.defaultPanelId() : DashboardPanelId.METRICS;
        }
        String raw = CONFIG_SERVICE.get(DASHBOARD_UI_SCOPE, windowKey(workspaceId, windowId, "panel"), PropertyCodecs.STRING,
                windowId.defaultPanelId().id());
        DashboardPanelId panelId = DashboardPanelId.byId(raw);
        return panelId != null ? panelId : windowId.defaultPanelId();
    }

    public static void setDashboardWindowPanelId(String workspaceId, DashboardWindowId windowId, DashboardPanelId panelId) {
        if (workspaceId == null || workspaceId.isBlank() || windowId == null || panelId == null) {
            return;
        }
        CONFIG_SERVICE.set(DASHBOARD_UI_SCOPE, windowKey(workspaceId, windowId, "panel"), PropertyCodecs.STRING, panelId.id());
    }

    public static List<DashboardWindowId> getDashboardPanelWindowOrder(String workspaceId, DashboardPanelId panelId, List<DashboardWindowId> fallback) {
        if (workspaceId == null || workspaceId.isBlank() || panelId == null) {
            return fallback != null ? List.copyOf(fallback) : List.of();
        }
        List<String> raw = CONFIG_SERVICE.get(DASHBOARD_UI_SCOPE, panelKey(workspaceId, panelId, "window_order"),
                PropertyCodecs.STRING_LIST, fallback != null ? fallback.stream().map(DashboardWindowId::id).toList() : List.of());
        List<DashboardWindowId> resolved = new ArrayList<>();
        for (String value : raw) {
            DashboardWindowId windowId = DashboardWindowId.byId(value);
            if (windowId != null && !resolved.contains(windowId)) {
                resolved.add(windowId);
            }
        }
        return resolved;
    }

    public static void setDashboardPanelWindowOrder(String workspaceId, DashboardPanelId panelId, List<DashboardWindowId> windows) {
        if (workspaceId == null || workspaceId.isBlank() || panelId == null || windows == null) {
            return;
        }
        CONFIG_SERVICE.set(DASHBOARD_UI_SCOPE, panelKey(workspaceId, panelId, "window_order"), PropertyCodecs.STRING_LIST,
                windows.stream().map(DashboardWindowId::id).toList());
    }

    public static DashboardWindowId getDashboardActiveWindow(String workspaceId, DashboardPanelId panelId, DashboardWindowId fallback) {
        if (workspaceId == null || workspaceId.isBlank() || panelId == null) {
            return fallback;
        }
        String raw = CONFIG_SERVICE.get(DASHBOARD_UI_SCOPE, panelKey(workspaceId, panelId, "active_window"), PropertyCodecs.STRING,
                fallback != null ? fallback.id() : "");
        DashboardWindowId windowId = DashboardWindowId.byId(raw);
        return windowId != null ? windowId : fallback;
    }

    public static void setDashboardActiveWindow(String workspaceId, DashboardPanelId panelId, DashboardWindowId windowId) {
        if (workspaceId == null || workspaceId.isBlank() || panelId == null || windowId == null) {
            return;
        }
        CONFIG_SERVICE.set(DASHBOARD_UI_SCOPE, panelKey(workspaceId, panelId, "active_window"), PropertyCodecs.STRING, windowId.id());
    }

    private static String panelKey(String workspaceId, DashboardPanelId panelId, String suffix) {
        return "workspace." + workspaceId + ".panel." + panelId.id() + "." + suffix;
    }

    private static String slotKey(String workspaceId, DashboardDockSlotId slotId, String suffix) {
        return "workspace." + workspaceId + ".slot." + slotId.value() + "." + suffix;
    }

    private static String windowKey(String workspaceId, DashboardWindowId windowId, String suffix) {
        return "workspace." + workspaceId + ".window." + windowId.id() + "." + suffix;
    }

    private static String workspaceKey(String workspaceId, String suffix) {
        return "workspace." + workspaceId + "." + suffix;
    }

    private static BackendKind parseBackendKind(String raw) {
        if (raw == null || raw.isBlank()) {
            return BackendKind.OPENGL;
        }
        String normalized = raw.trim()
                .replace('-', '_')
                .replace(' ', '_')
                .toUpperCase(Locale.ROOT);
        if ("GL".equals(normalized) || "DESKTOP_GL".equals(normalized) || "MINECRAFT_GL".equals(normalized)) {
            return BackendKind.OPENGL;
        }
        try {
            BackendKind parsed = BackendKind.valueOf(normalized);
            return parsed == BackendKind.UNINITIALIZED ? BackendKind.OPENGL : parsed;
        } catch (IllegalArgumentException ignored) {
            return BackendKind.OPENGL;
        }
    }

    private static void ensureRenderDefaults() {
        if (!CONFIG_SERVICE.contains(RENDER_SCOPE, PROP_GRAPHICS_BACKEND)) {
            CONFIG_SERVICE.set(RENDER_SCOPE, PROP_GRAPHICS_BACKEND, PropertyCodecs.STRING, BackendKind.OPENGL.name());
        }
    }

    public static void setLoaded() {
        loaded = true;
    }

    public static void attachCoreSettings(ModuleSettingRegistry registry) {
        if (attachedRegistry == registry) {
            return;
        }
        if (attachedRegistry != null) {
            attachedRegistry.removeListener(CORE_SETTING_LISTENER);
        }
        attachedRegistry = registry;
        CORE_SETTINGS.attach(registry);
        syncPersistedModuleSettings(registry);
        registry.addListener(CORE_SETTING_LISTENER);
    }

    public static PropertySettingAdapter coreSettings() {
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

    private static boolean isSettingActive(KeyId settingId) {
        ModuleSettingRegistry registry = CORE_SETTINGS.registry();
        return registry == null || registry.isActive(settingId);
    }

    private static boolean previewBoolean(KeyId settingId, Supplier<Boolean> fallback) {
        return CORE_SETTINGS.getPreviewBoolean(settingId, fallback);
    }

    public static void persistModuleSetting(SettingNode<?> setting, Object value) {
        if (setting == null || setting.isGroup()) {
            return;
        }
        CONFIG_SERVICE.set(moduleSettingScope(setting.moduleId()), setting.id().toString(), PropertyCodecs.STRING,
                encodeSettingValue(setting, value));
    }

    private static void syncPersistedModuleSettings(ModuleSettingRegistry registry) {
        if (registry == null) {
            return;
        }
        for (SettingNode<?> setting : registry.allSettings()) {
            if (setting == null || setting.isGroup() || CORE_SETTINGS.hasBinding(setting.id())) {
                continue;
            }
            if (!CONFIG_SERVICE.contains(moduleSettingScope(setting.moduleId()), setting.id().toString())) {
                continue;
            }
            Object persisted = readPersistedModuleSetting(setting);
            if (persisted != null) {
                registry.setValue(setting.id(), persisted);
            }
        }
    }

    private static Object readPersistedModuleSetting(SettingNode<?> setting) {
        String raw = CONFIG_SERVICE.get(moduleSettingScope(setting.moduleId()), setting.id().toString(), PropertyCodecs.STRING, null);
        if (raw == null) {
            return null;
        }
        try {
            return setting.coerceValue(raw);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String encodeSettingValue(SettingNode<?> setting, Object value) {
        Object normalized;
        try {
            normalized = setting.coerceValue(value);
        } catch (Exception ignored) {
            normalized = value;
        }
        if (normalized instanceof Enum<?> enumValue) {
            return enumValue.name();
        }
        return String.valueOf(normalized);
    }

    private static ConfigScope moduleSettingScope(String moduleId) {
        return new ConfigScope("setting", moduleSettingsFileId(moduleId));
    }

    private static String moduleSettingsFileId(String moduleId) {
        if (moduleId == null || moduleId.isBlank()) {
            return SketchRender.MOD_ID;
        }
        return moduleId.endsWith("_test") ? moduleId : SketchRender.MOD_ID;
    }

    private static void writeBoolean(KeyId settingId, Consumer<Boolean> persistedWriter, Supplier<Boolean> persistedReader, boolean value) {
        persistedWriter.accept(value);
        CORE_SETTINGS.syncValueToCore(settingId, persistedReader.get());
    }

    private static boolean storedCullEntity() {
        return CONFIG_SERVICE.get(CULLING_SCOPE, PROP_CULL_ENTITY, PropertyCodecs.BOOLEAN, true);
    }

    private static void setStoredCullEntity(boolean value) {
        CONFIG_SERVICE.set(CULLING_SCOPE, PROP_CULL_ENTITY, PropertyCodecs.BOOLEAN, value);
    }

    private static boolean storedCullBlockEntity() {
        return CONFIG_SERVICE.get(CULLING_SCOPE, PROP_CULL_BLOCK_ENTITY, PropertyCodecs.BOOLEAN, true);
    }

    private static void setStoredCullBlockEntity(boolean value) {
        CONFIG_SERVICE.set(CULLING_SCOPE, PROP_CULL_BLOCK_ENTITY, PropertyCodecs.BOOLEAN, value);
    }

    private static boolean storedCullChunk() {
        return CONFIG_SERVICE.get(CULLING_SCOPE, PROP_CULL_CHUNK, PropertyCodecs.BOOLEAN, true);
    }

    private static void setStoredCullChunk(boolean value) {
        CONFIG_SERVICE.set(CULLING_SCOPE, PROP_CULL_CHUNK, PropertyCodecs.BOOLEAN, value);
    }

    private static boolean storedAsyncChunkRebuild() {
        return CONFIG_SERVICE.get(CULLING_SCOPE, PROP_ASYNC_CHUNK_REBUILD, PropertyCodecs.BOOLEAN, true);
    }

    private static void setStoredAsyncChunkRebuild(boolean value) {
        CONFIG_SERVICE.set(CULLING_SCOPE, PROP_ASYNC_CHUNK_REBUILD, PropertyCodecs.BOOLEAN, value);
    }

    private static boolean storedAutoDisableAsync() {
        return CONFIG_SERVICE.get(CULLING_SCOPE, PROP_AUTO_DISABLE_ASYNC, PropertyCodecs.BOOLEAN, true);
    }

    private static void setStoredAutoDisableAsync(boolean value) {
        CONFIG_SERVICE.set(CULLING_SCOPE, PROP_AUTO_DISABLE_ASYNC, PropertyCodecs.BOOLEAN, value);
    }

    private static Double storedDepthUpdateDelay() {
        return CONFIG_SERVICE.get(CULLING_SCOPE, PROP_DEPTH_UPDATE_DELAY, PropertyCodecs.DOUBLE, 4.0D);
    }

    private static void setStoredDepthUpdateDelay(double value) {
        CONFIG_SERVICE.set(CULLING_SCOPE, PROP_DEPTH_UPDATE_DELAY, PropertyCodecs.DOUBLE, value);
    }

    private static List<String> storedEntitiesSkip() {
        return CONFIG_SERVICE.get(CULLING_SCOPE, PROP_ENTITY_SKIP, PropertyCodecs.STRING_LIST, DEFAULT_ENTITY_SKIP);
    }

    private static List<String> storedBlockEntitiesSkip() {
        return CONFIG_SERVICE.get(CULLING_SCOPE, PROP_BLOCK_ENTITY_SKIP, PropertyCodecs.STRING_LIST, DEFAULT_BLOCK_ENTITY_SKIP);
    }

    private static void onCoreSettingChanged(SettingChangeEvent event) {
        if (!CullingModuleDescriptor.CULL_CHUNK.equals(event.settingId())) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.levelRenderer != null) {
            minecraft.levelRenderer.allChanged();
        }
    }

    private static void registerBindings() {
        CORE_SETTINGS.bindBoolean(
                CullingModuleDescriptor.CULL_ENTITY,
                Config::storedCullEntity,
                Config::setStoredCullEntity,
                Config::supportsEntityCulling,
                () -> "sketch_render.detail.gl44");
        CORE_SETTINGS.bindBoolean(
                CullingModuleDescriptor.CULL_BLOCK_ENTITY,
                Config::storedCullBlockEntity,
                Config::setStoredCullBlockEntity,
                Config::supportsEntityCulling,
                () -> "sketch_render.detail.gl44");
        CORE_SETTINGS.bindBoolean(
                CullingModuleDescriptor.CULL_CHUNK,
                Config::storedCullChunk,
                Config::setStoredCullChunk,
                Config::supportsChunkCulling,
                () -> SketchRender.hasSodium() ? "sketch_render.detail.gl46" : "sketch_render.detail.sodium");
        CORE_SETTINGS.bindBoolean(
                CullingModuleDescriptor.ASYNC_CHUNK_REBUILD,
                Config::storedAsyncChunkRebuild,
                Config::setStoredAsyncChunkRebuild,
                () -> SketchRender.hasSodium()
                        && previewBoolean(CullingModuleDescriptor.CULL_CHUNK,
                        () -> supportsChunkCulling() && storedCullChunk()),
                () -> SketchRender.hasSodium() ? "sketch_render.detail.async_chunk_build" : "sketch_render.detail.sodium");
        CORE_SETTINGS.bindBoolean(
                CullingModuleDescriptor.AUTO_DISABLE_ASYNC,
                Config::storedAutoDisableAsync,
                Config::setStoredAutoDisableAsync,
                () -> true,
                () -> null);
        CORE_SETTINGS.bindFloat(
                CullingModuleDescriptor.DEPTH_UPDATE_DELAY,
                () -> storedDepthUpdateDelay().floatValue(),
                value -> setStoredDepthUpdateDelay(value.doubleValue()),
                () -> true,
                () -> null);
    }

    private static void migrateLegacyForgeConfigIfNeeded() {
        Path targetPath = CONFIG_SERVICE.resolvePath(CULLING_SCOPE);
        if (Files.exists(targetPath)) {
            return;
        }

        Path legacyPath = FMLPaths.CONFIGDIR.get().resolve(SketchRender.MOD_ID + "-client.toml");
        if (!Files.exists(legacyPath)) {
            return;
        }

        try {
            String section = "";
            for (String rawLine : Files.readAllLines(legacyPath, StandardCharsets.UTF_8)) {
                String line = stripComment(rawLine).trim();
                if (line.isEmpty()) {
                    continue;
                }
                if (line.startsWith("[") && line.endsWith("]")) {
                    section = line.substring(1, line.length() - 1).trim();
                    continue;
                }

                int separatorIndex = line.indexOf('=');
                if (separatorIndex < 0) {
                    continue;
                }

                String key = line.substring(0, separatorIndex).trim();
                String value = line.substring(separatorIndex + 1).trim();

                switch (section + "|" + key) {
                    case "Culling Precision|level" -> setStoredDepthUpdateDelay(parseDouble(value, 4.0D));
                    case "Cull entity|enabled" -> setStoredCullEntity(parseBoolean(value, true));
                    case "Cull block entity|enabled" -> setStoredCullBlockEntity(parseBoolean(value, true));
                    case "Cull chunk|enabled" -> setStoredCullChunk(parseBoolean(value, true));
                    case "Async chunk rebuild|enabled" -> setStoredAsyncChunkRebuild(parseBoolean(value, true));
                    case "Auto disable async rebuild|enabled" -> setStoredAutoDisableAsync(parseBoolean(value, true));
                    case "Entity ResourceLocation|list" -> {
                        CONFIG_SERVICE.set(CULLING_SCOPE, PROP_ENTITY_SKIP, PropertyCodecs.STRING_LIST, parseStringList(value));
                        invalidateCullingCaches();
                    }
                    case "Block Entity ResourceLocation|list" -> {
                        CONFIG_SERVICE.set(CULLING_SCOPE, PROP_BLOCK_ENTITY_SKIP, PropertyCodecs.STRING_LIST, parseStringList(value));
                        invalidateCullingCaches();
                    }
                    default -> {
                    }
                }
            }
        } catch (IOException ignored) {
        }
    }

    private static void migrateLegacyPropertiesIfNeeded() {
        Path legacyPath = FMLPaths.CONFIGDIR.get().resolve(SketchRender.MOD_ID + ".properties");
        if (!Files.exists(legacyPath)) {
            return;
        }
        Path settingsPath = CONFIG_SERVICE.resolvePath(CULLING_SCOPE);
        Path dashboardPath = CONFIG_SERVICE.resolvePath(DASHBOARD_UI_SCOPE);
        if (Files.exists(settingsPath) && Files.exists(dashboardPath)) {
            return;
        }

        Properties properties = new Properties();
        try (java.io.Reader reader = Files.newBufferedReader(legacyPath, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException ignored) {
            return;
        }

        if (!Files.exists(settingsPath)) {
            migrateLegacyPrefix(properties, SketchRender.MOD_ID + ".render.", RENDER_SCOPE);
            migrateLegacyPrefix(properties, SketchRender.MOD_ID + ".culling.", CULLING_SCOPE);
        }
        if (!Files.exists(dashboardPath)) {
            migrateLegacyPrefix(properties, SketchRender.MOD_ID + ".dashboard_ui.", DASHBOARD_UI_SCOPE);
        }
    }

    private static void migrateLegacyPrefix(Properties properties, String legacyPrefix, ConfigScope targetScope) {
        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith(legacyPrefix)) {
                continue;
            }
            String targetKey = key.substring(legacyPrefix.length());
            CONFIG_SERVICE.set(targetScope, targetKey, PropertyCodecs.STRING, properties.getProperty(key));
        }
    }

    private static String stripComment(String line) {
        int commentIndex = line.indexOf('#');
        return commentIndex >= 0 ? line.substring(0, commentIndex) : line;
    }

    private static boolean parseBoolean(String value, boolean fallback) {
        String normalized = value.trim();
        if ("true".equalsIgnoreCase(normalized) || "false".equalsIgnoreCase(normalized)) {
            return Boolean.parseBoolean(normalized);
        }
        return fallback;
    }

    private static double parseDouble(String value, double fallback) {
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static List<String> parseStringList(String value) {
        String trimmed = value.trim();
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        List<String> result = new ArrayList<>();
        if (trimmed.isBlank()) {
            return result;
        }
        for (String token : trimmed.split(",")) {
            String normalized = token.trim();
            if (normalized.startsWith("\"") && normalized.endsWith("\"") && normalized.length() >= 2) {
                normalized = normalized.substring(1, normalized.length() - 1);
            }
            if (!normalized.isEmpty()) {
                result.add(normalized);
            }
        }
        return result;
    }

    private static void invalidateCullingCaches() {
        cachedEntitySkipSet = null;
        cachedBlockEntitySkipSet = null;
    }

    static {
        migrateLegacyPropertiesIfNeeded();
        migrateLegacyForgeConfigIfNeeded();
        ensureRenderDefaults();
        registerBindings();
    }
}

