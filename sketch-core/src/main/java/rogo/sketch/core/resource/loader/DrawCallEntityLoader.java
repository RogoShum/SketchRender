package rogo.sketch.core.resource.loader;

import com.google.gson.JsonObject;
import rogo.sketch.core.api.model.BakedTypeMesh;
import rogo.sketch.core.api.model.PreparedMesh;
import rogo.sketch.core.data.MeshIndexMode;
import rogo.sketch.core.data.PrimitiveType;
import rogo.sketch.core.data.format.VertexLayoutSpec;
import rogo.sketch.core.api.graphics.DescriptorStability;
import rogo.sketch.core.api.graphics.SubmissionCapability;
import rogo.sketch.core.graphics.ecs.GraphicsBuiltinComponents;
import rogo.sketch.core.graphics.ecs.GraphicsEntityBlueprint;
import rogo.sketch.core.model.MeshGroup;
import rogo.sketch.core.pipeline.PartialRenderSetting;
import rogo.sketch.core.pipeline.PipelineType;
import rogo.sketch.core.pipeline.RenderSetting;
import rogo.sketch.core.pipeline.RenderSettingCompiler;
import rogo.sketch.core.pipeline.module.diagnostic.SketchDiagnostics;
import rogo.sketch.core.pipeline.parmeter.RasterizationParameter;
import rogo.sketch.core.pipeline.parmeter.RenderParameter;
import rogo.sketch.core.pipeline.geometry.GeometrySourceKey;
import rogo.sketch.core.resource.GraphicsResourceManager;
import rogo.sketch.core.resource.ResourceReference;
import rogo.sketch.core.resource.ResourceTypes;
import rogo.sketch.core.resource.descriptor.BufferUpdatePolicy;
import rogo.sketch.core.data.layout.StructLayout;
import rogo.sketch.core.util.KeyId;
import rogo.sketch.core.pipeline.flow.v2.GeometryBatchKey;
import rogo.sketch.core.pipeline.flow.ecs.GraphicsContainerHints;

import java.util.Objects;

public class DrawCallEntityLoader implements ResourceLoader<GraphicsEntityBlueprint> {
    @Override
    public KeyId getResourceType() {
        return ResourceTypes.DRAW_CALL;
    }

