package rogo.sketch.compat.sodium;

import com.mojang.blaze3d.vertex.VertexFormatElement;
import me.jellysquid.mods.sodium.client.render.chunk.region.RenderRegion;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL15;
import rogo.sketch.Config;
import rogo.sketch.render.resource.buffer.CounterBuffer;
import rogo.sketch.render.resource.buffer.IndirectCommandBuffer;
import rogo.sketch.render.resource.buffer.PersistentReadSSBO;
import rogo.sketch.render.resource.buffer.ShaderStorageBuffer;

public class MeshResource {
    private static int RENDER_DISTANCE = -1;
    private static int SPACE_PARTITION_SIZE = 0;
    public static int QUEUE_UPDATE_COUNT = 0;
    public static int LAST_QUEUE_UPDATE_COUNT = 0;
    public static int THEORETICAL_REGION_QUANTITY = 0;

    public static int CURRENT_FRAME = 0;
    public static int ORDERED_REGION_SIZE = 0;

    public static ShaderStorageBuffer COMMAND_BUFFER;
    public static ShaderStorageBuffer BATCH_COUNTER;
    public static ShaderStorageBuffer REGION_INDEX_BUFFER;
    public static ShaderStorageBuffer MAX_ELEMENT_BUFFER;
    public static PersistentReadSSBO PERSISTENT_MAX_ELEMENT_BUFFER;
    public static CounterBuffer CULLING_COUNTER;
    public static CounterBuffer ELEMENT_COUNTER;

    public static final RegionMeshManager MESH_MANAGER = new RegionMeshManager();

    static {
        COMMAND_BUFFER = new ShaderStorageBuffer(IndirectCommandBuffer.INSTANCE);
        CULLING_COUNTER = new CounterBuffer(VertexFormatElement.Type.INT);
        BATCH_COUNTER = new ShaderStorageBuffer(CULLING_COUNTER);
        ELEMENT_COUNTER = new CounterBuffer(VertexFormatElement.Type.INT);
        MAX_ELEMENT_BUFFER = new ShaderStorageBuffer(ELEMENT_COUNTER);
        REGION_INDEX_BUFFER = new ShaderStorageBuffer(1, 16, GL15.GL_DYNAMIC_DRAW);

        PERSISTENT_MAX_ELEMENT_BUFFER = new PersistentReadSSBO(1, Integer.BYTES);
    }

    public static void addIndexedRegion(RenderRegion region) {
        MESH_MANAGER.addRegion(region);

        if (Config.getCullChunk()) {
            int regionSize = MESH_MANAGER.size();
            int passSize = IndirectCommandBuffer.PASS_SIZE * regionSize;

            if (regionSize * IndirectCommandBuffer.REGION_PASS_COMMAND_SIZE * 20L > IndirectCommandBuffer.INSTANCE.getCapacity()) {
                IndirectCommandBuffer.INSTANCE.resize(MESH_MANAGER.size() * IndirectCommandBuffer.REGION_PASS_COMMAND_SIZE);
                COMMAND_BUFFER.setBufferPointer(IndirectCommandBuffer.INSTANCE.getMemoryAddress());
                COMMAND_BUFFER.setCapacity(IndirectCommandBuffer.INSTANCE.getCapacity());
                COMMAND_BUFFER.resetUpload(GL15.GL_STATIC_DRAW);
            }

            if (passSize * CULLING_COUNTER.getStride() > CULLING_COUNTER.getCapacity()) {
                CULLING_COUNTER.resize(passSize);
                BATCH_COUNTER.setBufferPointer(CULLING_COUNTER.getMemoryAddress());
                BATCH_COUNTER.setCapacity(CULLING_COUNTER.getCapacity());
                BATCH_COUNTER.resetUpload(GL15.GL_STATIC_DRAW);
            }

            REGION_INDEX_BUFFER.ensureCapacity(regionSize, true);
        }
    }

    public static void removeRegion(RenderRegion region) {
        MESH_MANAGER.removeRegion(region);
    }

    public static void clearRegions() {
        MESH_MANAGER.refresh();
        IndirectCommandBuffer.INSTANCE.resize(IndirectCommandBuffer.REGION_COMMAND_SIZE);
        CULLING_COUNTER.resize(1);
        REGION_INDEX_BUFFER.dispose();
        REGION_INDEX_BUFFER = new ShaderStorageBuffer(1, 16, GL15.GL_DYNAMIC_DRAW);
    }

    //TODO need fix
    public static void updateDistance(int renderDistance) {
        if (MeshResource.RENDER_DISTANCE != renderDistance) {
            MeshResource.RENDER_DISTANCE = renderDistance;
            SPACE_PARTITION_SIZE = 2 * renderDistance + 1;

            if (Minecraft.getInstance().level != null && Config.getCullChunk()) {
                THEORETICAL_REGION_QUANTITY = (int) (SPACE_PARTITION_SIZE * SPACE_PARTITION_SIZE * Minecraft.getInstance().level.getSectionsCount() * 1.2 / 256);
                MESH_MANAGER.initCapacity(THEORETICAL_REGION_QUANTITY);
            }
        }
    }

    public static int getRenderDistance() {
        return RENDER_DISTANCE;
    }

    public static int getSpacePartitionSize() {
        return SPACE_PARTITION_SIZE;
    }
}