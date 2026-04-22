package rogo.sketch.config;

import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class SketchConfigService {
    private final Path rootDirectory;
    private final @Nullable String optionalFolder;
    private final ConfigLayoutStrategy layoutStrategy;
    private final String defaultNamespace;
    private final Map<Path, ConfigDocument> documents = new LinkedHashMap<>();

    public SketchConfigService(
            Path rootDirectory,
            @Nullable String optionalFolder,
            ConfigLayoutStrategy layoutStrategy,
            String defaultNamespace) {
        this.rootDirectory = Objects.requireNonNull(rootDirectory, "rootDirectory");
        this.optionalFolder = optionalFolder;
        this.layoutStrategy = Objects.requireNonNull(layoutStrategy, "layoutStrategy");
        this.defaultNamespace = Objects.requireNonNull(defaultNamespace, "defaultNamespace");
    }

    public <T> T get(ConfigScope scope, String key, PropertyCodec<T> codec, T fallback) {
        String raw = documentFor(scope).get(resolveKey(scope, key));
        if (raw == null) {
            return fallback;
        }
        try {
            return codec.decode(raw);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    public <T> void set(ConfigScope scope, String key, PropertyCodec<T> codec, T value) {
        ConfigDocument document = documentFor(scope);
        document.set(resolveKey(scope, key), codec.encode(value));
        document.save("Sketch configuration");
    }

    public boolean contains(ConfigScope scope, String key) {
        return documentFor(scope).contains(resolveKey(scope, key));
    }

    public Path resolvePath(ConfigScope scope) {
        String namespace = resolveNamespace(scope);
        Path baseDir = optionalFolder == null || optionalFolder.isBlank()
                ? rootDirectory
                : rootDirectory.resolve(optionalFolder);

        return switch (layoutStrategy) {
            case SINGLE_FILE -> baseDir.resolve(defaultNamespace + ".properties");
            case BY_NAMESPACE -> baseDir.resolve(namespace + ".properties");
            case BY_NAMESPACE_AND_MODULE -> baseDir.resolve(namespace)
                    .resolve(resolveModule(scope) + ".properties");
            case BY_NAMESPACE_PREFIXED_MODULE_FILE -> baseDir.resolve(namespace + "_" + resolveModule(scope) + ".properties");
        };
    }

    private String resolveKey(ConfigScope scope, String key) {
        String namespace = resolveNamespace(scope);
        String module = resolveModule(scope);
        return switch (layoutStrategy) {
            case SINGLE_FILE -> namespace + "." + module + "." + key;
            case BY_NAMESPACE -> module + "." + key;
            case BY_NAMESPACE_AND_MODULE -> key;
            case BY_NAMESPACE_PREFIXED_MODULE_FILE -> key;
        };
    }

    private String resolveNamespace(ConfigScope scope) {
        return scope.namespace() == null || scope.namespace().isBlank() ? defaultNamespace : scope.namespace();
    }

    private String resolveModule(ConfigScope scope) {
        return scope.moduleId() == null || scope.moduleId().isBlank() ? "global" : scope.moduleId();
    }

    private ConfigDocument documentFor(ConfigScope scope) {
        Path path = resolvePath(scope);
        return documents.computeIfAbsent(path, ConfigDocument::new);
    }
}