    @Override
    public GraphicsEntityBlueprint load(ResourceLoadContext context) {
        try {
            KeyId keyId = context.getResourceId();
            JsonObject json = context.getJson();
            if (json == null)
                return null;

            // Strict key checking
            if (!json.has("renderSetting") || json.get("renderSetting").isJsonNull()) {
                SketchDiagnostics.get().warn("draw-call-loader", "DrawCallGraphics " + keyId + " missing 'renderSetting'");
                return null;
            }
            if (!json.has("modelName") || json.get("modelName").isJsonNull()) {
                SketchDiagnostics.get().warn("draw-call-loader", "DrawCallGraphics " + keyId + " missing 'modelName'");
                return null;
            }
            if (!json.has("meshName") || json.get("meshName").isJsonNull()) {
                SketchDiagnostics.get().warn("draw-call-loader", "DrawCallGraphics " + keyId + " missing 'meshName'");
                return null;
            }
            if (!json.has("stage") || json.get("stage").isJsonNull()) {
                SketchDiagnostics.get().warn("draw-call-loader", "DrawCallGraphics " + keyId + " missing 'stage'");
                return null;
            }

            KeyId partialRenderSettingId = KeyId.of(json.get("renderSetting").getAsString());
            String modelName = json.get("modelName").getAsString();
            String meshName = json.get("meshName").getAsString();
            String stageId = json.get("stage").getAsString();

            KeyId modelId = KeyId.of(modelName);
            KeyId meshId = KeyId.of(meshName);

            ResourceReference<MeshGroup> meshGroupRef = GraphicsResourceManager.getInstance()
                    .getReference(ResourceTypes.MESH, modelId);

            if (!meshGroupRef.isAvailable()) {
                SketchDiagnostics.get().warn(
                        "draw-call-loader",
                        "MeshGroup " + modelId + " not available for DrawCallGraphics " + keyId);
                return null;
            }

            MeshGroup meshGroup = meshGroupRef.get();
            PreparedMesh preparedMesh = meshGroup.getMesh(meshId);
            if (preparedMesh == null) {
                SketchDiagnostics.get().warn(
                        "draw-call-loader",
                        "Mesh " + meshId + " not found in MeshGroup " + modelId + " for DrawCallGraphics " + keyId);
                return null;
            }

            StructLayout format = meshGroup.getVertexFormat();
            PrimitiveType primitiveType = meshGroup.getPrimitiveType();
            MeshIndexMode indexMode = resolveIndexMode(meshGroup, preparedMesh);

            VertexLayoutSpec vertexLayoutSpec = VertexLayoutSpec.builder()
                    .addStatic(BakedTypeMesh.BAKED_MESH, format)
                    .build();
            RenderParameter renderParameter = new RasterizationParameter(vertexLayoutSpec, primitiveType, indexMode,
                    BufferUpdatePolicy.IMMUTABLE, false);
            PartialRenderSetting partialRenderSetting = (PartialRenderSetting) GraphicsResourceManager.getInstance()
                    .getReference(ResourceTypes.PARTIAL_RENDER_SETTING, partialRenderSettingId)
                    .get();
            long descriptorVersion = Objects.hash(partialRenderSetting != null ? partialRenderSetting : PartialRenderSetting.EMPTY);
            return GraphicsEntityBlueprint.builder()
                    .put(GraphicsBuiltinComponents.IDENTITY, new GraphicsBuiltinComponents.IdentityComponent(keyId))
                    .put(GraphicsBuiltinComponents.LIFECYCLE, new GraphicsBuiltinComponents.LifecycleComponent(true, false))
                    .put(GraphicsBuiltinComponents.RESOURCE_ORIGIN, new GraphicsBuiltinComponents.ResourceOriginComponent(ResourceTypes.DRAW_CALL))
                    .put(GraphicsBuiltinComponents.STAGE_BINDING, new GraphicsBuiltinComponents.StageBindingComponent(
                            KeyId.of(stageId),
                            PipelineType.RASTERIZATION,
                            renderParameter))
                    .put(GraphicsBuiltinComponents.CONTAINER_HINT, new GraphicsBuiltinComponents.ContainerHintComponent(
                            GraphicsContainerHints.DEFAULT,
                            Long.valueOf(0L),
                            0L,
                            0))
                    .put(GraphicsBuiltinComponents.RASTER_RENDERABLE, new GraphicsBuiltinComponents.RasterRenderableComponent(true))
                    .put(GraphicsBuiltinComponents.SUBMISSION_CAPABILITY, new GraphicsBuiltinComponents.SubmissionCapabilityComponent(
                            SubmissionCapability.DIRECT_BATCHABLE))
                    .put(GraphicsBuiltinComponents.PREPARED_MESH, new GraphicsBuiltinComponents.PreparedMeshComponent(() -> preparedMesh))
                    .put(GraphicsBuiltinComponents.RENDER_DESCRIPTOR, new GraphicsBuiltinComponents.RenderDescriptorComponent(
                            DescriptorStability.STABLE,
                            () -> descriptorVersion,
                            parameter -> RenderSettingCompiler.compile(RenderSetting.fromPartial(
                                    parameter,
                                    partialRenderSetting != null ? partialRenderSetting : PartialRenderSetting.EMPTY))))
                    .put(GraphicsBuiltinComponents.DESCRIPTOR_VERSION, new GraphicsBuiltinComponents.DescriptorVersionComponent(() -> descriptorVersion))
                    .put(GraphicsBuiltinComponents.GEOMETRY_VERSION, new GraphicsBuiltinComponents.GeometryVersionComponent(() ->
                            Objects.hash(GeometrySourceKey.fromPreparedMesh(preparedMesh), SubmissionCapability.DIRECT_BATCHABLE)))
                    .build();

        } catch (Exception e) {
            SketchDiagnostics.get().error("draw-call-loader", "Failed to load DrawCallGraphics", e);
            return null;
        }
    }

    private MeshIndexMode resolveIndexMode(MeshGroup meshGroup, PreparedMesh preparedMesh) {
        Object metadataValue = meshGroup.getMetadata().get("indexMode");
        if (metadataValue instanceof String rawValue) {
            return switch (rawValue.toLowerCase()) {
                case "explicit_local" -> MeshIndexMode.EXPLICIT_LOCAL;
                case "generated" -> MeshIndexMode.GENERATED;
                case "none" -> MeshIndexMode.NONE;
                default -> preparedMesh.isIndexed() ? MeshIndexMode.EXPLICIT_LOCAL : MeshIndexMode.NONE;
            };
        }
        return preparedMesh.isIndexed() ? MeshIndexMode.EXPLICIT_LOCAL : MeshIndexMode.NONE;
    }
}

