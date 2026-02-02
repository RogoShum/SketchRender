package rogo.sketch.vanilla.graphics;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import rogo.sketch.SketchRender;
import rogo.sketch.core.api.graphics.AsyncTickable;
import rogo.sketch.core.api.model.PreparedMesh;
import rogo.sketch.core.data.builder.VertexStreamBuilder;
import rogo.sketch.core.instance.MeshGraphics;
import rogo.sketch.core.model.MeshGroup;
import rogo.sketch.core.pipeline.PartialRenderSetting;
import rogo.sketch.core.resource.GraphicsResourceManager;
import rogo.sketch.core.resource.ResourceReference;
import rogo.sketch.core.resource.ResourceTypes;
import rogo.sketch.core.util.GraphicsData;
import rogo.sketch.core.util.KeyId;
import rogo.sketch.core.util.TripleBuffer;

public class CubeTestGraphics extends MeshGraphics implements AsyncTickable {
    private final ResourceReference<PartialRenderSetting> renderSetting;
    public static final KeyId ENTITY_POS = KeyId.of("entity_pos");
    public static final KeyId ENTITY_TRANSFORM = KeyId.of("entity_transform");
    private final ResourceReference<MeshGroup> cube = GraphicsResourceManager.getInstance()
            .getReference(ResourceTypes.MESH, KeyId.of(SketchRender.MOD_ID, "cube"));

    private final Vector3f offset;
    private final Vector3f scale;
    private final Vector3f rotation;
    private final boolean attachHead;
    private final KeyId meshName;

    // Triple Buffer for Zero-Allocation Thread Safety
    private final TripleBuffer<CubeData> tripleBuffer = new TripleBuffer<>(CubeData::new);

    public CubeTestGraphics(KeyId keyId, ResourceReference<PartialRenderSetting> renderSetting, boolean attachHead,
                            KeyId meshName, Vector3f offset, Vector3f scale, Vector3f rotation) {
        super(keyId);
        this.attachHead = attachHead;
        this.offset = offset;
        this.scale = scale;
        this.rotation = rotation;
        this.meshName = meshName;
        this.renderSetting = renderSetting;
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
        Entity player = Minecraft.getInstance().player;
        if (player != null) {
            Vec3 playerPos = attachHead ? player.getEyePosition()
                    : player.getPosition(0);

            // Write to NEXT buffer
            CubeData next = tripleBuffer.getWrite();
            next.cubePos.set(playerPos.toVector3f());
        }
    }

    @Override
    public void swapData() {
        tripleBuffer.swap();
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
        if (componentKey.equals(ENTITY_POS)) {
            // Read from Current buffer
            CubeData current = tripleBuffer.getRead();
            CubeData prev = tripleBuffer.getPrev();

            builder.put(prev.cubePos.x(), prev.cubePos.y(), prev.cubePos.z());
            builder.put(current.cubePos.x(), current.cubePos.y(), current.cubePos.z());
        } else if (componentKey.equals(ENTITY_TRANSFORM)) {
            builder.put(offset.x(), offset.y(), offset.z());
            builder.put(scale.x(), scale.y(), scale.z());

            if (Minecraft.getInstance().player != null) {
                Vector3f playerDir = Minecraft.getInstance().player.getForward().toVector3f().add(rotation).normalize();
                builder.put(playerDir.x(), playerDir.y(), playerDir.z());
            } else {
                builder.put(0f, 0f, 1f);
            }
        }
    }

    // Data Class
    private static class CubeData extends GraphicsData<CubeData> {
        final Vector3f cubePos = new Vector3f();

        @Override
        public void copyFrom(CubeData other) {
            this.cubePos.set(other.cubePos);
        }
    }
}