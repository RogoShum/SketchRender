package rogo.sketch.render.shader.preprocessor;

import rogo.sketch.util.Identifier;

import java.util.List;
import java.util.Set;

/**
 * Result of shader preprocessing containing the processed source and metadata
 */
public record PreprocessorResult(
        String processedSource,
        Set<Identifier> importedFiles,
        List<String> warnings,
        int finalGlslVersion
) {
    
    /**
     * Create a simple result with just processed source
     */
    public static PreprocessorResult simple(String processedSource) {
        return new PreprocessorResult(processedSource, Set.of(), List.of(), 0);
    }
    
    /**
     * Check if there were any warnings during preprocessing
     */
    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }
    
    /**
     * Get a formatted string of all warnings
     */
    public String getWarningsString() {
        if (warnings.isEmpty()) {
            return "";
        }
        return String.join("\n", warnings);
    }
}
