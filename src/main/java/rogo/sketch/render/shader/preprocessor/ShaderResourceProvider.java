package rogo.sketch.render.shader.preprocessor;

import rogo.sketch.util.Identifier;

import java.util.Optional;

/**
 * Interface for providing shader resources during preprocessing
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
        // Default implementation treats all imports as absolute
        return Identifier.of(importPath);
    }
}
