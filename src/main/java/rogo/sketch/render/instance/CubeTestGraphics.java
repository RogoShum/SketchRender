package rogo.sketch.render.instance;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import rogo.sketch.SketchRender;
import rogo.sketch.api.graphics.InstanceDataProvider;
import rogo.sketch.api.model.PreparedMesh;
import rogo.sketch.render.data.builder.VertexDataBuilder;
import rogo.sketch.render.model.MeshGroup;
import rogo.sketch.render.resource.GraphicsResourceManager;
import rogo.sketch.render.resource.ResourceReference;
import rogo.sketch.render.resource.ResourceTypes;
import rogo.sketch.util.Identifier;

public class CubeTestGraphics extends MeshGraphics implements InstanceDataProvider {
    private final ResourceReference<MeshGroup> cube = GraphicsResourceManager.getInstance().getReference(ResourceTypes.MESH, Identifier.of(SketchRender.MOD_ID, "cube"));
    private Vector3f cubePos = new Vector3f();
    private final Vector3f offset;
    private final Vector3f scale;
    private final Vector3f rotation;
    private final boolean attachHead;

    public CubeTestGraphics(Identifier identifier, boolean attachHead, Vector3f offset, Vector3f scale, Vector3f rotation) {
        super(identifier);
        this.attachHead = attachHead;
        this.offset = offset;
        this.scale = scale;
        this.rotation = rotation;
    }

    @Override
    public boolean shouldTick() {
        return Minecraft.getInstance().player != null;
    }

    public void tick() {
        Entity player = Minecraft.getInstance().player;
        if (player != null) {
            Vec3 playerPos = player.getPosition(Minecraft.getInstance().getPartialTick());
            Vec3 cameraPos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();

            Vector3f offset = playerPos.subtract(cameraPos).toVector3f();
            cubePos = offset.mul(1);
        }
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
        return cube.get().getMesh("cube_geometry");
    }

    @Override
    public void fillInstanceData(int bindingPoint, VertexDataBuilder builder) {
        if (bindingPoint == 1) {
            Entity player = Minecraft.getInstance().player;
            if (player != null) {
                Vec3 playerPos = attachHead ? player.getEyePosition(Minecraft.getInstance().getPartialTick()) : player.getPosition(Minecraft.getInstance().getPartialTick());
                cubePos = playerPos.toVector3f();
            }

            builder.put(cubePos.x(), cubePos.y(), cubePos.z()).endVertex();
        } else if (bindingPoint == 2) {
            builder.put(offset.x(), offset.y(), offset.z()).endVertex();
            builder.put(scale.x(), scale.y(), scale.z()).endVertex();
            Vector3f playerDir = Minecraft.getInstance().player.getForward().toVector3f().add(rotation).normalize();
            builder.put(playerDir.x(), playerDir.y(), playerDir.z()).endVertex();
        }
    }
}