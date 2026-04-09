package rogo.sketch.backend.vulkan;

import rogo.sketch.core.resource.ResourceTypes;
import rogo.sketch.core.shader.ShaderType;
import rogo.sketch.core.shader.variant.ShaderVariantSpec;
import rogo.sketch.core.util.KeyId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static rogo.sketch.core.shader.ShaderType.FRAGMENT;
import static rogo.sketch.core.shader.ShaderType.VERTEX;

final class VulkanShaderInterfaceDecorator {
    private static final Pattern INTERFACE_DECL_PATTERN = Pattern.compile(
            "(?m)^(\\s*)(?:layout\\s*\\([^)]*\\)\\s+)?((?:flat|smooth|noperspective|centroid|sample)\\s+)?(in|out)\\s+(\\w+)\\s+(\\w+)\\s*(;.*)$");
    private static final Pattern SAMPLER_OR_IMAGE_DECL_PATTERN = Pattern.compile(
            "(?m)^(\\s*)(?:layout\\s*\\(([^)]*)\\)\\s*)?uniform\\s+((?:(?:readonly|writeonly|coherent|volatile|restrict)\\s+)*)((?:[iu]?sampler|[iu]?image)\\w*)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*(\\[[^\\]]+\\])?\\s*(;.*)$");
    private static final Pattern UNIFORM_BLOCK_DECL_PATTERN = Pattern.compile(
            "(?m)^(\\s*)(?:layout\\s*\\(([^)]*)\\)\\s*)?uniform\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\{");
    private static final Pattern STORAGE_BLOCK_DECL_PATTERN = Pattern.compile(
            "(?m)^(\\s*)(?:layout\\s*\\(([^)]*)\\)\\s*)?((?:(?:readonly|writeonly|coherent|volatile|restrict)\\s+)*)buffer\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\{");

    private VulkanShaderInterfaceDecorator() {
    }

    static DecoratedSources decorateGraphicsVariant(ShaderVariantSpec spec) {
        String vertexSource = decorateStageSource(spec, VERTEX);
        String fragmentSource = decorateStageSource(spec, FRAGMENT);
        if (vertexSource == null || fragmentSource == null) {
            return new DecoratedSources(vertexSource, fragmentSource);
        }

        Map<String, Integer> vertexInputLocations = spec.activeAttributeLocations();
        Map<String, Integer> vertexOutputLocations = assignLocations(vertexSource, "out", 0, null);
        Map<String, Integer> fragmentInputLocations = assignLocations(fragmentSource, "in", 0, vertexOutputLocations);
        Map<String, Integer> fragmentOutputLocations = assignLocations(fragmentSource, "out", 0, null);

        String decoratedVertex = injectLocations(vertexSource, "in", vertexInputLocations);
        decoratedVertex = injectLocations(decoratedVertex, "out", vertexOutputLocations);

        String decoratedFragment = injectLocations(fragmentSource, "in", fragmentInputLocations);
        decoratedFragment = injectLocations(decoratedFragment, "out", fragmentOutputLocations);

        return new DecoratedSources(decoratedVertex, decoratedFragment);
    }

    static String decorateComputeVariant(ShaderVariantSpec spec) {
        return decorateStageSource(spec, ShaderType.COMPUTE);
    }

    private static String decorateStageSource(ShaderVariantSpec spec, ShaderType shaderType) {
        if (spec == null || shaderType == null || spec.processedSources() == null) {
            return null;
        }
        String source = spec.processedSources().get(shaderType);
        if (source == null || source.isBlank()) {
            return source;
        }
        Map<KeyId, Map<KeyId, Integer>> resourceBindings = spec.interfaceSpec() != null
                ? spec.interfaceSpec().resourceBindings()
                : Map.of();
        return injectResourceBindings(source, resourceBindings);
    }

    private static String injectResourceBindings(
            String source,
            Map<KeyId, Map<KeyId, Integer>> resourceBindings) {
        if (source == null || source.isBlank() || resourceBindings == null || resourceBindings.isEmpty()) {
            return source;
        }

        String decorated = injectSamplersAndImages(
                source,
                resourceBindings.get(ResourceTypes.TEXTURE),
                resourceBindings.get(ResourceTypes.IMAGE));
        decorated = injectUniformBlocks(decorated, resourceBindings.get(ResourceTypes.UNIFORM_BUFFER));
        decorated = injectStorageBlocks(decorated, resourceBindings.get(ResourceTypes.STORAGE_BUFFER));
        return decorated;
    }

    private static String injectSamplersAndImages(
            String source,
            Map<KeyId, Integer> samplerBindings,
            Map<KeyId, Integer> imageBindings) {
        if (source == null || source.isBlank()) {
            return source;
        }
        Matcher matcher = SAMPLER_OR_IMAGE_DECL_PATTERN.matcher(source);
        StringBuilder result = new StringBuilder();
        int lastEnd = 0;
        while (matcher.find()) {
            result.append(source, lastEnd, matcher.start());
            String indent = matcher.group(1) != null ? matcher.group(1) : "";
            String layout = matcher.group(2);
            String qualifiers = matcher.group(3) != null ? matcher.group(3) : "";
            String type = matcher.group(4);
            String name = matcher.group(5);
            String arraySuffix = matcher.group(6) != null ? matcher.group(6) : "";
            String suffix = matcher.group(7) != null ? matcher.group(7) : ";";

            Integer binding = resolveBinding(
                    type != null && type.contains("image") ? imageBindings : samplerBindings,
                    name);
            if (binding == null || hasBindingLayout(layout)) {
                result.append(matcher.group(0));
            } else {
                result.append(indent)
                        .append(withBindingLayout(layout, binding))
                        .append(" uniform ")
                        .append(qualifiers)
                        .append(type)
                        .append(' ')
                        .append(name)
                        .append(arraySuffix)
                        .append(suffix);
            }
            lastEnd = matcher.end();
        }
        result.append(source.substring(lastEnd));
        return result.toString();
    }

