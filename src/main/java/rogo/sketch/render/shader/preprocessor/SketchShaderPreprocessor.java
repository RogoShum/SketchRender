package rogo.sketch.render.shader.preprocessor;

import rogo.sketch.util.Identifier;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Modern shader preprocessor with advanced import and macro capabilities
 * Inspired by Minecraft's shader system but with more flexible features
 */
public class SketchShaderPreprocessor implements ShaderPreprocessor {

    // Regex patterns for preprocessing
    private static final Pattern IMPORT_PATTERN = Pattern.compile(
            "^\\s*#\\s*(?:import|include)\\s+(?:\"([^\"]+)\"|<([^>]+)>)\\s*(?://.*)?$",
            Pattern.MULTILINE
    );

    private static final Pattern DEFINE_PATTERN = Pattern.compile(
            "^\\s*#\\s*define\\s+(\\w+)(?:\\s+(.*))?\\s*$",
            Pattern.MULTILINE
    );

    private static final Pattern UNDEF_PATTERN = Pattern.compile(
            "^\\s*#\\s*undef\\s+(\\w+)\\s*$",
            Pattern.MULTILINE
    );

    private static final Pattern IFDEF_PATTERN = Pattern.compile(
            "^\\s*#\\s*ifdef\\s+(\\w+)\\s*$",
            Pattern.MULTILINE
    );

    private static final Pattern IFNDEF_PATTERN = Pattern.compile(
            "^\\s*#\\s*ifndef\\s+(\\w+)\\s*$",
            Pattern.MULTILINE
    );

    private static final Pattern IF_PATTERN = Pattern.compile(
            "^\\s*#\\s*if\\s+(.+)$",
            Pattern.MULTILINE
    );

    private static final Pattern ELSE_PATTERN = Pattern.compile(
            "^\\s*#\\s*else\\s*$",
            Pattern.MULTILINE
    );

    private static final Pattern ENDIF_PATTERN = Pattern.compile(
            "^\\s*#\\s*endif\\s*$",
            Pattern.MULTILINE
    );

    private static final Pattern VERSION_PATTERN = Pattern.compile(
            "^\\s*#\\s*version\\s+(\\d+)\\s*(?:core|compatibility|es)?\\s*$",
            Pattern.MULTILINE
    );

    private static final Pattern MACRO_USAGE_PATTERN = Pattern.compile(
            "\\b(\\w+)\\b"
    );

    private ShaderResourceProvider resourceProvider;
    private final Set<Identifier> lastImportedFiles = new HashSet<>();
    private final Map<String, PreprocessorResult> cache = new HashMap<>();

    @Override
    public void setResourceProvider(ShaderResourceProvider resourceProvider) {
        this.resourceProvider = resourceProvider;
    }

    @Override
    public Set<Identifier> getLastImportedFiles() {
        return new HashSet<>(lastImportedFiles);
    }

    @Override
    public void clearCache() {
        cache.clear();
    }

    @Override
    public PreprocessorResult process(String source, Identifier shaderIdentifier, Map<String, String> macros)
            throws ShaderPreprocessorException {

        if (resourceProvider == null) {
            throw new ShaderPreprocessorException("Resource provider not set");
        }

        lastImportedFiles.clear();

        ProcessingContext context = new ProcessingContext(shaderIdentifier, macros);
        String processed = processRecursive(source, shaderIdentifier, context, new HashSet<>());

        return new PreprocessorResult(
                processed,
                new HashSet<>(lastImportedFiles),
                context.warnings,
                context.finalGlslVersion
        );
    }

    private String processRecursive(String source, Identifier currentFile, ProcessingContext context,
                                    Set<Identifier> importStack) throws ShaderPreprocessorException {

        if (importStack.contains(currentFile)) {
            throw new ShaderPreprocessorException(
                    "Circular import detected: " + String.join(" -> ",
                            importStack.stream().map(Identifier::toString).toArray(String[]::new)) +
                            " -> " + currentFile
            );
        }

        importStack.add(currentFile);

        try {
            String processed = source;

            // Process version directive
            processed = processVersion(processed, context);

            // Process imports first
            processed = processImports(processed, currentFile, context, importStack);

            // Process macros and conditionals
            processed = processMacrosAndConditionals(processed, context);

            return processed;

        } finally {
            importStack.remove(currentFile);
        }
    }

