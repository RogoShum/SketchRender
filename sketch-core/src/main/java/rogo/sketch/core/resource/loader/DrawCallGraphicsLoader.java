package rogo.sketch.core.resource.loader;

import com.google.gson.JsonObject;
import rogo.sketch.core.api.model.BakedTypeMesh;
import rogo.sketch.core.api.model.PreparedMesh;
import rogo.sketch.core.data.PrimitiveType;
import rogo.sketch.core.data.Usage;
import rogo.sketch.core.data.format.DataFormat;
import rogo.sketch.core.data.format.VertexLayoutSpec;
import rogo.sketch.core.instance.DrawCallGraphics;
import rogo.sketch.core.model.MeshGroup;
import rogo.sketch.core.pipeline.GraphicsPipeline;
import rogo.sketch.core.pipeline.parmeter.RasterizationParameter;
import rogo.sketch.core.pipeline.parmeter.RenderParameter;
import rogo.sketch.core.resource.GraphicsResourceManager;
import rogo.sketch.core.resource.ResourceReference;
import rogo.sketch.core.resource.ResourceTypes;
import rogo.sketch.core.util.KeyId;

public class DrawCallGraphicsLoader implements ResourceLoader<DrawCallGraphics> {
    private final GraphicsPipeline<?> graphicsPipeline;

    public DrawCallGraphicsLoader(GraphicsPipeline<?> graphicsPipeline) {
        this.graphicsPipeline = graphicsPipeline;
    }

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
                System.err.println("DrawCallGraphics " + keyId + " missing 'renderSetting'");
                return null;
            }
            if (!json.has("modelName") || json.get("modelName").isJsonNull()) {
                System.err.println("DrawCallGraphics " + keyId + " missing 'modelName'");
                return null;
            }
            if (!json.has("meshName") || json.get("meshName").isJsonNull()) {
                System.err.println("DrawCallGraphics " + keyId + " missing 'meshName'");
                return null;
            }
            if (!json.has("stage") || json.get("stage").isJsonNull()) {
                System.err.println("DrawCallGraphics " + keyId + " missing 'stage'");
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
                System.err.println("MeshGroup " + modelId + " not available for DrawCallGraphics " + keyId);
                return null;
            }

            MeshGroup meshGroup = meshGroupRef.get();
            PreparedMesh preparedMesh = meshGroup.getMesh(meshId);
            if (preparedMesh == null) {
                System.err.println(
                        "Mesh " + meshId + " not found in MeshGroup " + modelId + " for DrawCallGraphics " + keyId);
                return null;
            }

            DataFormat format = meshGroup.getVertexFormat();
            PrimitiveType primitiveType = meshGroup.getPrimitiveType();

            VertexLayoutSpec vertexLayoutSpec = VertexLayoutSpec.builder()
                    .addStatic(BakedTypeMesh.BAKED_MESH, format)
                    .build();
            RenderParameter renderParameter = new RasterizationParameter(vertexLayoutSpec, primitiveType,
                    Usage.STATIC_DRAW, false);

            DrawCallGraphics graphics = new DrawCallGraphics(keyId, partialRenderSettingId, preparedMesh);
            graphicsPipeline.addGraphInstance(KeyId.of(stageId), graphics, renderParameter);

            return graphics;

        } catch (Exception e) {
            System.err.println("Failed to load DrawCallGraphics: " + e.getMessage());
            return null;
        }
    }
}