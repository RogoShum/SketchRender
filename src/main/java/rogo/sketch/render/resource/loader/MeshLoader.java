package rogo.sketch.render.resource.loader;

import com.google.gson.*;
import org.joml.Matrix4f;
import rogo.sketch.render.data.DataType;
import rogo.sketch.render.data.PrimitiveType;
import rogo.sketch.render.data.Usage;
import rogo.sketch.render.data.builder.VertexDataBuilder;
import rogo.sketch.render.data.format.DataFormat;
import rogo.sketch.render.model.BakedMesh;
import rogo.sketch.render.model.MeshBone;
import rogo.sketch.render.model.MeshGroup;
import rogo.sketch.render.resource.buffer.IndexBufferResource;
import rogo.sketch.render.resource.buffer.VertexBufferObject;
import rogo.sketch.render.resource.buffer.VertexResource;
import rogo.sketch.render.vertex.VertexResourceManager;
import rogo.sketch.util.KeyId;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Loader for Mesh resources from JSON
 * Supports loading complete mesh definitions with bones and sub-meshes
 */
public class MeshLoader implements ResourceLoader<MeshGroup> {

    @Override
    public MeshGroup load(KeyId keyId, ResourceData data, Gson gson,
                          Function<KeyId, Optional<BufferedReader>> resourceProvider) {
        try {
            String jsonData = data.getString();
            if (jsonData == null)
                return null;

            JsonObject json = gson.fromJson(jsonData, JsonObject.class);

            // Get mesh name and primitive type
            String name = json.has("name") ? json.get("name").getAsString() : keyId.toString();
            PrimitiveType primitiveType = parsePrimitiveType(json.get("primitiveType").getAsString());

            // Determine vertex format from top level or first submesh
            DataFormat format = null;
            if (json.has("vertexFormat")) {
                format = parseDataFormat(json.getAsJsonObject("vertexFormat"));
            } else if (json.has("subMeshes")) {
                JsonArray subs = json.getAsJsonArray("subMeshes");
                if (subs.size() > 0) {
                    JsonObject first = subs.get(0).getAsJsonObject();
                    if (first.has("vertexFormat")) {
                        format = parseDataFormat(first.getAsJsonObject("vertexFormat"));
                    }
                }
            }

            if (format == null) {
                throw new IllegalStateException("Cannot determine vertex format (missing vertexFormat definition)");
            }

            MeshGroup meshGroup = new MeshGroup(name, primitiveType, format);

            // Load bones if present
            if (json.has("bones")) {
                loadBones(meshGroup, json.getAsJsonArray("bones"));
            }

            // Load sub-meshes
            if (json.has("subMeshes")) {
                loadSubMeshes(meshGroup, json.getAsJsonArray("subMeshes"));
            }

            // Load metadata if present
            if (json.has("metadata")) {
                loadMetadata(meshGroup, json.getAsJsonObject("metadata"));
            }

            return meshGroup;

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

    private void loadBones(MeshGroup meshGroup, JsonArray bonesArray) {
        // First pass: create all bones
        for (JsonElement element : bonesArray) {
            JsonObject boneObj = element.getAsJsonObject();

            String name = boneObj.get("name").getAsString();
            int id = boneObj.get("id").getAsInt();

            Matrix4f localTransform = parseMatrix4f(boneObj, "localTransform");
            Matrix4f inverseBindPose = parseMatrix4f(boneObj, "inverseBindPose");

            MeshBone bone = new MeshBone(name, id, localTransform, inverseBindPose);
            meshGroup.addBone(bone);

            // Set as root if specified
            if (boneObj.has("isRoot") && boneObj.get("isRoot").getAsBoolean()) {
                meshGroup.setRootBone(bone);
            }
        }

        // Second pass: establish parent-child relationships
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

    private void loadSubMeshes(MeshGroup meshGroup, JsonArray subMeshesArray) {
        DataFormat groupFormat = meshGroup.getVertexFormat();

        // Staging data
        List<Float> allVertices = new ArrayList<>();
        List<Integer> allIndices = new ArrayList<>();
        List<SubMeshEntry> entries = new ArrayList<>();

        int currentVertexOffset = 0;
        int currentIndexOffset = 0;

        // 1. Parse all submeshes and accumulate data
        for (JsonElement element : subMeshesArray) {
            JsonObject subMeshObj = element.getAsJsonObject();
            String name = subMeshObj.get("name").getAsString();

            // Validate vertex format if present
            if (subMeshObj.has("vertexFormat")) {
                DataFormat subFormat = parseDataFormat(subMeshObj.getAsJsonObject("vertexFormat"));
                if (!subFormat.equals(groupFormat)) {
                    throw new IllegalArgumentException(
                            "SubMesh format mismatch in " + name + ". Group expects " + groupFormat);
                }
            }

            List<Float> vertices = new ArrayList<>();
            List<Integer> indices = new ArrayList<>();

            // Load vertex data
            if (subMeshObj.has("vertices")) {
                JsonArray verticesArray = subMeshObj.getAsJsonArray("vertices");
                for (JsonElement vertexElement : verticesArray) {
                    vertices.add(vertexElement.getAsFloat());
                }
            }

            // Load index data
            if (subMeshObj.has("indices")) {
                JsonArray indicesArray = subMeshObj.getAsJsonArray("indices");
                for (JsonElement indexElement : indicesArray) {
                    indices.add(indexElement.getAsInt());
                }
            }
            int floatsPerVertex = groupFormat.getStride() / 4;
            int subVertexCount = vertices.size() / floatsPerVertex;
            int subIndexCount = indices.size();

            entries.add(new SubMeshEntry(name, subMeshObj, currentVertexOffset, currentIndexOffset, subVertexCount,
                    subIndexCount));

            allVertices.addAll(vertices);
            // Adjust indices for global offset if we are merging?
            // Actually, for multi-draw or separate draw calls, we usually use "BaseVertex"
            // to handle offset.
            // But BakedMesh stores "srcVertexOffset".
            // If we use standard GL_ELEMENT_ARRAY_BUFFER, the indices are absolute or
            // relative?
            // Usually they are relative to the start of the VBO unless "BaseVertex" is
            // used.
            // BUT if we merge into one VBO/IBO, the indices from the file are 0-based local
            // to that mesh.
            // When we put them in a global buffer, should we offset them?
            // If we use "DrawElementsBaseVertex", we don't need to offset indices.
            // BakedMesh implementation implies we might use it as a source copy...
            // BUT if we want to render it directly, we need correct indices.
            // Let's assume we want to support rendering without BaseVertex for widest
            // compatibility/simplicity where possible,
            // OR we assume the renderer handles BaseVertex.
            // BakedMesh tracks `srcVertexOffset`.
            // If the renderer uses `glDrawElementsBaseVertex`, we keep 0-based indices.
            // If the renderer merges, it might re-index.
            // HOWEVER, BakedMesh is often used for "Copying" (Zero Copy) to a target.
            // If we copy, we copy the indices AS IS.
            // So we should NOT offset indices here. The vertices define the base.

            allIndices.addAll(indices);

            currentVertexOffset += subVertexCount;
            currentIndexOffset += subIndexCount;
        }

        // 2. Create VertexResource and Fill
        // Create unique VertexResource (bypassing cache or using it as a unique
        // container)
        // Since this is a specific asset, we create a new one.
        VertexResourceManager vrm = VertexResourceManager
                .getInstance();

        // We manually create VertexResource to ensure it's not shared/cached (unless we
        // used a unique key)
        // Just use internal logic or access new VertexResource?
        // VertexResourceManager doesn't expose public constructor of VertexResource
        // easily (package private?)
        // Let's check VertexResource visibility... it is public class but constructor?
        // VertexResource constructor is public in the snippet I read earlier!
        // "public class VertexResource ... public VertexResource(PrimitiveType...)"

        VertexResource resource = new VertexResource(meshGroup.getPrimitiveType(), allIndices.size() > 0);

        // Create VBO for Binding 0
        VertexBufferObject vbo = new VertexBufferObject(Usage.STATIC_DRAW);
        resource.attachVBO(0, vbo, groupFormat, false);

        // Upload Vertices
        VertexDataBuilder builder = vrm.createBuilder(groupFormat, meshGroup.getPrimitiveType(), allVertices.size());
        int floatsPerVertex = groupFormat.getStride() / 4;

        // Convert List to array for faster filling? Or just iterate.
        // Direct fill
        float[] vData = new float[allVertices.size()];
        for (int i = 0; i < allVertices.size(); i++)
            vData[i] = allVertices.get(i);

        fillVertices(builder, vData, groupFormat);
        resource.upload(0, builder);

        // Upload Indices if present
        if (allIndices.size() > 0) {
            IndexBufferResource ibo = resource.getIndexBuffer();
            ibo.clear();
            for (int index : allIndices) {
                ibo.addIndex(index);
            }
            ibo.upload();
        }

        // 3. Create BakedMeshes
        for (SubMeshEntry entry : entries) {
            BakedMesh bakedMesh = new BakedMesh(
                    resource,
                    entry.vertexOffset,
                    entry.indexOffset,
                    entry.vertexCount,
                    entry.indexCount);

            meshGroup.addMesh(entry.name, bakedMesh);

            // Metadata
            if (entry.jsonObj.has("material")) {
                meshGroup.setMetadata("material_" + entry.name, entry.jsonObj.get("material").getAsString());
            }
        }
    }

    private record SubMeshEntry(String name, JsonObject jsonObj, int vertexOffset, int indexOffset, int vertexCount,
                                int indexCount) {
    }

    private static void fillVertices(VertexDataBuilder filler, float[] data, DataFormat format) {
        int strideFloats = format.getStride() / 4;
        int count = data.length / strideFloats;
        for (int i = 0; i < count; i++) {
            for (int j = 0; j < strideFloats; j++) {
                filler.putFloat(data[i * strideFloats + j]);
            }
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
            case "vec2" -> DataType.VEC2F;
            case "vec3" -> DataType.VEC3F;
            case "vec4" -> DataType.VEC4F;
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

    private void loadMetadata(MeshGroup meshGroup, JsonObject metadataObj) {
        for (Map.Entry<String, JsonElement> entry : metadataObj.entrySet()) {
            String key = entry.getKey();
            JsonElement value = entry.getValue();

            if (value.isJsonPrimitive()) {
                JsonPrimitive primitive = value.getAsJsonPrimitive();
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