package rogo.sketch.core.resource.loader;

import com.google.gson.*;
import rogo.sketch.core.instance.FunctionGraphics;
import rogo.sketch.core.instance.StandardFunctionGraphics;
import rogo.sketch.core.pipeline.GraphicsPipeline;
import rogo.sketch.core.resource.ResourceTypes;
import rogo.sketch.core.state.gl.ColorMaskState;
import rogo.sketch.core.util.KeyId;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public class FunctionGraphicsLoader implements ResourceLoader<FunctionGraphics> {
    private final GraphicsPipeline<?> graphicsPipeline;
    private final Gson internalGson = new GsonBuilder()
            .registerTypeAdapter(KeyId.class, new KeyId.GsonAdapter())
            .setPrettyPrinting()
            .create();

    public FunctionGraphicsLoader(GraphicsPipeline<?> graphicsPipeline) {
        this.graphicsPipeline = graphicsPipeline;
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
            Gson gson = context.getGson();
            if (json == null)
                return null;

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
                        case "clearRenderTarget":
                            commandList.add(parseClearCommand(cmdJson));
                            break;
                        case "drawBuffers":
                            commandList.add(parseDrawBuffersCommand(cmdJson));
                            break;
                        case "genMipmap":
                            commandList.add(parseGenMipmapCommand(cmdJson));
                            break;
                        case "bindRenderTarget":
                            commandList.add(parseBindingRenderTargetCommand(cmdJson));
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
        ColorMaskState colorMaskState = null;
        if (json.has("colorMaskState") && json.get("colorMaskState").isJsonObject()) {
            JsonObject colorMaskStateJson = json.getAsJsonObject("colorMaskState");
            colorMaskState = new ColorMaskState();
            colorMaskState.deserializeFromJson(colorMaskStateJson, internalGson);
        }

        return new StandardFunctionGraphics.ClearCommand(KeyId.of(rtId), color, depth, clearColor, clearDepth, colorMaskState);
    }

    private StandardFunctionGraphics.Command parseDrawBuffersCommand(JsonObject json) {
        if (!json.has("renderTarget"))
            return null;
        JsonObject rtJson = json.getAsJsonObject("renderTarget");
        String rtId = rtJson.has("id") ? rtJson.get("id").getAsString() : null;
        if (rtId == null)
            return null;

        List<Object> components = null;

        if (rtJson.has("colorComponents")) {
            components = new ArrayList<>();
            JsonArray compArray = rtJson.getAsJsonArray("colorComponents");

            for (JsonElement element : compArray) {
                if (element.getAsJsonPrimitive().isNumber()) {
                    components.add(element.getAsInt());
                } else {
                    components.add(KeyId.of(element.getAsString()));
                }
            }
        }

        return new StandardFunctionGraphics.DrawBuffersCommand(KeyId.of(rtId), components);
    }

    private StandardFunctionGraphics.Command parseGenMipmapCommand(JsonObject json) {
        if (!json.has("texture"))
            return null;

        String textureId = json.get("texture").getAsString();
        return new StandardFunctionGraphics.GenMipmapCommand(KeyId.of(textureId));
    }

    private StandardFunctionGraphics.Command parseBindingRenderTargetCommand(JsonObject json) {
        if (!json.has("renderTarget"))
            return null;
        String rtId = json.get("renderTarget").getAsString();

        if (rtId == null)
            return null;

        return new StandardFunctionGraphics.BindRenderTargetCommand(KeyId.of(rtId));
    }
}