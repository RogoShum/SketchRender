package rogo.sketch.core.shader.preprocessor;

import rogo.sketch.core.util.KeyId;

import java.util.*;

/**
 * Interface for shader preprocessing operations including imports and macro handling
 */
public interface ShaderPreprocessor {
    
    /**
     * Process a shader source with imports and macros
     * 
     * @param source The raw shader source code
     * @param shaderKeyId Identifier for the shader being processed
     * @param macros Map of macro definitions to apply
     * @return Processed shader source code
     * @throws ShaderPreprocessorException if preprocessing fails
     */
    PreprocessorResult process(String source, KeyId shaderKeyId, Map<String, String> macros)
            throws ShaderPreprocessorException;
    
    /**
     * Process shader source with default empty macros
     */
    default PreprocessorResult process(String source, KeyId shaderKeyId)
            throws ShaderPreprocessorException {
        return process(source, shaderKeyId, Collections.emptyMap());
    }
    
    /**
     * Set the resource provider for loading imported files
     */
    void setResourceProvider(ShaderResourceProvider resourceProvider);
    
    /**
     * Get all imported file identifiers from the last processing operation
     */
    Set<KeyId> getLastImportedFiles();
    
    /**
     * Clear any cached data
     */
    void clearCache();
}