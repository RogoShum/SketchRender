package rogo.sketch.render.resource.loader;

import com.google.gson.Gson;
import rogo.sketch.render.data.PrimitiveType;
import rogo.sketch.render.data.format.DataFormat;
import rogo.sketch.render.model.DynamicMesh;
import rogo.sketch.render.model.MeshGroup;
import rogo.sketch.render.vertex.DefaultDataFormats;
import rogo.sketch.util.Identifier;

import java.io.BufferedReader;
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
    public MeshGroup load(Identifier identifier, ResourceData resourceData, Gson gson, Function<Identifier, Optional<BufferedReader>> resourceProvider) {
        BufferedReader reader = resourceData.getReader();
        if (reader == null) return null;

        try (reader) {
            List<Float> positions = new ArrayList<>();
            List<Float> texCoords = new ArrayList<>();
            List<Float> normals = new ArrayList<>();

            List<Float> finalVertices = new ArrayList<>();

            String line;
            while ((line = reader.readLine()) != null) {
                String[] tokens = line.trim().split("\\s+");
                if (tokens.length == 0) continue;

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
            MeshGroup meshGroup = new MeshGroup(identifier.toString(), PrimitiveType.TRIANGLES, format);

            // Convert List to array
            float[] data = new float[finalVertices.size()];
            for (int i = 0; i < finalVertices.size(); i++) data[i] = finalVertices.get(i);

            int vertexCount = data.length / 8; // 3+2+3 = 8 floats per vertex

            final float[] finalData = data;
            DynamicMesh mesh = new DynamicMesh(
                    format,
                    PrimitiveType.TRIANGLES,
                    vertexCount,
                    0, // Not indexed (unindexed array draw)
                    filler -> {
                        int stride = 8;
                        for (int i = 0; i < vertexCount; i++) {
                            int base = i * stride;
                            filler.putFloat(finalData[base + 0]); // x
                            filler.putFloat(finalData[base + 1]); // y
                            filler.putFloat(finalData[base + 2]); // z
                            filler.putFloat(finalData[base + 3]); // u
                            filler.putFloat(finalData[base + 4]); // v
                            filler.putFloat(finalData[base + 5]); // nx
                            filler.putFloat(finalData[base + 6]); // ny
                            filler.putFloat(finalData[base + 7]); // nz
                        }
                    }
            );

            meshGroup.addMesh("main", mesh);
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