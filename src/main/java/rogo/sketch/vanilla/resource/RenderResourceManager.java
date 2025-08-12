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

public class RenderResourceManager implements ResourceManagerReloadListener {
    private static final Gson GSON = new Gson();
    private static final Identifier[] SUB_DIRS = {
            ResourceTypes.SHADER_PROGRAM,
            ResourceTypes.TEXTURE,
            ResourceTypes.RENDER_TARGET,
            ResourceTypes.RENDER_SETTING
    };

    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        GraphicsResourceManager.getInstance().clearJsonResources();
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
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(entry.getValue().open()))) {

                    JsonObject json = GSON.fromJson(reader, JsonObject.class);
                    handleJsonResource(subDir, id, json, resourceManager);

                } catch (IOException | JsonParseException e) {
                    System.err.println("Failed to load JSON " + id + ": " + e.getMessage());
                }
            }
        }
    }

    private void handleJsonResource(Identifier type, ResourceLocation id, JsonObject json, ResourceManager resourceManager) {
        if (type.equals(ResourceTypes.SHADER_PROGRAM)) {
            GraphicsResourceManager.getInstance().registerJson(type, Identifier.valueOf(id), json.toString(), (identifier) -> {
                ResourceLocation loc = new ResourceLocation(identifier.toString());
                loc = new ResourceLocation(loc.getNamespace(), "render/shader_type/" + loc.getPath());
                try {
                    BufferedReader reader = resourceManager.openAsReader(loc);
                    return Optional.of(reader);
                } catch (Throwable t) {
                    t.printStackTrace();

                    return Optional.empty();
                }
            });
        } else {
            GraphicsResourceManager.getInstance().registerJson(type, Identifier.valueOf(id), json.getAsString());
        }
    }
}