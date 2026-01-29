package rogo.sketch.core.resource.loader;

import com.google.gson.Gson;
import rogo.sketch.core.api.model.BakedTypeMesh;
import rogo.sketch.core.data.PrimitiveType;
import rogo.sketch.core.data.Usage;
import rogo.sketch.core.data.builder.VertexStreamBuilder;
import rogo.sketch.core.data.format.ComponentSpec;
import rogo.sketch.core.data.format.DataFormat;
import rogo.sketch.core.model.BakedMesh;
import rogo.sketch.core.model.MeshGroup;
import rogo.sketch.core.resource.buffer.VertexBufferObject;
import rogo.sketch.core.resource.buffer.VertexResource;
import rogo.sketch.core.util.KeyId;
import rogo.sketch.core.vertex.DefaultDataFormats;
import rogo.sketch.core.vertex.VertexResourceManager;

import java.io.BufferedReader;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Simple Loader for OBJ files.
 * Supports vertices (v), texture coordinates (vt), normals (vn) and faces (f).
 * Assumes TRIANGLES primitive type and POS_UV_NORMAL format.
 */
public class ObjLoader implements ResourceLoader<MeshGroup> {

    @Override
    public MeshGroup load(KeyId keyId, ResourceData resourceData, Gson gson,
                          Function<KeyId, Optional<InputStream>> resourceProvider) {
        BufferedReader reader = resourceData.getReader();
        if (reader == null)
            return null;

        try (reader) {
            List<Float> positions = new ArrayList<>();
            List<Float> texCoords = new ArrayList<>();
            List<Float> normals = new ArrayList<>();

            List<Float> finalVertices = new ArrayList<>();

            String line;
            while ((line = reader.readLine()) != null) {
                String[] tokens = line.trim().split("\\s+");
                if (tokens.length == 0)
                    continue;

                switch (tokens[0]) {
                    case "v" -> {
                        positions.add(Float.parseFloat(tokens[1]));
                        positions.add(Float.parseFloat(tokens[2]));
                        positions.add(Float.parseFloat(tokens[3]));
                    }
                    case "vt" -> {
                        texCoords.add(Float.parseFloat(tokens[1]));
                        texCoords.add(Float.parseFloat(tokens[2]));
                    }
                    case "vn" -> {
                        normals.add(Float.parseFloat(tokens[1]));
                        normals.add(Float.parseFloat(tokens[2]));
                        normals.add(Float.parseFloat(tokens[3]));
                    }
                    case "f" -> {
                        // Fan triangulation for polygons > 3 vertices
                        for (int i = 1; i < tokens.length - 1; i++) {
                            processVertex(tokens[1], positions, texCoords, normals, finalVertices);
                            processVertex(tokens[i], positions, texCoords, normals, finalVertices);
                            processVertex(tokens[i + 1], positions, texCoords, normals, finalVertices);
                        }
                    }
                }
            }

            // Create MeshGroup
            // Use POS_UV_NORMAL format (Position 3, UV 2, Normal 3)
            DataFormat format = DefaultDataFormats.OBJ;
            MeshGroup meshGroup = new MeshGroup(keyId.toString(), PrimitiveType.TRIANGLES, format);

            // Create VertexResource
            VertexResource resource = new VertexResource(
                    PrimitiveType.TRIANGLES, false); // OBJ usually unindexed in this loader?

            // Note: The loader implementation below generates unindexed triangles (f v1 v2
            // v3 -> 3 vertices)
            // But BakedMesh can work with unindexed too.

            // Create VBO
            VertexBufferObject vbo = new VertexBufferObject(Usage.STATIC_DRAW);
            resource.attachVBO(ComponentSpec.immutable(BakedTypeMesh.BAKED_MESH, 0, format, false), vbo);

            int vertexCount = finalVertices.size() / 8; // 3+2+3 = 8 floats per vertex

            // Upload
            VertexResourceManager vrm = VertexResourceManager
                    .globalInstance();
            VertexStreamBuilder builder = vrm.createBuilder(format,
                    PrimitiveType.TRIANGLES, false, finalVertices.size());

            // Fill
            float[] vData = new float[finalVertices.size()];
            for (int i = 0; i < finalVertices.size(); i++)
                vData[i] = finalVertices.get(i);

            int strideFloats = 8;
            for (int i = 0; i < vertexCount; i++) {
                int base = i * strideFloats;
                builder.put(vData[base + 0]); // x
                builder.put(vData[base + 1]); // y
                builder.put(vData[base + 2]); // z
                builder.put(vData[base + 3]); // u
                builder.put(vData[base + 4]); // v
                builder.put(vData[base + 5]); // nx
                builder.put(vData[base + 6]); // ny
                builder.put(vData[base + 7]); // nz
            }

            resource.upload(BakedTypeMesh.BAKED_MESH, builder);

            // Create BakedMesh
            BakedMesh bakedMesh = new BakedMesh(
                    resource,
                    KeyId.of("main"),
                    0, // srcVertexOffset
                    0, // srcIndexOffset
                    vertexCount,
                    0 // indexCount (unindexed)
            );

            meshGroup.addMesh(KeyId.of("main"), bakedMesh);
            return meshGroup;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void processVertex(String token, List<Float> v, List<Float> vt, List<Float> vn, List<Float> output) {
        String[] parts = token.split("/");

        // Position
        int idxV = Integer.parseInt(parts[0]) - 1;
        output.add(v.get(idxV * 3));
        output.add(v.get(idxV * 3 + 1));
        output.add(v.get(idxV * 3 + 2));

        // TexCoord
        if (parts.length > 1 && !parts[1].isEmpty()) {
            int idxVT = Integer.parseInt(parts[1]) - 1;
            output.add(vt.get(idxVT * 2));
            output.add(vt.get(idxVT * 2 + 1));
        } else {
            output.add(0f);
            output.add(0f);
        }

        // Normal
        if (parts.length > 2 && !parts[2].isEmpty()) {
            int idxVN = Integer.parseInt(parts[2]) - 1;
            output.add(vn.get(idxVN * 3));
            output.add(vn.get(idxVN * 3 + 1));
            output.add(vn.get(idxVN * 3 + 2));
        } else {
            output.add(0f);
            output.add(1f);
            output.add(0f);
        }
    }
}