    private static String injectUniformBlocks(String source, Map<KeyId, Integer> uniformBindings) {
        if (source == null || source.isBlank() || uniformBindings == null || uniformBindings.isEmpty()) {
            return source;
        }
        Matcher matcher = UNIFORM_BLOCK_DECL_PATTERN.matcher(source);
        StringBuilder result = new StringBuilder();
        int lastEnd = 0;
        while (matcher.find()) {
            result.append(source, lastEnd, matcher.start());
            String indent = matcher.group(1) != null ? matcher.group(1) : "";
            String layout = matcher.group(2);
            String blockName = matcher.group(3);
            Integer binding = resolveBinding(uniformBindings, blockName);
            if (binding == null || hasBindingLayout(layout)) {
                result.append(matcher.group(0));
            } else {
                result.append(indent)
                        .append(withBindingLayout(layout, binding))
                        .append(" uniform ")
                        .append(blockName)
                        .append(" {");
            }
            lastEnd = matcher.end();
        }
        result.append(source.substring(lastEnd));
        return result.toString();
    }

    private static String injectStorageBlocks(String source, Map<KeyId, Integer> storageBindings) {
        if (source == null || source.isBlank() || storageBindings == null || storageBindings.isEmpty()) {
            return source;
        }
        Matcher matcher = STORAGE_BLOCK_DECL_PATTERN.matcher(source);
        StringBuilder result = new StringBuilder();
        int lastEnd = 0;
        while (matcher.find()) {
            result.append(source, lastEnd, matcher.start());
            String indent = matcher.group(1) != null ? matcher.group(1) : "";
            String layout = matcher.group(2);
            String qualifiers = matcher.group(3) != null ? matcher.group(3) : "";
            String blockName = matcher.group(4);
            Integer binding = resolveBinding(storageBindings, blockName);
            if (binding == null || hasBindingLayout(layout)) {
                result.append(matcher.group(0));
            } else {
                result.append(indent)
                        .append(withBindingLayout(layout, binding))
                        .append(' ')
                        .append(qualifiers)
                        .append("buffer ")
                        .append(blockName)
                        .append(" {");
            }
            lastEnd = matcher.end();
        }
        result.append(source.substring(lastEnd));
        return result.toString();
    }

    private static Integer resolveBinding(Map<KeyId, Integer> bindings, String name) {
        if (bindings == null || bindings.isEmpty() || name == null || name.isBlank()) {
            return null;
        }
        return bindings.get(KeyId.of(name));
    }

    private static boolean hasBindingLayout(String layout) {
        return layout != null && layout.contains("binding");
    }

    private static String withBindingLayout(String layout, int binding) {
        if (layout == null || layout.isBlank()) {
            return "layout(binding = " + binding + ")";
        }
        return "layout(" + layout.trim() + ", binding = " + binding + ")";
    }

    private static Map<String, Integer> assignLocations(
            String source,
            String storageQualifier,
            int startingLocation,
            Map<String, Integer> preferredLocations) {
        Map<String, Integer> assigned = new LinkedHashMap<>();
        Matcher matcher = INTERFACE_DECL_PATTERN.matcher(source);
        int nextLocation = startingLocation;
        while (matcher.find()) {
            if (!storageQualifier.equals(matcher.group(3))) {
                continue;
            }
            String name = matcher.group(5);
            if (name == null || name.startsWith("gl_")) {
                continue;
            }
            Integer preferred = preferredLocations != null ? preferredLocations.get(name) : null;
            int location = preferred != null ? preferred : nextLocation++;
            assigned.put(name, location);
            if (location >= nextLocation) {
                nextLocation = location + 1;
            }
        }
        return assigned;
    }

    private static String injectLocations(String source, String storageQualifier, Map<String, Integer> locations) {
        if (source == null || source.isBlank() || locations.isEmpty()) {
            return source;
        }

        Matcher matcher = INTERFACE_DECL_PATTERN.matcher(source);
        StringBuilder result = new StringBuilder();
        int lastEnd = 0;
        while (matcher.find()) {
            result.append(source, lastEnd, matcher.start());

            String full = matcher.group(0);
            String indent = matcher.group(1);
            String interpolation = matcher.group(2) != null ? matcher.group(2) : "";
            String qualifier = matcher.group(3);
            String type = matcher.group(4);
            String name = matcher.group(5);
            String suffix = matcher.group(6);

            if (!storageQualifier.equals(qualifier)
                    || name == null
                    || name.startsWith("gl_")
                    || full.contains("layout(")
                    || !locations.containsKey(name)) {
                result.append(full);
            } else {
                result.append(indent)
                        .append("layout(location = ")
                        .append(locations.get(name))
                        .append(") ")
                        .append(interpolation)
                        .append(qualifier)
                        .append(' ')
                        .append(type)
                        .append(' ')
                        .append(name)
                        .append(suffix);
            }

            lastEnd = matcher.end();
        }
        result.append(source.substring(lastEnd));
        return result.toString();
    }

    record DecoratedSources(String vertexSource, String fragmentSource) {
    }
}

