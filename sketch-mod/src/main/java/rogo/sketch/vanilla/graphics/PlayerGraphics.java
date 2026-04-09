package rogo.sketch.vanilla.graphics;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import rogo.sketch.core.api.graphics.Graphics;
import rogo.sketch.core.util.KeyId;
import rogo.sketch.module.transform.FrameTransformSource;
import rogo.sketch.module.transform.TransformWriter;

/**
 * Player transform provider collected in the frame graph after sync frame prepare.
 */
public class PlayerGraphics implements Graphics, FrameTransformSource {
    private final KeyId id;

    public PlayerGraphics(KeyId keyId) {
        this.id = keyId;
    }

    @Override
    public KeyId getIdentifier() {
        return id;
    }

    @Override
    public boolean shouldDiscard() {
        return false;
    }
    
    @Override
    public boolean shouldRender() {
        return false;
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