    private String processVersion(String source, ProcessingContext context) {
        Matcher matcher = VERSION_PATTERN.matcher(source);
        if (matcher.find()) {
            int version = Integer.parseInt(matcher.group(1));
            context.finalGlslVersion = Math.max(context.finalGlslVersion, version);
        }
        return source;
    }

    private String processImports(String source, Identifier currentFile, ProcessingContext context,
                                  Set<Identifier> importStack) throws ShaderPreprocessorException {

        Matcher matcher = IMPORT_PATTERN.matcher(source);
        StringBuilder result = new StringBuilder();
        int lastEnd = 0;

        while (matcher.find()) {
            result.append(source, lastEnd, matcher.start());

            String importPath = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            boolean isSystemImport = matcher.group(2) != null; // <> style import

            Identifier importId = resourceProvider.resolveImport(currentFile, importPath);

            if (lastImportedFiles.contains(importId)) {
                // Already imported, skip to avoid duplication
                result.append("// Already imported: ").append(importPath).append("\n");
            } else {
                Optional<String> importedSource = resourceProvider.loadShaderSource(importId);
                if (importedSource.isPresent()) {
                    lastImportedFiles.add(importId);

                    String processedImport = processRecursive(importedSource.get(), importId, context, importStack);

                    result.append("// Begin import: ").append(importPath).append("\n");
                    result.append(processedImport);
                    if (!processedImport.endsWith("\n")) {
                        result.append("\n");
                    }
                    result.append("// End import: ").append(importPath).append("\n");
                } else {
                    String errorMsg = "Failed to import: " + importPath;
                    if (isSystemImport) {
                        context.warnings.add(errorMsg);
                        result.append("// Warning: ").append(errorMsg).append("\n");
                    } else {
                        throw new ShaderPreprocessorException(errorMsg, currentFile, getLineNumber(source, matcher.start()));
                    }
                }
            }

            lastEnd = matcher.end();
        }

        result.append(source.substring(lastEnd));
        return result.toString();
    }

    private String processMacrosAndConditionals(String source, ProcessingContext context)
            throws ShaderPreprocessorException {

        String[] lines = source.split("\n");
        List<String> result = new ArrayList<>();
        Stack<ConditionalBlock> conditionalStack = new Stack<>();
        Map<String, String> localMacros = new HashMap<>(context.macros);

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int lineNumber = i + 1;

            // Check if we're in a disabled conditional block
            boolean shouldIncludeLine = conditionalStack.isEmpty() ||
                    conditionalStack.stream().allMatch(block -> block.isActive);

            // Process preprocessor directives even in disabled blocks
            if (line.trim().startsWith("#")) {
                // Handle #define and #undef - parse them for conditional compilation but keep them in output
                if (processDefine(line, localMacros)) {
                    if (shouldIncludeLine) {
                        // Always keep source-level #define directives - they are part of the shader
                        result.add(line);
                    }
                    continue;
                }

                if (processUndef(line, localMacros)) {
                    if (shouldIncludeLine) {
                        // Always keep source-level #undef directives
                        result.add(line);
                    }
                    continue;
                }

                if (processConditional(line, localMacros, conditionalStack, context, lineNumber)) {
                    if (shouldIncludeLine) {
                        result.add("// " + line); // Comment out processed conditionals
                    }
                    continue;
                }
            }

            if (shouldIncludeLine) {
                // Apply macro substitutions
                String processedLine = applyMacroSubstitutions(line, localMacros);
                result.add(processedLine);
            }
        }

        if (!conditionalStack.isEmpty()) {
            throw new ShaderPreprocessorException(
                    "Unclosed conditional directive(s): " + conditionalStack.size() + " remaining"
            );
        }

