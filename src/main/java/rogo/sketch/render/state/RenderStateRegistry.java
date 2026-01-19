package rogo.sketch.render.state;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.lwjgl.opengl.GL11;
import rogo.sketch.api.RenderStateComponent;
import rogo.sketch.render.state.gl.*;
import rogo.sketch.util.KeyId;

import java.util.HashMap;
import java.util.Map;

public class RenderStateRegistry {
    private static final Map<KeyId, RenderStateComponent> defaultComponents = new HashMap<>();
    private static boolean initialized = false;

    public static void init() {
        if (initialized)
            return;

        // Register GL default states with OpenGL default values
        registerDefault(new BlendState(false, GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA));
        registerDefault(new DepthTestState(true, GL11.GL_LESS));
        registerDefault(new DepthMaskState(true));
        registerDefault(new CullState(true, GL11.GL_BACK, GL11.GL_CCW));
        registerDefault(new PolygonModeState(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL));
        registerDefault(new ScissorState(false, 0, 0, 0, 0));
        registerDefault(new StencilState(false, GL11.GL_ALWAYS, 0, 0xFF, GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP));
        registerDefault(new ViewportState(0, 0, 1920, 1080));
        registerDefault(new ShaderState()); // No default shader
        registerDefault(RenderTargetState.defaultFramebuffer()); // Default framebuffer
        registerDefault(new ColorMaskState(true, true, true, true));
        registerDefault(new PolygonOffsetState(false, 0, 0));
        registerDefault(new LogicOpState(false, GL11.GL_COPY));

        initialized = true;
    }

    public static <T extends RenderStateComponent> void registerDefault(T defaultComponent) {
        defaultComponents.put(defaultComponent.getIdentifier(), defaultComponent);
    }

    public static boolean isRegistered(KeyId type) {
        return defaultComponents.containsKey(type);
    }

    public static Map<KeyId, RenderStateComponent> getDefaults() {
        // Return immutable copy to prevent external modification
        return Map.copyOf(defaultComponents);
    }

    /**
     * Load a render state component from JSON using the default component as
     * prototype
     */
    public static RenderStateComponent loadComponentFromJson(KeyId componentType, JsonObject json, Gson gson) {
        RenderStateComponent defaultComponent = defaultComponents.get(componentType);
        if (defaultComponent == null) {
            throw new IllegalArgumentException(
                    "No default component found for render state component type: " + componentType);
        }

        // Create new instance and deserialize
        RenderStateComponent instance = defaultComponent.createInstance();
        instance.deserializeFromJson(json, gson);
        return instance;
    }

    /**
     * Check if a default component exists for the given type
     */
    public static boolean hasComponent(KeyId componentType) {
        return defaultComponents.containsKey(componentType);
    }

    /**
     * Get all components with defaults applied, then override with provided
     * components
     */
    public static Map<KeyId, RenderStateComponent> buildStateMap(Map<KeyId, RenderStateComponent> overrides) {
        Map<KeyId, RenderStateComponent> result = new HashMap<>(getDefaults());
        result.putAll(overrides);
        return result;
    }

    /**
     * Create a FullRenderState with defaults only
     */
    public static FullRenderState createDefaultFullRenderState() {
        return new FullRenderState(getDefaults());
    }

    /**
     * Create a FullRenderState with defaults and overrides
     */
    public static FullRenderState createFullRenderState(Map<KeyId, RenderStateComponent> overrides) {
        return new FullRenderState(buildStateMap(overrides));
    }
}