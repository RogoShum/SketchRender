package rogo.sketch.core.resource.loader;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import rogo.sketch.core.api.RenderStateComponent;
import rogo.sketch.core.pipeline.PartialRenderSetting;
import rogo.sketch.core.resource.ResourceBinding;
import rogo.sketch.core.resource.ResourceTypes;
import rogo.sketch.core.shader.config.MacroContext;
import rogo.sketch.core.shader.variant.ShaderVariantKey;
import rogo.sketch.core.state.DefaultRenderStates;
import rogo.sketch.core.state.FullRenderState;
import rogo.sketch.core.util.KeyId;

import java.io.InputStream;
import java.util.*;
import java.util.function.Function;

/**
 * Loader for PartialRenderSetting resources from JSON
 * 
 * Enhanced JSON format:
 * {
 *   "renderState": {
 *     "shader_program": {
 *       "identifier": "sketch:entity_render",
 *       "template": "sketch:shadow_support",  // optional macro template
 *       "macros": { "CUSTOM": "42" },         // optional inline macros
 *       "flags": ["INSTANCED", "TRANSLUCENT"] // optional shader flags
 *     }
 *   },
 *   "flags": ["ENABLE_NORMAL"],  // global flags for this setting
 *   "resourceBinding": { ... }
 * }
 */
public class RenderSettingLoader implements ResourceLoader<PartialRenderSetting> {
    
    // Cache: render setting id -> shader variant key
    private final Map<KeyId, ShaderVariantKey> shaderVariantCache = new HashMap<>();
    
    /**
     * Get the shader variant key for a render setting.
     * @param settingId The render setting identifier
     * @return The variant key, or null if not found
     */
    public ShaderVariantKey getShaderVariantKey(KeyId settingId) {
        return shaderVariantCache.get(settingId);
    }

    @Override
    public KeyId getResourceType() {
        return ResourceTypes.PARTIAL_RENDER_SETTING;
    }

    @Override
    public PartialRenderSetting load(ResourceLoadContext context) {
        try {
            KeyId keyId = context.getResourceId();
            JsonObject json = context.getJson();
            if (json == null) return null;
            
            Gson gson = context.getGson();
            
            // Parse global flags for this render setting
            Set<String> globalFlags = parseFlags(json, "flags");
            
            // Parse shader-specific configuration
            ShaderVariantKey variantKey = parseShaderConfig(json, globalFlags);
            if (variantKey != null && !variantKey.isEmpty()) {
                shaderVariantCache.put(keyId, variantKey);
            }
            
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
     * Parse flags array from JSON.
     */
    private Set<String> parseFlags(JsonObject json, String key) {
        Set<String> flags = new HashSet<>();
        if (json.has(key) && json.get(key).isJsonArray()) {
            JsonArray flagsArray = json.getAsJsonArray(key);
            for (JsonElement element : flagsArray) {
                if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                    flags.add(element.getAsString());
                }
            }
        }
        return flags;
    }
    
    /**
     * Parse shader configuration from renderState.shader_program.
     * Supports:
     * - identifier: shader program id
     * - template: macro template id
     * - macros: inline macro definitions
     * - flags: shader variant flags
     */
    private ShaderVariantKey parseShaderConfig(JsonObject json, Set<String> globalFlags) {
        if (!json.has("renderState")) {
            return globalFlags.isEmpty() ? ShaderVariantKey.EMPTY : ShaderVariantKey.of(globalFlags);
        }
        
        JsonObject renderState = json.getAsJsonObject("renderState");
        if (!renderState.has("shader_program")) {
            return globalFlags.isEmpty() ? ShaderVariantKey.EMPTY : ShaderVariantKey.of(globalFlags);
        }
        
        JsonElement shaderElement = renderState.get("shader_program");
        if (!shaderElement.isJsonObject()) {
            return globalFlags.isEmpty() ? ShaderVariantKey.EMPTY : ShaderVariantKey.of(globalFlags);
        }
        
        JsonObject shaderConfig = shaderElement.getAsJsonObject();
        
        // Collect all flags
        Set<String> allFlags = new HashSet<>(globalFlags);
        
        // Parse shader-specific flags
        if (shaderConfig.has("flags")) {
            allFlags.addAll(parseFlags(shaderConfig, "flags"));
        }
        
        // Parse macros (add as flags for now, since they affect variant)
        if (shaderConfig.has("macros") && shaderConfig.get("macros").isJsonObject()) {
            JsonObject macros = shaderConfig.getAsJsonObject("macros");
            for (Map.Entry<String, JsonElement> entry : macros.entrySet()) {
                // For variant key, we only track macro presence, not values
                // Values are handled by MacroContext
                allFlags.add(entry.getKey());
                
                // Also register the macro value in config context
                JsonElement value = entry.getValue();
                if (value.isJsonPrimitive()) {
                    MacroContext.getInstance().setConfigMacro(
                            entry.getKey(), 
                            value.getAsString()
                    );
                }
            }
        }
        
        // Load macro template if specified
        if (shaderConfig.has("template")) {
            String templateId = shaderConfig.get("template").getAsString();
            // Template loading would be handled by a separate MacroTemplateLoader
            // For now, just add template as a flag for tracking
            allFlags.add("TEMPLATE:" + templateId);
        }
        
        return allFlags.isEmpty() ? ShaderVariantKey.EMPTY : ShaderVariantKey.of(allFlags);
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