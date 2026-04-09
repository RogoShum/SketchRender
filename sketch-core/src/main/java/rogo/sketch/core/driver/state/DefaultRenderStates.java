package rogo.sketch.core.driver.state;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import rogo.sketch.core.api.RenderStateComponent;
import rogo.sketch.core.driver.state.component.*;
import rogo.sketch.core.util.KeyId;

import java.util.*;

public class DefaultRenderStates {
    private static final Map<KeyId, Integer> keyToIndex = new HashMap<>();
    private static final List<KeyId> indexToKey = new ArrayList<>();
    private static RenderStateComponent[] defaultStateArray;
    private static boolean initialized = false;

    public static void init() {
        if (initialized)
            return;

        Map<KeyId, RenderStateComponent> tempDefaults = new LinkedHashMap<>();
        // Register GL default states with OpenGL default values
        registerTemp(tempDefaults, new BlendState(
                false,
                BlendFactor.SRC_ALPHA,
                BlendFactor.ONE_MINUS_SRC_ALPHA,
                BlendFactor.SRC_ALPHA,
                BlendFactor.ONE_MINUS_SRC_ALPHA,
                BlendOp.ADD,
                BlendOp.ADD));
        registerTemp(tempDefaults, new DepthTestState(true, CompareOp.LESS));
        registerTemp(tempDefaults, new DepthMaskState(true));
        registerTemp(tempDefaults, new CullState(true, CullFaceMode.BACK, FrontFaceMode.CCW));
        registerTemp(tempDefaults, new ScissorState(false, 0, 0, 0, 0));
        registerTemp(tempDefaults, new StencilState(false, CompareOp.ALWAYS, 0, 0xFF,
                StencilOperation.KEEP, StencilOperation.KEEP, StencilOperation.KEEP));
        registerTemp(tempDefaults, new ViewportState(0, 0, 1920, 1080));
        registerTemp(tempDefaults, new ShaderState()); // No default shader
        registerTemp(tempDefaults, RenderTargetState.defaultFramebuffer()); // Default framebuffer
        registerTemp(tempDefaults, new ColorMaskState(true, true, true, true));
        registerTemp(tempDefaults, new PolygonOffsetState(false, 0, 0));
        registerTemp(tempDefaults, new LogicOpState(false, LogicOp.COPY));

        int index = 0;
        defaultStateArray = new RenderStateComponent[tempDefaults.size()];

        for (Map.Entry<KeyId, RenderStateComponent> entry : tempDefaults.entrySet()) {
            KeyId key = entry.getKey();
            RenderStateComponent value = entry.getValue();

            keyToIndex.put(key, index);
            indexToKey.add(key);
            defaultStateArray[index] = value;
            index++;
        }

        initialized = true;
    }

    public static <T extends RenderStateComponent> void registerTemp(Map<KeyId, RenderStateComponent> map, T defaultComponent) {
        map.put(defaultComponent.getIdentifier(), defaultComponent);
    }

    public static int getIndex(KeyId key) {
        Integer idx = keyToIndex.get(key);
        if (idx == null) {
            throw new IllegalArgumentException("Unregistered RenderState KeyId: " + key);
        }

        return idx;
    }

    public static KeyId getKey(int index) {
        return indexToKey.get(index);
    }

    public static boolean isRegistered(KeyId type) {
        return keyToIndex.containsKey(type);
    }

    /**
     * Load a render state component from JSON using the default component as
     * prototype
     */
    public static RenderStateComponent loadComponentFromJson(KeyId componentType, JsonObject json, Gson gson) {
        RenderStateComponent defaultComponent = defaultStateArray[getIndex(componentType)];
        if (defaultComponent == null) {
            throw new IllegalArgumentException("No default component found for render state component type: " + componentType);
        }

        // Create new instance and deserialize
        RenderStateComponent instance = defaultComponent.createInstance();
        instance.deserializeFromJson(json, gson);
        return instance;
    }

    public static RenderStateComponent getDefaultComponent(KeyId componentType) {
        Integer index = keyToIndex.get(componentType);
        if (index == null) {
            throw new IllegalArgumentException("Unregistered RenderState KeyId: " + componentType);
        }
        return defaultStateArray[index];
    }

    public static RenderStatePatch createPatch(Map<KeyId, RenderStateComponent> overrides) {
        if (overrides == null || overrides.isEmpty()) {
            return RenderStatePatch.empty();
        }
        for (KeyId type : overrides.keySet()) {
            if (!isRegistered(type)) {
                throw new IllegalArgumentException("Unregistered RenderState KeyId: " + type);
            }
        }
        return RenderStatePatch.of(overrides);
    }
}

