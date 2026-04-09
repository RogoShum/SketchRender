package rogo.sketch.core.resource.loader;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.joml.Matrix4f;
import rogo.sketch.core.data.MeshIndexMode;
import rogo.sketch.core.data.PrimitiveType;
import rogo.sketch.core.data.layout.StructLayout;
import rogo.sketch.core.data.type.ValueType;
import rogo.sketch.core.model.MeshBone;
import rogo.sketch.core.model.MeshGroup;
import rogo.sketch.core.resource.ResourceTypes;
import rogo.sketch.core.util.KeyId;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loader for canonical mesh resources.
 * <p>
 * Mesh resources are now strict:
 * - JSON meshes must use {@code primitiveTopology} and {@code indexMode}
 * - OBJ meshes are parsed into the same canonical submesh form
 * - old global indices / recalculateIndices semantics are rejected
 */
public class MeshLoader implements ResourceLoader<MeshGroup> {
    private final ObjLoader objLoader = new ObjLoader();

    @Override
    public KeyId getResourceType() {
        return ResourceTypes.MESH;
    }

    @Override
    public MeshGroup load(ResourceLoadContext context) {
        String source = context.getString();
        if (source == null || source.isBlank()) {
            return null;
        }
        String trimmed = source.stripLeading();
        if (!trimmed.startsWith("{")) {
            return objLoader.load(context);
        }

        KeyId keyId = context.getResourceId();
        JsonObject json = context.getJson();
        if (json == null) {
            return null;
        }
        if (json.has("primitiveType")) {
            throw new IllegalArgumentException("Mesh " + keyId + " uses deprecated field 'primitiveType'");
        }
        if (json.has("recalculateIndices")) {
            throw new IllegalArgumentException("Mesh " + keyId + " uses deprecated field 'recalculateIndices'");
        }

        String name = json.has("name") ? json.get("name").getAsString() : keyId.toString();
        PrimitiveType primitiveType = parsePrimitiveType(requireString(json, "primitiveTopology"));
        MeshIndexMode indexMode = parseIndexMode(requireString(json, "indexMode"));
        StructLayout format = resolveVertexFormat(json);
        MeshGroup meshGroup = new MeshGroup(name, primitiveType, format);

        if (json.has("bones")) {
            loadBones(meshGroup, json.getAsJsonArray("bones"));
        }

        List<CanonicalMeshCompiler.CanonicalSubMesh> subMeshes = parseSubMeshes(
                meshGroup.getName(),
                primitiveType,
                indexMode,
                format,
                json.getAsJsonArray("subMeshes"));
        CanonicalMeshCompiler.compile(keyId, meshGroup, indexMode, subMeshes);

        if (json.has("metadata") && json.get("metadata").isJsonObject()) {
            loadMetadata(meshGroup, json.getAsJsonObject("metadata"));
        }
        meshGroup.setMetadata("indexMode", indexMode.name().toLowerCase());
        return meshGroup;
    }

    private String requireString(JsonObject json, String key) {
        if (json == null || !json.has(key) || json.get(key).isJsonNull()) {
            throw new IllegalArgumentException("Missing required mesh field '" + key + "'");
        }
        return json.get(key).getAsString();
    }

    private PrimitiveType parsePrimitiveType(String type) {
        return switch (type.toLowerCase()) {
            case "triangles" -> PrimitiveType.TRIANGLES;
            case "quads" -> PrimitiveType.QUADS;
            case "lines" -> PrimitiveType.LINES;
            case "points" -> PrimitiveType.POINTS;
            case "triangle_strip" -> PrimitiveType.TRIANGLE_STRIP;
            case "triangle_fan" -> PrimitiveType.TRIANGLE_FAN;
            case "line_strip" -> PrimitiveType.LINE_STRIP;
            case "line_loop" -> PrimitiveType.LINE_LOOP;
            default -> throw new IllegalArgumentException("Unknown primitive topology: " + type);
        };
    }

    private MeshIndexMode parseIndexMode(String rawValue) {
        return switch (rawValue.toLowerCase()) {
            case "none" -> MeshIndexMode.NONE;
            case "explicit_local" -> MeshIndexMode.EXPLICIT_LOCAL;
            case "generated" -> MeshIndexMode.GENERATED;
            default -> throw new IllegalArgumentException("Unknown mesh indexMode: " + rawValue);
        };
    }

