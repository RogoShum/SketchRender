package rogo.sketch.render.resource.loader;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import rogo.sketch.api.RenderStateComponent;
import rogo.sketch.render.PartialRenderSetting;
import rogo.sketch.render.resource.ResourceBinding;
import rogo.sketch.render.state.FullRenderState;
import rogo.sketch.render.state.RenderStateRegistry;
import rogo.sketch.util.Identifier;

import java.io.BufferedReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Loader for PartialRenderSetting resources from JSON
 */
public class RenderSettingLoader implements ResourceLoader<PartialRenderSetting> {

    @Override
    public PartialRenderSetting loadFromJson(Identifier identifier, String jsonData, Gson gson, Function<Identifier, Optional<BufferedReader>> resourceProvider) {
        try {
            JsonObject json = gson.fromJson(jsonData, JsonObject.class);
            FullRenderState renderState = loadFullRenderState(json, gson);
            ResourceBinding resourceBinding = loadResourceBinding(json, gson);

            boolean shouldSwitchRenderState = true;

            if (json.has("shouldSwitchRenderState")) {
                shouldSwitchRenderState = json.get("shouldSwitchRenderState").getAsBoolean();
            }

            return PartialRenderSetting.reloadable(renderState, resourceBinding, shouldSwitchRenderState, identifier);
        } catch (Exception e) {
            System.err.println("Failed to load render setting from JSON: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Load FullRenderState from JSON
     */
    private FullRenderState loadFullRenderState(JsonObject json, Gson gson) {
        if (!json.has("renderState")) {
            // Return defaults only
            return RenderStateRegistry.createDefaultFullRenderState();
        }

        JsonObject renderStateObj = json.getAsJsonObject("renderState");
        Map<Identifier, RenderStateComponent> overrideComponents = new HashMap<>();

        // Load each render state component override from JSON
        for (Map.Entry<String, JsonElement> entry : renderStateObj.entrySet()) {
            String componentTypeName = entry.getKey();
            JsonElement componentElement = entry.getValue();

            if (componentElement.isJsonObject()) {
                JsonObject componentObj = componentElement.getAsJsonObject();
                Identifier componentType = Identifier.of(componentTypeName);

                if (RenderStateRegistry.hasComponent(componentType)) {
                    RenderStateComponent component = RenderStateRegistry.loadComponentFromJson(componentType, componentObj, gson);
                    if (component != null) {
                        overrideComponents.put(component.getIdentifier(), component);
                    }
                } else {
                    System.err.println("No default component found for render state component: " + componentTypeName);
                }
            }
        }

        // Build state map with defaults + overrides
        return RenderStateRegistry.createFullRenderState(overrideComponents);
    }

    /**
     * Load ResourceBinding from JSON
     */
    private ResourceBinding loadResourceBinding(JsonObject json, Gson gson) {
        ResourceBinding resourceBinding = new ResourceBinding();

        if (!json.has("resourceBinding")) {
            return resourceBinding;
        }

        JsonObject bindingObj = json.getAsJsonObject("resourceBinding");

        // Parse resource bindings by type
        for (Map.Entry<String, JsonElement> typeEntry : bindingObj.entrySet()) {
            String resourceTypeName = typeEntry.getKey();
            JsonElement bindingsElement = typeEntry.getValue();

            if (bindingsElement.isJsonObject()) {
                Identifier resourceType = Identifier.of(resourceTypeName);
                JsonObject bindings = bindingsElement.getAsJsonObject();

                // Parse individual bindings
                for (Map.Entry<String, JsonElement> bindingEntry : bindings.entrySet()) {
                    String bindingName = bindingEntry.getKey();
                    JsonElement resourceElement = bindingEntry.getValue();

                    if (resourceElement.isJsonPrimitive() && resourceElement.getAsJsonPrimitive().isString()) {
                        String resourceIdentifierStr = resourceElement.getAsString();
                        Identifier bindingId = Identifier.of(bindingName);
                        Identifier resourceId = Identifier.of(resourceIdentifierStr);

                        resourceBinding.addBinding(resourceType, bindingId, resourceId);
                    }
                }
            }
        }

        return resourceBinding;
    }
} 