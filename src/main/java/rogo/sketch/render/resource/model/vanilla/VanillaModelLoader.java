package rogo.sketch.render.resource.model.vanilla;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import rogo.sketch.render.data.PrimitiveType;
import rogo.sketch.render.data.format.DataFormat;
import rogo.sketch.render.model.Mesh;
import rogo.sketch.render.model.MeshBuilder;
import rogo.sketch.render.model.ModelMesh;
import rogo.sketch.render.model.MeshCompiler;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for converting Minecraft models to ModelMesh objects.
 * Handles both block models and item models from vanilla Minecraft.
 */
public class VanillaModelLoader {
    
    // Standard vertex format for Minecraft models (position + UV + normal)
    private static final DataFormat MINECRAFT_VERTEX_FORMAT = DataFormat.builder("MinecraftVertex")
            .vec3Attribute("position")    // 3D position
            .vec2Attribute("texCoord")    // Texture coordinates
            .vec3Attribute("normal")      // Surface normal
            .vec4Attribute("color")       // Vertex color (RGBA)
            .build();
    
    /**
     * Load a block model and convert it to ModelMesh
     */
    public static ModelMesh loadBlockModel(ResourceLocation modelLocation) {
        try {
            // Get the baked model from Minecraft's model manager
            BakedModel bakedModel = Minecraft.getInstance()
                    .getModelManager()
                    .getModel(modelLocation);
            
            if (bakedModel == null) {
                System.err.println("Failed to load model: " + modelLocation);
                return null;
            }
            
            return convertBakedModelToMesh(modelLocation.toString(), bakedModel);
            
        } catch (Exception e) {
            System.err.println("Error loading block model " + modelLocation + ": " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Load an item model and convert it to ModelMesh
     */
    public static ModelMesh loadItemModel(ResourceLocation modelLocation) {
        // Items use the same system as blocks in modern Minecraft
        return loadBlockModel(modelLocation);
    }
    
    /**
     * Load a model from JSON definition
     */
    public static ModelMesh loadFromJson(String jsonContent, String modelName) {
        try {
            JsonObject jsonModel = JsonParser.parseString(jsonContent).getAsJsonObject();
            
            // Extract elements (cubes/quads) from the JSON
            List<MinecraftCube> cubes = new ArrayList<>();
            
            if (jsonModel.has("elements")) {
                JsonArray elements = jsonModel.getAsJsonArray("elements");
                for (JsonElement element : elements) {
                    JsonObject cubeObj = element.getAsJsonObject();
                    cubes.add(parseMinecraftCube(cubeObj));
                }
            }
            
            return convertCubesToMesh(modelName, cubes);
            
        } catch (Exception e) {
            System.err.println("Error loading model from JSON: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Convert a Minecraft BakedModel to our ModelMesh
     */
    private static ModelMesh convertBakedModelToMesh(String name, BakedModel bakedModel) {
        MeshBuilder builder = MeshBuilder.create(name, PrimitiveType.QUADS);
        
        List<Float> vertices = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();
        
        RandomSource random = RandomSource.create(0); // Deterministic for consistency
        
        // Get quads for all faces
        for (Direction direction : Direction.values()) {
            List<BakedQuad> quads = bakedModel.getQuads(null, direction, random);
            addQuadsToMesh(quads, vertices, indices);
        }
        
        // Get quads without specific face (interior quads)
        List<BakedQuad> generalQuads = bakedModel.getQuads(null, null, random);
        addQuadsToMesh(generalQuads, vertices, indices);
        
        // Create sub-mesh with all the geometry
        builder.subMesh("main", 0, vertices.size() / getVertexFloatCount(), MINECRAFT_VERTEX_FORMAT);
        
//        // Add vertices
//        float[] vertexArray = new float[vertices.size()];
//        for (int i = 0; i < vertices.size(); i++) {
//            vertexArray[i] = vertices.get(i);
//        }
//        builder.vertices(vertexArray);
        
        // Add indices
        int[] indexArray = new int[indices.size()];
        for (int i = 0; i < indices.size(); i++) {
            indexArray[i] = indices.get(i);
        }
        builder.indices(indexArray);
        
        // Build and compile
        Mesh mesh = builder.build();
        return MeshCompiler.compile(mesh, MeshCompiler.staticMeshOptions()).getModelMesh();
    }
    
    private static void addQuadsToMesh(List<BakedQuad> quads, List<Float> vertices, List<Integer> indices) {
        for (BakedQuad quad : quads) {
            int[] vertexData = quad.getVertices();
            int baseVertexIndex = vertices.size() / getVertexFloatCount();
            
            // Each quad has 4 vertices, each vertex has 8 ints in the vertex data
            // Format: x, y, z, color, u, v, normal, lightmap
            for (int i = 0; i < 4; i++) {
                int offset = i * 8;
                
                // Position (x, y, z) - convert from int bits to float
                float x = Float.intBitsToFloat(vertexData[offset]);
                float y = Float.intBitsToFloat(vertexData[offset + 1]);
                float z = Float.intBitsToFloat(vertexData[offset + 2]);
                
                // Color (ABGR packed into int)
                int colorPacked = vertexData[offset + 3];
                float r = ((colorPacked >> 16) & 0xFF) / 255.0f;
                float g = ((colorPacked >> 8) & 0xFF) / 255.0f;
                float b = (colorPacked & 0xFF) / 255.0f;
                float a = ((colorPacked >> 24) & 0xFF) / 255.0f;
                
                // Texture coordinates (u, v) - convert from int bits to float
                float u = Float.intBitsToFloat(vertexData[offset + 4]);
                float v = Float.intBitsToFloat(vertexData[offset + 5]);
                
                // Normal (packed into int)
                int normalPacked = vertexData[offset + 6];
                float nx = ((normalPacked >> 0) & 0xFF) / 127.5f - 1.0f;
                float ny = ((normalPacked >> 8) & 0xFF) / 127.5f - 1.0f;
                float nz = ((normalPacked >> 16) & 0xFF) / 127.5f - 1.0f;
                
                // Add vertex: position, texCoord, normal, color
                vertices.add(x);
                vertices.add(y);
                vertices.add(z);
                vertices.add(u);
                vertices.add(v);
                vertices.add(nx);
                vertices.add(ny);
                vertices.add(nz);
                vertices.add(r);
                vertices.add(g);
                vertices.add(b);
                vertices.add(a);
            }
            
            // Add quad indices (two triangles)
            indices.add(baseVertexIndex + 0);
            indices.add(baseVertexIndex + 1);
            indices.add(baseVertexIndex + 2);
            indices.add(baseVertexIndex + 3);
        }
    }
    
    private static int getVertexFloatCount() {
        return MINECRAFT_VERTEX_FORMAT.getStride() / 4; // 4 bytes per float
    }
    
    /**
     * Convert Minecraft cube definitions to mesh
     */
    private static ModelMesh convertCubesToMesh(String name, List<MinecraftCube> cubes) {
        MeshBuilder builder = MeshBuilder.create(name, PrimitiveType.QUADS);
        
        List<Float> vertices = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();
        
        for (MinecraftCube cube : cubes) {
            addCubeToMesh(cube, vertices, indices);
        }
        
        // Create sub-mesh
        builder.subMesh("main", 0, vertices.size() / getVertexFloatCount(), MINECRAFT_VERTEX_FORMAT);
        
//        // Add vertices and indices
//        float[] vertexArray = new float[vertices.size()];
//        for (int i = 0; i < vertices.size(); i++) {
//            vertexArray[i] = vertices.get(i);
//        }
//        builder.vertices(vertexArray);
        
        int[] indexArray = new int[indices.size()];
        for (int i = 0; i < indices.size(); i++) {
            indexArray[i] = indices.get(i);
        }
        builder.indices(indexArray);
        
        // Build and compile
        Mesh mesh = builder.build();
        return MeshCompiler.compile(mesh, MeshCompiler.staticMeshOptions()).getModelMesh();
    }
    
    private static void addCubeToMesh(MinecraftCube cube, List<Float> vertices, List<Integer> indices) {
        // Convert Minecraft 16x16x16 coordinate system to normalized coordinates
        float x1 = cube.from[0] / 16.0f;
        float y1 = cube.from[1] / 16.0f;
        float z1 = cube.from[2] / 16.0f;
        float x2 = cube.to[0] / 16.0f;
        float y2 = cube.to[1] / 16.0f;
        float z2 = cube.to[2] / 16.0f;
        
        // Define the 6 faces of the cube
        // Each face is a quad with 4 vertices
        addCubeFace(vertices, indices, "down",  x1, y1, z1, x2, y1, z2, 0, -1, 0);
        addCubeFace(vertices, indices, "up",    x1, y2, z1, x2, y2, z2, 0, 1, 0);
        addCubeFace(vertices, indices, "north", x1, y1, z1, x2, y2, z1, 0, 0, -1);
        addCubeFace(vertices, indices, "south", x1, y1, z2, x2, y2, z2, 0, 0, 1);
        addCubeFace(vertices, indices, "west",  x1, y1, z1, x1, y2, z2, -1, 0, 0);
        addCubeFace(vertices, indices, "east",  x2, y1, z1, x2, y2, z2, 1, 0, 0);
    }
    
    private static void addCubeFace(List<Float> vertices, List<Integer> indices, String face,
                                   float x1, float y1, float z1, float x2, float y2, float z2,
                                   float nx, float ny, float nz) {
        int baseIndex = vertices.size() / getVertexFloatCount();
        
        // Define face vertices based on face direction
        switch (face) {
            case "down": // Y- face
                addVertex(vertices, x1, y1, z2, 0, 0, nx, ny, nz, 1, 1, 1, 1);
                addVertex(vertices, x2, y1, z2, 1, 0, nx, ny, nz, 1, 1, 1, 1);
                addVertex(vertices, x2, y1, z1, 1, 1, nx, ny, nz, 1, 1, 1, 1);
                addVertex(vertices, x1, y1, z1, 0, 1, nx, ny, nz, 1, 1, 1, 1);
                break;
            case "up": // Y+ face
                addVertex(vertices, x1, y2, z1, 0, 0, nx, ny, nz, 1, 1, 1, 1);
                addVertex(vertices, x2, y2, z1, 1, 0, nx, ny, nz, 1, 1, 1, 1);
                addVertex(vertices, x2, y2, z2, 1, 1, nx, ny, nz, 1, 1, 1, 1);
                addVertex(vertices, x1, y2, z2, 0, 1, nx, ny, nz, 1, 1, 1, 1);
                break;
            case "north": // Z- face
                addVertex(vertices, x2, y1, z1, 0, 1, nx, ny, nz, 1, 1, 1, 1);
                addVertex(vertices, x1, y1, z1, 1, 1, nx, ny, nz, 1, 1, 1, 1);
                addVertex(vertices, x1, y2, z1, 1, 0, nx, ny, nz, 1, 1, 1, 1);
                addVertex(vertices, x2, y2, z1, 0, 0, nx, ny, nz, 1, 1, 1, 1);
                break;
            case "south": // Z+ face
                addVertex(vertices, x1, y1, z2, 0, 1, nx, ny, nz, 1, 1, 1, 1);
                addVertex(vertices, x2, y1, z2, 1, 1, nx, ny, nz, 1, 1, 1, 1);
                addVertex(vertices, x2, y2, z2, 1, 0, nx, ny, nz, 1, 1, 1, 1);
                addVertex(vertices, x1, y2, z2, 0, 0, nx, ny, nz, 1, 1, 1, 1);
                break;
            case "west": // X- face
                addVertex(vertices, x1, y1, z1, 0, 1, nx, ny, nz, 1, 1, 1, 1);
                addVertex(vertices, x1, y1, z2, 1, 1, nx, ny, nz, 1, 1, 1, 1);
                addVertex(vertices, x1, y2, z2, 1, 0, nx, ny, nz, 1, 1, 1, 1);
                addVertex(vertices, x1, y2, z1, 0, 0, nx, ny, nz, 1, 1, 1, 1);
                break;
            case "east": // X+ face
                addVertex(vertices, x2, y1, z2, 0, 1, nx, ny, nz, 1, 1, 1, 1);
                addVertex(vertices, x2, y1, z1, 1, 1, nx, ny, nz, 1, 1, 1, 1);
                addVertex(vertices, x2, y2, z1, 1, 0, nx, ny, nz, 1, 1, 1, 1);
                addVertex(vertices, x2, y2, z2, 0, 0, nx, ny, nz, 1, 1, 1, 1);
                break;
        }
        
        // Add quad indices
        indices.add(baseIndex + 0);
        indices.add(baseIndex + 1);
        indices.add(baseIndex + 2);
        indices.add(baseIndex + 3);
    }
    
    private static void addVertex(List<Float> vertices, float x, float y, float z, float u, float v,
                                 float nx, float ny, float nz, float r, float g, float b, float a) {
        // Position
        vertices.add(x);
        vertices.add(y);
        vertices.add(z);
        // Texture coordinates
        vertices.add(u);
        vertices.add(v);
        // Normal
        vertices.add(nx);
        vertices.add(ny);
        vertices.add(nz);
        // Color
        vertices.add(r);
        vertices.add(g);
        vertices.add(b);
        vertices.add(a);
    }
    
    private static MinecraftCube parseMinecraftCube(JsonObject cubeObj) {
        MinecraftCube cube = new MinecraftCube();
        
        // Parse "from" and "to" positions
        if (cubeObj.has("from")) {
            JsonArray fromArray = cubeObj.getAsJsonArray("from");
            cube.from = new float[]{
                fromArray.get(0).getAsFloat(),
                fromArray.get(1).getAsFloat(),
                fromArray.get(2).getAsFloat()
            };
        }
        
        if (cubeObj.has("to")) {
            JsonArray toArray = cubeObj.getAsJsonArray("to");
            cube.to = new float[]{
                toArray.get(0).getAsFloat(),
                toArray.get(1).getAsFloat(),
                toArray.get(2).getAsFloat()
            };
        }
        
        // Parse faces (texture mapping)
        if (cubeObj.has("faces")) {
            JsonObject facesObj = cubeObj.getAsJsonObject("faces");
            // For simplicity, we're not parsing detailed face information
            // In a full implementation, you'd parse texture references and UV mappings
        }
        
        return cube;
    }
    
    /**
     * Simple data class representing a Minecraft model cube
     */
    private static class MinecraftCube {
        float[] from = {0, 0, 0};
        float[] to = {16, 16, 16};
        // Additional properties like rotation, faces would go here
    }
}
