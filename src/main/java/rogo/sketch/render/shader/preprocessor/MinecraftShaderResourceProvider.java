package rogo.sketch.render.shader.preprocessor;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import rogo.sketch.util.Identifier;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Shader resource provider that integrates with Minecraft's resource system
 */
public class MinecraftShaderResourceProvider implements ShaderResourceProvider {
    
    private final ResourceProvider resourceProvider;
    private final String basePath;
    
    public MinecraftShaderResourceProvider(ResourceProvider resourceProvider) {
        this(resourceProvider, "shaders/");
    }
    
    public MinecraftShaderResourceProvider(ResourceProvider resourceProvider, String basePath) {
        this.resourceProvider = resourceProvider;
        this.basePath = basePath.endsWith("/") ? basePath : basePath + "/";
    }
    
    @Override
    public Optional<String> loadShaderSource(Identifier identifier) {
        try {
            ResourceLocation location = parseIdentifier(identifier);
            BufferedReader reader = resourceProvider.openAsReader(location);
            String content = reader.lines().collect(Collectors.joining("\n"));
            return Optional.of(content);
        } catch (IOException e) {
            return Optional.empty();
        }
    }
    
    @Override
    public Identifier resolveImport(Identifier baseShader, String importPath) {
        // Handle absolute imports (starting with namespace:)
        if (importPath.contains(":")) {
            return Identifier.of(importPath);
        }
        
        // Handle relative imports
        String baseString = baseShader.toString();
        if (baseString.contains(":")) {
            String[] baseParts = baseString.split(":", 2);
            String namespace = baseParts[0];
            String basePath = baseParts[1];
            
            // Resolve relative path
            if (importPath.startsWith("./")) {
                // Same directory
                String directory = getDirectory(basePath);
                return Identifier.of(namespace + ":" + directory + importPath.substring(2));
            } else if (importPath.startsWith("../")) {
                // Parent directory
                String directory = getParentDirectory(basePath);
                return Identifier.of(namespace + ":" + directory + importPath.substring(3));
            } else {
                // Relative to base directory
                String directory = getDirectory(basePath);
                return Identifier.of(namespace + ":" + directory + importPath);
            }
        }
        
        // Default: treat as absolute
        return Identifier.of(importPath);
    }
    
    private ResourceLocation parseIdentifier(Identifier identifier) {
        String idString = identifier.toString();
        
        if (idString.contains(":")) {
            String[] parts = idString.split(":", 2);
            String namespace = parts[0];
            String path = parts[1];
            
            // Add base path if not already present
            if (!path.startsWith(basePath)) {
                path = basePath + path;
            }
            
            // Add .glsl extension if no extension present
            if (!path.contains(".")) {
                path += ".glsl";
            }
            
            return new ResourceLocation(namespace, path);
        } else {
            // Default namespace
            String path = basePath + idString;
            if (!path.contains(".")) {
                path += ".glsl";
            }
            return new ResourceLocation("sketch", path);
        }
    }
    
    private String getDirectory(String path) {
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash >= 0) {
            return path.substring(0, lastSlash + 1);
        }
        return "";
    }
    
    private String getParentDirectory(String path) {
        String directory = getDirectory(path);
        if (directory.length() > 1) {
            // Remove trailing slash and find parent
            directory = directory.substring(0, directory.length() - 1);
            int lastSlash = directory.lastIndexOf('/');
            if (lastSlash >= 0) {
                return directory.substring(0, lastSlash + 1);
            }
        }
        return "";
    }
}
