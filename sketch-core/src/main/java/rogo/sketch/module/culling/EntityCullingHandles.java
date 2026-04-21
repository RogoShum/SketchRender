package rogo.sketch.module.culling;

import rogo.sketch.core.api.ResourceObject;
import rogo.sketch.core.pipeline.kernel.FrameResourceHandle;
import rogo.sketch.core.util.KeyId;

import java.util.List;

/**
 * Core-owned entity culling resource handles used by the stage-submit seam.
 */
public final class EntityCullingHandles {
    public static final FrameResourceHandle<ResourceObject> ENTITY_CULL_INPUT_SSBO = FrameResourceHandle.of(
            KeyId.of("sketch_render", "entity_cull_input_ssbo"),
            ResourceObject.class,
            CullingModuleDescriptor.MODULE_ID,
            "culling.entityCullInputSsbo");
    public static final FrameResourceHandle<ResourceObject> ENTITY_CULL_RESULT_SSBO = FrameResourceHandle.of(
            KeyId.of("sketch_render", "entity_cull_result_ssbo"),
            ResourceObject.class,
            CullingModuleDescriptor.MODULE_ID,
            "culling.entityCullResultSsbo");
    public static final FrameResourceHandle<ResourceObject> ENTITY_INDIRECT_ARGS = FrameResourceHandle.of(
            KeyId.of("sketch_render", "entity_indirect_args"),
            ResourceObject.class,
            CullingModuleDescriptor.MODULE_ID,
            "culling.entityIndirectArgs");

    private EntityCullingHandles() {
    }

    public static List<FrameResourceHandle<?>> all() {
        return List.of(ENTITY_CULL_INPUT_SSBO, ENTITY_CULL_RESULT_SSBO, ENTITY_INDIRECT_ARGS);
    }
}
