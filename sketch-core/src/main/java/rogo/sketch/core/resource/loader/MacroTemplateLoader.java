package rogo.sketch.core.resource.loader;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import rogo.sketch.core.resource.ResourceTypes;
import rogo.sketch.core.shader.config.MacroContext;
import rogo.sketch.core.shader.config.MacroTemplate;
import rogo.sketch.core.util.KeyId;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Loader for MacroTemplate resources from JSON.
 * 
 * JSON format:
 * {
 *   "macros": { "SHADOW_SIZE": "2048", "MAX_LIGHTS": "16" },
 *   "flags": ["ENABLE_SHADOW", "HIGH_QUALITY"],
 *   "requires": ["ENABLE_SHADOW_MAP"]
 * }
 */
public class MacroTemplateLoader implements ResourceLoader<MacroTemplate> {
    
    @Override
    public KeyId getResourceType() {
        return ResourceTypes.MACRO_TEMPLATE;
    }
    
    @Override
    public MacroTemplate load(ResourceLoadContext context) {
        try {
            KeyId keyId = context.getResourceId();
            JsonObject json = context.getJson();
            if (json == null) return null;
            
            // Parse macros
            Map<String, String> macros = new HashMap<>();
            if (json.has("macros") && json.get("macros").isJsonObject()) {
                JsonObject macrosObj = json.getAsJsonObject("macros");
                for (Map.Entry<String, JsonElement> entry : macrosObj.entrySet()) {
                    JsonElement value = entry.getValue();
                    if (value.isJsonPrimitive()) {
                        macros.put(entry.getKey(), value.getAsString());
                    }
                }
            }
            
            // Parse flags
            Set<String> flags = new HashSet<>();
            if (json.has("flags") && json.get("flags").isJsonArray()) {
                JsonArray flagsArray = json.getAsJsonArray("flags");
                for (JsonElement element : flagsArray) {
                    if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                        flags.add(element.getAsString());
                    }
                }
            }
            
            // Parse requires
            Set<String> requires = new HashSet<>();
            if (json.has("requires") && json.get("requires").isJsonArray()) {
                JsonArray requiresArray = json.getAsJsonArray("requires");
                for (JsonElement element : requiresArray) {
                    if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                        requires.add(element.getAsString());
                    }
                }
            }
            
            // Create template
            MacroTemplate template = new MacroTemplate(keyId, macros, flags, requires);
            
            // Register with MacroContext
            MacroContext.getInstance().registerMacroTemplate(keyId, template);
            
            return template;
            
        } catch (Exception e) {
            System.err.println("Failed to load macro template: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
}

