package rogo.sketch.core.resource.loader;

import com.google.gson.JsonObject;
import rogo.sketch.core.api.model.BakedTypeMesh;
import rogo.sketch.core.api.model.PreparedMesh;
import rogo.sketch.core.data.MeshIndexMode;
import rogo.sketch.core.data.PrimitiveType;
import rogo.sketch.core.data.format.VertexLayoutSpec;
import rogo.sketch.core.instance.DrawCallGraphics;
import rogo.sketch.core.model.MeshGroup;
import rogo.sketch.core.pipeline.module.diagnostic.SketchDiagnostics;
import rogo.sketch.core.pipeline.parmeter.RasterizationParameter;
import rogo.sketch.core.pipeline.parmeter.RenderParameter;
import rogo.sketch.core.resource.GraphicsResourceManager;
import rogo.sketch.core.resource.ResourceReference;
import rogo.sketch.core.resource.ResourceTypes;
import rogo.sketch.core.resource.descriptor.BufferUpdatePolicy;
import rogo.sketch.core.data.layout.StructLayout;
import rogo.sketch.core.util.KeyId;

public class DrawCallGraphicsLoader implements ResourceLoader<DrawCallGraphics> {
    @Override
    public KeyId getResourceType() {
        return ResourceTypes.DRAW_CALL;
    }

    @Override
    public DrawCallGraphics load(ResourceLoadContext context) {
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

            return new DrawCallGraphics(keyId, KeyId.of(stageId), partialRenderSettingId, preparedMesh, renderParameter);

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

