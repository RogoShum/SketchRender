package rogo.sketch.core.shader.preprocessor;

import rogo.sketch.core.util.KeyId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validates authored shader source before interface extraction/backend decoration.
 */
public final class ShaderAuthoringValidator {
    private static final Pattern COMMENT_BLOCK = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern COMMENT_LINE = Pattern.compile("//.*?$", Pattern.MULTILINE);
    private static final Map<Pattern, String> FORBIDDEN_PATTERNS = new LinkedHashMap<>();

    static {
        FORBIDDEN_PATTERNS.put(
                Pattern.compile("\\blayout\\s*\\([^)]*\\blocation\\s*="),
                "Authoring shaders must not declare final attribute/stage locations with layout(location = N)");
        FORBIDDEN_PATTERNS.put(
                Pattern.compile("\\blayout\\s*\\([^)]*\\bbinding\\s*="),
                "Authoring shaders must not declare final resource bindings with layout(binding = N)");
        FORBIDDEN_PATTERNS.put(
                Pattern.compile("\\bgl_FragData\\b"),
                "Compatibility builtin gl_FragData is forbidden in formal shader authoring");
        FORBIDDEN_PATTERNS.put(
                Pattern.compile("\\btexture2D[A-Za-z0-9_]*\\s*\\("),
                "Compatibility texture2D* functions are forbidden in formal shader authoring");
        FORBIDDEN_PATTERNS.put(
                Pattern.compile("\\btextureCube[A-Za-z0-9_]*\\s*\\("),
                "Compatibility textureCube* functions are forbidden in formal shader authoring");
    }

    private ShaderAuthoringValidator() {
    }

    public static void validate(String source, KeyId shaderKeyId) throws ShaderPreprocessorException {
        if (source == null || source.isBlank()) {
            return;
        }

        String sanitized = stripComments(source);
        for (Map.Entry<Pattern, String> entry : FORBIDDEN_PATTERNS.entrySet()) {
            Matcher matcher = entry.getKey().matcher(sanitized);
            if (!matcher.find()) {
                continue;
            }
            throw new ShaderPreprocessorException(
                    entry.getValue(),
                    shaderKeyId,
                    lineNumberOf(sanitized, matcher.start()));
        }
    }

    private static String stripComments(String source) {
        String withoutBlocks = COMMENT_BLOCK.matcher(source).replaceAll("");
        return COMMENT_LINE.matcher(withoutBlocks).replaceAll("");
    }

    private static int lineNumberOf(String source, int indexExclusive) {
        int line = 1;
        int limit = Math.min(indexExclusive, source.length());
        for (int i = 0; i < limit; i++) {
            if (source.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }
}

