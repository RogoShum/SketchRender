package rogo.sketch.render.shader.preprocessor;

import rogo.sketch.util.Identifier;

import java.io.BufferedReader;
import java.util.Optional;
import java.util.function.Function;

/**
 * Shader-specific resource provider for preprocessing operations
 */
public interface ShaderResourceProvider {

    /**
     * Load shader source by identifier
     *
     * @param identifier The resource identifier
     * @return The shader source code, or empty if not found
     */
    Optional<String> loadShaderSource(Identifier identifier);

    /**
     * Check if a shader resource exists
     *
     * @param identifier The resource identifier
     * @return true if the resource exists
     */
    default boolean exists(Identifier identifier) {
        return loadShaderSource(identifier).isPresent();
    }

    /**
     * Resolve a relative import path against a base shader identifier
     *
     * @param baseShader The identifier of the shader doing the import
     * @param importPath The import path (relative or absolute)
     * @return The resolved identifier
     */
    default Identifier resolveImport(Identifier baseShader, String importPath) {
        // Handle both relative and absolute imports
        if (importPath.contains(":")) {
            // Absolute path with namespace
            return Identifier.of(importPath);
        } else {
            // Relative path - use the same namespace as the base shader
            String baseStr = baseShader.toString();
            if (baseStr.contains(":")) {
                String[] parts = baseStr.split(":", 2);
                return Identifier.of(parts[0] + ":" + importPath);
            } else {
                return Identifier.of("minecraft:" + importPath);
            }
        }
    }

    /**
     * Create a ShaderResourceProvider from a generic resource provider
     * This adapter handles the path resolution for shader imports
     */
    static ShaderResourceProvider fromGenericProvider(Function<Identifier, Optional<BufferedReader>> genericProvider) {
        return new ShaderResourceProvider() {
            @Override
            public Optional<String> loadShaderSource(Identifier identifier) {
                // Try render/shader_include path first
                Optional<String> result = loadFromPath(identifier, "render/resource/shader_include/");
                if (result.isPresent()) {
                    return result;
                }

                // Then try shaders/include path as fallback
                return loadFromPath(identifier, "shaders/include/");
            }

            private Optional<String> loadFromPath(Identifier identifier, String pathPrefix) {
                Identifier resourcePath = addPathPrefix(identifier, pathPrefix);

                Optional<BufferedReader> reader = genericProvider.apply(resourcePath);
                if (reader.isPresent()) {
                    try (BufferedReader br = reader.get()) {
                        return Optional.of(br.lines().collect(java.util.stream.Collectors.joining("\n")));
                    } catch (Exception e) {
                        return Optional.empty();
                    }
                }
                return Optional.empty();
            }

            private Identifier addPathPrefix(Identifier identifier, String pathPrefix) {
                String identifierStr = identifier.toString();
                if (identifierStr.contains(":")) {
                    String[] parts = identifierStr.split(":", 2);
                    return Identifier.of(parts[0] + ":" + pathPrefix + parts[1]);
                } else {
                    return Identifier.of("minecraft:" + pathPrefix + identifierStr);
                }
            }
        };
    }
}