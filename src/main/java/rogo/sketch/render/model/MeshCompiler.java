package rogo.sketch.render.model;

import rogo.sketch.render.data.PrimitiveType;
import rogo.sketch.render.data.Usage;
import rogo.sketch.render.data.filler.VertexFiller;
import rogo.sketch.render.data.format.DataFormat;
import rogo.sketch.render.resource.buffer.VertexResource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility class for compiling Mesh objects into ModelMesh objects with GPU buffers.
 * <p>
 * The compiler combines vertex data from all sub-meshes into a single VertexResource
 * and tracks the offsets for efficient batch rendering.
 */
public class MeshCompiler {

    /**
     * Compilation options for controlling the compilation process
     */
    public static class CompilationOptions {
        private Usage vertexUsage = Usage.STATIC_DRAW;
        private boolean validateData = true;
        private boolean optimizeVertexOrder = false;
        private boolean mergeCompatibleSubMeshes = false;

        public CompilationOptions setVertexUsage(Usage usage) {
            this.vertexUsage = usage;
            return this;
        }

        public CompilationOptions setValidateData(boolean validate) {
            this.validateData = validate;
            return this;
        }

        public CompilationOptions setOptimizeVertexOrder(boolean optimize) {
            this.optimizeVertexOrder = optimize;
            return this;
        }

        public CompilationOptions setMergeCompatibleSubMeshes(boolean merge) {
            this.mergeCompatibleSubMeshes = merge;
            return this;
        }

        public Usage getVertexUsage() {
            return vertexUsage;
        }

        public boolean isValidateData() {
            return validateData;
        }

        public boolean isOptimizeVertexOrder() {
            return optimizeVertexOrder;
        }

        public boolean isMergeCompatibleSubMeshes() {
            return mergeCompatibleSubMeshes;
        }
    }

    /**
     * Result of mesh compilation containing the ModelMesh and compilation info
     */
    public static class CompilationResult {
        private final BakedMesh modelMesh;
        private final Map<String, Object> compilationInfo;

        public CompilationResult(BakedMesh modelMesh, Map<String, Object> compilationInfo) {
            this.modelMesh = modelMesh;
            this.compilationInfo = compilationInfo;
        }

        public BakedMesh getModelMesh() {
            return modelMesh;
        }

        public Map<String, Object> getCompilationInfo() {
            return compilationInfo;
        }
    }

    /**
     * Compile a Mesh into a ModelMesh with default options
     */
    public static BakedMesh compile(MeshGroup meshGroup) {
        return compile(meshGroup, new CompilationOptions()).getModelMesh();
    }

    /**
     * Compile a Mesh into a ModelMesh with custom options
     */
    public static CompilationResult compile(MeshGroup meshGroup, CompilationOptions options) {
        if (options.isValidateData() && !meshGroup.isValid()) {
            throw new IllegalArgumentException("Mesh validation failed: " + meshGroup.getName());
        }

        CompilationContext context = new CompilationContext(meshGroup, options);

        try {
            // Step 1: Analyze mesh structure
            analyzeMesh(context);

            // Step 2: Group sub-meshes by format and primitive type
            groupSubMeshes(context);

            // Step 3: Create vertex resources for each group
            createVertexResources(context);

            // Step 4: Fill vertex data
            fillVertexData(context);

            // Step 5: Create sub-mesh instances
            createSubMeshInstances(context);

            // Step 6: Create final ModelMesh
            BakedMesh modelMesh = createModelMesh(context);

            return new CompilationResult(modelMesh, context.getCompilationInfo());

        } catch (Exception e) {
            // Clean up any resources created during compilation
            context.cleanup();
            throw new RuntimeException("Failed to compile mesh: " + meshGroup.getName(), e);
        }
    }

    /**
     * Internal compilation context
     */
    private static class CompilationContext {
        final MeshGroup meshGroup;
        final CompilationOptions options;
        final Map<String, Object> compilationInfo;

