package rogo.sketch.core.shader.variant;

import rogo.sketch.core.shader.ShaderType;
import rogo.sketch.core.shader.vertex.ActiveShaderVertexLayout;
import rogo.sketch.core.util.KeyId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ShaderProgramInterfaceResolver {
    private static final Pattern COMMENT_BLOCK = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern COMMENT_LINE = Pattern.compile("//.*?$", Pattern.MULTILINE);
    private static final Pattern SAMPLER_OR_IMAGE_UNIFORM = Pattern.compile(
            "uniform\\s+((?:[iu]?sampler|[iu]?image)\\w*)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*(?:\\[(\\d+)\\])?\\s*;",
            Pattern.MULTILINE);
    private static final Pattern STANDALONE_UNIFORM = Pattern.compile(
            "uniform\\s+(?![A-Za-z_][A-Za-z0-9_]*\\s*\\{)([A-Za-z_][A-Za-z0-9_]*)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*(?:\\[(\\d+)\\])?\\s*;",
            Pattern.MULTILINE);
    private static final Pattern UNIFORM_BLOCK = Pattern.compile(
            "(?:layout\\s*\\([^)]*\\)\\s*)?uniform\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\{(.*?)}\\s*([A-Za-z_][A-Za-z0-9_]*)?\\s*;",
            Pattern.DOTALL);
    private static final Pattern STORAGE_BLOCK = Pattern.compile(
            "(?:layout\\s*\\([^)]*\\)\\s*)?(?:readonly\\s+|writeonly\\s+|coherent\\s+|volatile\\s+|restrict\\s+)*buffer\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\{(.*?)}\\s*([A-Za-z_][A-Za-z0-9_]*)?\\s*;",
            Pattern.DOTALL);
    private static final Pattern BLOCK_MEMBER = Pattern.compile(
            "([A-Za-z_][A-Za-z0-9_]*)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*(?:\\[(\\d+)\\])?\\s*;",
            Pattern.MULTILINE);

    private ShaderProgramInterfaceResolver() {
    }

    static ShaderProgramInterfaceSpec resolve(
            Map<ShaderType, String> processedSources,
            ActiveShaderVertexLayout activeVertexLayout,
            Map<KeyId, Map<KeyId, Integer>> resourceBindings) {
        Map<String, MutableUniformSpec> uniforms = new LinkedHashMap<>();
        if (processedSources != null) {
            for (Map.Entry<ShaderType, String> entry : processedSources.entrySet()) {
                ShaderType shaderType = entry.getKey();
                String source = entry.getValue();
                if (shaderType == null || source == null || source.isBlank()) {
                    continue;
                }
                String sanitized = stripComments(source);
                collectStandaloneUniforms(sanitized, shaderType, uniforms);
                collectBlockUniforms(sanitized, shaderType, uniforms, UNIFORM_BLOCK);
                collectBlockUniforms(sanitized, shaderType, uniforms, STORAGE_BLOCK);
            }
        }

        List<ShaderUniformSpec> schema = uniforms.values().stream()
                .sorted(Comparator.comparing(MutableUniformSpec::name))
                .map(MutableUniformSpec::toImmutable)
                .toList();

        return new ShaderProgramInterfaceSpec(
                activeVertexLayout,
                resourceBindings,
                new ShaderUniformSchema(schema));
    }

    private static void collectStandaloneUniforms(
            String source,
            ShaderType shaderType,
            Map<String, MutableUniformSpec> uniforms) {
        Matcher samplerOrImageMatcher = SAMPLER_OR_IMAGE_UNIFORM.matcher(source);
        while (samplerOrImageMatcher.find()) {
            registerUniform(
                    uniforms,
                    samplerOrImageMatcher.group(2),
                    samplerOrImageMatcher.group(1),
                    samplerOrImageMatcher.group(3),
                    shaderType,
                    false,
                    true);
        }

        Matcher standaloneMatcher = STANDALONE_UNIFORM.matcher(source);
        while (standaloneMatcher.find()) {
            String glslType = standaloneMatcher.group(1);
            if (glslType.startsWith("sampler") || glslType.startsWith("isampler")
                    || glslType.startsWith("usampler") || glslType.startsWith("image")
                    || glslType.startsWith("iimage") || glslType.startsWith("uimage")) {
                continue;
            }
            registerUniform(
                    uniforms,
                    standaloneMatcher.group(2),
                    glslType,
                    standaloneMatcher.group(3),
                    shaderType,
                    false,
                    true);
        }
    }

    private static void collectBlockUniforms(
            String source,
            ShaderType shaderType,
            Map<String, MutableUniformSpec> uniforms,
            Pattern blockPattern) {
        Matcher blockMatcher = blockPattern.matcher(source);
        while (blockMatcher.find()) {
            String blockName = blockMatcher.group(1);
            String blockBody = blockMatcher.group(2);
            Matcher memberMatcher = BLOCK_MEMBER.matcher(blockBody);
            while (memberMatcher.find()) {
                registerUniform(
                        uniforms,
                        blockName + "." + memberMatcher.group(2),
                        memberMatcher.group(1),
                        memberMatcher.group(3),
                        shaderType,
                        true,
                        false);
            }
        }
    }

    private static void registerUniform(
            Map<String, MutableUniformSpec> uniforms,
            String name,
            String glslType,
            String arraySize,
            ShaderType shaderType,
            boolean blockMember,
            boolean standaloneUniform) {
        if (name == null || glslType == null || shaderType == null) {
            return;
        }
        MutableUniformSpec spec = uniforms.computeIfAbsent(
                name,
                ignored -> new MutableUniformSpec(name, glslType, parseArraySize(arraySize), blockMember, standaloneUniform));
        spec.stageMask.add(shaderType);
    }

    private static int parseArraySize(String rawArraySize) {
        if (rawArraySize == null || rawArraySize.isBlank()) {
            return 1;
        }
        try {
            return Math.max(Integer.parseInt(rawArraySize.trim()), 1);
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    private static String stripComments(String source) {
        String withoutBlocks = COMMENT_BLOCK.matcher(source).replaceAll("");
        return COMMENT_LINE.matcher(withoutBlocks).replaceAll("");
    }

    private static final class MutableUniformSpec {
        private final String name;
        private final String glslType;
        private final int arraySize;
        private final EnumSet<ShaderType> stageMask = EnumSet.noneOf(ShaderType.class);
        private final boolean blockMember;
        private final boolean standaloneUniform;

        private MutableUniformSpec(
                String name,
                String glslType,
                int arraySize,
                boolean blockMember,
                boolean standaloneUniform) {
            this.name = name;
            this.glslType = glslType;
            this.arraySize = arraySize;
            this.blockMember = blockMember;
            this.standaloneUniform = standaloneUniform;
        }

        private String name() {
            return name;
        }

        private ShaderUniformSpec toImmutable() {
            return new ShaderUniformSpec(name, glslType, arraySize, stageMask, blockMember, standaloneUniform);
        }
    }
}

