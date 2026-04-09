package rogo.sketch.core.resource.loader;

import com.google.gson.*;
import rogo.sketch.core.instance.FunctionGraphics;
import rogo.sketch.core.instance.StandardFunctionGraphics;
import rogo.sketch.core.instance.function.FunctionCommandCodecRegistry;
import rogo.sketch.core.pipeline.module.diagnostic.SketchDiagnostics;
import rogo.sketch.core.resource.ResourceTypes;
import rogo.sketch.core.util.KeyId;

import java.util.ArrayList;
import java.util.List;

public class FunctionGraphicsLoader implements ResourceLoader<FunctionGraphics> {
    private final FunctionCommandCodecRegistry codecRegistry;

    public FunctionGraphicsLoader() {
        this(FunctionCommandCodecRegistry.standard());
    }

    public FunctionGraphicsLoader(FunctionCommandCodecRegistry codecRegistry) {
        this.codecRegistry = codecRegistry;
    }

    @Override
    public KeyId getResourceType() {
        return ResourceTypes.FUNCTION;
    }

    @Override
    public FunctionGraphics load(ResourceLoadContext context) {
        try {
            KeyId keyId = context.getResourceId();
            JsonObject json = context.getJson();
            if (json == null)
                return null;

            String stageId = json.has("stage") ? json.get("stage").getAsString() : null;
            if (stageId == null) {
                SketchDiagnostics.get().warn("function-loader", "FunctionGraphics " + keyId + " missing 'stage' reference");
                return null;
            }

            StandardFunctionGraphics.Command[] graphicsCommands = null;
            List<StandardFunctionGraphics.Command> commandList = new ArrayList<>();

            if (json.has("command") && json.get("command").isJsonArray()) {
                JsonArray commands = json.getAsJsonArray("command");
                for (JsonElement cmdElem : commands) {
                    if (!cmdElem.isJsonObject())
                        continue;
                    JsonObject cmdJson = cmdElem.getAsJsonObject();
                    String operate = cmdJson.has("operate") ? cmdJson.get("operate").getAsString() : "";
                    StandardFunctionGraphics.Command command = codecRegistry.decode(operate, cmdJson);
                    if (command != null) {
                        commandList.add(command);
                    }
                }
            }

            graphicsCommands = commandList.toArray(new StandardFunctionGraphics.Command[0]);
            StandardFunctionGraphics graphics = new StandardFunctionGraphics(keyId, KeyId.of(stageId), graphicsCommands);

            if (json.has("priority")) {
                graphics.setPriority(json.get("priority").getAsInt());
            }
            return graphics;
        } catch (Exception e) {
            SketchDiagnostics.get().error("function-loader", "Failed to load FunctionGraphics", e);
            return null;
        }
    }
}

