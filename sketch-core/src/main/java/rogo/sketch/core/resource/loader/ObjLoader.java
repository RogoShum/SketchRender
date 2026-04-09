package rogo.sketch.core.resource.loader;

import rogo.sketch.core.data.MeshIndexMode;
import rogo.sketch.core.data.PrimitiveType;
import rogo.sketch.core.model.MeshGroup;
import rogo.sketch.core.resource.ResourceTypes;
import rogo.sketch.core.util.KeyId;
import rogo.sketch.core.vertex.DefaultDataFormats;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Formal OBJ loader.
 * <p>
 * OBJ is parsed into canonical local-indexed submeshes and then compiled through the
 * same canonical mesh path as JSON mesh resources.
 */
public class ObjLoader implements ResourceLoader<MeshGroup> {

    @Override
    public MeshGroup load(ResourceLoadContext context) {
        BufferedReader reader = context.getReader();
        if (reader == null) {
            return null;
        }

        try (reader) {
            ObjParseState state = new ObjParseState(context.getResourceId());
            String line;
            while ((line = reader.readLine()) != null) {
                parseLine(state, line);
            }

            MeshGroup meshGroup = new MeshGroup(
                    state.meshName(),
                    PrimitiveType.TRIANGLES,
                    DefaultDataFormats.OBJ);
            CanonicalMeshCompiler.compile(
                    context.getResourceId(),
                    meshGroup,
                    MeshIndexMode.EXPLICIT_LOCAL,
                    state.buildCanonicalSubMeshes());
            meshGroup.setMetadata("indexMode", MeshIndexMode.EXPLICIT_LOCAL.name().toLowerCase(Locale.ROOT));
            meshGroup.setMetadata("sourceFormat", "obj");
            return meshGroup;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse OBJ mesh " + context.getResourceId(), e);
        }
    }

    @Override
    public KeyId getResourceType() {
        return ResourceTypes.MESH;
    }

    private void parseLine(ObjParseState state, String rawLine) throws IOException {
        String line = stripComment(rawLine).trim();
        if (line.isEmpty()) {
            return;
        }

        String[] tokens = line.split("\\s+");
        if (tokens.length == 0) {
            return;
        }

        switch (tokens[0]) {
            case "v" -> state.addPosition(parseFloat(tokens, 1), parseFloat(tokens, 2), parseFloat(tokens, 3));
            case "vt" -> state.addTexCoord(parseFloat(tokens, 1), parseFloat(tokens, 2));
            case "vn" -> state.addNormal(parseFloat(tokens, 1), parseFloat(tokens, 2), parseFloat(tokens, 3));
            case "o" -> state.setObjectName(joinTokens(tokens, 1));
            case "g" -> state.setGroupName(joinTokens(tokens, 1));
            case "usemtl" -> state.setMaterialName(joinTokens(tokens, 1));
            case "f" -> parseFace(state, tokens);
            default -> {
                // Ignore unsupported directives for now. OBJ support is intentionally scoped.
            }
        }
    }

    private void parseFace(ObjParseState state, String[] tokens) throws IOException {
        if (tokens.length < 4) {
            throw new IOException("OBJ face requires at least three vertices");
        }
        FaceVertex[] face = new FaceVertex[tokens.length - 1];
        for (int i = 1; i < tokens.length; i++) {
            face[i - 1] = FaceVertex.parse(tokens[i], state);
        }
        for (int i = 1; i < face.length - 1; i++) {
            state.currentSubMesh().addTriangle(face[0], face[i], face[i + 1], state);
        }
    }

    private float parseFloat(String[] tokens, int index) throws IOException {
        if (index >= tokens.length) {
            throw new IOException("OBJ directive missing numeric component");
        }
        return Float.parseFloat(tokens[index]);
    }

    private String stripComment(String line) {
        int commentIndex = line.indexOf('#');
        return commentIndex >= 0 ? line.substring(0, commentIndex) : line;
    }

