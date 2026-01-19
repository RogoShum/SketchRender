package rogo.sketch.render.shader.preprocessor;

import rogo.sketch.util.KeyId;

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
     * @param keyId The resource identifier
     * @return The shader source code, or empty if not found
     */
    Optional<String> loadShaderSource(KeyId keyId);

    /**
     * Check if a shader resource exists
     *
     * @param keyId The resource identifier
     * @return true if the resource exists
     */
    default boolean exists(KeyId keyId) {
        return loadShaderSource(keyId).isPresent();
    }

    /**
     * Resolve a relative import path against a base shader identifier
     *
     * @param baseShader The identifier of the shader doing the import
     * @param importPath The import path (relative or absolute)
     * @return The resolved identifier
     */
    default KeyId resolveImport(KeyId baseShader, String importPath) {
        // Handle both relative and absolute imports
        if (importPath.contains(":")) {
            // Absolute path with namespace
            return KeyId.of(importPath);
        } else {
            // Relative path - use the same namespace as the base shader
            String baseStr = baseShader.toString();
            if (baseStr.contains(":")) {
                String[] parts = baseStr.split(":", 2);
                return KeyId.of(parts[0] + ":" + importPath);
            } else {
                return KeyId.of("minecraft:" + importPath);
            }
        }
    }

    /**
     * Create a ShaderResourceProvider from a generic resource provider
     * This adapter handles the path resolution for shader imports
     */
    static ShaderResourceProvider fromGenericProvider(Function<KeyId, Optional<BufferedReader>> genericProvider) {
        return new ShaderResourceProvider() {
            @Override
            public Optional<String> loadShaderSource(KeyId keyId) {
                // Try render/shader_include path first
                Optional<String> result = loadFromPath(keyId, "render/resource/shader_include/");
                if (result.isPresent()) {
                    return result;
                }

                // Then try shaders/include path as fallback
                return loadFromPath(keyId, "shaders/include/");
            }

            private Optional<String> loadFromPath(KeyId keyId, String pathPrefix) {
                KeyId resourcePath = addPathPrefix(keyId, pathPrefix);

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

            private KeyId addPathPrefix(KeyId keyId, String pathPrefix) {
                String identifierStr = keyId.toString();
                if (identifierStr.contains(":")) {
                    String[] parts = identifierStr.split(":", 2);
                    return KeyId.of(parts[0] + ":" + pathPrefix + parts[1]);
                } else {
                    return KeyId.of("minecraft:" + pathPrefix + identifierStr);
                }
            }
        };
    }
}