        return String.join("\n", result);
    }

    private boolean processDefine(String line, Map<String, String> macros) {
        Matcher matcher = DEFINE_PATTERN.matcher(line);
        if (matcher.find()) {
            String name = matcher.group(1);
            String value = matcher.group(2);
            macros.put(name, value != null ? value.trim() : "1");
            return true;
        }
        return false;
    }

    private boolean processUndef(String line, Map<String, String> macros) {
        Matcher matcher = UNDEF_PATTERN.matcher(line);
        if (matcher.find()) {
            String name = matcher.group(1);
            macros.remove(name);
            return true;
        }
        return false;
    }

    private boolean processConditional(String line, Map<String, String> macros,
                                       Stack<ConditionalBlock> stack, ProcessingContext context,
                                       int lineNumber) throws ShaderPreprocessorException {

        Matcher ifdefMatcher = IFDEF_PATTERN.matcher(line);
        if (ifdefMatcher.find()) {
            String macro = ifdefMatcher.group(1);
            boolean condition = macros.containsKey(macro);
            stack.push(new ConditionalBlock(ConditionalType.IFDEF, condition, false));
            return true;
        }

        Matcher ifndefMatcher = IFNDEF_PATTERN.matcher(line);
        if (ifndefMatcher.find()) {
            String macro = ifndefMatcher.group(1);
            boolean condition = !macros.containsKey(macro);
            stack.push(new ConditionalBlock(ConditionalType.IFNDEF, condition, false));
            return true;
        }

        Matcher ifMatcher = IF_PATTERN.matcher(line);
        if (ifMatcher.find()) {
            String expression = ifMatcher.group(1).trim();
            boolean condition = evaluateExpression(expression, macros);
            stack.push(new ConditionalBlock(ConditionalType.IF, condition, false));
            return true;
        }

        if (ELSE_PATTERN.matcher(line).find()) {
            if (stack.isEmpty()) {
                throw new ShaderPreprocessorException("Unexpected #else directive", context.shaderIdentifier, lineNumber);
            }
            ConditionalBlock current = stack.pop();
            if (current.hasElse) {
                throw new ShaderPreprocessorException("Multiple #else directives", context.shaderIdentifier, lineNumber);
            }
            stack.push(new ConditionalBlock(current.type, !current.originalCondition, true));
            return true;
        }

        if (ENDIF_PATTERN.matcher(line).find()) {
            if (stack.isEmpty()) {
                throw new ShaderPreprocessorException("Unexpected #endif directive", context.shaderIdentifier, lineNumber);
            }
            stack.pop();
            return true;
        }

        return false;
    }

    private boolean evaluateExpression(String expression, Map<String, String> macros) {
        // Simple expression evaluator for common cases
        expression = expression.trim();

        // Handle "defined(MACRO)" syntax
        if (expression.startsWith("defined(") && expression.endsWith(")")) {
            String macro = expression.substring(8, expression.length() - 1).trim();
            return macros.containsKey(macro);
        }

        // Handle simple macro existence check
        if (expression.matches("\\w+")) {
            return macros.containsKey(expression);
        }

        // Handle negation
        if (expression.startsWith("!")) {
            return !evaluateExpression(expression.substring(1).trim(), macros);
        }

        // Default: try to evaluate as macro value comparison
        String[] parts = expression.split("==|!=|<=|>=|<|>", 2);
        if (parts.length == 2) {
            String left = parts[0].trim();
            String right = parts[1].trim();

            String leftValue = macros.getOrDefault(left, left);
            String rightValue = macros.getOrDefault(right, right);

            if (expression.contains("==")) {
                return leftValue.equals(rightValue);
            } else if (expression.contains("!=")) {
                return !leftValue.equals(rightValue);
            }
            // Add more operators as needed
        }

        // Default to true for unknown expressions (with warning)
        return true;
    }

    private String applyMacroSubstitutions(String line, Map<String, String> macros) {
        String result = line;

        for (Map.Entry<String, String> macro : macros.entrySet()) {
            String name = macro.getKey();
            String value = macro.getValue();

            // Simple word boundary replacement
            result = result.replaceAll("\\b" + Pattern.quote(name) + "\\b", value);
        }

        return result;
    }

    private int getLineNumber(String source, int position) {
        int lineNumber = 1;
        for (int i = 0; i < position && i < source.length(); i++) {
            if (source.charAt(i) == '\n') {
                lineNumber++;
            }
        }
        return lineNumber;
    }

    private static class ProcessingContext {
        final Identifier shaderIdentifier;
        final Map<String, String> macros;
        final List<String> warnings = new ArrayList<>();
        int finalGlslVersion = 0;

        ProcessingContext(Identifier shaderIdentifier, Map<String, String> macros) {
            this.shaderIdentifier = shaderIdentifier;
            this.macros = new HashMap<>(macros);
        }
    }

    private static class ConditionalBlock {
        final ConditionalType type;
        final boolean originalCondition;
        final boolean isActive;
        final boolean hasElse;

        ConditionalBlock(ConditionalType type, boolean condition, boolean hasElse) {
            this.type = type;
            this.originalCondition = condition;
            this.isActive = condition;
            this.hasElse = hasElse;
        }
    }

    private enum ConditionalType {
        IFDEF, IFNDEF, IF
    }
}
