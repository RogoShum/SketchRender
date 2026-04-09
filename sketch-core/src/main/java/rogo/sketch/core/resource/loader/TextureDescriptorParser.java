package rogo.sketch.core.resource.loader;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import rogo.sketch.core.resource.descriptor.ImageFormat;
import rogo.sketch.core.resource.descriptor.ImageUsage;
import rogo.sketch.core.resource.descriptor.ResolvedImageResource;
import rogo.sketch.core.resource.descriptor.SamplerFilter;
import rogo.sketch.core.resource.descriptor.SamplerWrap;
import rogo.sketch.core.util.KeyId;

import java.util.EnumSet;
import java.util.Locale;

public final class TextureDescriptorParser {
    private TextureDescriptorParser() {
    }

    public static ResolvedImageResource parse(KeyId identifier, JsonObject json, int width, int height, String sourcePathHint) {
        ImageFormat format = json.has("format")
                ? ImageFormat.fromString(json.get("format").getAsString())
                : ImageFormat.RGBA8_UNORM;
        boolean renderTarget = json.has("isRenderTarget") && json.get("isRenderTarget").getAsBoolean();
        boolean mipmaps = json.has("mipmaps") && json.get("mipmaps").getAsBoolean();
        int mipLevels = json.has("mipLevels") ? Math.max(1, json.get("mipLevels").getAsInt()) : 1;
        SamplerFilter minFilter = readFilter(json, "minFilter", json.has("filter") ? json.get("filter").getAsString() : "LINEAR");
        SamplerFilter magFilter = readFilter(json, "magFilter", json.has("filter") ? json.get("filter").getAsString() : "LINEAR");
        SamplerFilter mipmapFilter = mipmaps
                ? readOptionalFilter(json, json.has("mipmapFilter") ? "mipmapFilter" : (json.has("mipmapFormat") ? "mipmapFormat" : null))
                : null;
        SamplerWrap wrapS = readWrap(json, "wrapS", json.has("wrap") ? json.get("wrap").getAsString() : "REPEAT");
        SamplerWrap wrapT = readWrap(json, "wrapT", json.has("wrap") ? json.get("wrap").getAsString() : "REPEAT");

        EnumSet<ImageUsage> usages = parseUsages(json);
        if (usages.isEmpty()) {
            if (renderTarget) {
                usages.add(format.isDepthFormat() ? ImageUsage.DEPTH_ATTACHMENT : ImageUsage.COLOR_ATTACHMENT);
                usages.add(ImageUsage.SAMPLED);
                usages.add(ImageUsage.TRANSFER_SRC);
                usages.add(ImageUsage.TRANSFER_DST);
            } else {
                usages.add(ImageUsage.SAMPLED);
                usages.add(ImageUsage.TRANSFER_DST);
                if (mipmaps) {
                    usages.add(ImageUsage.TRANSFER_SRC);
                }
            }
        }

        String sourcePath = null;
        if (sourcePathHint != null && !sourcePathHint.isBlank()) {
            sourcePath = sourcePathHint;
        } else if (json.has("imagePath")) {
            sourcePath = json.get("imagePath").getAsString();
        } else if (json.has("resourceLocation")) {
            sourcePath = json.get("resourceLocation").getAsString();
        } else if (json.has("mcResourceLocation")) {
            sourcePath = json.get("mcResourceLocation").getAsString();
        }

        return new ResolvedImageResource(
                identifier,
                Math.max(1, width),
                Math.max(1, height),
                mipLevels,
                format,
                usages,
                minFilter,
                magFilter,
                mipmapFilter,
                wrapS,
                wrapT,
                sourcePath);
    }

    private static EnumSet<ImageUsage> parseUsages(JsonObject json) {
        EnumSet<ImageUsage> usages = EnumSet.noneOf(ImageUsage.class);
        if (!json.has("usages") || !json.get("usages").isJsonArray()) {
            return usages;
        }
        JsonArray array = json.getAsJsonArray("usages");
        for (int i = 0; i < array.size(); i++) {
            usages.add(parseUsage(array.get(i).getAsString()));
        }
        return usages;
    }

    private static ImageUsage parseUsage(String value) {
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "SAMPLED" -> ImageUsage.SAMPLED;
            case "STORAGE" -> ImageUsage.STORAGE;
            case "COLOR_ATTACHMENT" -> ImageUsage.COLOR_ATTACHMENT;
            case "DEPTH_ATTACHMENT" -> ImageUsage.DEPTH_ATTACHMENT;
            case "TRANSFER_SRC" -> ImageUsage.TRANSFER_SRC;
            case "TRANSFER_DST" -> ImageUsage.TRANSFER_DST;
            default -> throw new IllegalArgumentException("Unsupported image usage: " + value);
        };
    }

    private static SamplerFilter readFilter(JsonObject json, String fieldName, String fallback) {
        return json.has(fieldName)
                ? SamplerFilter.fromString(json.get(fieldName).getAsString())
                : SamplerFilter.fromString(fallback);
    }

    private static SamplerFilter readOptionalFilter(JsonObject json, String fieldName) {
        if (fieldName == null || !json.has(fieldName)) {
            return null;
        }
        return SamplerFilter.fromString(json.get(fieldName).getAsString());
    }

    private static SamplerWrap readWrap(JsonObject json, String fieldName, String fallback) {
        return json.has(fieldName)
                ? SamplerWrap.fromString(json.get(fieldName).getAsString())
                : SamplerWrap.fromString(fallback);
    }
}

