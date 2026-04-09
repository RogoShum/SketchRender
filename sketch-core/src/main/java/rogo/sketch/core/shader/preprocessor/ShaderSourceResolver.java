package rogo.sketch.core.shader.preprocessor;

import rogo.sketch.core.util.KeyId;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ShaderSourceResolver {
    private static final Pattern IMPORT_PATTERN = Pattern.compile(
            "^\\s*#\\s*(?:import|include)\\s+(?:\"([^\"]+)\"|<([^>]+)>)\\s*(?://.*)?$",
            Pattern.MULTILINE);
    private static final Pattern VERSION_PATTERN = Pattern.compile(
            "^\\s*#\\s*version\\s+(\\d+)\\s*(?:core|compatibility|es)?\\s*$",
            Pattern.MULTILINE);

    private ShaderSourceResolver() {
    }

    static ResolvedSource resolve(String source, KeyId shaderKeyId, ShaderResourceProvider resourceProvider)
            throws ShaderPreprocessorException {
        if (resourceProvider == null) {
            throw new ShaderPreprocessorException("Resource provider not set");
        }
        Set<KeyId> importedFiles = new LinkedHashSet<>();
        List<String> warnings = new ArrayList<>();
        String resolved = resolveRecursive(
                source,
                shaderKeyId,
                resourceProvider,
                importedFiles,
                warnings,
                new LinkedHashSet<>());
        return new ResolvedSource(resolved, importedFiles, warnings, detectVersion(resolved));
    }

    private static String resolveRecursive(
            String source,
            KeyId currentFile,
            ShaderResourceProvider resourceProvider,
            Set<KeyId> importedFiles,
            List<String> warnings,
            Set<KeyId> importStack) throws ShaderPreprocessorException {
        if (!importStack.add(currentFile)) {
            throw new ShaderPreprocessorException("Circular import detected: " + currentFile);
        }
        try {
            Matcher matcher = IMPORT_PATTERN.matcher(source);
            StringBuilder result = new StringBuilder();
            int lastEnd = 0;
            while (matcher.find()) {
                result.append(source, lastEnd, matcher.start());
                String importPath = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
                boolean systemImport = matcher.group(2) != null;
                KeyId importId = resourceProvider.resolveImport(currentFile, importPath);
                if (importedFiles.contains(importId)) {
                    result.append("// already imported: ").append(importPath).append('\n');
                    lastEnd = matcher.end();
                    continue;
                }

                Optional<String> importedSource = resourceProvider.loadShaderSource(importId);
                if (importedSource.isEmpty()) {
                    String message = "Failed to import: " + importPath;
                    if (systemImport) {
                        warnings.add(message);
                        result.append("// warning: ").append(message).append('\n');
                    } else {
                        throw new ShaderPreprocessorException(message, currentFile, lineNumber(source, matcher.start()));
                    }
                } else {
                    importedFiles.add(importId);
                    String processed = resolveRecursive(
                            importedSource.get(),
                            importId,
                            resourceProvider,
                            importedFiles,
                            warnings,
                            importStack);
                    result.append("// begin import: ").append(importPath).append('\n');
                    result.append(processed);
                    if (!processed.endsWith("\n")) {
                        result.append('\n');
                    }
                    result.append("// end import: ").append(importPath).append('\n');
                }
                lastEnd = matcher.end();
            }
            result.append(source.substring(lastEnd));
            return result.toString();
        } finally {
            importStack.remove(currentFile);
        }
    }

    static int detectVersion(String source) {
        if (source == null || source.isBlank()) {
            return 0;
        }
        Matcher matcher = VERSION_PATTERN.matcher(source);
        int maxVersion = 0;
        while (matcher.find()) {
            maxVersion = Math.max(maxVersion, Integer.parseInt(matcher.group(1)));
        }
        return maxVersion;
    }

    private static int lineNumber(String source, int position) {
        int lineNumber = 1;
        for (int i = 0; i < position && i < source.length(); i++) {
            if (source.charAt(i) == '\n') {
                lineNumber++;
            }
        }
        return lineNumber;
    }

    record ResolvedSource(String source, Set<KeyId> importedFiles, List<String> warnings, int glslVersion) {
    }
}

