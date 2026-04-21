package rogo.sketch.vktest;

import rogo.sketch.core.resource.PackFeatureDefinition;
import rogo.sketch.core.resource.ResourceScanProvider;
import rogo.sketch.core.resource.ResourceTypes;
import rogo.sketch.core.util.KeyId;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

final class VkTestClasspathResourceScanProvider implements ResourceScanProvider {
    private static final String VK_TEST_NAMESPACE = "sketch_vktest";
    private static final String CORE_NAMESPACE = "sketch_render";
    private static final Map<KeyId, String> TYPE_TO_PATH = Map.of(
            ResourceTypes.MACRO_TEMPLATE, "render/resource/macro_template",
            ResourceTypes.SHADER_TEMPLATE, "render/resource/shader_template",
            ResourceTypes.TEXTURE, "render/resource/texture",
            ResourceTypes.RENDER_TARGET, "render/resource/render_target",
            ResourceTypes.PARTIAL_RENDER_SETTING, "render/resource/partial_render_setting",
            ResourceTypes.MESH, "render/resource/mesh",
            ResourceTypes.FUNCTION, "render/resource/function",
            ResourceTypes.DRAW_CALL, "render/resource/draw_call");

    @Override
    public Map<KeyId, InputStream> scanResources(KeyId resourceType) {
        String pathPrefix = TYPE_TO_PATH.get(resourceType);
        if (pathPrefix == null) {
            return Collections.emptyMap();
        }

        Map<KeyId, InputStream> resources = new HashMap<>();
        for (String classpathEntry : classpathEntries()) {
            Path entryPath = Path.of(classpathEntry);
            if (Files.isDirectory(entryPath)) {
                scanDirectoryEntry(entryPath, pathPrefix, resourceType, resources);
            } else if (classpathEntry.endsWith(".jar")) {
                scanJarEntry(entryPath, pathPrefix, resourceType, resources);
            }
        }
        return resources;
    }

    @Override
    public Optional<InputStream> getSubResource(KeyId identifier) {
        String resourcePath = toClasspathResourcePath(identifier);
        for (String classpathEntry : classpathEntries()) {
            Path entryPath = Path.of(classpathEntry);
            try {
                if (Files.isDirectory(entryPath)) {
                    Path candidate = entryPath.resolve(resourcePath.replace('/', File.separatorChar));
                    if (Files.exists(candidate) && Files.isRegularFile(candidate)) {
                        return Optional.of(new ByteArrayInputStream(Files.readAllBytes(candidate)));
                    }
                } else if (classpathEntry.endsWith(".jar")) {
                    try (JarFile jarFile = new JarFile(entryPath.toFile())) {
                        JarEntry jarEntry = jarFile.getJarEntry(resourcePath);
                        if (jarEntry != null) {
                            try (InputStream inputStream = jarFile.getInputStream(jarEntry)) {
                                return Optional.of(new ByteArrayInputStream(inputStream.readAllBytes()));
                            }
                        }
                    }
                }
            } catch (IOException ignored) {
            }
        }
        return Optional.empty();
    }

    @Override
    public List<PackFeatureDefinition> getPackFeatures() {
        return Collections.emptyList();
    }

    private void scanDirectoryEntry(Path classpathRoot, String pathPrefix, KeyId resourceType, Map<KeyId, InputStream> resources) {
        Path assetsRoot = classpathRoot.resolve("assets");
        if (!Files.isDirectory(assetsRoot)) {
            return;
        }
        try (var namespaces = Files.list(assetsRoot)) {
            namespaces.filter(Files::isDirectory).forEach(namespaceDir -> {
                String namespace = namespaceDir.getFileName().toString();
                if (!isScannedNamespace(namespace)) {
                    return;
                }
                Path resourceDir = namespaceDir.resolve(pathPrefix.replace('/', File.separatorChar));
                if (!Files.isDirectory(resourceDir)) {
                    return;
                }
                try (var files = Files.walk(resourceDir)) {
                    files.filter(Files::isRegularFile)
                            .filter(path -> isResourceCandidate(path.getFileName().toString(), resourceType))
                            .forEach(path -> putScannedResource(resources, namespace, path));
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }

    private void scanJarEntry(Path jarPath, String pathPrefix, KeyId resourceType, Map<KeyId, InputStream> resources) {
        String normalizedPrefix = "assets/";
        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            var entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                if (!name.startsWith(normalizedPrefix)) {
                    continue;
                }
                int namespaceStart = normalizedPrefix.length();
                int namespaceEnd = name.indexOf('/', namespaceStart);
                if (namespaceEnd < 0) {
                    continue;
                }
                String namespace = name.substring(namespaceStart, namespaceEnd);
                if (!isScannedNamespace(namespace)) {
                    continue;
                }
                String expectedPrefix = normalizedPrefix + namespace + "/" + pathPrefix + "/";
                if (!name.startsWith(expectedPrefix)) {
                    continue;
                }
                String fileName = name.substring(name.lastIndexOf('/') + 1);
                if (!isResourceCandidate(fileName, resourceType)) {
                    continue;
                }
                try (InputStream inputStream = jarFile.getInputStream(entry)) {
                    resources.put(KeyId.of(namespace + ":" + stripExtension(fileName)),
                            new ByteArrayInputStream(inputStream.readAllBytes()));
                }
            }
        } catch (IOException ignored) {
        }
    }

    private void putScannedResource(Map<KeyId, InputStream> resources, String namespace, Path path) {
        try {
            String fileName = path.getFileName().toString();
            resources.put(KeyId.of(namespace + ":" + stripExtension(fileName)),
                    new ByteArrayInputStream(Files.readAllBytes(path)));
        } catch (IOException ignored) {
        }
    }

    private boolean isResourceCandidate(String fileName, KeyId resourceType) {
        String lowerCase = fileName.toLowerCase();
        if (ResourceTypes.MESH.equals(resourceType)) {
            return lowerCase.endsWith(".json") || lowerCase.endsWith(".obj");
        }
        return lowerCase.endsWith(".json");
    }

    private static String stripExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
    }

    private static String toClasspathResourcePath(KeyId identifier) {
        String value = identifier.toString();
        String namespace = "minecraft";
        String path = value;
        int colonIndex = value.indexOf(':');
        if (colonIndex >= 0) {
            namespace = value.substring(0, colonIndex);
            path = value.substring(colonIndex + 1);
        }
        return "assets/" + namespace + "/" + path;
    }

    private static String[] classpathEntries() {
        String classpath = System.getProperty("java.class.path", "");
        return classpath.isBlank() ? new String[0] : classpath.split(File.pathSeparator);
    }

    private static boolean isScannedNamespace(String namespace) {
        return VK_TEST_NAMESPACE.equals(namespace) || CORE_NAMESPACE.equals(namespace);
    }
}
