package rogo.sketch.core.state;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.lwjgl.opengl.GL11;
import rogo.sketch.core.api.RenderStateComponent;
import rogo.sketch.core.state.gl.*;
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
        registerTemp(tempDefaults, new BlendState(false, GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA));
        registerTemp(tempDefaults, new DepthTestState(true, GL11.GL_LESS));
        registerTemp(tempDefaults, new DepthMaskState(true));
        registerTemp(tempDefaults, new CullState(true, GL11.GL_BACK, GL11.GL_CCW));
        registerTemp(tempDefaults, new ScissorState(false, 0, 0, 0, 0));
        registerTemp(tempDefaults, new StencilState(false, GL11.GL_ALWAYS, 0, 0xFF, GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP));
        registerTemp(tempDefaults, new ViewportState(0, 0, 1920, 1080));
        registerTemp(tempDefaults, new ShaderState()); // No default shader
        registerTemp(tempDefaults, RenderTargetState.defaultFramebuffer()); // Default framebuffer
        registerTemp(tempDefaults, new ColorMaskState(true, true, true, true));
        registerTemp(tempDefaults, new PolygonOffsetState(false, 0, 0));
        registerTemp(tempDefaults, new LogicOpState(false, GL11.GL_COPY));

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
        return getIndex(type) > -1;
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

    /**
     * Create a FullRenderState with defaults only
     */
    public static FullRenderState createDefaultFullRenderState() {
        return new FullRenderState(defaultStateArray.clone());
    }

    /**
     * Create a FullRenderState with defaults and overrides
     */
    public static FullRenderState createFullRenderState(Map<KeyId, RenderStateComponent> overrides) {
        RenderStateComponent[] snapshot = defaultStateArray.clone();

        if (overrides != null && !overrides.isEmpty()) {
            for (Map.Entry<KeyId, RenderStateComponent> entry : overrides.entrySet()) {
                Integer index = keyToIndex.get(entry.getKey());
                if (index != null) {
                    snapshot[index] = entry.getValue();
                } else {
                    throw new IllegalArgumentException("Unregistered RenderState KeyId: " + entry.getKey());
                }
            }
        }

        return new FullRenderState(snapshot);
    }
}