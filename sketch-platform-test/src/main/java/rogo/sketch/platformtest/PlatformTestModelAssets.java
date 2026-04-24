package rogo.sketch.platformtest;

import com.google.gson.Gson;
import rogo.sketch.core.model.MeshGroup;
import rogo.sketch.core.resource.GraphicsResourceManager;
import rogo.sketch.core.resource.loader.MeshLoader;
import rogo.sketch.core.resource.loader.ResourceLoadContext;
import rogo.sketch.core.util.KeyId;

import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

final class PlatformTestModelAssets {
    private static final String ACCEPT_PROPERTY = "acceptPlatformTestOptionalModelLicenses";
    private static final List<OptionalSceneModel> OPTIONAL_SCENE_MODELS = List.of(
            new OptionalSceneModel(
                    "sponza",
                    "sponza",
                    "sponza.obj",
                    "https://casual-effects.com/g3d/data10/common/model/crytek_sponza/sponza.zip",
                    "CC BY 3.0",
                    "McGuire Computer Graphics Archive / Crytek Sponza"),
            new OptionalSceneModel(
                    "rungholt",
                    "rungholt",
                    "rungholt.obj",
                    "https://casual-effects.com/g3d/data10/research/model/rungholt/rungholt.zip",
                    "CC BY 3.0",
                    "McGuire Computer Graphics Archive / Rungholt scene"));

    private static boolean missingNoticePrinted;

    private PlatformTestModelAssets() {
    }

    static void validateOptionalSceneModelsIfPresent(
            Gson gson,
            GraphicsResourceManager resourceManager,
            PrintStream out) {
        Path root = optionalModelRoot();
        boolean missing = false;
        for (OptionalSceneModel model : OPTIONAL_SCENE_MODELS) {
            Path objPath = root.resolve(model.directory()).resolve(model.objFile());
            if (!Files.isRegularFile(objPath)) {
                missing = true;
                continue;
            }
            MeshGroup meshGroup = loadObjModel(model, objPath, gson, resourceManager);
            if (out != null) {
                out.println("platform-test optional scene model available: "
                        + model.name()
                        + " subMeshes="
                        + meshGroup.getSubMeshes().size()
                        + " path="
                        + objPath.toAbsolutePath());
            }
        }
        if (missing) {
            printMissingOptionalSceneModelNotice(out);
        }
    }

    static Optional<LoadedSceneModel> loadFirstAvailableScene(
            Gson gson,
            GraphicsResourceManager resourceManager,
            PrintStream out) {
        Path root = optionalModelRoot();
        for (OptionalSceneModel model : OPTIONAL_SCENE_MODELS) {
            Path objPath = root.resolve(model.directory()).resolve(model.objFile());
            if (!Files.isRegularFile(objPath)) {
                continue;
            }
            MeshGroup meshGroup = loadObjModel(model, objPath, gson, resourceManager);
            if (out != null) {
                out.println("platform-test real scene model selected: "
                        + model.name()
                        + " subMeshes="
                        + meshGroup.getSubMeshes().size()
                        + " path="
                        + objPath.toAbsolutePath());
                out.println("  license: " + model.license() + "; attribution: " + model.attribution());
            }
            return Optional.of(new LoadedSceneModel(
                    model.name(),
                    objPath,
                    meshGroup,
                    loadDiffuseTextureMap(objPath),
                    model.license(),
                    model.attribution()));
        }
        printMissingOptionalSceneModelNotice(out);
        return Optional.empty();
    }

    private static MeshGroup loadObjModel(
            OptionalSceneModel model,
            Path objPath,
            Gson gson,
            GraphicsResourceManager resourceManager) {
        MeshLoader meshLoader = new MeshLoader();
        try (InputStream stream = Files.newInputStream(objPath)) {
            ResourceLoadContext context = new ResourceLoadContext(
                    KeyId.of("sketch_platformtest:optional_scene_" + model.name()),
                    stream,
                    gson,
                    id -> Optional.empty(),
                    resourceManager);
            MeshGroup meshGroup = meshLoader.load(context);
            if (meshGroup == null || meshGroup.getSubMeshes().isEmpty()) {
                throw new IllegalStateException("Optional scene model produced no submeshes: " + objPath);
            }
            return meshGroup;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to validate optional scene model " + model.name() + ": " + objPath, e);
        }
    }

    private static Map<String, Path> loadDiffuseTextureMap(Path objPath) {
        Map<String, Path> textures = new LinkedHashMap<>();
        for (Path mtlPath : materialLibraries(objPath)) {
            parseDiffuseTextures(mtlPath, textures);
        }
        return Map.copyOf(textures);
    }

