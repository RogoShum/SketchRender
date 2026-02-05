package rogo.sketch.vanilla.graphics;

import net.minecraft.client.Minecraft;
import org.joml.Vector3f;
import rogo.sketch.SketchRender;
import rogo.sketch.core.api.graphics.AsyncTickable;
import rogo.sketch.core.api.graphics.TransformableGraphics;
import rogo.sketch.core.api.model.PreparedMesh;
import rogo.sketch.core.data.builder.VertexStreamBuilder;
import rogo.sketch.core.instance.MeshGraphics;
import rogo.sketch.core.model.MeshGroup;
import rogo.sketch.core.pipeline.PartialRenderSetting;
import rogo.sketch.core.resource.GraphicsResourceManager;
import rogo.sketch.core.resource.ResourceReference;
import rogo.sketch.core.resource.ResourceTypes;
import rogo.sketch.core.transform.Transform;
import rogo.sketch.core.util.KeyId;

/**
 * CubeTestGraphics - Test graphics using the Transform system.
 * 
 * This graphics instance has its own Transform which is a child of the player's Transform.
 * The cube's local transform (offset, scale, rotation) is set in the constructor
 * and combined with the parent (player) transform on the GPU.
 */
public class CubeTestGraphics extends MeshGraphics implements AsyncTickable, TransformableGraphics {
    private final ResourceReference<PartialRenderSetting> renderSetting;
    
    // Transform component keys
    public static final KeyId TRANSFORM_ID = KeyId.of("transform_id");
    
    private final ResourceReference<MeshGroup> cube = GraphicsResourceManager.getInstance()
            .getReference(ResourceTypes.MESH, KeyId.of(SketchRender.MOD_ID, "cube"));

    private final KeyId meshName;
    
    // The cube's own transform (child of player transform)
    private final Transform cubeTransform = new Transform(true);
    
    private Vector3f offset, scale, rotation;

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
        
        // Set up local transform
        cubeTransform.setLocalPosition(offset);
        cubeTransform.setLocalScale(scale);
        cubeTransform.setLocalRotation(rotation.x, rotation.y, rotation.z);
        this.offset = offset;
        this.scale = scale;
        this.rotation = rotation;
    }
    
    /**
     * Set the parent transform (e.g., player transform).
     * This must be called before the cube is rendered.
     */
    public void setParentTransform(Transform parent) {
        cubeTransform.setParent(parent);
    }
    
    @Override
    public Transform getTransform() {
        return cubeTransform;
    }

    @Override
    public PartialRenderSetting getPartialRenderSetting() {
        if (renderSetting.isAvailable()) {
            return renderSetting.get();
        }
        return null;
    }

    @Override
    public void asyncTick() {
        cubeTransform.setLocalPosition(offset);
        cubeTransform.setLocalScale(scale);
        cubeTransform.setLocalRotation(rotation.x, rotation.y, rotation.z);
    }

    @Override
    public void swapData() {
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
            // Write the transform ID so the vertex shader can look up the world matrix
            int transformId = cubeTransform.getRegisteredId();
            builder.put(transformId);
        }
    }
    
    /**
     * Get the cube's transform.
     */
    public Transform getCubeTransform() {
        return cubeTransform;
    }
}
