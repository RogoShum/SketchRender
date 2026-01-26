package rogo.sketch.core.resource.loader;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import rogo.sketch.core.instance.FunctionGraphics;
import rogo.sketch.core.instance.StandardFunctionGraphics;
import rogo.sketch.core.pipeline.GraphicsPipeline;
import rogo.sketch.core.util.KeyId;

import java.io.InputStream;
import java.util.*;
import java.util.function.Function;

public class FunctionGraphicsLoader implements ResourceLoader<FunctionGraphics> {
    private final GraphicsPipeline<?> graphicsPipeline;

    public FunctionGraphicsLoader(GraphicsPipeline<?> graphicsPipeline) {
        this.graphicsPipeline = graphicsPipeline;
    }

    @Override
    public FunctionGraphics load(KeyId keyId, ResourceData data, Gson gson, Function<KeyId, Optional<InputStream>> resourceProvider) {
        try {
            String jsonData = data.getString();
            if (jsonData == null)
                return null;

            JsonObject json = gson.fromJson(jsonData, JsonObject.class);

            String stageId = json.has("stage") ? json.get("stage").getAsString() : null;
            if (stageId == null) {
                System.err.println("FunctionGraphics " + keyId + " missing 'stage' reference");
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

                    switch (operate) {
                        case "clear":
                            commandList.add(parseClearCommand(cmdJson));
                            break;
                        case "drawBuffers":
                            commandList.add(parseDrawBuffersCommand(cmdJson));
                            break;
                        case "genMipmap":
                            commandList.add(parseGenMipmapCommand(cmdJson));
                            break;
                    }
                }
            }

            graphicsCommands = commandList.toArray(new StandardFunctionGraphics.Command[0]);
            StandardFunctionGraphics graphics = new StandardFunctionGraphics(keyId, graphicsCommands);

            if (json.has("priority")) {
                graphics.setPriority(json.get("priority").getAsInt());
            }

            graphicsPipeline.addFunction(KeyId.of(stageId), graphics);
            return graphics;
        } catch (Exception e) {
            System.err.println("Failed to load FunctionGraphics: " + e.getMessage());
            return null;
        }
    }

    private StandardFunctionGraphics.Command parseClearCommand(JsonObject json) {
        String rtId = json.has("renderTarget") ? json.get("renderTarget").getAsString() : null;
        if (rtId == null)
            return null;

        String channels = json.has("clearChannels") ? json.get("clearChannels").getAsString() : "";
        boolean color = channels.contains("color");
        boolean depth = channels.contains("depth");

        float[] clearColor = new float[]{0, 0, 0, 0};
        if (json.has("color") && json.get("color").isJsonArray()) {
            JsonArray clrArr = json.getAsJsonArray("color");
            for (int i = 0; i < Math.min(4, clrArr.size()); i++) {
                clearColor[i] = clrArr.get(i).getAsFloat();
            }
        }

        float clearDepth = json.has("depth") ? json.get("depth").getAsFloat() : 1.0f;

        return new StandardFunctionGraphics.ClearCommand(KeyId.of(rtId), color, depth, clearColor, clearDepth);
    }

    private StandardFunctionGraphics.Command parseDrawBuffersCommand(JsonObject json) {
        if (!json.has("renderTarget"))
            return null;
        JsonObject rtJson = json.getAsJsonObject("renderTarget");
        String rtId = rtJson.has("id") ? rtJson.get("id").getAsString() : null;
        if (rtId == null)
            return null;

        Map<KeyId, Boolean> components = new HashMap<>();
        if (rtJson.has("colorComponents")) {
            JsonObject compJson = rtJson.getAsJsonObject("colorComponents");
            for (Map.Entry<String, JsonElement> entry : compJson.entrySet()) {
                components.put(KeyId.of(entry.getKey()), entry.getValue().getAsBoolean());
            }
        }

        return new StandardFunctionGraphics.DrawBuffersCommand(KeyId.of(rtId), components);
    }

    private StandardFunctionGraphics.Command parseGenMipmapCommand(JsonObject json) {
        if (!json.has("renderTarget"))
            return null;
        JsonObject rtJson = json.getAsJsonObject("renderTarget");
        String rtId = rtJson.has("id") ? rtJson.get("id").getAsString() : null;
        if (rtId == null)
            return null;

        Map<KeyId, Boolean> components = new HashMap<>();
        if (rtJson.has("colorComponents")) {
            JsonObject compJson = rtJson.getAsJsonObject("colorComponents");
            for (Map.Entry<String, JsonElement> entry : compJson.entrySet()) {
                components.put(KeyId.of(entry.getKey()), entry.getValue().getAsBoolean());
            }
        }

        return new StandardFunctionGraphics.GenMipmapCommand(KeyId.of(rtId), components);
    }
}