    private StructLayout resolveVertexFormat(JsonObject json) {
        if (json.has("vertexFormat") && json.get("vertexFormat").isJsonObject()) {
            return parseDataFormat(json.getAsJsonObject("vertexFormat"));
        }
        if (json.has("subMeshes") && json.get("subMeshes").isJsonArray()) {
            JsonArray subMeshes = json.getAsJsonArray("subMeshes");
            if (!subMeshes.isEmpty()) {
                JsonObject first = subMeshes.get(0).getAsJsonObject();
                if (first.has("vertexFormat") && first.get("vertexFormat").isJsonObject()) {
                    return parseDataFormat(first.getAsJsonObject("vertexFormat"));
                }
            }
        }
        throw new IllegalArgumentException("Missing vertexFormat definition");
    }

    private List<CanonicalMeshCompiler.CanonicalSubMesh> parseSubMeshes(
            String meshName,
            PrimitiveType primitiveType,
            MeshIndexMode indexMode,
            StructLayout format,
            JsonArray subMeshesArray) {
        if (subMeshesArray == null || subMeshesArray.isEmpty()) {
            throw new IllegalArgumentException("Mesh " + meshName + " has no subMeshes");
        }

        List<CanonicalMeshCompiler.CanonicalSubMesh> subMeshes = new ArrayList<>(subMeshesArray.size());
        int expectedFloatsPerVertex = sourceFloatsPerVertex(format);
        for (JsonElement element : subMeshesArray) {
            JsonObject subMeshObj = element.getAsJsonObject();
            String name = requireString(subMeshObj, "name");

            if (subMeshObj.has("primitiveType")) {
                throw new IllegalArgumentException("SubMesh " + meshName + "#" + name + " uses deprecated field 'primitiveType'");
            }
            if (subMeshObj.has("recalculateIndices")) {
                throw new IllegalArgumentException("SubMesh " + meshName + "#" + name + " uses deprecated field 'recalculateIndices'");
            }

            if (subMeshObj.has("vertexFormat") && subMeshObj.get("vertexFormat").isJsonObject()) {
                StructLayout subFormat = parseDataFormat(subMeshObj.getAsJsonObject("vertexFormat"));
                if (!subFormat.equals(format)) {
                    throw new IllegalArgumentException("SubMesh format mismatch in " + meshName + "#" + name);
                }
            }

            List<Float> vertices = new ArrayList<>();
            JsonArray verticesArray = subMeshObj.getAsJsonArray("vertices");
            if (verticesArray == null || verticesArray.isEmpty()) {
                throw new IllegalArgumentException("SubMesh " + meshName + "#" + name + " has no vertices");
            }
            for (JsonElement vertexElement : verticesArray) {
                vertices.add(vertexElement.getAsFloat());
            }
            if (vertices.size() % expectedFloatsPerVertex != 0) {
                throw new IllegalArgumentException("SubMesh " + meshName + "#" + name + " vertex payload does not match format");
            }

            List<Integer> indices = new ArrayList<>();
            if (subMeshObj.has("indices")) {
                JsonArray indicesArray = subMeshObj.getAsJsonArray("indices");
                for (JsonElement indexElement : indicesArray) {
                    indices.add(indexElement.getAsInt());
                }
            }

            validateIndexPayload(meshName, name, primitiveType, indexMode, vertices.size() / expectedFloatsPerVertex, indices);

            Map<String, Object> metadata = new LinkedHashMap<>();
            if (subMeshObj.has("material") && !subMeshObj.get("material").isJsonNull()) {
                metadata.put("material", subMeshObj.get("material").getAsString());
            }
            if (subMeshObj.has("bone") && !subMeshObj.get("bone").isJsonNull()) {
                metadata.put("bone", subMeshObj.get("bone").getAsString());
            }
            subMeshes.add(new CanonicalMeshCompiler.CanonicalSubMesh(name, vertices, indices, metadata));
        }
        return subMeshes;
    }

    private void validateIndexPayload(
            String meshName,
            String subMeshName,
            PrimitiveType primitiveType,
            MeshIndexMode indexMode,
            int vertexCount,
            List<Integer> indices) {
        String fullName = meshName + "#" + subMeshName;
        switch (indexMode) {
            case NONE -> {
                if (indices != null && !indices.isEmpty()) {
                    throw new IllegalArgumentException("SubMesh " + fullName + " declares indices but indexMode is none");
                }
            }
            case EXPLICIT_LOCAL -> {
                if (indices == null || indices.isEmpty()) {
                    throw new IllegalArgumentException("SubMesh " + fullName + " requires explicit local indices");
                }
                for (Integer index : indices) {
                    if (index == null || index < 0 || index >= vertexCount) {
                        throw new IllegalArgumentException("SubMesh " + fullName + " contains out-of-range local index " + index);
                    }
                }
            }
            case GENERATED -> {
                if (!rogo.sketch.core.data.TopologyIndexGenerator.supportsGeneratedIndices(primitiveType)) {
                    throw new IllegalArgumentException(
                            "SubMesh " + fullName + " uses generated indices but topology " + primitiveType + " is not supported");
                }
                if (indices != null && !indices.isEmpty()) {
                    throw new IllegalArgumentException("SubMesh " + fullName + " must not declare indices when indexMode is generated");
                }
            }
        }
    }