    private String joinTokens(String[] tokens, int startInclusive) {
        if (startInclusive >= tokens.length) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = startInclusive; i < tokens.length; i++) {
            if (i > startInclusive) {
                builder.append('_');
            }
            builder.append(tokens[i]);
        }
        return builder.toString();
    }

    private record FaceVertex(int positionIndex, int texCoordIndex, int normalIndex) {
        private static FaceVertex parse(String token, ObjParseState state) throws IOException {
            String[] parts = token.split("/", -1);
            int positionIndex = resolveObjIndex(parts, 0, state.positionCount(), "position");
            int texCoordIndex = resolveObjIndex(parts, 1, state.texCoordCount(), "texCoord");
            int normalIndex = resolveObjIndex(parts, 2, state.normalCount(), "normal");
            return new FaceVertex(positionIndex, texCoordIndex, normalIndex);
        }

        private static int resolveObjIndex(String[] parts, int partIndex, int currentCount, String label) throws IOException {
            if (partIndex >= parts.length || parts[partIndex].isEmpty()) {
                return -1;
            }
            int raw = Integer.parseInt(parts[partIndex]);
            int resolved = raw > 0 ? raw - 1 : currentCount + raw;
            if (resolved < 0 || resolved >= currentCount) {
                throw new IOException("OBJ " + label + " index out of range: " + raw);
            }
            return resolved;
        }
    }

    private static final class ObjParseState {
        private final KeyId resourceId;
        private final List<float[]> positions = new ArrayList<>();
        private final List<float[]> texCoords = new ArrayList<>();
        private final List<float[]> normals = new ArrayList<>();
        private final Map<String, ObjSubMesh> subMeshes = new LinkedHashMap<>();
        private String objectName = "main";
        private String groupName = "main";
        private String materialName = "default";

        private ObjParseState(KeyId resourceId) {
            this.resourceId = resourceId;
        }

        private void addPosition(float x, float y, float z) {
            positions.add(new float[]{x, y, z});
        }

        private void addTexCoord(float u, float v) {
            texCoords.add(new float[]{u, v});
        }

        private void addNormal(float x, float y, float z) {
            normals.add(new float[]{x, y, z});
        }

        private void setObjectName(String objectName) {
            if (!objectName.isBlank()) {
                this.objectName = sanitize(objectName);
            }
        }

        private void setGroupName(String groupName) {
            if (!groupName.isBlank()) {
                this.groupName = sanitize(groupName);
            }
        }

        private void setMaterialName(String materialName) {
            this.materialName = materialName == null || materialName.isBlank()
                    ? "default"
                    : sanitize(materialName);
        }

        private ObjSubMesh currentSubMesh() {
            String key = objectName + "__" + groupName + "__" + materialName;
            return subMeshes.computeIfAbsent(key, ignored -> new ObjSubMesh(key, materialName));
        }

        private int positionCount() {
            return positions.size();
        }

        private int texCoordCount() {
            return texCoords.size();
        }

        private int normalCount() {
            return normals.size();
        }

        private float[] position(int index) {
            return positions.get(index);
        }

        private float[] texCoord(int index) {
            return index >= 0 ? texCoords.get(index) : null;
        }

        private float[] normal(int index) {
            return index >= 0 ? normals.get(index) : null;
        }

        private String meshName() {
            return resourceId.toString();
        }

        private List<CanonicalMeshCompiler.CanonicalSubMesh> buildCanonicalSubMeshes() {
            if (subMeshes.isEmpty()) {
                throw new IllegalArgumentException("OBJ mesh " + resourceId + " produced no submeshes");
            }
            List<CanonicalMeshCompiler.CanonicalSubMesh> result = new ArrayList<>(subMeshes.size());
            for (ObjSubMesh subMesh : subMeshes.values()) {
                Map<String, Object> metadata = new LinkedHashMap<>();
                if (!subMesh.materialName().isBlank()) {
                    metadata.put("material", subMesh.materialName());
                }
                result.add(new CanonicalMeshCompiler.CanonicalSubMesh(
                        subMesh.name(),
                        subMesh.vertices(),
                        subMesh.indices(),
                        metadata));
            }
            return result;
        }

        private static String sanitize(String raw) {
            String lowered = raw.toLowerCase(Locale.ROOT);
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
    }

    private record VertexTuple(int positionIndex, int texCoordIndex, int normalIndex) {
    }

    private static final class ObjSubMesh {
        private final String name;
        private final String materialName;
        private final Map<VertexTuple, Integer> vertexLookup = new LinkedHashMap<>();
        private final List<Float> vertices = new ArrayList<>();
        private final List<Integer> indices = new ArrayList<>();

        private ObjSubMesh(String name, String materialName) {
            this.name = name;
            this.materialName = materialName;
        }

        private void addTriangle(FaceVertex a, FaceVertex b, FaceVertex c, ObjParseState state) {
            indices.add(resolveVertex(a, state));
            indices.add(resolveVertex(b, state));
            indices.add(resolveVertex(c, state));
        }

        private int resolveVertex(FaceVertex faceVertex, ObjParseState state) {
            VertexTuple key = new VertexTuple(faceVertex.positionIndex(), faceVertex.texCoordIndex(), faceVertex.normalIndex());
            Integer existing = vertexLookup.get(key);
            if (existing != null) {
                return existing;
            }

            int newIndex = vertexLookup.size();
            vertexLookup.put(key, newIndex);

            float[] position = state.position(faceVertex.positionIndex());
            float[] texCoord = state.texCoord(faceVertex.texCoordIndex());
            float[] normal = state.normal(faceVertex.normalIndex());

            vertices.add(position[0]);
            vertices.add(position[1]);
            vertices.add(position[2]);
            vertices.add(texCoord != null ? texCoord[0] : 0.0f);
            vertices.add(texCoord != null ? texCoord[1] : 0.0f);
            vertices.add(normal != null ? normal[0] : 0.0f);
            vertices.add(normal != null ? normal[1] : 1.0f);
            vertices.add(normal != null ? normal[2] : 0.0f);
            return newIndex;
        }

        private String name() {
            return name;
        }

        private String materialName() {
            return materialName;
        }

        private List<Float> vertices() {
            return vertices;
        }

        private List<Integer> indices() {
            return indices;
        }
    }
}

