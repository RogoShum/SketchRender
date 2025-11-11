package rogo.sketch.render.resource.model.vanilla;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import rogo.sketch.render.data.PrimitiveType;
import rogo.sketch.render.data.format.DataFormat;
import rogo.sketch.render.model.MeshGroup;
import rogo.sketch.render.model.MeshCompiler;
import rogo.sketch.render.model.ModelMesh;
import rogo.sketch.render.model.SubMesh;
import rogo.sketch.util.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Advanced converter for Minecraft models to our mesh system.
 * Provides caching and batch conversion capabilities.
 */
public class MinecraftModelConverter {

    private static final DataFormat BLOCK_VERTEX_FORMAT = DataFormat.builder("BlockVertex")
            .vec3Attribute("position")
            .vec2Attribute("texCoord")
            .vec3Attribute("normal")
            .vec4Attribute("color")
            .build();

    private static MinecraftModelConverter instance;
    private final Map<ResourceLocation, ModelMesh> modelCache = new HashMap<>();
    private final RandomSource random = RandomSource.create(0);

    private MinecraftModelConverter() {
    }

    public static MinecraftModelConverter getInstance() {
        if (instance == null) {
            instance = new MinecraftModelConverter();
        }
        return instance;
    }

    /**
     * Convert a Minecraft model to ModelMesh with caching
     */
    public ModelMesh getOrCreateModelMesh(ResourceLocation modelLocation) {
        return modelCache.computeIfAbsent(modelLocation, this::convertModel);
    }

    /**
     * Convert multiple models in batch
     */
    public Map<ResourceLocation, ModelMesh> convertModels(List<ResourceLocation> modelLocations) {
        Map<ResourceLocation, ModelMesh> results = new HashMap<>();

        for (ResourceLocation location : modelLocations) {
            ModelMesh mesh = getOrCreateModelMesh(location);
            if (mesh != null) {
                results.put(location, mesh);
            }
        }

        return results;
    }

    /**
     * Clear the model cache
     */
    public void clearCache() {
        // Dispose all cached models
        modelCache.values().forEach(ModelMesh::dispose);
        modelCache.clear();
    }

    /**
     * Get cache statistics
     */
    public CacheStats getCacheStats() {
        int totalModels = modelCache.size();
        int totalVertices = modelCache.values().stream()
                .mapToInt(ModelMesh::getTotalVertexCount)
                .sum();
        int totalIndices = modelCache.values().stream()
                .mapToInt(ModelMesh::getTotalIndexCount)
                .sum();

        return new CacheStats(totalModels, totalVertices, totalIndices);
    }

    private ModelMesh convertModel(ResourceLocation modelLocation) {
        try {
            BakedModel bakedModel = Minecraft.getInstance()
                    .getModelManager()
                    .getModel(modelLocation);

            if (bakedModel == null) {
                System.err.println("Model not found: " + modelLocation);
                return null;
            }

            return convertBakedModel(modelLocation, bakedModel);

        } catch (Exception e) {
            System.err.println("Failed to convert model " + modelLocation + ": " + e.getMessage());
            return null;
        }
    }

    private ModelMesh convertBakedModel(ResourceLocation location, BakedModel bakedModel) {
        String meshName = location.getNamespace() + "_" + location.getPath().replace('/', '_');
        MeshGroup meshGroup = new MeshGroup(meshName, PrimitiveType.QUADS);

        // Convert each face direction to a separate sub-mesh for better culling
        for (Direction direction : Direction.values()) {
            List<BakedQuad> quads = bakedModel.getQuads(null, direction, random);
            if (!quads.isEmpty()) {
                createSubMeshFromQuads(meshGroup, direction.getName(), quads);
            }
        }

        // Handle general quads (no specific direction)
        List<BakedQuad> generalQuads = bakedModel.getQuads(null, null, random);
        if (!generalQuads.isEmpty()) {
            createSubMeshFromQuads(meshGroup, "general", generalQuads);
        }

        // Add metadata
        meshGroup.setMetadata("source", "minecraft");
        meshGroup.setMetadata("original_location", location.toString());
        meshGroup.setMetadata("is_block_model", true);

        // Compile to ModelMesh
        return MeshCompiler.compile(meshGroup, MeshCompiler.staticMeshOptions()).getModelMesh();
    }

