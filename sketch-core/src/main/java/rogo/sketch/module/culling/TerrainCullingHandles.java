package rogo.sketch.module.culling;

import rogo.sketch.core.api.ResourceObject;
import rogo.sketch.core.pipeline.kernel.FrameResourceHandle;
import rogo.sketch.core.resource.vision.Texture;
import rogo.sketch.core.util.KeyId;
import org.joml.Vector3i;

import java.util.List;

/**
 * Core-owned terrain/Hi-Z resource handles used by the culling module seam.
 */
public final class TerrainCullingHandles {
    public static final FrameResourceHandle<Texture> HIZ_TEXTURE = FrameResourceHandle.of(
            KeyId.of("sketch_render", "hiz_texture"),
            Texture.class,
            CullingModuleDescriptor.MODULE_ID,
            "culling.hizTexture");
    public static final FrameResourceHandle<Vector3i[]> HIZ_DEPTH_INFO = FrameResourceHandle.of(
            KeyId.of("sketch_render", "hiz_depth_info"),
            Vector3i[].class,
            CullingModuleDescriptor.MODULE_ID,
            "culling.hizDepthInfo");
    public static final FrameResourceHandle<ResourceObject> TERRAIN_CULL_INPUT_SSBO = FrameResourceHandle.of(
            KeyId.of("sketch_render", "terrain_cull_input_ssbo"),
            ResourceObject.class,
            CullingModuleDescriptor.MODULE_ID,
            "culling.terrainCullInputSsbo");
    public static final FrameResourceHandle<ResourceObject> TERRAIN_CULL_RESULT_SSBO = FrameResourceHandle.of(
            KeyId.of("sketch_render", "terrain_cull_result_ssbo"),
            ResourceObject.class,
            CullingModuleDescriptor.MODULE_ID,
            "culling.terrainCullResultSsbo");
    public static final FrameResourceHandle<ResourceObject> TERRAIN_INDIRECT_ARGS = FrameResourceHandle.of(
            KeyId.of("sketch_render", "terrain_indirect_args"),
            ResourceObject.class,
            CullingModuleDescriptor.MODULE_ID,
            "culling.terrainIndirectArgs");

    private TerrainCullingHandles() {
    }

    public static List<FrameResourceHandle<?>> all() {
        return List.of(HIZ_TEXTURE, HIZ_DEPTH_INFO, TERRAIN_CULL_INPUT_SSBO, TERRAIN_CULL_RESULT_SSBO, TERRAIN_INDIRECT_ARGS);
    }
}
