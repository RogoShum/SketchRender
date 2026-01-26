package rogo.sketch.core.shader.preprocessor;

import rogo.sketch.core.util.KeyId;

import java.util.*;

/**
 * Result of shader preprocessing containing the processed source and metadata
 */
public class PreprocessorResult {
    private final String processedSource;
    private final Set<KeyId> importedFiles;
    private final List<String> warnings;
    private final int finalGlslVersion;
    
    public PreprocessorResult(String processedSource, Set<KeyId> importedFiles,
                            List<String> warnings, int finalGlslVersion) {
        this.processedSource = processedSource;
        this.importedFiles = importedFiles;
        this.warnings = warnings;
        this.finalGlslVersion = finalGlslVersion;
    }
    
    public String processedSource() {
        return processedSource;
    }
    
    public Set<KeyId> importedFiles() {
        return importedFiles;
    }
    
    public List<String> warnings() {
        return warnings;
    }
    
    public int finalGlslVersion() {
        return finalGlslVersion;
    }
    
    /**
     * Create a simple result with just processed source
     */
    public static PreprocessorResult simple(String processedSource) {
        return new PreprocessorResult(processedSource, new HashSet<>(), new ArrayList<>(), 0);
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
