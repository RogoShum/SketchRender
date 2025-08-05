package rogo.sketchrender.render.state;

import rogo.sketchrender.api.RenderStateComponent;

import java.util.HashMap;
import java.util.Map;

public class RenderStateRegistry {
    private static final Map<Class<? extends RenderStateComponent>, RenderStateComponent> defaultComponents = new HashMap<>();

    public static <T extends RenderStateComponent> void registerDefault(T defaultComponent) {
        defaultComponents.put(defaultComponent.getType(), defaultComponent);
    }

    public static boolean isRegistered(Class<? extends RenderStateComponent> type) {
        return defaultComponents.containsKey(type);
    }

    public static Map<Class<? extends RenderStateComponent>, RenderStateComponent> getDefaults() {
        return defaultComponents;
    }
}