        // Analysis results
        final Map<DataFormat, List<SubMesh>> subMeshesByFormat;
        final Map<PrimitiveType, List<SubMesh>> subMeshesByPrimitive;

        // Compilation results
        final List<VertexResource> vertexResources;
        final List<SubMeshInstance> subMeshInstances;

        CompilationContext(MeshGroup meshGroup, CompilationOptions options) {
            this.meshGroup = meshGroup;
            this.options = options;
            this.compilationInfo = new HashMap<>();
            this.subMeshesByFormat = new HashMap<>();
            this.subMeshesByPrimitive = new HashMap<>();
            this.vertexResources = new ArrayList<>();
            this.subMeshInstances = new ArrayList<>();
        }

        Map<String, Object> getCompilationInfo() {
            return compilationInfo;
        }

        void cleanup() {
            for (VertexResource resource : vertexResources) {
                if (!resource.isDisposed()) {
                    resource.dispose();
                }
            }
        }
    }

    /**
     * Step 1: Analyze mesh structure
     */
    private static void analyzeMesh(CompilationContext context) {
        context.compilationInfo.put("originalSubMeshCount", context.meshGroup.getSubMeshCount());
        context.compilationInfo.put("originalVertexCount", context.meshGroup.getTotalVertexCount());
        context.compilationInfo.put("originalIndexCount", context.meshGroup.getTotalIndexCount());
        context.compilationInfo.put("boneCount", context.meshGroup.getBoneCount());
        context.compilationInfo.put("hasAnimation", context.meshGroup.hasAnimation());
    }

    /**
     * Step 2: Group sub-meshes by format and primitive type
     */
    private static void groupSubMeshes(CompilationContext context) {
        PrimitiveType meshPrimitiveType = context.meshGroup.getPrimitiveType();

        for (SubMesh subMesh : context.meshGroup.getSubMeshes()) {
            // Group by format
            DataFormat format = subMesh.getVertexFormat();
            context.subMeshesByFormat.computeIfAbsent(format, k -> new ArrayList<>()).add(subMesh);

            // Group by primitive type (all sub-meshes use mesh's primitive type)
            context.subMeshesByPrimitive.computeIfAbsent(meshPrimitiveType, k -> new ArrayList<>()).add(subMesh);
        }

        context.compilationInfo.put("formatGroupCount", context.subMeshesByFormat.size());
        context.compilationInfo.put("primitiveTypeGroupCount", context.subMeshesByPrimitive.size());
    }

    /**
     * Step 3: Create vertex resources for each format group
     */
    private static void createVertexResources(CompilationContext context) {
        // For simplicity, create one vertex resource per format
        // In a more advanced implementation, we could create resources per format+primitive combination

        PrimitiveType meshPrimitiveType = context.meshGroup.getPrimitiveType();

        for (Map.Entry<DataFormat, List<SubMesh>> entry : context.subMeshesByFormat.entrySet()) {
            DataFormat format = entry.getKey();
            List<SubMesh> subMeshes = entry.getValue();

            // Use mesh's primitive type for all sub-meshes
            VertexResource vertexResource = new VertexResource(
                    format,
                    null, // No dynamic format for now
                    rogo.sketch.render.vertex.DrawMode.NORMAL,
                    meshPrimitiveType,
                    context.options.getVertexUsage()
            );

            context.vertexResources.add(vertexResource);
        }
    }

