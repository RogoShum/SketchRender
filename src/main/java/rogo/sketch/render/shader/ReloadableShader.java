package rogo.sketch.render.shader;

import rogo.sketch.render.resource.ReloadableResourceSupport;
import rogo.sketch.render.shader.config.ShaderConfiguration;
import rogo.sketch.render.shader.config.ShaderConfigurationManager;
import rogo.sketch.render.shader.preprocessor.PreprocessorResult;
import rogo.sketch.render.shader.preprocessor.ShaderPreprocessor;
import rogo.sketch.render.shader.preprocessor.ShaderPreprocessorException;
import rogo.sketch.render.shader.preprocessor.ShaderResourceProvider;
import rogo.sketch.util.Identifier;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.*;
import java.util.function.Function;

/**
 * Base class for reloadable shaders that support macro-based recompilation
 * Extends the generic reloadable resource support with shader-specific functionality
 */
public abstract class ReloadableShader extends ReloadableResourceSupport<Shader> {

    protected final Map<ShaderType, String> originalSources;
    protected final ShaderPreprocessor preprocessor;

    private PreprocessorResult lastPreprocessingResult;
    private int lastConfigurationHash = -1;

    public ReloadableShader(Identifier identifier,
                            Map<ShaderType, String> originalSources,
                            ShaderPreprocessor preprocessor,
                            Function<Identifier, Optional<BufferedReader>> resourceProvider) {
        super(identifier, resourceProvider);
        this.originalSources = new HashMap<>(originalSources);
        this.preprocessor = preprocessor;

        // Register for configuration changes
        ShaderConfigurationManager.getInstance()
                .addConfigurationListener(identifier, this::onConfigurationChanged);
    }

    @Override
    protected ResourceLoadResult<Shader> performReload() throws IOException {
        // Preprocess sources with current configuration
        Map<ShaderType, String> processedSources = preprocessSources();

        // Create the shader instance
        Shader shader = createShaderInstance(processedSources);

        // Extract dependencies from preprocessing result
        Set<Identifier> dependencies = lastPreprocessingResult != null ?
                lastPreprocessingResult.importedFiles() : Collections.emptySet();

        return ResourceLoadResult.of(shader, dependencies);
    }

    /**
     * Preprocess all shader sources with current configuration
     */
    protected Map<ShaderType, String> preprocessSources() throws IOException {
        try {
            // Set up the resource provider for the preprocessor
            if (preprocessor != null && resourceProvider != null) {
                // Create a ShaderResourceProvider adapter
                ShaderResourceProvider adapter = ShaderResourceProvider.fromGenericProvider(resourceProvider);
                preprocessor.setResourceProvider(adapter);
            }

            ShaderConfiguration config = ShaderConfigurationManager.getInstance()
                    .getConfiguration(resourceIdentifier);
            Map<String, String> macros = new HashMap<>(config.getMacros());

            Map<ShaderType, String> processedSources = new HashMap<>();

            for (Map.Entry<ShaderType, String> entry : originalSources.entrySet()) {
                PreprocessorResult result = preprocessor.process(
                        entry.getValue(),
                        resourceIdentifier,
                        macros
                );
                processedSources.put(entry.getKey(), result.processedSource());

                // Store the result from the main shader type for dependency tracking
                if (lastPreprocessingResult == null || entry.getKey() == getMainShaderType()) {
                    lastPreprocessingResult = result;
                }
            }

            // Update configuration hash
            lastConfigurationHash = config.getConfigurationHash();

            return processedSources;

        } catch (ShaderPreprocessorException e) {
            throw new IOException("Shader preprocessing failed for " + resourceIdentifier, e);
        }
    }

    /**
     * Create the actual shader instance from processed sources
     * Subclasses must implement this to create ComputeShader or GraphicsShader
     */
    protected abstract Shader createShaderInstance(Map<ShaderType, String> processedSources) throws IOException;

    /**
     * Get the main shader type for dependency tracking
     */
    protected ShaderType getMainShaderType() {
        if (originalSources.containsKey(ShaderType.COMPUTE)) {
            return ShaderType.COMPUTE;
        } else if (originalSources.containsKey(ShaderType.FRAGMENT)) {
            return ShaderType.FRAGMENT;
        } else {
            return originalSources.keySet().iterator().next();
        }
    }

    @Override
    protected boolean hasConfigurationChanges() {
        ShaderConfiguration currentConfig = ShaderConfigurationManager.getInstance()
                .getConfiguration(resourceIdentifier);
        return lastConfigurationHash != currentConfig.getConfigurationHash();
    }

    /**
     * Handle configuration change events
     */
    private void onConfigurationChanged(ShaderConfiguration newConfiguration) {
        try {
            forceReload();
        } catch (IOException e) {
            System.err.println("Failed to reload shader after configuration change: " + e.getMessage());
        }
    }

    /**
     * Get the original shader sources (before preprocessing)
     */
    public Map<ShaderType, String> getOriginalSources() {
        return new HashMap<>(originalSources);
    }

    /**
     * Get the last preprocessing result
     */
    public PreprocessorResult getLastPreprocessingResult() {
        return lastPreprocessingResult;
    }

    @Override
    public void dispose() {
        // Remove configuration listener
        ShaderConfigurationManager.getInstance()
                .removeConfigurationListener(resourceIdentifier, this::onConfigurationChanged);

        super.dispose();
    }
}
