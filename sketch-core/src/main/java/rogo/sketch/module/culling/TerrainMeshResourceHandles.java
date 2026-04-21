package rogo.sketch.module.culling;

import rogo.sketch.core.backend.BackendStorageBuffer;
import rogo.sketch.core.pipeline.kernel.FrameResourceHandle;
import rogo.sketch.core.util.KeyId;

import java.util.List;

/**
 * Core-owned terrain mesh handles introduced during the MeshResource phase-A
 * migration. These handles describe the logical terrain mesh resource owner
 * independent of the temporary mod/OpenGL backing implementation.
 */
public final class TerrainMeshResourceHandles {
    public static final FrameResourceHandle<TerrainMeshIndirectBuffer> TERRAIN_INDIRECT_COMMANDS = FrameResourceHandle.of(
            KeyId.of("sketch_render", "terrain_indirect_commands"),
            TerrainMeshIndirectBuffer.class,
            CullingModuleDescriptor.MODULE_ID,
            "culling.terrainIndirectCommands");
    public static final FrameResourceHandle<BackendStorageBuffer> TERRAIN_REGION_INDEX = FrameResourceHandle.of(
            KeyId.of("sketch_render", "terrain_region_index"),
            BackendStorageBuffer.class,
            CullingModuleDescriptor.MODULE_ID,
            "culling.terrainRegionIndex");
    public static final FrameResourceHandle<TerrainMeshCounterBuffer> TERRAIN_CULL_COUNTER = FrameResourceHandle.of(
            KeyId.of("sketch_render", "terrain_cull_counter"),
            TerrainMeshCounterBuffer.class,
            CullingModuleDescriptor.MODULE_ID,
            "culling.terrainCullCounter");
    public static final FrameResourceHandle<TerrainMeshCounterBuffer> TERRAIN_ELEMENT_COUNTER = FrameResourceHandle.of(
            KeyId.of("sketch_render", "terrain_element_counter"),
            TerrainMeshCounterBuffer.class,
            CullingModuleDescriptor.MODULE_ID,
            "culling.terrainElementCounter");
    public static final FrameResourceHandle<TerrainMeshReadbackBuffer> TERRAIN_MAX_ELEMENT_READBACK = FrameResourceHandle.of(
            KeyId.of("sketch_render", "terrain_max_element_readback"),
            TerrainMeshReadbackBuffer.class,
            CullingModuleDescriptor.MODULE_ID,
            "culling.terrainMaxElementReadback");
    public static final FrameResourceHandle<BackendStorageBuffer> TERRAIN_MESH_DATA = FrameResourceHandle.of(
            KeyId.of("sketch_render", "terrain_mesh_data"),
            BackendStorageBuffer.class,
            CullingModuleDescriptor.MODULE_ID,
            "culling.terrainMeshData");

    private TerrainMeshResourceHandles() {
    }

    public static List<FrameResourceHandle<?>> all() {
        return List.of(
                TERRAIN_INDIRECT_COMMANDS,
                TERRAIN_REGION_INDEX,
                TERRAIN_CULL_COUNTER,
                TERRAIN_ELEMENT_COUNTER,
                TERRAIN_MAX_ELEMENT_READBACK,
                TERRAIN_MESH_DATA);
    }
}