    private static List<Path> materialLibraries(Path objPath) {
        if (objPath == null || !Files.isRegularFile(objPath)) {
            return List.of();
        }
        List<Path> libraries = new java.util.ArrayList<>();
        try (var lines = Files.lines(objPath, StandardCharsets.UTF_8)) {
            lines.map(String::trim)
                    .filter(line -> line.startsWith("mtllib "))
                    .map(line -> line.substring("mtllib ".length()).trim())
                    .filter(value -> !value.isBlank())
                    .forEach(value -> libraries.add(objPath.getParent().resolve(value).normalize()));
        } catch (Exception ignored) {
        }
        if (libraries.isEmpty()) {
            Path sameBase = objPath.resolveSibling(stripExtension(objPath.getFileName().toString()) + ".mtl");
            if (Files.isRegularFile(sameBase)) {
                libraries.add(sameBase);
            }
        }
        return libraries.stream()
                .filter(Files::isRegularFile)
                .toList();
    }

    private static void parseDiffuseTextures(Path mtlPath, Map<String, Path> textures) {
        if (mtlPath == null || textures == null || !Files.isRegularFile(mtlPath)) {
            return;
        }
        String currentMaterial = null;
        try (var reader = Files.newBufferedReader(mtlPath, StandardCharsets.UTF_8)) {
            String rawLine;
            while ((rawLine = reader.readLine()) != null) {
                String line = stripComment(rawLine).trim();
                if (line.isBlank()) {
                    continue;
                }
                if (line.startsWith("newmtl ")) {
                    currentMaterial = sanitizeObjToken(line.substring("newmtl ".length()).trim());
                    continue;
                }
                if (currentMaterial == null || !line.startsWith("map_Kd ")) {
                    continue;
                }
                String textureToken = lastTexturePathToken(line.substring("map_Kd ".length()).trim());
                if (textureToken == null || textureToken.isBlank()) {
                    continue;
                }
                Path texturePath = mtlPath.getParent().resolve(textureToken).normalize();
                if (Files.isRegularFile(texturePath)) {
                    textures.putIfAbsent(currentMaterial, texturePath);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static String stripComment(String line) {
        int commentIndex = line != null ? line.indexOf('#') : -1;
        return commentIndex >= 0 ? line.substring(0, commentIndex) : line;
    }

    private static String lastTexturePathToken(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] tokens = raw.trim().split("\\s+");
        for (int index = tokens.length - 1; index >= 0; index--) {
            String token = tokens[index];
            if (!token.isBlank() && !token.startsWith("-")) {
                return token;
            }
        }
        return null;
    }

    private static String sanitizeObjToken(String raw) {
        String lowered = raw == null ? "" : raw.toLowerCase(Locale.ROOT);
        StringBuilder builder = new StringBuilder(lowered.length());
        for (int i = 0; i < lowered.length(); i++) {
            char c = lowered.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == ':' || c == '/' || c == '-') {
                builder.append(c);
            } else {
                builder.append('_');
            }
        }
        return builder.toString();
    }

    private static String stripExtension(String filename) {
        int dot = filename != null ? filename.lastIndexOf('.') : -1;
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    private static void printMissingOptionalSceneModelNotice(PrintStream out) {
        if (missingNoticePrinted || out == null) {
            return;
        }
        missingNoticePrinted = true;
        out.println("platform-test optional scene models are not installed.");
        out.println("  This is expected for normal builds; Sponza and Rungholt are CC BY 3.0 assets and are not bundled.");
        out.println("  To install them locally, run:");
        out.println("    .\\gradlew.bat :sketch-platform-test:downloadPlatformTestOptionalModels -P"
                + ACCEPT_PROPERTY
                + "=true --console=plain");
        out.println("  The task uses plan/model/*.zip when those local archives exist; otherwise it downloads from the listed URLs.");
        for (OptionalSceneModel model : OPTIONAL_SCENE_MODELS) {
            out.println("  " + model.name() + ": " + model.url());
            out.println("    license: " + model.license() + "; attribution: " + model.attribution());
        }
    }

    private static Path optionalModelRoot() {
        String override = System.getProperty("sketch.platformtest.modelRoot");
        if (override != null && !override.isBlank()) {
            return Path.of(override);
        }
        return Path.of("models", "external");
    }

    private record OptionalSceneModel(
            String name,
            String directory,
            String objFile,
            String url,
            String license,
            String attribution) {
    }

    record LoadedSceneModel(
            String name,
            Path objPath,
            MeshGroup meshGroup,
            Map<String, Path> diffuseTextures,
            String license,
            String attribution) {
    }
}
