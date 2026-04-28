package rogo.sketch.core.instance.function;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import rogo.sketch.core.graphics.ecs.FunctionCommands;
import rogo.sketch.core.util.KeyId;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class FunctionCommandCodecRegistry {
    private static final FunctionCommandCodecRegistry STANDARD = createStandard();

    private final Map<String, FunctionCommandCodec> codecs = new ConcurrentHashMap<>();

    public static FunctionCommandCodecRegistry standard() {
        return STANDARD;
    }

    public FunctionCommandCodecRegistry register(String operation, FunctionCommandCodec codec) {
        if (operation == null || operation.isBlank() || codec == null) {
            return this;
        }
        codecs.put(operation, codec);
        return this;
    }

    public FunctionCommands.Command decode(String operation, JsonObject json) {
        if (operation == null || operation.isBlank() || json == null) {
            return null;
        }
        FunctionCommandCodec codec = codecs.get(operation);
        return codec != null ? codec.decode(json) : null;
    }

    private static FunctionCommandCodecRegistry createStandard() {
        return new FunctionCommandCodecRegistry()
                .register("clear", FunctionCommandCodecRegistry::decodeClear)
                .register("clearRenderTarget", FunctionCommandCodecRegistry::decodeClear)
                .register("copyTexture", FunctionCommandCodecRegistry::decodeCopyTexture)
                .register("genMipmap", FunctionCommandCodecRegistry::decodeGenMipmap);
    }

    private static FunctionCommands.Command decodeClear(JsonObject json) {
        String rtId = json.has("renderTarget") ? json.get("renderTarget").getAsString() : null;
        if (rtId == null || rtId.isBlank()) {
            return null;
        }

        String channels = json.has("clearChannels") ? json.get("clearChannels").getAsString() : "";
        boolean clearColor = channels.contains("color");
        boolean clearDepth = channels.contains("depth");

        float[] clearColorValue = new float[]{0f, 0f, 0f, 0f};
        if (json.has("color") && json.get("color").isJsonArray()) {
            JsonArray colorArray = json.getAsJsonArray("color");
            for (int i = 0; i < Math.min(4, colorArray.size()); i++) {
                clearColorValue[i] = colorArray.get(i).getAsFloat();
            }
        }

        float clearDepthValue = json.has("depth") ? json.get("depth").getAsFloat() : 1.0f;
        boolean[] colorMask = null;
        if (json.has("colorMaskState") && json.get("colorMaskState").isJsonObject()) {
            JsonObject colorMaskStateJson = json.getAsJsonObject("colorMaskState");
            colorMask = new boolean[]{
                    colorMaskStateJson.has("red") && colorMaskStateJson.get("red").getAsBoolean(),
                    colorMaskStateJson.has("green") && colorMaskStateJson.get("green").getAsBoolean(),
                    colorMaskStateJson.has("blue") && colorMaskStateJson.get("blue").getAsBoolean(),
                    colorMaskStateJson.has("alpha") && colorMaskStateJson.get("alpha").getAsBoolean()
            };
        }

        boolean restorePreviousRenderTarget = !json.has("restorePreviousRenderTarget")
                || json.get("restorePreviousRenderTarget").getAsBoolean();

        return new FunctionCommands.ClearCommand(
                KeyId.of(rtId),
                decodeColorAttachments(json),
                clearColor,
                clearDepth,
                clearColorValue,
                clearDepthValue,
                colorMask,
                restorePreviousRenderTarget);
    }

    private static FunctionCommands.Command decodeGenMipmap(JsonObject json) {
        if (!json.has("texture")) {
            return null;
        }
        String textureId = json.get("texture").getAsString();
        return textureId == null || textureId.isBlank()
                ? null
                : new FunctionCommands.GenMipmapCommand(KeyId.of(textureId));
    }

    private static FunctionCommands.Command decodeCopyTexture(JsonObject json) {
        if (!json.has("sourceTexture") || !json.has("destinationTexture")) {
            return null;
        }
        String sourceTextureId = json.get("sourceTexture").getAsString();
        String destinationTextureId = json.get("destinationTexture").getAsString();
        if (sourceTextureId == null || sourceTextureId.isBlank()
                || destinationTextureId == null || destinationTextureId.isBlank()) {
            return null;
        }
        int width = json.has("width") ? json.get("width").getAsInt() : 0;
        int height = json.has("height") ? json.get("height").getAsInt() : 0;
        boolean depthCopy = !json.has("depthCopy") || json.get("depthCopy").getAsBoolean();
        return new FunctionCommands.CopyTextureCommand(
                KeyId.of(sourceTextureId),
                KeyId.of(destinationTextureId),
                width,
                height,
                depthCopy);
    }

    private static List<Object> decodeColorAttachments(JsonObject json) {
        JsonElement attachmentsElement = json.get("colorAttachments");
        if (attachmentsElement == null && json.has("renderTarget") && json.get("renderTarget").isJsonObject()) {
            attachmentsElement = json.getAsJsonObject("renderTarget").get("colorComponents");
        }
        if (attachmentsElement == null || !attachmentsElement.isJsonArray()) {
            return List.of();
        }

        List<Object> attachments = new ArrayList<>();
        for (JsonElement element : attachmentsElement.getAsJsonArray()) {
            if (element == null || element.isJsonNull()) {
                continue;
            }
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
                attachments.add(element.getAsInt());
            } else {
                attachments.add(KeyId.of(element.getAsString()));
            }
        }
        return List.copyOf(attachments);
    }

    @FunctionalInterface
    public interface FunctionCommandCodec {
        FunctionCommands.Command decode(JsonObject json);
    }
}

