package rogo.sketch.vanilla.resource;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import rogo.sketch.render.resource.GraphicsResourceManager;
import rogo.sketch.render.resource.ResourceTypes;
import rogo.sketch.util.Identifier;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public class RenderResourceManager implements ResourceManagerReloadListener {
    private static final Gson GSON = new Gson();
    private static final Identifier[] SUB_DIRS = {
            ResourceTypes.SHADER_PROGRAM,
            ResourceTypes.TEXTURE,
            ResourceTypes.RENDER_TARGET,
            ResourceTypes.PARTIAL_RENDER_SETTING
    };

    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        GraphicsResourceManager.getInstance().clearAllResources();
        Function<Identifier, Optional<BufferedReader>> subResourceProvider = (identifier) -> {
            try {
                ResourceLocation loc = new ResourceLocation(identifier.toString());
                BufferedReader reader = resourceManager.openAsReader(loc);
                return Optional.of(reader);
            } catch (Exception e) {
                return Optional.empty();
            }
        };

        GraphicsResourceManager.getInstance().setSubResourceProvider(subResourceProvider);
        scanAndLoad(resourceManager);
    }

    private void scanAndLoad(ResourceManager resourceManager) {
        for (Identifier subDir : SUB_DIRS) {
            String pathPrefix = "render/" + subDir;

            Map<ResourceLocation, Resource> found = resourceManager.listResources(
                    pathPrefix,
                    loc -> loc.getPath().endsWith(".json")
            );

            for (Map.Entry<ResourceLocation, Resource> entry : found.entrySet()) {
                ResourceLocation id = entry.getKey();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(entry.getValue().open()))) {
                    JsonObject json = GSON.fromJson(reader, JsonObject.class);
                    String identifier = getIdentifierWithoutExtension(id);
                    handleJsonResource(subDir, Identifier.of(identifier), json);
                } catch (IOException | JsonParseException e) {
                    System.err.println("Failed to load JSON " + id + ": " + e.getMessage());
                }
            }
        }
    }

    private void handleJsonResource(Identifier type, Identifier identifier, JsonObject json) {
        GraphicsResourceManager.getInstance().registerJson(type, identifier, json.toString());
    }

    public static String getIdentifierWithoutExtension(ResourceLocation loc) {
        String path = loc.getPath();

        int lastSlash = path.lastIndexOf('/');
        String fileName = (lastSlash >= 0) ? path.substring(lastSlash + 1) : path;

        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0) {
            fileName = fileName.substring(0, lastDot);
        }
        return loc.getNamespace() + ":" + fileName;
    }
}