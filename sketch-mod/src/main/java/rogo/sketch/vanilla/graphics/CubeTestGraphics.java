package rogo.sketch.vanilla.graphics;

import net.minecraft.client.Minecraft;
import org.joml.Vector3f;
import rogo.sketch.SketchRender;
import rogo.sketch.core.api.graphics.AsyncTickTransformSource;
import rogo.sketch.core.api.graphics.Graphics;
import rogo.sketch.core.api.graphics.TransformIdAware;
import rogo.sketch.core.api.graphics.TransformParentSource;
import rogo.sketch.core.api.model.PreparedMesh;
import rogo.sketch.core.data.builder.VertexStreamBuilder;
import rogo.sketch.core.instance.MeshGraphics;
import rogo.sketch.core.model.MeshGroup;
import rogo.sketch.core.pipeline.PartialRenderSetting;
import rogo.sketch.core.resource.GraphicsResourceManager;
import rogo.sketch.core.resource.ResourceReference;
import rogo.sketch.core.resource.ResourceTypes;
import rogo.sketch.core.transform.TransformWriter;
import rogo.sketch.core.util.KeyId;

/**
 * Test graphics whose local transform is collected asynchronously after each tick.
 * Parent linkage is resolved by the transform module through graphics references.
 */
public class CubeTestGraphics extends MeshGraphics
        implements AsyncTickTransformSource, TransformParentSource, TransformIdAware {
    private final ResourceReference<PartialRenderSetting> renderSetting;
    
    // Transform component keys
    public static final KeyId TRANSFORM_ID = KeyId.of("transform_id");
    
    private final ResourceReference<MeshGroup> cube = GraphicsResourceManager.getInstance()
            .getReference(ResourceTypes.MESH, KeyId.of(SketchRender.MOD_ID, "cube"));

    private final KeyId meshName;
    
    private Vector3f offset, scale, rotation;
    private Graphics parentGraphics;
    private int transformId = -1;

    /**
     * Create a new CubeTestGraphics with local transform parameters.
     * 
     * @param keyId Unique identifier
     * @param renderSetting Render settings reference
     * @param meshName Mesh name to use
     * @param offset Local position offset from parent
     * @param scale Local scale
     * @param rotation Local rotation in radians (pitch, yaw, roll)
     */
    public CubeTestGraphics(KeyId keyId, ResourceReference<PartialRenderSetting> renderSetting,
                            KeyId meshName, Vector3f offset, Vector3f scale, Vector3f rotation) {
        super(keyId);
        this.meshName = meshName;
        this.renderSetting = renderSetting;
        
        this.offset = offset;
        this.scale = scale;
        this.rotation = rotation;
    }

    public void setParentGraphics(Graphics parentGraphics) {
        this.parentGraphics = parentGraphics;
    }

    @Override
    public PartialRenderSetting getPartialRenderSetting() {
        if (renderSetting.isAvailable()) {
            return renderSetting.get();
        }
        return null;
    }

    @Override
    public void writeAsyncTickTransform(TransformWriter writer) {
        writer.setPosition(offset);
        writer.setScale(scale);
        writer.setRotation(rotation);
    }

    @Override
    public Graphics getTransformParent() {
        return parentGraphics;
    }

    @Override
    public boolean shouldDiscard() {
        return false;
    }

    @Override
    public boolean shouldRender() {
        return Minecraft.getInstance().player != null;
    }

    @Override
    public PreparedMesh getPreparedMesh() {
        return cube.get().getMesh(meshName);
    }

    @Override
    public void fillVertex(KeyId componentKey, VertexStreamBuilder builder) {
        if (componentKey.equals(TRANSFORM_ID)) {
            builder.put(transformId);
        }
    }

    @Override
    public void setTransformId(int transformId) {
        this.transformId = transformId;
    }
}