    private int sourceFloatsPerVertex(StructLayout format) {
        int total = 0;
        for (var element : format.getElements()) {
            total += element.getComponentCount();
        }
        return total;
    }

    private void loadBones(MeshGroup meshGroup, JsonArray bonesArray) {
        for (JsonElement element : bonesArray) {
            JsonObject boneObj = element.getAsJsonObject();

            String name = boneObj.get("name").getAsString();
            int id = boneObj.get("id").getAsInt();

            Matrix4f localTransform = parseMatrix4f(boneObj, "localTransform");
            Matrix4f inverseBindPose = parseMatrix4f(boneObj, "inverseBindPose");

            MeshBone bone = new MeshBone(name, id, localTransform, inverseBindPose);
            meshGroup.addBone(bone);

            if (boneObj.has("isRoot") && boneObj.get("isRoot").getAsBoolean()) {
                meshGroup.setRootBone(bone);
            }
        }

        for (JsonElement element : bonesArray) {
            JsonObject boneObj = element.getAsJsonObject();
            String boneName = boneObj.get("name").getAsString();
            MeshBone bone = meshGroup.findBone(boneName);

            if (boneObj.has("parent") && bone != null) {
                String parentName = boneObj.get("parent").getAsString();
                MeshBone parent = meshGroup.findBone(parentName);
                if (parent != null) {
                    parent.addChild(bone);
                }
            }
        }
    }

    private Matrix4f parseMatrix4f(JsonObject obj, String key) {
        if (!obj.has(key)) {
            return new Matrix4f();
        }

        JsonArray matrixArray = obj.getAsJsonArray(key);
        Matrix4f matrix = new Matrix4f();
        if (matrixArray.size() == 16) {
            float[] values = new float[16];
            for (int i = 0; i < 16; i++) {
                values[i] = matrixArray.get(i).getAsFloat();
            }
            matrix.set(values);
        }
        return matrix;
    }

    private StructLayout parseDataFormat(JsonObject formatObj) {
        String formatName = formatObj.get("name").getAsString();
        JsonArray elementsArray = formatObj.getAsJsonArray("elements");

        StructLayout.Builder builder = StructLayout.builder(formatName);
        for (JsonElement element : elementsArray) {
            JsonObject elementObj = element.getAsJsonObject();

            String elementName = elementObj.get("name").getAsString();
            String dataTypeStr = elementObj.get("dataType").getAsString();
            boolean normalized = elementObj.has("normalized") && elementObj.get("normalized").getAsBoolean();
            boolean sortKey = elementObj.has("sortKey") && elementObj.get("sortKey").getAsBoolean();
            boolean padding = elementObj.has("padding") && elementObj.get("padding").getAsBoolean();

            ValueType dataType = parseDataType(dataTypeStr);
            builder.add(elementName, dataType, normalized, sortKey, padding);
        }
        return builder.build();
    }

    private ValueType parseDataType(String type) {
        ValueType parsed = ValueType.getByName(type);
        if (parsed == null) {
            throw new IllegalArgumentException("Unknown data type: " + type);
        }
        return parsed;
    }

    private void loadMetadata(MeshGroup meshGroup, JsonObject metadataObj) {
        for (Map.Entry<String, JsonElement> entry : metadataObj.entrySet()) {
            String key = entry.getKey();
            JsonElement value = entry.getValue();

            if (value.isJsonPrimitive()) {
                var primitive = value.getAsJsonPrimitive();
                if (primitive.isString()) {
                    meshGroup.setMetadata(key, primitive.getAsString());
                } else if (primitive.isNumber()) {
                    meshGroup.setMetadata(key, primitive.getAsNumber());
                } else if (primitive.isBoolean()) {
                    meshGroup.setMetadata(key, primitive.getAsBoolean());
                }
            } else {
                meshGroup.setMetadata(key, value.toString());
            }
        }
    }
}