    private void createSubMeshFromQuads(MeshGroup meshGroup, String name, List<BakedQuad> quads) {
        List<Float> vertices = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();

        int quadIndex = 0;
        for (BakedQuad quad : quads) {
            extractQuadData(quad, vertices, indices, quadIndex * 4);
            quadIndex++;
        }

        if (vertices.isEmpty()) {
            return;
        }

        // Create sub-mesh
        int vertexCount = vertices.size() / getVertexStride();
        SubMesh subMesh = meshGroup.createSubMesh(name, meshGroup.getSubMeshCount(), vertexCount, BLOCK_VERTEX_FORMAT);

        // Add vertices
        for (Float vertex : vertices) {
            subMesh.addVertex(vertex);
        }

        // Add indices
        for (Integer index : indices) {
            subMesh.addIndex(index);
        }

        // Set material based on texture
        if (!quads.isEmpty()) {
            String textureName = getTextureNameFromQuad(quads.get(0));
            if (textureName != null) {
                subMesh.setMaterialName(textureName);
            }
        }
    }

    private void extractQuadData(BakedQuad quad, List<Float> vertices, List<Integer> indices, int baseIndex) {
        int[] vertexData = quad.getVertices();

        // Minecraft quad format: 4 vertices, 8 ints per vertex
        // [x, y, z, color, u, v, normal, lightmap]
        for (int i = 0; i < 4; i++) {
            int offset = i * 8;

            // Position
            float x = Float.intBitsToFloat(vertexData[offset]);
            float y = Float.intBitsToFloat(vertexData[offset + 1]);
            float z = Float.intBitsToFloat(vertexData[offset + 2]);

            // Color (ABGR format)
            int colorPacked = vertexData[offset + 3];
            float r = ((colorPacked >> 16) & 0xFF) / 255.0f;
            float g = ((colorPacked >> 8) & 0xFF) / 255.0f;
            float b = (colorPacked & 0xFF) / 255.0f;
            float a = ((colorPacked >> 24) & 0xFF) / 255.0f;

            // Texture coordinates
            float u = Float.intBitsToFloat(vertexData[offset + 4]);
            float v = Float.intBitsToFloat(vertexData[offset + 5]);

            // Normal (packed)
            int normalPacked = vertexData[offset + 6];
            float nx = ((normalPacked >> 0) & 0xFF) / 127.5f - 1.0f;
            float ny = ((normalPacked >> 8) & 0xFF) / 127.5f - 1.0f;
            float nz = ((normalPacked >> 16) & 0xFF) / 127.5f - 1.0f;

            // Add vertex data
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

        // Add quad indices (as quad, not triangles)
        indices.add(baseIndex + 0);
        indices.add(baseIndex + 1);
        indices.add(baseIndex + 2);
        indices.add(baseIndex + 3);
    }

    private int getVertexStride() {
        return BLOCK_VERTEX_FORMAT.getStride() / 4; // 4 bytes per float
    }

    private String getTextureNameFromQuad(BakedQuad quad) {
        try {
            // Try to extract texture name from the quad's sprite
            if (quad.getSprite() != null) {
                return quad.getSprite().atlasLocation().toString();
            }
        } catch (Exception e) {
            // Ignore errors in texture extraction
        }
        return null;
    }

    /**
     * Register a Minecraft model as a resource in the GraphicsResourceManager
     */
    public void registerMinecraftModel(ResourceLocation mcLocation, Identifier resourceId) {
        ModelMesh modelMesh = getOrCreateModelMesh(mcLocation);
        if (modelMesh != null) {
            rogo.sketch.render.resource.GraphicsResourceManager.getInstance()
                    .registerDirect(rogo.sketch.render.resource.ResourceTypes.MESH, resourceId, modelMesh.getOriginalMesh());
        }
    }

    /**
     * Batch register multiple Minecraft models
     */
    public void registerMinecraftModels(Map<ResourceLocation, Identifier> mappings) {
        for (Map.Entry<ResourceLocation, Identifier> entry : mappings.entrySet()) {
            registerMinecraftModel(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Create a resource supplier for dynamic model loading
     */
    public static class MinecraftModelSupplier implements java.util.function.Supplier<java.util.Optional<rogo.sketch.api.ResourceObject>> {
        private final ResourceLocation mcLocation;

        public MinecraftModelSupplier(ResourceLocation mcLocation) {
            this.mcLocation = mcLocation;
        }

        @Override
        public java.util.Optional<rogo.sketch.api.ResourceObject> get() {
            ModelMesh modelMesh = getInstance().getOrCreateModelMesh(mcLocation);
            if (modelMesh != null) {
                return java.util.Optional.of(modelMesh.getOriginalMesh());
            }
            return java.util.Optional.empty();
        }
    }

    /**
     * Cache statistics
     */
    public static class CacheStats {
        public final int totalModels;
        public final int totalVertices;
        public final int totalIndices;

        CacheStats(int totalModels, int totalVertices, int totalIndices) {
            this.totalModels = totalModels;
            this.totalVertices = totalVertices;
            this.totalIndices = totalIndices;
        }

        @Override
        public String toString() {
            return "CacheStats{" +
                    "models=" + totalModels +
                    ", vertices=" + totalVertices +
                    ", indices=" + totalIndices +
                    '}';
        }
    }
}
