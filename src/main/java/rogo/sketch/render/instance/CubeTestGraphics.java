package rogo.sketch.render.instance;

import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ResourceManager;
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

    public CubeTestGraphics(Identifier identifier) {
        super(identifier);
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
                Vec3 playerPos = player.getEyePosition(Minecraft.getInstance().getPartialTick());
                cubePos = playerPos.toVector3f();
            }

            builder.put(cubePos.x(), cubePos.y(), cubePos.z());
        }
    }
}