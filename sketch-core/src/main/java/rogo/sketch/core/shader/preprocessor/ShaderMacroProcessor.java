package rogo.sketch.core.shader.preprocessor;

import rogo.sketch.core.util.KeyId;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class ShaderMacroProcessor {
    private static final int MAX_EXPANSION_DEPTH = 32;

    private ShaderMacroProcessor() {
    }

    static ProcessedSource process(String source, KeyId shaderKeyId, Map<String, String> initialMacros)
            throws ShaderPreprocessorException {
        Map<String, MacroDefinition> macros = new LinkedHashMap<>();
        if (initialMacros != null) {
            for (Map.Entry<String, String> entry : initialMacros.entrySet()) {
                macros.put(entry.getKey(), MacroDefinition.objectLike(entry.getKey(), entry.getValue()));
            }
        }

        List<LogicalLine> lines = toLogicalLines(source);
        List<String> warnings = new ArrayList<>();
        StringBuilder result = new StringBuilder(source.length());
        Deque<ConditionalFrame> conditionals = new ArrayDeque<>();
        int glslVersion = ShaderSourceResolver.detectVersion(source);

        for (LogicalLine line : lines) {
            String trimmed = line.text().trim();
            boolean active = isActive(conditionals);

            if (trimmed.startsWith("#")) {
                if (trimmed.startsWith("#define")) {
                    if (active) {
                        MacroDefinition definition = parseDefine(trimmed, shaderKeyId, line.startLine());
                        macros.put(definition.name(), definition);
                        result.append(line.text()).append('\n');
                    }
                    continue;
                }
                if (trimmed.startsWith("#undef")) {
                    if (active) {
                        String name = parseUndef(trimmed, shaderKeyId, line.startLine());
                        macros.remove(name);
                        result.append(line.text()).append('\n');
                    }
                    continue;
                }
                if (trimmed.startsWith("#ifdef")) {
                    String name = trimmed.substring("#ifdef".length()).trim();
                    pushConditional(conditionals, macros.containsKey(name));
                    continue;
                }
                if (trimmed.startsWith("#ifndef")) {
                    String name = trimmed.substring("#ifndef".length()).trim();
                    pushConditional(conditionals, !macros.containsKey(name));
                    continue;
                }
                if (trimmed.startsWith("#if")) {
                    String expression = trimmed.substring("#if".length()).trim();
                    pushConditional(conditionals, evaluateExpression(expression, macros));
                    continue;
                }
                if (trimmed.startsWith("#elif")) {
                    if (conditionals.isEmpty()) {
                        throw new ShaderPreprocessorException("Unexpected #elif", shaderKeyId, line.startLine());
                    }
                    ConditionalFrame frame = conditionals.peek();
                    if (frame.seenElse) {
                        throw new ShaderPreprocessorException("Unexpected #elif after #else", shaderKeyId, line.startLine());
                    }
                    boolean nextActive = frame.parentActive && !frame.branchTaken
                            && evaluateExpression(trimmed.substring("#elif".length()).trim(), macros);
                    frame.currentActive = nextActive;
                    frame.branchTaken |= nextActive;
                    continue;
                }
                if (trimmed.startsWith("#else")) {
                    if (conditionals.isEmpty()) {
                        throw new ShaderPreprocessorException("Unexpected #else", shaderKeyId, line.startLine());
                    }
                    ConditionalFrame frame = conditionals.peek();
                    if (frame.seenElse) {
                        throw new ShaderPreprocessorException("Duplicate #else", shaderKeyId, line.startLine());
                    }
                    frame.seenElse = true;
                    frame.currentActive = frame.parentActive && !frame.branchTaken;
                    frame.branchTaken = true;
                    continue;
                }
                if (trimmed.startsWith("#endif")) {
                    if (conditionals.isEmpty()) {
                        throw new ShaderPreprocessorException("Unexpected #endif", shaderKeyId, line.startLine());
                    }
                    conditionals.pop();
                    continue;
                }
                if (trimmed.startsWith("#version")) {
                    if (active) {
                        glslVersion = Math.max(glslVersion, ShaderSourceResolver.detectVersion(line.text()));
                        result.append(line.text()).append('\n');
                    }
                    continue;
                }
                if (trimmed.startsWith("#pragma") || trimmed.startsWith("#extension")) {
                    if (active) {
                        result.append(line.text()).append('\n');
                    }
                    continue;
                }
                if (active) {
                    result.append(line.text()).append('\n');
                }
                continue;
            }

            if (active) {
                result.append(expandLine(line.text(), macros, 0)).append('\n');
            }
        }

        if (!conditionals.isEmpty()) {
            throw new ShaderPreprocessorException("Unclosed conditional block(s)", shaderKeyId, lines.isEmpty() ? 1 : lines.get(lines.size() - 1).startLine());
        }

        return new ProcessedSource(result.toString(), warnings, glslVersion);
    }

    private static void pushConditional(Deque<ConditionalFrame> conditionals, boolean branchCondition) {
        boolean parentActive = isActive(conditionals);
        boolean active = parentActive && branchCondition;
        conditionals.push(new ConditionalFrame(parentActive, active, active, false));
    }

    private static boolean isActive(Deque<ConditionalFrame> conditionals) {
        for (ConditionalFrame frame : conditionals) {
            if (!frame.currentActive) {
                return false;
            }
        }
        return true;
    }

    private static List<LogicalLine> toLogicalLines(String source) {
        String normalized = source.replace("\r\n", "\n").replace('\r', '\n');
        String[] physicalLines = normalized.split("\n", -1);
        List<LogicalLine> logicalLines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int startLine = 1;
        boolean continuing = false;

        for (int i = 0; i < physicalLines.length; i++) {
            String line = physicalLines[i];
            if (!continuing) {
                startLine = i + 1;
            }
            if (endsWithContinuation(line)) {
                current.append(line, 0, line.length() - 1);
                continuing = true;
            } else {
                current.append(line);
                logicalLines.add(new LogicalLine(current.toString(), startLine));
                current.setLength(0);
                continuing = false;
            }
        }
        if (continuing) {
            logicalLines.add(new LogicalLine(current.toString(), startLine));
        }
        return logicalLines;
    }

    private static boolean endsWithContinuation(String line) {
        int index = line.length() - 1;
        while (index >= 0 && Character.isWhitespace(line.charAt(index))) {
            index--;
        }
        return index >= 0 && line.charAt(index) == '\\';
    }

    private static MacroDefinition parseDefine(String line, KeyId shaderKeyId, int lineNumber) throws ShaderPreprocessorException {
        String rest = line.substring("#define".length()).trim();
        if (rest.isEmpty()) {
            throw new ShaderPreprocessorException("Missing macro name", shaderKeyId, lineNumber);
        }

        int index = 0;
        while (index < rest.length() && isIdentifierPart(rest.charAt(index))) {
            index++;
        }
        if (index == 0) {
            throw new ShaderPreprocessorException("Invalid macro name", shaderKeyId, lineNumber);
        }

        String name = rest.substring(0, index);
        if (index < rest.length() && rest.charAt(index) == '(') {
            int closing = rest.indexOf(')', index);
            if (closing < 0) {
                throw new ShaderPreprocessorException("Unclosed macro parameter list", shaderKeyId, lineNumber);
            }
            String parametersRaw = rest.substring(index + 1, closing).trim();
            List<String> parameters = new ArrayList<>();
            if (!parametersRaw.isEmpty()) {
                for (String parameter : parametersRaw.split(",")) {
                    String trimmed = parameter.trim();
                    if (!trimmed.isEmpty()) {
                        parameters.add(trimmed);
                    }
                }
            }
            String body = rest.substring(closing + 1).trim();
            return MacroDefinition.functionLike(name, parameters, body);
        }

        String body = rest.substring(index).trim();
        return MacroDefinition.objectLike(name, body);
    }

    private static String parseUndef(String line, KeyId shaderKeyId, int lineNumber) throws ShaderPreprocessorException {
        String name = line.substring("#undef".length()).trim();
        if (name.isEmpty()) {
            throw new ShaderPreprocessorException("Missing macro name", shaderKeyId, lineNumber);
        }
        return name;
    }

    private static String expandLine(String line, Map<String, MacroDefinition> macros, int depth) {
        if (line == null || line.isEmpty() || macros.isEmpty() || depth >= MAX_EXPANSION_DEPTH) {
            return line;
        }
        String expanded = line;
        for (int i = 0; i < MAX_EXPANSION_DEPTH; i++) {
            String next = expandFunctions(expandObjects(expanded, macros, depth + 1), macros, depth + 1);
            if (next.equals(expanded)) {
                return next;
            }
            expanded = next;
        }
        return expanded;
    }

    private static String expandObjects(String line, Map<String, MacroDefinition> macros, int depth) {
        StringBuilder result = new StringBuilder(line.length());
        int index = 0;
        while (index < line.length()) {
            char c = line.charAt(index);
            if (isIdentifierStart(c)) {
                int end = index + 1;
                while (end < line.length() && isIdentifierPart(line.charAt(end))) {
                    end++;
                }
                String identifier = line.substring(index, end);
                MacroDefinition macro = macros.get(identifier);
                if (macro != null && !macro.functionLike()) {
                    result.append(expandLine(macro.body(), macros, depth + 1));
                } else {
                    result.append(identifier);
                }
                index = end;
            } else {
                result.append(c);
                index++;
            }
        }
        return result.toString();
    }

    private static String expandFunctions(String line, Map<String, MacroDefinition> macros, int depth) {
        StringBuilder result = new StringBuilder(line.length());
        int index = 0;
        while (index < line.length()) {
            char c = line.charAt(index);
            if (!isIdentifierStart(c)) {
                result.append(c);
                index++;
                continue;
            }

            int end = index + 1;
            while (end < line.length() && isIdentifierPart(line.charAt(end))) {
                end++;
            }
            String identifier = line.substring(index, end);
            MacroDefinition macro = macros.get(identifier);
            if (macro == null || !macro.functionLike() || end >= line.length() || line.charAt(end) != '(') {
                result.append(identifier);
                index = end;
                continue;
            }

            Invocation invocation = parseInvocation(line, end);
            if (invocation == null) {
                result.append(identifier);
                index = end;
                continue;
            }

            result.append(expandFunctionInvocation(macro, invocation.arguments(), macros, depth + 1));
            index = invocation.endIndex();
        }
        return result.toString();
    }

    private static Invocation parseInvocation(String source, int openParenIndex) {
        int depth = 0;
        int argumentStart = openParenIndex + 1;
        List<String> arguments = new ArrayList<>();
        for (int i = openParenIndex; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    arguments.add(source.substring(argumentStart, i).trim());
                    return new Invocation(arguments, i + 1);
                }
            } else if (c == ',' && depth == 1) {
                arguments.add(source.substring(argumentStart, i).trim());
                argumentStart = i + 1;
            }
        }
        return null;
    }

    private static String expandFunctionInvocation(
            MacroDefinition macro,
            List<String> rawArguments,
            Map<String, MacroDefinition> macros,
            int depth) {
        if (depth >= MAX_EXPANSION_DEPTH) {
            return macro.body();
        }
        List<String> parameters = macro.parameters();
        String body = macro.body();
        for (int i = 0; i < parameters.size(); i++) {
            String parameter = parameters.get(i);
            String argument = i < rawArguments.size() ? expandLine(rawArguments.get(i), macros, depth + 1) : "";
            body = replaceIdentifier(body, parameter, argument);
        }
        return expandLine(body, macros, depth + 1);
    }

    private static String replaceIdentifier(String source, String identifier, String replacement) {
        StringBuilder result = new StringBuilder(source.length());
        int index = 0;
        while (index < source.length()) {
            char c = source.charAt(index);
            if (isIdentifierStart(c)) {
                int end = index + 1;
                while (end < source.length() && isIdentifierPart(source.charAt(end))) {
                    end++;
                }
                String token = source.substring(index, end);
                result.append(token.equals(identifier) ? replacement : token);
                index = end;
            } else {
                result.append(c);
                index++;
            }
        }
        return result.toString();
    }

    private static boolean evaluateExpression(String expression, Map<String, MacroDefinition> macros)
            throws ShaderPreprocessorException {
        return new ExpressionParser(expression, macros).parse() != 0L;
    }

    private static boolean isIdentifierStart(char c) {
        return Character.isLetter(c) || c == '_';
    }

    private static boolean isIdentifierPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    record ProcessedSource(String source, List<String> warnings, int glslVersion) {
    }

    private record LogicalLine(String text, int startLine) {
    }

    private record Invocation(List<String> arguments, int endIndex) {
    }

    private record MacroDefinition(String name, List<String> parameters, String body, boolean functionLike) {
        static MacroDefinition objectLike(String name, String body) {
            return new MacroDefinition(name, List.of(), body == null || body.isBlank() ? "1" : body, false);
        }

        static MacroDefinition functionLike(String name, List<String> parameters, String body) {
            return new MacroDefinition(name, List.copyOf(parameters), body == null ? "" : body, true);
        }
    }

    private static final class ConditionalFrame {
        private final boolean parentActive;
        private boolean currentActive;
        private boolean branchTaken;
        private boolean seenElse;

        private ConditionalFrame(boolean parentActive, boolean currentActive, boolean branchTaken, boolean seenElse) {
            this.parentActive = parentActive;
            this.currentActive = currentActive;
            this.branchTaken = branchTaken;
            this.seenElse = seenElse;
        }
    }

    private static final class ExpressionParser {
        private final String expression;
        private final Map<String, MacroDefinition> macros;
        private int index;

        private ExpressionParser(String expression, Map<String, MacroDefinition> macros) {
            this.expression = expression != null ? expression : "";
            this.macros = macros;
        }

        private long parse() throws ShaderPreprocessorException {
            index = 0;
            long value = parseLogicalOr();
            skipWhitespace();
            if (index < expression.length()) {
                throw new ShaderPreprocessorException("Unexpected token in #if expression: " + expression.substring(index));
            }
            return value;
        }

        private long parseLogicalOr() throws ShaderPreprocessorException {
            long value = parseLogicalAnd();
            while (match("||")) {
                value = truthy(value) || truthy(parseLogicalAnd()) ? 1L : 0L;
            }
            return value;
        }

        private long parseLogicalAnd() throws ShaderPreprocessorException {
            long value = parseEquality();
            while (match("&&")) {
                value = truthy(value) && truthy(parseEquality()) ? 1L : 0L;
            }
            return value;
        }

        private long parseEquality() throws ShaderPreprocessorException {
            long value = parseRelational();
            while (true) {
                if (match("==")) {
                    value = value == parseRelational() ? 1L : 0L;
                } else if (match("!=")) {
                    value = value != parseRelational() ? 1L : 0L;
                } else {
                    return value;
                }
            }
        }

        private long parseRelational() throws ShaderPreprocessorException {
            long value = parseAdditive();
            while (true) {
                if (match("<=")) {
                    value = value <= parseAdditive() ? 1L : 0L;
                } else if (match(">=")) {
                    value = value >= parseAdditive() ? 1L : 0L;
                } else if (match("<")) {
                    value = value < parseAdditive() ? 1L : 0L;
                } else if (match(">")) {
                    value = value > parseAdditive() ? 1L : 0L;
                } else {
                    return value;
                }
            }
        }

        private long parseAdditive() throws ShaderPreprocessorException {
            long value = parseMultiplicative();
            while (true) {
                if (match("+")) {
                    value += parseMultiplicative();
                } else if (match("-")) {
                    value -= parseMultiplicative();
                } else {
                    return value;
                }
            }
        }

        private long parseMultiplicative() throws ShaderPreprocessorException {
            long value = parseUnary();
            while (true) {
                if (match("*")) {
                    value *= parseUnary();
                } else if (match("/")) {
                    long rhs = parseUnary();
                    value = rhs == 0L ? 0L : value / rhs;
                } else if (match("%")) {
                    long rhs = parseUnary();
                    value = rhs == 0L ? 0L : value % rhs;
                } else {
                    return value;
                }
            }
        }

        private long parseUnary() throws ShaderPreprocessorException {
            if (match("!")) {
                return truthy(parseUnary()) ? 0L : 1L;
            }
            if (match("+")) {
                return parseUnary();
            }
            if (match("-")) {
                return -parseUnary();
            }
            return parsePrimary();
        }

        private long parsePrimary() throws ShaderPreprocessorException {
            skipWhitespace();
            if (match("(")) {
                long value = parseLogicalOr();
                expect(")");
                return value;
            }
            if (peekIdentifier("defined")) {
                consumeIdentifier("defined");
                skipWhitespace();
                if (match("(")) {
                    String identifier = readIdentifier();
                    expect(")");
                    return macros.containsKey(identifier) ? 1L : 0L;
                }
                String identifier = readIdentifier();
                return macros.containsKey(identifier) ? 1L : 0L;
            }
            if (hasIdentifier()) {
                String identifier = readIdentifier();
                return resolveIdentifierValue(identifier, macros);
            }
            return readNumber();
        }

        private long resolveIdentifierValue(String identifier, Map<String, MacroDefinition> macros)
                throws ShaderPreprocessorException {
            MacroDefinition macro = macros.get(identifier);
            if (macro == null || macro.functionLike()) {
                return 0L;
            }
            String body = macro.body().trim();
            if (body.isEmpty()) {
                return 1L;
            }
            if ("true".equalsIgnoreCase(body)) {
                return 1L;
            }
            if ("false".equalsIgnoreCase(body)) {
                return 0L;
            }
            Long literal = parseIntegerLiteral(body);
            if (literal != null) {
                return literal;
            }
            return new ExpressionParser(body, macros).parse();
        }

        private long readNumber() throws ShaderPreprocessorException {
            skipWhitespace();
            int start = index;
            if (start < expression.length() && Character.isDigit(expression.charAt(start))) {
                index++;
                while (index < expression.length()) {
                    char c = expression.charAt(index);
                    if (Character.isLetterOrDigit(c) || c == 'x' || c == 'X') {
                        index++;
                    } else {
                        break;
                    }
                }
                String raw = expression.substring(start, index);
                Long literal = parseIntegerLiteral(raw);
                if (literal != null) {
                    return literal;
                }
            }
            throw new ShaderPreprocessorException("Invalid #if expression near: " + expression.substring(start));
        }

        private boolean peekIdentifier(String identifier) {
            skipWhitespace();
            return expression.regionMatches(index, identifier, 0, identifier.length())
                    && (index + identifier.length() == expression.length()
                    || !isIdentifierPart(expression.charAt(index + identifier.length())));
        }

        private void consumeIdentifier(String identifier) {
            index += identifier.length();
        }

        private boolean hasIdentifier() {
            skipWhitespace();
            return index < expression.length() && isIdentifierStart(expression.charAt(index));
        }

        private String readIdentifier() throws ShaderPreprocessorException {
            skipWhitespace();
            if (!hasIdentifier()) {
                throw new ShaderPreprocessorException("Expected identifier in #if expression");
            }
            int start = index;
            index++;
            while (index < expression.length() && isIdentifierPart(expression.charAt(index))) {
                index++;
            }
            return expression.substring(start, index);
        }

        private void expect(String token) throws ShaderPreprocessorException {
            if (!match(token)) {
                throw new ShaderPreprocessorException("Expected '" + token + "' in #if expression");
            }
        }

        private boolean match(String token) {
            skipWhitespace();
            if (expression.startsWith(token, index)) {
                index += token.length();
                return true;
            }
            return false;
        }

        private void skipWhitespace() {
            while (index < expression.length() && Character.isWhitespace(expression.charAt(index))) {
                index++;
            }
        }

        private boolean truthy(long value) {
            return value != 0L;
        }

        private Long parseIntegerLiteral(String raw) {
            try {
                String normalized = raw.trim().toLowerCase(Locale.ROOT);
                while (!normalized.isEmpty()) {
                    char tail = normalized.charAt(normalized.length() - 1);
                    if (tail == 'u' || tail == 'l') {
                        normalized = normalized.substring(0, normalized.length() - 1);
                    } else {
                        break;
                    }
                }
                if (normalized.startsWith("0x")) {
                    return Long.parseUnsignedLong(normalized.substring(2), 16);
                }
                return Long.parseLong(normalized);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
    }
}