    /**
     * Step 4: Fill vertex data into vertex resources
     */
    private static void fillVertexData(CompilationContext context) {
        int resourceIndex = 0;

        for (Map.Entry<DataFormat, List<SubMesh>> entry : context.subMeshesByFormat.entrySet()) {
            DataFormat format = entry.getKey();
            List<SubMesh> subMeshes = entry.getValue();
            VertexResource vertexResource = context.vertexResources.get(resourceIndex++);

            // Calculate total vertex count for this resource
            int totalVertices = subMeshes.stream().mapToInt(SubMesh::getVertexCount).sum();

            // Pre-allocate buffer
            vertexResource.preAllocateStaticBuffer(totalVertices);

            // Create vertex filler
            PrimitiveType primitiveType = context.meshGroup.getPrimitiveType();
            VertexFiller vertexFiller = new VertexFiller(format, primitiveType, totalVertices);

            int currentVertexOffset = 0;
            int currentIndexOffset = 0;

            // Fill data for each sub-mesh
            for (SubMesh subMesh : subMeshes) {
                fillSubMeshData(vertexFiller, subMesh, currentVertexOffset);

                // Create sub-mesh instance
                SubMeshInstance instance = new SubMeshInstance(
                        subMesh,
                        currentVertexOffset,
                        currentIndexOffset
                );
                context.subMeshInstances.add(instance);

                currentVertexOffset += subMesh.getVertexCount();
                if (subMesh.usesIndexBuffer()) {
                    currentIndexOffset += subMesh.getIndexCount();
                }
            }

            // Upload to GPU
            vertexResource.uploadFromVertexFiller(vertexFiller);

            // Clean up filler
            vertexFiller.dispose();
        }
    }

    /**
     * Fill vertex data for a single sub-mesh
     */
    private static void fillSubMeshData(VertexFiller filler, SubMesh subMesh, int vertexOffset) {
        float[] vertexData = subMesh.getVertexData();
        int floatsPerVertex = subMesh.getVertexFloatCount();

        // Fill vertices
        for (int i = 0; i < subMesh.getVertexCount(); i++) {
            filler.vertex(vertexOffset + i);

            // Fill vertex attributes
            for (int j = 0; j < floatsPerVertex; j++) {
                filler.putFloat(vertexData[i * floatsPerVertex + j]);
            }
        }
    }

    /**
     * Step 5: Create sub-mesh instances (already done in fillVertexData)
     */
    private static void createSubMeshInstances(CompilationContext context) {
        context.compilationInfo.put("compiledSubMeshCount", context.subMeshInstances.size());
    }

    /**
     * Step 6: Create the final ModelMesh
     */
    private static BakedMesh createModelMesh(CompilationContext context) {
        // For simplicity, use the first vertex resource
        // In a more advanced implementation, we might merge multiple resources
        VertexResource primaryResource = context.vertexResources.get(0);

        // If we have multiple resources, we need to merge them
        if (context.vertexResources.size() > 1) {
            primaryResource = mergeVertexResources(context.vertexResources, context);
        }

        return new BakedMesh(
                context.meshGroup.getName() + "_compiled",
                context.meshGroup,
                primaryResource,
                context.subMeshInstances
        );
    }

    /**
     * Merge multiple vertex resources into one (simplified implementation)
     */
    private static VertexResource mergeVertexResources(List<VertexResource> resources, CompilationContext context) {
        // For now, just return the first resource
        // A full implementation would merge the data from all resources
        context.compilationInfo.put("warning", "Multiple vertex resources detected, using first one only");
        return resources.get(0);
    }

    /**
     * Create default compilation options
     */
    public static CompilationOptions defaultOptions() {
        return new CompilationOptions();
    }

    /**
     * Create optimized compilation options for static meshes
     */
    public static CompilationOptions staticMeshOptions() {
        return new CompilationOptions()
                .setVertexUsage(Usage.STATIC_DRAW)
                .setOptimizeVertexOrder(true)
                .setMergeCompatibleSubMeshes(true);
    }

    /**
     * Create compilation options for dynamic meshes
     */
    public static CompilationOptions dynamicMeshOptions() {
        return new CompilationOptions()
                .setVertexUsage(Usage.DYNAMIC_DRAW)
                .setOptimizeVertexOrder(false)
                .setMergeCompatibleSubMeshes(false);
    }
}
