package rogo.sketch.vanilla.graphics;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import rogo.sketch.core.api.graphics.FrameTransformSource;
import rogo.sketch.core.api.model.PreparedMesh;
import rogo.sketch.core.data.builder.VertexStreamBuilder;
import rogo.sketch.core.instance.MeshGraphics;
import rogo.sketch.core.pipeline.PartialRenderSetting;
import rogo.sketch.core.transform.TransformWriter;
import rogo.sketch.core.util.KeyId;

/**
 * Player transform provider collected in the frame graph after sync frame prepare.
 */
public class PlayerGraphics extends MeshGraphics implements FrameTransformSource {

    public PlayerGraphics(KeyId keyId) {
        super(keyId);
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

    @Override
    public void writeFrameTransform(TransformWriter writer) {
        Player player = Minecraft.getInstance().player;
        if (player != null) {
            Vec3 pos = player.getPosition(Minecraft.getInstance().getPartialTick());

            writer.setPosition((float) pos.x, (float) pos.y, (float) pos.z);

            float pitch = (float) Math.toRadians(player.getXRot());
            float yaw = (float) Math.toRadians(-player.getYRot());
            writer.setRotation(pitch, yaw, 0);
        } else {
            writer.reset();
        }
    }
}