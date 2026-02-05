package rogo.sketch.vanilla.graphics;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import rogo.sketch.core.api.graphics.AsyncTickable;
import rogo.sketch.core.api.graphics.Tickable;
import rogo.sketch.core.api.graphics.TransformableGraphics;
import rogo.sketch.core.api.model.PreparedMesh;
import rogo.sketch.core.data.builder.VertexStreamBuilder;
import rogo.sketch.core.instance.MeshGraphics;
import rogo.sketch.core.pipeline.PartialRenderSetting;
import rogo.sketch.core.transform.Transform;
import rogo.sketch.core.util.KeyId;

/**
 * PlayerGraphics - Graphics instance representing the player's transform.
 * 
 * This class provides a Transform that tracks the player's position and rotation.
 * Other graphics (like CubeTestGraphics) can use this as a parent transform
 * to be positioned relative to the player.
 */
public class PlayerGraphics extends MeshGraphics implements Tickable, TransformableGraphics {
    
    // The player's transform
    private final Transform playerTransform = new Transform(false);
    
    public PlayerGraphics(KeyId keyId) {
        super(keyId);
    }
    
    @Override
    public Transform getTransform() {
        return playerTransform;
    }

    @Override
    public boolean shouldDiscard() {
        return false;
    }
    
    @Override
    public boolean shouldRender() {
        // This graphics doesn't render anything itself
        // It just provides the player transform for children
        return false;
    }
    
    @Override
    public PartialRenderSetting getPartialRenderSetting() {
        return PartialRenderSetting.EMPTY;
    }
    
    @Override
    public PreparedMesh getPreparedMesh() {
        return null;
    }
    
    @Override
    public void fillVertex(KeyId componentKey, VertexStreamBuilder builder) {
        // No vertices to fill - this is just a transform provider
    }
    
    /**
     * Get the player transform for use as a parent by child graphics.
     */
    public Transform getPlayerTransform() {
        return playerTransform;
    }

    @Override
    public void tick() {
        Player player = Minecraft.getInstance().player;
        if (player != null) {
            Vec3 pos = player.position();

            // Update player transform position
            playerTransform.setLocalPosition((float) pos.x, (float) pos.y, (float) pos.z);

            // Update player rotation (convert to radians)
            float pitch = (float) Math.toRadians(player.getXRot());
            float yaw = (float) Math.toRadians(-player.getYRot());
            playerTransform.setLocalRotation(pitch, yaw, 0);
        }
    }
}