package rogo.sketch.core.resource.loader;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import rogo.sketch.core.api.RenderStateComponent;
import rogo.sketch.core.pipeline.PartialRenderSetting;
import rogo.sketch.core.resource.ResourceBinding;
import rogo.sketch.core.state.DefaultRenderStates;
import rogo.sketch.core.state.FullRenderState;
import rogo.sketch.core.util.KeyId;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Loader for PartialRenderSetting resources from JSON
 */
public class RenderSettingLoader implements ResourceLoader<PartialRenderSetting> {

    @Override
    public PartialRenderSetting load(KeyId keyId, ResourceData data, Gson gson, Function<KeyId, Optional<InputStream>> resourceProvider) {
        try {
            String jsonData = data.getString();
            if (jsonData == null) return null;

            JsonObject json = gson.fromJson(jsonData, JsonObject.class);
            FullRenderState renderState = loadFullRenderState(json, gson);
            ResourceBinding resourceBinding = loadResourceBinding(json, gson);

            boolean shouldSwitchRenderState = true;

            if (json.has("shouldSwitchRenderState")) {
                shouldSwitchRenderState = json.get("shouldSwitchRenderState").getAsBoolean();
            }

            return PartialRenderSetting.create(renderState, resourceBinding, shouldSwitchRenderState);
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
            return DefaultRenderStates.createDefaultFullRenderState();
        }

        JsonObject renderStateObj = json.getAsJsonObject("renderState");
        Map<KeyId, RenderStateComponent> overrideComponents = new HashMap<>();

        // Load each render state component override from JSON
        for (Map.Entry<String, JsonElement> entry : renderStateObj.entrySet()) {
            String componentTypeName = entry.getKey();
            JsonElement componentElement = entry.getValue();

            if (componentElement.isJsonObject()) {
                JsonObject componentObj = componentElement.getAsJsonObject();
                KeyId componentType = KeyId.of(componentTypeName);

                if (DefaultRenderStates.isRegistered(componentType)) {
                    RenderStateComponent component = DefaultRenderStates.loadComponentFromJson(componentType, componentObj, gson);
                    overrideComponents.put(component.getIdentifier(), component);
                } else {
                    System.err.println("No default component found for render state component: " + componentTypeName);
                }
            }
        }

        // Build state map with defaults + overrides
        return DefaultRenderStates.createFullRenderState(overrideComponents);
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
                KeyId resourceType = KeyId.of(resourceTypeName);
                JsonObject bindings = bindingsElement.getAsJsonObject();

                // Parse individual bindings
                for (Map.Entry<String, JsonElement> bindingEntry : bindings.entrySet()) {
                    String bindingName = bindingEntry.getKey();
                    JsonElement resourceElement = bindingEntry.getValue();

                    if (resourceElement.isJsonPrimitive() && resourceElement.getAsJsonPrimitive().isString()) {
                        String resourceIdentifierStr = resourceElement.getAsString();
                        KeyId bindingId = KeyId.of(bindingName);
                        KeyId resourceId = KeyId.of(resourceIdentifierStr);

                        resourceBinding.addBinding(resourceType, bindingId, resourceId);
                    }
                }
            }
        }

        return resourceBinding;
    }
}