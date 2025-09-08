package rogo.sketch.render.resource.loader;

import com.google.gson.*;
import org.joml.Matrix4f;
import rogo.sketch.render.data.DataType;
import rogo.sketch.render.data.PrimitiveType;
import rogo.sketch.render.data.format.DataFormat;
import rogo.sketch.render.model.Mesh;
import rogo.sketch.render.model.MeshBone;
import rogo.sketch.render.model.SubMesh;
import rogo.sketch.util.Identifier;

import java.io.BufferedReader;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Loader for Mesh resources from JSON
 * Supports loading complete mesh definitions with bones and sub-meshes
 */
public class MeshLoader implements ResourceLoader<Mesh> {

    @Override
    public Mesh loadFromJson(Identifier identifier, String jsonData, Gson gson, Function<Identifier, Optional<BufferedReader>> resourceProvider) {
        try {
            JsonObject json = gson.fromJson(jsonData, JsonObject.class);

            // Get mesh name and primitive type
            String name = json.has("name") ? json.get("name").getAsString() : identifier.toString();
            PrimitiveType primitiveType = parsePrimitiveType(json.get("primitiveType").getAsString());

            Mesh mesh = new Mesh(name, primitiveType);

            // Load bones if present
            if (json.has("bones")) {
                loadBones(mesh, json.getAsJsonArray("bones"));
            }

            // Load sub-meshes
            if (json.has("subMeshes")) {
                loadSubMeshes(mesh, json.getAsJsonArray("subMeshes"));
            }

            // Load metadata if present
            if (json.has("metadata")) {
                loadMetadata(mesh, json.getAsJsonObject("metadata"));
            }

            return mesh;

        } catch (Exception e) {
            System.err.println("Failed to load mesh from JSON: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
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
            default -> throw new IllegalArgumentException("Unknown primitive type: " + type);
        };
    }

    private void loadBones(Mesh mesh, JsonArray bonesArray) {
        // First pass: create all bones
        for (JsonElement element : bonesArray) {
            JsonObject boneObj = element.getAsJsonObject();

            String name = boneObj.get("name").getAsString();
            int id = boneObj.get("id").getAsInt();

            Matrix4f localTransform = parseMatrix4f(boneObj, "localTransform");
            Matrix4f inverseBindPose = parseMatrix4f(boneObj, "inverseBindPose");

            MeshBone bone = new MeshBone(name, id, localTransform, inverseBindPose);
            mesh.addBone(bone);

            // Set as root if specified
            if (boneObj.has("isRoot") && boneObj.get("isRoot").getAsBoolean()) {
                mesh.setRootBone(bone);
            }
        }

        // Second pass: establish parent-child relationships
        for (JsonElement element : bonesArray) {
            JsonObject boneObj = element.getAsJsonObject();

            String boneName = boneObj.get("name").getAsString();
            MeshBone bone = mesh.findBone(boneName);

            if (boneObj.has("parent")) {
                String parentName = boneObj.get("parent").getAsString();
                MeshBone parent = mesh.findBone(parentName);
                if (parent != null) {
                    bone.setParent(parent);
                }
            }
        }
    }

    private Matrix4f parseMatrix4f(JsonObject obj, String key) {
        if (!obj.has(key)) {
            return new Matrix4f(); // Identity matrix
        }

        JsonArray matrixArray = obj.getAsJsonArray(key);
        Matrix4f matrix = new Matrix4f();

        if (matrixArray.size() == 16) {
            // Full 4x4 matrix
            float[] values = new float[16];
            for (int i = 0; i < 16; i++) {
                values[i] = matrixArray.get(i).getAsFloat();
            }
            matrix.set(values);
        }

        return matrix;
    }

    private void loadSubMeshes(Mesh mesh, JsonArray subMeshesArray) {
        for (JsonElement element : subMeshesArray) {
            JsonObject subMeshObj = element.getAsJsonObject();

            String name = subMeshObj.get("name").getAsString();
            int id = subMeshObj.get("id").getAsInt();
            int vertexCount = subMeshObj.get("vertexCount").getAsInt();

            // Parse vertex format
            DataFormat vertexFormat = parseDataFormat(subMeshObj.getAsJsonObject("vertexFormat"));

            SubMesh subMesh = new SubMesh(name, id, vertexCount, vertexFormat);

            // Load vertex data
            if (subMeshObj.has("vertices")) {
                JsonArray verticesArray = subMeshObj.getAsJsonArray("vertices");
                for (JsonElement vertexElement : verticesArray) {
                    subMesh.addVertex(vertexElement.getAsFloat());
                }
            }

            // Load index data
            if (subMeshObj.has("indices")) {
                JsonArray indicesArray = subMeshObj.getAsJsonArray("indices");
                for (JsonElement indexElement : indicesArray) {
                    subMesh.addIndex(indexElement.getAsInt());
                }
            }

            // Set material
            if (subMeshObj.has("material")) {
                subMesh.setMaterialName(subMeshObj.get("material").getAsString());
            }

            // Set render priority
            if (subMeshObj.has("renderPriority")) {
                subMesh.setRenderPriority(subMeshObj.get("renderPriority").getAsInt());
            }

            // Set visibility
            if (subMeshObj.has("visible")) {
                subMesh.setVisible(subMeshObj.get("visible").getAsBoolean());
            }

            // Bind to bone if specified
            if (subMeshObj.has("bone")) {
                String boneName = subMeshObj.get("bone").getAsString();
                MeshBone bone = mesh.findBone(boneName);
                if (bone != null) {
                    subMesh.bindToBone(bone);
                }
            }

            mesh.addSubMesh(subMesh);
        }
    }

    private DataFormat parseDataFormat(JsonObject formatObj) {
        String formatName = formatObj.get("name").getAsString();
        JsonArray elementsArray = formatObj.getAsJsonArray("elements");

        DataFormat.Builder builder = DataFormat.builder(formatName);

        for (JsonElement element : elementsArray) {
            JsonObject elementObj = element.getAsJsonObject();

            String elementName = elementObj.get("name").getAsString();
            String dataTypeStr = elementObj.get("dataType").getAsString();
            boolean normalized = elementObj.has("normalized") && elementObj.get("normalized").getAsBoolean();

            DataType dataType = parseDataType(dataTypeStr);
            builder.add(elementName, dataType, normalized);
        }

        return builder.build();
    }

    private DataType parseDataType(String type) {
        return switch (type.toLowerCase()) {
            case "float" -> DataType.FLOAT;
            case "vec2" -> DataType.VEC2;
            case "vec3" -> DataType.VEC3;
            case "vec4" -> DataType.VEC4;
            case "int" -> DataType.INT;
            case "vec2i" -> DataType.VEC2I;
            case "vec3i" -> DataType.VEC3I;
            case "vec4i" -> DataType.VEC4I;
            case "mat2" -> DataType.MAT2;
            case "mat3" -> DataType.MAT3;
            case "mat4" -> DataType.MAT4;
            case "byte" -> DataType.BYTE;
            case "ubyte" -> DataType.UBYTE;
            case "short" -> DataType.SHORT;
            case "ushort" -> DataType.USHORT;
            case "uint" -> DataType.UINT;
            case "double" -> DataType.DOUBLE;
            default -> throw new IllegalArgumentException("Unknown data type: " + type);
        };
    }

    private void loadMetadata(Mesh mesh, JsonObject metadataObj) {
        for (Map.Entry<String, JsonElement> entry : metadataObj.entrySet()) {
            String key = entry.getKey();
            JsonElement value = entry.getValue();

            if (value.isJsonPrimitive()) {
                JsonPrimitive primitive = value.getAsJsonPrimitive();
                if (primitive.isString()) {
                    mesh.setMetadata(key, primitive.getAsString());
                } else if (primitive.isNumber()) {
                    mesh.setMetadata(key, primitive.getAsNumber());
                } else if (primitive.isBoolean()) {
                    mesh.setMetadata(key, primitive.getAsBoolean());
                }
            } else {
                // Store complex objects as strings
                mesh.setMetadata(key, value.toString());
            }
        }
    }
}