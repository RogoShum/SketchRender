package rogo.sketch.core.shader.preprocessor;

import rogo.sketch.core.shader.vertex.ShaderVertexLayout;
import rogo.sketch.core.util.KeyId;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Shader preprocessor orchestrator.
 * <p>
 * Processing order:
 * 1. SourceResolver: resolves #import/#include
 * 2. MacroProcessor: handles macros and conditional compilation
 * 3. ShaderAuthoringValidator: rejects forbidden authoring syntax
 * 4. Interface extraction: handled later by ShaderTemplate
 * 5. Backend decoration: handled by GL/Vulkan backend paths
 */
public class SketchShaderPreprocessor implements ShaderPreprocessor {
    private ShaderResourceProvider resourceProvider;
    private final Set<KeyId> lastImportedFiles = new HashSet<>();

    @Override
    public void setResourceProvider(ShaderResourceProvider resourceProvider) {
        this.resourceProvider = resourceProvider;
    }

    @Override
    public Set<KeyId> getLastImportedFiles() {
        return new HashSet<>(lastImportedFiles);
    }

    @Override
    public void clearCache() {
        // No local cache. Template/variant caches handle reuse at a higher level.
    }

    @Override
    public PreprocessorResult process(String source, KeyId shaderKeyId, Map<String, String> macros, ShaderVertexLayout vertexLayout)
            throws ShaderPreprocessorException {
        ShaderSourceResolver.ResolvedSource resolved = ShaderSourceResolver.resolve(source, shaderKeyId, resourceProvider);
        ShaderMacroProcessor.ProcessedSource processed = ShaderMacroProcessor.process(resolved.source(), shaderKeyId, macros);
        ShaderAuthoringValidator.validate(processed.source(), shaderKeyId);

        lastImportedFiles.clear();
        lastImportedFiles.addAll(resolved.importedFiles());

        ArrayList<String> warnings = new ArrayList<>(resolved.warnings());
        warnings.addAll(processed.warnings());
        return new PreprocessorResult(
                processed.source(),
                new HashSet<>(resolved.importedFiles()),
                warnings,
                Math.max(resolved.glslVersion(), processed.glslVersion()));
    }

    /**
     * Vertex attribute location injection is no longer part of the formal preprocessing path.
     * GL uses pre-link binding and Vulkan decorates stage interfaces from interface metadata.
     */
    @Override
    public String injectVertexAttributeLayouts(String source, ShaderVertexLayout vertexLayout) {
        return source;
    }
}

