package rogo.sketch.core.resource.loader;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import rogo.sketch.core.resource.ResourceTypes;
import rogo.sketch.core.shader.config.MacroContext;
import rogo.sketch.core.shader.config.MacroEntryDescriptor;
import rogo.sketch.core.shader.config.MacroEntryType;
import rogo.sketch.core.shader.config.MacroTemplate;
import rogo.sketch.core.ui.control.ChoiceOptionSpec;
import rogo.sketch.core.ui.control.ChoicePresentation;
import rogo.sketch.core.ui.control.ChoiceSpec;
import rogo.sketch.core.ui.control.ControlSpec;
import rogo.sketch.core.ui.control.NumericSpec;
import rogo.sketch.core.util.KeyId;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

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
            if (json == null) {
                return null;
            }

            Map<String, MacroEntryDescriptor> entries = new LinkedHashMap<>();
            parseLegacyMacros(json, entries);
            parseDefinitions(json, entries);
            Set<String> requires = parseRequires(json);

            MacroTemplate template = new MacroTemplate(keyId, entries, requires);
            MacroContext.getInstance().registerMacroTemplate(keyId, template);
            return template;
        } catch (Exception e) {
            System.err.println("Failed to load macro template: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private void parseLegacyMacros(JsonObject json, Map<String, MacroEntryDescriptor> entries) {
        if (json.has("macros") && json.get("macros").isJsonObject()) {
            JsonObject macrosObj = json.getAsJsonObject("macros");
            for (Map.Entry<String, JsonElement> entry : macrosObj.entrySet()) {
                JsonElement value = entry.getValue();
                if (value.isJsonPrimitive()) {
                    entries.put(entry.getKey(), MacroEntryDescriptor.constantValue(entry.getKey(), value.getAsString()));
                }
            }
        }

        if (json.has("flags") && json.get("flags").isJsonArray()) {
            JsonArray flagsArray = json.getAsJsonArray("flags");
            for (JsonElement element : flagsArray) {
                if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                    entries.put(element.getAsString(), MacroEntryDescriptor.constantFlag(element.getAsString()));
                }
            }
        }
    }

    private void parseDefinitions(JsonObject json, Map<String, MacroEntryDescriptor> entries) {
        if (!json.has("definitions") || !json.get("definitions").isJsonArray()) {
            return;
        }
        JsonArray definitions = json.getAsJsonArray("definitions");
        for (JsonElement element : definitions) {
            if (!element.isJsonObject()) {
                continue;
            }
            MacroEntryDescriptor descriptor = parseDefinition(element.getAsJsonObject());
            if (descriptor != null) {
                entries.put(descriptor.name(), descriptor);
            }
        }
    }

    private MacroEntryDescriptor parseDefinition(JsonObject json) {
        if (!json.has("name")) {
            return null;
        }
        String name = json.get("name").getAsString();
        String typeName = json.has("type") ? json.get("type").getAsString() : "constant";
        boolean editable = json.has("editable") && json.get("editable").getAsBoolean();
        String value = json.has("value") && json.get("value").isJsonPrimitive() ? json.get("value").getAsString() : null;
        String displayKey = json.has("displayKey") ? json.get("displayKey").getAsString() : null;
        String summaryKey = json.has("summaryKey") ? json.get("summaryKey").getAsString() : null;
        String detailKey = json.has("detailKey") ? json.get("detailKey").getAsString() : null;

        ControlSpec controlSpec = null;
        MacroEntryType type = switch (typeName.toLowerCase()) {
            case "flag" -> MacroEntryType.FLAG;
            case "choice" -> MacroEntryType.CHOICE;
            case "value" -> MacroEntryType.VALUE;
            default -> MacroEntryType.CONSTANT;
        };

        if (type == MacroEntryType.CHOICE && json.has("options") && json.get("options").isJsonArray()) {
            JsonArray optionsArray = json.getAsJsonArray("options");
            java.util.List<ChoiceOptionSpec> options = new java.util.ArrayList<>();
            for (JsonElement optionElement : optionsArray) {
                if (!optionElement.isJsonObject()) {
                    continue;
                }
                JsonObject optionJson = optionElement.getAsJsonObject();
                if (!optionJson.has("value")) {
                    continue;
                }
                String optionValue = optionJson.get("value").getAsString();
                String optionDisplay = optionJson.has("displayKey") ? optionJson.get("displayKey").getAsString() : optionValue;
                String optionSummary = optionJson.has("summaryKey") ? optionJson.get("summaryKey").getAsString() : null;
                String optionDetail = optionJson.has("detailKey") ? optionJson.get("detailKey").getAsString() : null;
                options.add(new ChoiceOptionSpec(optionValue, optionDisplay, optionSummary, optionDetail));
            }
            ChoicePresentation presentation = ChoicePresentation.AUTO;
            if (json.has("presentation")) {
                presentation = ChoicePresentation.valueOf(json.get("presentation").getAsString().toUpperCase());
            }
            controlSpec = ControlSpec.choice(new ChoiceSpec(options, presentation));
        } else if ((type == MacroEntryType.VALUE || type == MacroEntryType.CONSTANT) && json.has("numeric") && json.get("numeric").isJsonObject()) {
            JsonObject numeric = json.getAsJsonObject("numeric");
            boolean integer = !numeric.has("kind") || "integer".equalsIgnoreCase(numeric.get("kind").getAsString());
            double minValue = numeric.has("min") ? numeric.get("min").getAsDouble() : 0.0D;
            double maxValue = numeric.has("max") ? numeric.get("max").getAsDouble() : 1.0D;
            double step = numeric.has("step") ? numeric.get("step").getAsDouble() : 1.0D;
            String format = numeric.has("format") ? numeric.get("format").getAsString() : (integer ? "%d" : "%.2f");
            NumericSpec numericSpec = integer
                    ? NumericSpec.integer((int) Math.round(minValue), (int) Math.round(maxValue), (int) Math.max(1, Math.round(step)), format)
                    : NumericSpec.floating(minValue, maxValue, step, format);
            controlSpec = editable ? ControlSpec.number(numericSpec) : null;
        }

        return new MacroEntryDescriptor(name, type, editable, value, displayKey, summaryKey, detailKey, controlSpec);
    }

    private Set<String> parseRequires(JsonObject json) {
        Set<String> requires = new LinkedHashSet<>();
        if (json.has("requires") && json.get("requires").isJsonArray()) {
            JsonArray requiresArray = json.getAsJsonArray("requires");
            for (JsonElement element : requiresArray) {
                if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                    requires.add(element.getAsString());
                }
            }
        }
        return requires;
    }
}
