package rogo.sketch.core.shader.variant;

import rogo.sketch.core.resource.ResourceTypes;
import rogo.sketch.core.shader.ShaderType;
import rogo.sketch.core.util.KeyId;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ShaderResourceBindingResolver {
    private static final Pattern COMMENT_BLOCK = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern COMMENT_LINE = Pattern.compile("//.*?$", Pattern.MULTILINE);
    private static final Pattern BINDING_LAYOUT = Pattern.compile("\\bbinding\\s*=\\s*(\\d+)");
    private static final Pattern STORAGE_BLOCK = Pattern.compile(
            "(?:layout\\s*\\(([^)]*)\\)\\s*)?(?:readonly\\s+|writeonly\\s+|coherent\\s+|volatile\\s+|restrict\\s+)*buffer\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\{",
            Pattern.MULTILINE);
    private static final Pattern UNIFORM_BLOCK = Pattern.compile(
            "(?:layout\\s*\\(([^)]*)\\)\\s*)?uniform\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\{",
            Pattern.MULTILINE);
    private static final Pattern IMAGE_DECL = Pattern.compile(
            "(?:layout\\s*\\(([^)]*)\\)\\s*)?uniform\\s+(?:readonly\\s+|writeonly\\s+|coherent\\s+|volatile\\s+|restrict\\s+)*(?:[iu]?image\\w*)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*(?:\\[[^\\]]+\\])?\\s*;",
            Pattern.MULTILINE);
    private static final Pattern SAMPLER_DECL = Pattern.compile(
            "(?:layout\\s*\\(([^)]*)\\)\\s*)?uniform\\s+(?:[iu]?sampler\\w*)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*(?:\\[[^\\]]+\\])?\\s*;",
            Pattern.MULTILINE);

    private ShaderResourceBindingResolver() {
    }

    static Map<KeyId, Map<KeyId, Integer>> resolve(Map<ShaderType, String> processedSources) {
        return resolve(processedSources, Map.of());
    }

    static Map<KeyId, Map<KeyId, Integer>> resolve(
            Map<ShaderType, String> processedSources,
            Map<KeyId, Map<KeyId, Integer>> declaredBindings) {
        Map<KeyId, Map<KeyId, Integer>> bindings = new LinkedHashMap<>();
        Map<KeyId, LinkedHashSet<KeyId>> declarationsByType = new LinkedHashMap<>();
        if (processedSources == null || processedSources.isEmpty()) {
            return bindings;
        }

        for (String source : processedSources.values()) {
            if (source == null || source.isBlank()) {
                continue;
            }
            String sanitized = stripComments(source);
            collectDeclarations(declarationsByType, ResourceTypes.STORAGE_BUFFER, STORAGE_BLOCK, sanitized);
            collectDeclarations(declarationsByType, ResourceTypes.UNIFORM_BUFFER, UNIFORM_BLOCK, sanitized);
            collectDeclarations(declarationsByType, ResourceTypes.IMAGE, IMAGE_DECL, sanitized);
            collectDeclarations(declarationsByType, ResourceTypes.TEXTURE, SAMPLER_DECL, sanitized);
        }

        validateDeclaredBindings(declarationsByType, declaredBindings);

        int nextGlobalBinding = 0;
        HashSet<Integer> usedBindings = collectDeclaredBindingSlots(declaredBindings);
        for (Map.Entry<KeyId, LinkedHashSet<KeyId>> entry : declarationsByType.entrySet()) {
            KeyId resourceType = ResourceTypes.normalize(entry.getKey());
            Map<KeyId, Integer> typeBindings = new LinkedHashMap<>();
            Map<KeyId, Integer> declaredTypeBindings = declaredBindings != null
                    ? declaredBindings.get(resourceType)
                    : null;
            for (KeyId bindingName : entry.getValue()) {
                Integer declaredBinding = declaredTypeBindings != null ? declaredTypeBindings.get(bindingName) : null;
                int binding;
                if (declaredBinding != null) {
                    binding = declaredBinding;
                } else {
                    while (usedBindings.contains(nextGlobalBinding)) {
                        nextGlobalBinding++;
                    }
                    binding = nextGlobalBinding++;
                    usedBindings.add(binding);
                }
                typeBindings.put(bindingName, binding);
            }
            if (!typeBindings.isEmpty()) {
                bindings.put(resourceType, typeBindings);
            }
        }

        return bindings;
    }

    private static void collectDeclarations(
            Map<KeyId, LinkedHashSet<KeyId>> declarationsByType,
            KeyId resourceType,
            Pattern pattern,
            String source) {
        Matcher matcher = pattern.matcher(source);
        while (matcher.find()) {
            KeyId bindingName = KeyId.of(matcher.group(2));
            declarationsByType
                    .computeIfAbsent(ResourceTypes.normalize(resourceType), ignored -> new LinkedHashSet<>())
                    .add(bindingName);
        }
    }

    private static void validateDeclaredBindings(
            Map<KeyId, LinkedHashSet<KeyId>> declarationsByType,
            Map<KeyId, Map<KeyId, Integer>> declaredBindings) {
        validateGlobalBindingUniqueness(declaredBindings);
        boolean hasDeclarations = declarationsByType != null && !declarationsByType.isEmpty();
        boolean hasDeclaredBindings = declaredBindings != null && !declaredBindings.isEmpty();
        if (hasDeclarations && hasDeclaredBindings) {
            for (Map.Entry<KeyId, LinkedHashSet<KeyId>> entry : declarationsByType.entrySet()) {
                KeyId resourceType = ResourceTypes.normalize(entry.getKey());
                Map<KeyId, Integer> declaredTypeBindings = declaredBindings.get(resourceType);
                for (KeyId name : entry.getValue()) {
                    if (declaredTypeBindings == null || !declaredTypeBindings.containsKey(name)) {
                        throw new IllegalArgumentException(
                                "Shader template declares " + resourceType
                                        + " resource " + name
                                        + " but resourceBindings is missing an entry for it");
                    }
                }
            }
            return;
        }
        for (Map.Entry<KeyId, LinkedHashSet<KeyId>> entry : declarationsByType.entrySet()) {
            KeyId resourceType = ResourceTypes.normalize(entry.getKey());
            List<KeyId> names = List.copyOf(entry.getValue());
            if (names.size() <= 1) {
                continue;
            }
            Map<KeyId, Integer> declaredTypeBindings = declaredBindings != null
                    ? declaredBindings.get(resourceType)
                    : null;
            if (declaredTypeBindings == null || declaredTypeBindings.isEmpty()) {
                throw new IllegalArgumentException(
                        "Shader template declares multiple " + resourceType
                                + " resources " + names
                                + " but does not define resourceBindings for that type");
            }
            for (KeyId name : names) {
                if (!declaredTypeBindings.containsKey(name)) {
                    throw new IllegalArgumentException(
                            "Shader template declares multiple " + resourceType
                                    + " resources " + names
                                    + " but resourceBindings is missing an entry for " + name);
                }
            }
        }
    }

    private static void validateGlobalBindingUniqueness(Map<KeyId, Map<KeyId, Integer>> declaredBindings) {
        if (declaredBindings == null || declaredBindings.isEmpty()) {
            return;
        }
        Map<Integer, BindingKey> used = new HashMap<>();
        for (Map.Entry<KeyId, Map<KeyId, Integer>> typeEntry : declaredBindings.entrySet()) {
            KeyId resourceType = ResourceTypes.normalize(typeEntry.getKey());
            Map<KeyId, Integer> typeBindings = typeEntry.getValue();
            if (typeBindings == null) {
                continue;
            }
            for (Map.Entry<KeyId, Integer> bindingEntry : typeBindings.entrySet()) {
                Integer slot = bindingEntry.getValue();
                if (slot == null || slot < 0) {
                    throw new IllegalArgumentException("resourceBindings slot must be >= 0 for " + resourceType + "." + bindingEntry.getKey());
                }
                BindingKey previous = used.putIfAbsent(slot, new BindingKey(resourceType, bindingEntry.getKey()));
                if (previous != null) {
                    throw new IllegalArgumentException(
                            "resourceBindings slot " + slot + " is reused by "
                                    + previous.resourceType() + "." + previous.bindingName()
                                    + " and " + resourceType + "." + bindingEntry.getKey());
                }
            }
        }
    }

    private static HashSet<Integer> collectDeclaredBindingSlots(Map<KeyId, Map<KeyId, Integer>> declaredBindings) {
        HashSet<Integer> used = new HashSet<>();
        if (declaredBindings == null || declaredBindings.isEmpty()) {
            return used;
        }
        for (Map<KeyId, Integer> typeBindings : declaredBindings.values()) {
            if (typeBindings != null) {
                used.addAll(typeBindings.values());
            }
        }
        return used;
    }

    private static String stripComments(String source) {
        String withoutBlocks = COMMENT_BLOCK.matcher(source).replaceAll("");
        return COMMENT_LINE.matcher(withoutBlocks).replaceAll("");
    }

    private record BindingKey(KeyId resourceType, KeyId bindingName) {
    }
}

